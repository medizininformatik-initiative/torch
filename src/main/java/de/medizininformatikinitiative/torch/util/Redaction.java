package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.management.ExtractionRedactionWrapper;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Element;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Property;
import org.hl7.fhir.r4.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static de.medizininformatikinitiative.torch.util.FhirUtil.createAbsentReasonExtension;
import static java.util.Objects.requireNonNull;

/**
 * Redaction operations on copied Resources based on the StructureDefinition
 */
public class Redaction {

    private static final Logger logger = LoggerFactory.getLogger(Redaction.class);
    private static final String MASKED = "masked";
    private static final Extension ABSENT_REASON_EXTENSION = createAbsentReasonExtension(MASKED);
    private static final String EXTENSION = "extension";
    private static final String REFERENCE = "reference";

    private final StructureDefinitionHandler structureDefinitionHandler;

    /**
     * Constructor for Redaction
     *
     * @param structureDefinitionHandler StructureDefinitionHandler
     */
    public Redaction(StructureDefinitionHandler structureDefinitionHandler) {
        this.structureDefinitionHandler = requireNonNull(structureDefinitionHandler);
    }

    private static void handleReference(Property child, Set<String> references) {
        child.getValues().forEach(referenceValue -> {
            if (referenceValue instanceof Reference reference && reference.hasReference() && !references.contains(reference.getReference())) {
                referenceValue.setProperty(REFERENCE, HapiFactory.create("string").addExtension(ABSENT_REASON_EXTENSION));
                referenceValue.children().forEach(childValue -> {
                    String name = childValue.getName();
                    if (!REFERENCE.equals(name) && !EXTENSION.equals(name) && childValue.hasValues()) {
                        childValue.getValues().forEach(value -> referenceValue.removeChild(name, value));
                    }
                });
            }

        });
    }

    /**
     * @param wrapper ExtractionRedactionWrapper containing the resource, profiles and reference information
     *                relevant for redaction
     * @return Base with fulfilled required fields using Data Absent Reasons
     */
    public Base redact(ExtractionRedactionWrapper wrapper) {
        DomainResource resource = wrapper.resource();
        Map<String, Set<String>> references = wrapper.references();

        if (resource.hasMeta()) {
            Meta meta = resource.getMeta();
            List<CanonicalType> resourceProfiles;
            if (!resource.getResourceType().toString().equals("Patient")) {
                // Convert resource profiles to a list of strings
                resourceProfiles = meta.getProfile().stream().filter(profile -> wrapper.profiles().stream().anyMatch(wrapperProfile -> profile.toString().contains(wrapperProfile))).toList();
                List<CanonicalType> finalResourceProfiles = resourceProfiles;
                Set<String> validProfiles = wrapper.profiles().stream().filter(profile -> finalResourceProfiles.stream().anyMatch(resourceProfile -> resourceProfile.toString().contains(profile))).collect(Collectors.toSet());

                if (!validProfiles.equals(wrapper.profiles())) {
                    logger.error("Missing Profiles in Resource {} {}: {} for requested profiles {}", resource.getResourceType(), resource.getId(), resourceProfiles, wrapper.profiles());
                    throw new RuntimeException("Resource is missing required profiles: " + resourceProfiles);
                }
            } else {
                resourceProfiles = wrapper.profiles().stream().map(CanonicalType::new).toList();
            }
            Optional<CompiledStructureDefinition> definition = structureDefinitionHandler.getDefinition(wrapper.profiles());
            if (definition.isEmpty()) {
                logger.error("Unknown Profile in Resource {} {}", resource.getResourceType(), resource.getId());
                throw new RuntimeException("Trying to handle unknown profiles: " + wrapper.profiles());
            }
            meta.setProfile(resourceProfiles);
            return this.redact(resource, String.valueOf(resource.getResourceType()), definition.get(), references);
        }
        throw new RuntimeException("Trying to redact Resource without Meta");
    }

    /**
     * Executes redaction operation on the given base element recursively.
     *
     * @param base       Base to be redacted (e.g. a Resource or an Element)
     * @param elementId  Element IDs of parent currently handled; initially the resource type
     * @param definition Structure definition of the Resource.
     * @param references Allowed references
     * @return redacted Base
     */
    private Base redact(Base base, String elementId, CompiledStructureDefinition definition, Map<String, Set<String>> references) {
        ElementDefinition elementDefinition = definition.elementDefinitionById(elementId);

        redactUnknownExtensions(base, elementId, definition);

        if (base.isPrimitive()) {
            return base;
        }
        // Handle slicing and early exit when unknown slice
        //Only applicable on known Elements
        if (!(base instanceof Extension) && elementDefinition != null && elementDefinition.hasSlicing()) {
            ElementDefinition sliced = Slicing.checkSlicing(base, elementId, definition);
            if (sliced == null) {
                removeAllChildren(base);
                if (elementDefinition.getMin() > 0) {
                    base.setProperty(EXTENSION, createAbsentReasonExtension(MASKED));
                }
                return base;
            }
            elementId = sliced.getId();
        }

        redactChildren(base, elementId, definition, references);

        return base;
    }

