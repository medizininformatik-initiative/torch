package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.management.ExtractionRedactionWrapper;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Element;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Property;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
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

            Optional<StructureDefinition> structureDefinition = structureDefinitionHandler.getDefinition(wrapper.profiles());
            if (structureDefinition.isEmpty()) {
                logger.error("Unknown Profile in Resource {} {}", resource.getResourceType(), resource.getId());
                throw new RuntimeException("Trying to handle unknown profiles: " + wrapper.profiles());
            }

            meta.setProfile(resourceProfiles);

            return this.redact(resource, String.valueOf(resource.getResourceType()), 0, Definition.fromStructureDefinition(structureDefinition.get()), references);
        }
        throw new RuntimeException("Trying to redact Resource without Meta");
    }

    /**
     * Executes redaction operation on the given base element recursively.
     *
     * @param base                Base to be redacted (e.g. a Resource or an Element)
     * @param elementId           Element IDs of parent currently handled; initially the resource type"
     * @param recursion           recursion depth (for debug purposes)
     * @param definition Structure definition of the Resource.
     * @param references          Allowed references
     * @return redacted Base
     */
    private Base redact(Base base, String elementId, int recursion, Definition definition, Map<String, Set<String>> references) {
        ElementDefinition elementDefinition = definition.elementDefinitionById(elementId);
        if (elementDefinition == null) {
            throw new NoSuchElementException("Definition unknown for " + base.fhirType() + " in Element ID " + elementId + " in StructureDefinition " + definition.structureDefinition().getUrl());
        }

        String finalElementId;
        if (elementDefinition.hasSlicing()) {
            ElementDefinition slicedElementDefinition = Slicing.checkSlicing(base, elementId, definition);

            /* Slicing could not be resolved, but all elements should be sliced */
            if (slicedElementDefinition == null) {
                base.children().forEach(child -> child.getValues().forEach(value -> base.removeChild(child.getName(), value)));
                if (elementDefinition.getMin() > 0) {
                    base.setProperty("extension", createAbsentReasonExtension(MASKED));
                }

                return base;
            }
            finalElementId = slicedElementDefinition.getId();
        } else {
            finalElementId = elementDefinition.getId();
        }

        base.children().forEach(child -> {
            String childId = finalElementId + "." + child.getName();

            ElementDefinition childDefinition = definition.elementDefinitionById(childId);
            String type;
            int min;

            if (childDefinition == null) {
                type = child.getTypeCode();
                min = child.getMinCardinality();
            } else {
                type = childDefinition.getType().stream()
                        .map(ElementDefinition.TypeRefComponent::getWorkingCode)
                        .findFirst().orElse(child.getTypeCode());
                min = childDefinition.getMin();
            }

            if (child.hasValues() && childDefinition != null) {
                if ("Reference".equals(type)) {
                    Set<String> found = references.get(childId);
                    handleReference(child, found == null ? Set.of() : found);
                }

                child.getValues().forEach(value -> {
                    if (value.isEmpty() && min > 0) {
                        Element element = HapiFactory.create(type).addExtension(createAbsentReasonExtension(MASKED));
                        base.setProperty(child.getName(), element);
                    } else if (!value.isPrimitive()) {
                        redact(value, childId, recursion + 1, definition, references);
                    }
                });

            } else {
                if (min > 0 && !Objects.equals(child.getTypeCode(), "Extension")) {
                    /*
                    TODO find a good way to only work with reflection here?
                    Potential issue is primitive types don't have addExtension, somehow generic setField results in dem being set.
                    E.g. RecordedDate in Condition
                     */
                    if (Objects.equals(type, "BackboneElement")) {
                        String fieldName = child.getName();
                        ResourceUtils.setField(base, fieldName, createAbsentReasonExtension(MASKED));
                    } else {
                        try {
                            Element element = HapiFactory.create(type).addExtension(createAbsentReasonExtension(MASKED));
                            base.setProperty(child.getName(), element);
                        } catch (FHIRException e) {
                            logger.warn("Unresolvable elementID {} in field  {} Standard Type {} with cardinality {} ", finalElementId, child.getName(), type, min);
                        }
                    }
                }
            }
        });

        return base;
    }

    private static void handleReference(Property child, Set<String> references) {
        child.getValues().forEach(referenceValue -> {
            if (((Reference) referenceValue).hasReference() && !references.contains(((Reference) referenceValue).getReference())) {
                referenceValue.setProperty("reference", HapiFactory.create("string").addExtension(createAbsentReasonExtension("masked")));
            }
            referenceValue.children().forEach(childValue -> {
                String name = childValue.getName();
                if (!"reference".equals(name) && !"extension".equals(name) && childValue.hasValues()) {
                    childValue.getValues().forEach(value -> referenceValue.removeChild(name, value));
                }
            });
        });
    }
}
