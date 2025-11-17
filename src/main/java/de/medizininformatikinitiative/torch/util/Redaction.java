package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.exceptions.RedactionException;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.management.ExtractionRedactionWrapper;
import de.medizininformatikinitiative.torch.model.management.MultiElementContext;
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
import org.springframework.stereotype.Component;

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
@Component
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


    /**
     * Redacts disallowed {@link Reference} values in the given property.
     * <p>
     * If a reference is not in the provided {@code references} set, it is replaced
     * with a placeholder containing an absent reason extension, and all non-reference,
     * non-extension child elements are removed.
     * </p>
     *
     * @param child      the property containing reference values
     * @param references the set of allowed reference strings
     */
    private void handleReference(Property child, Set<String> references) {
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

    private static List<String> getTypes(Property child, List<String> collectedTypes) {
        return collectedTypes.isEmpty() ? List.of(child.getTypeCode().split("\\|")) : collectedTypes;
    }

    /**
     * Redacts a FHIR resource using structure definitions and allowed references.
     * <p>
     * Validates that required profiles are present and known. If valid, structure definitions
     * are resolved and used to redact the resource, filling required fields with Data Absent Reasons
     * where necessary.
     * </p>
     *
     * @param wrapper the wrapper containing the resource, profiles, and allowed references
     * @return the redacted resource with required fields fulfilled
     * @throws RuntimeException if required profiles are missing or unknown, or if meta is absent
     */
    public DomainResource redact(ExtractionRedactionWrapper wrapper) throws RedactionException {
        DomainResource resource = wrapper.resource();
        Meta meta = resource.getMeta();
        List<CanonicalType> resourceProfiles;
        if (!resource.getResourceType().toString().equals("Patient")) {
            // Convert resource profiles to a list of strings
            resourceProfiles = meta.getProfile().stream().filter(profile -> wrapper.profiles().stream().anyMatch(wrapperProfile -> profile.toString().contains(wrapperProfile))).toList();
            List<CanonicalType> finalResourceProfiles = resourceProfiles;
            Set<String> validProfiles = wrapper.profiles().stream().filter(profile -> finalResourceProfiles.stream().anyMatch(resourceProfile -> resourceProfile.toString().contains(profile))).collect(Collectors.toSet());

            if (!validProfiles.equals(wrapper.profiles())) {
                logger.error("Missing Profiles in Resource {} {}: {} for requested profiles {}", resource.getResourceType(), resource.getId(), resourceProfiles, wrapper.profiles());
                throw new RedactionException("Resource" + resource.getResourceType() + " " + resource.getId() + " is missing required profiles: " + resourceProfiles);
            }
        } else {
            resourceProfiles = wrapper.profiles().stream().map(CanonicalType::new).toList();
        }
        List<CompiledStructureDefinition> definitions = structureDefinitionHandler.getDefinitions(wrapper.profiles());
        if (definitions.isEmpty()) {
            logger.error("Unknown Profile in Resource {} {}", resource.getResourceType(), resource.getId());
            throw new RedactionException("Trying to handle unknown profiles: " + wrapper.profiles());
        }
        meta.setProfile(resourceProfiles);
        this.redact(resource, new MultiElementContext(String.valueOf(resource.getResourceType()), definitions), wrapper.references());
        return resource;
    }

    /**
     * Handles redaction of extensions for the given FHIR element:
     * <ul>
     *   <li>Removes extensions not allowed by slicing rules</li>
     *   <li>Recursively redacts known extensions according to structure definitions</li>
     * </ul>
     *  @param base    the FHIR element whose extensions are to be validated and redacted
     *
     * @param context    the element context used to evaluate and process extensions
     * @param references Map of allowed references
     */
    private void redactExtensions(Base base, MultiElementContext context, Map<String, Set<String>> references) {
        MultiElementContext extensionsContext = context.descend(EXTENSION);
        removeUnknownExtensions(base, extensionsContext);
        redactKnownExtensions(base, extensionsContext, references);
    }

    /**
     * Removes extensions from the given FHIR element that are not allowed by slicing rules.
     *
     * @param base    the FHIR element from which unknown extensions should be removed
     * @param context the context containing allowed extensions for validation
     */
    private void removeUnknownExtensions(Base base, MultiElementContext context) {
        getExtensions(base).stream().filter(context::shouldRedactExtension).forEach(extension -> base.removeChild(EXTENSION, extension));
    }

    /**
     * Redacts known extensions of the given FHIR element using the provided structure definitions.
     *
     * @param base       the FHIR element whose remaining extensions should be processed
     * @param context    the context for redacting extensions
     * @param references Map of allowed references
     */
    private void redactKnownExtensions(Base base, MultiElementContext context, Map<String, Set<String>> references) {
        getExtensions(base).forEach(extension -> redactChildren(extension, context, references));
    }

    private List<Extension> getExtensions(Base base) {
        return switch (base) {
            case Element element when element.hasExtension() -> List.copyOf(element.getExtension());
            case DomainResource domainResource when domainResource.hasExtension() ->
                    List.copyOf(domainResource.getExtension());
            default -> List.of();
        };
    }

    /**
     * Recursively redacts the given FHIR element based on its structure definitions and allowed references.
     * <p>
     * Handles slicing logic, removes unknown or disallowed children, and sets Data Absent Reason extensions
     * for required elements when necessary.
     * </p>
     *
     * @param dataElement the FHIR {@link Base} element to redact
     * @param context     element ID and associated structure definitions
     * @param references  Map of allowed references
     */
    private void redact(Base dataElement, MultiElementContext context, Map<String, Set<String>> references) {
        handleSlicing(dataElement, context).ifPresent(updatedContext -> {
            redactExtensions(dataElement, updatedContext, references);
            if (!dataElement.isPrimitive()) {
                redactChildren(dataElement, updatedContext, references);
            }
        });
    }

    /**
     * Handles slicing resolution for the given element and context.
     * If slicing is applicable and no match is found, redacts the element if required.
     * Unsliced contexts mixed with sliced contexts get passed through to preserve behaviour.
     *
     * @return updated ElementContexts if valid, otherwise Optional empty if element was removed due to slicing
     */
    private Optional<MultiElementContext> handleSlicing(Base dataElement, MultiElementContext context) {
        if (dataElement instanceof Extension || !context.hasSlicing()) {
            return Optional.of(context);
        }
        return context.resolveSlices(dataElement, slices -> {
            if (slices.isEmpty()) {
                removeAllChildren(dataElement);
                if (context.required()) {
                    dataElement.setProperty(EXTENSION, createAbsentReasonExtension(MASKED));
                }
                return true;
            }
            return false;
        });
    }

    private void removeAllChildren(Base base) {
        base.children().stream().flatMap(child -> child.getValues().stream().map(value -> Map.entry(child.getName(), value))).forEach(entry -> base.removeChild(entry.getKey(), entry.getValue()));
    }

    /**
     * Redacts the children of a given FHIR element based on the provided structure definition.
     * <p>
     * Constructs elementids from {@code baseid} and child name.
     * Attempts to look up the corresponding {@link ElementDefinition} from the given {@code definition}.
     * If a definition exists, it sets the type and minimum cardinality with the ones defined there;
     * otherwise, it falls back to the values derived directly from the child element itself.
     *
     * @param baseElement element whose children should be redacted
     * @param contexts    element ID and associated structure definitions
     * @param references  Map of allowed references
     */
    private void redactChildren(Base baseElement, MultiElementContext contexts, Map<String, Set<String>> references) {

        baseElement.children().forEach(child -> {
            MultiElementContext childContexts = contexts.descend(child.getName());
            List<String> types = getTypes(child, childContexts.workingCodes());

            if (child.hasValues()) {
                if (types.stream().anyMatch(type -> type.contains("Reference"))) {

                    handleReference(child, childContexts.allowedReferences(references));
                }
                for (Base value : child.getValues()) {
                    redact(value, childContexts, references);
                }
            } else if (child.getMinCardinality() > 0 || childContexts.required()) {
                addDataAbsentReason(baseElement, child, types.getFirst());
            }
        });
    }

    /**
     * Adds a DataAbsentReason for a child property of a base.
     *
     * @param base  the parent of the child
     * @param child property without values to be checked
     * @param type  type of the child to be handled
     */
    private void addDataAbsentReason(Base base, Property child, String type) {
        type = type.replaceFirst("^[^(|]*[(|]", "");
        try {
            if ("BackboneElement".equals(type)) {
                ResourceUtils.setField(base, child.getName(), createAbsentReasonExtension(MASKED));
            } else {
                Element element = HapiFactory.create(type).addExtension(createAbsentReasonExtension(MASKED));
                base.setProperty(child.getName(), element);
            }
        } catch (FHIRException e) {
            logger.warn("Unresolvable elementID {} in field {} Type {} ", base.fhirType(), child.getName(), type);
        }
    }
}