    /**
     * Checks for extension legality via slicing and explicit test for data-absent-reason system.
     *
     * @param base       to be handled
     * @param elementId  of base to be handled
     * @param definition applied to the base
     */
    private void redactUnknownExtensions(Base base, String elementId, CompiledStructureDefinition definition) {
        getExtensions(base).stream().filter(extension -> shouldRedactExtension(extension, elementId, definition)).forEach(extension -> base.removeChild(EXTENSION, extension));
    }

    private List<Extension> getExtensions(Base base) {
        return switch (base) {
            case Element element when element.hasExtension() -> List.copyOf(element.getExtension());
            case DomainResource domainResource when domainResource.hasExtension() ->
                    List.copyOf(domainResource.getExtension());
            default -> List.of();
        };
    }

    private boolean shouldRedactExtension(Extension extension, String elementId, CompiledStructureDefinition definition) {
        return !"http://hl7.org/fhir/StructureDefinition/data-absent-reason".equals(extension.getUrl()) && !isKnownExtension(extension, elementId, definition);
    }

    private boolean isKnownExtension(Extension extension, String elementId, CompiledStructureDefinition definition) {
        ElementDefinition sliced = Slicing.checkSlicing(extension, elementId + ".extension", definition);
        return sliced != null;
    }

    private void removeAllChildren(Base base) {
        base.children().forEach(child -> child.getValues().forEach(value -> base.removeChild(child.getName(), value)));
    }

    /**
     * Redacts the children of a given FHIR element based on the provided structure definition.
     * <p>
     * Constructs elementids from {@code baseid} and child name.
     * Attempts to look up the corresponding {@link ElementDefinition} from the given {@code definition}.
     * If a definition exists, it sets the type and minimum cardinality with the ones defined there;
     * otherwise, it falls back to the values derived directly from the child element itself.
     *
     * @param base       base whose children should be redacted
     * @param baseId     ElementId of the base
     * @param definition StructureDefinition to be applied
     * @param references Allowed references
     */
    private void redactChildren(Base base, String baseId, CompiledStructureDefinition definition, Map<String, Set<String>> references) {
        base.children().forEach(child -> {
            String childId = baseId + "." + child.getName();
            ElementDefinition childDef = definition.elementDefinitionById(childId);

            List<String> types = List.of(child.getTypeCode().split("\\|"));
            int min = child.getMinCardinality();

            if (childDef != null) {
                List<String> collectedTypes = childDef.getType().stream()
                        .map(ElementDefinition.TypeRefComponent::getWorkingCode).toList();

                types = collectedTypes.isEmpty() ? types : collectedTypes;
                min = childDef.getMin();
            }

            if (child.hasValues()) {
                if (types.stream().anyMatch(type -> type.contains("Reference"))) {
                    Set<String> allowedRefs = references.getOrDefault(childId, Set.of());
                    handleReference(child, allowedRefs);
                }
                for (Base value : child.getValues()) {
                    redact(value, childId, definition, references);
                }
            } else {

                if (min > 0) {
                    addDataAbsentReason(base, child, types.getFirst(), baseId);
                }
            }
        });
    }

    /**
     * Adds a DataAbsentReason for a child property of a base.
     *
     * @param base     the parent of the child
     * @param child    property without values to be checked
     * @param type     type of the child to be handled
     * @param parentId elementId of the parent
     */
    private void addDataAbsentReason(Base base, Property child, String type, String parentId) {
        type = type.replaceFirst("^[^(|]*[(|]", "");
        try {
            if ("BackboneElement".equals(type)) {
                ResourceUtils.setField(base, child.getName(), createAbsentReasonExtension(MASKED));
            } else {
                Element element = HapiFactory.create(type).addExtension(createAbsentReasonExtension(MASKED));
                base.setProperty(child.getName(), element);
            }
        } catch (FHIRException e) {
            logger.warn("Unresolvable elementID {} in field {} Type {} ", parentId, child.getName(), type);
        }
    }

}
