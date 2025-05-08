package de.medizininformatikinitiative.torch.util;


import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.management.ExtractionRedactionWrapper;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.medizininformatikinitiative.torch.util.FhirUtil.createAbsentReasonExtension;

/**
 * Redaction operations on copied Ressources based on the Structuredefinition
 */
public class Redaction {
    private static final Logger logger = LoggerFactory.getLogger(Redaction.class);
    private final StructureDefinitionHandler CDS;

    /**
     * Constructor for Redaction
     *
     * @param cds StructureDefinitionHandler
     */
    public Redaction(StructureDefinitionHandler cds) {
        this.CDS = cds;
    }

    /**
     * @param base    - Resource after Data Selection and Extraction process with possibly missing required fields
     * @param wrapper ExtractionRedactionWrapper containing the resource, profiles and reference information
     *                relevant for redaction
     * @return Base with fulfilled required fields using Data Absent Reasons
     */
    public Base redact(ExtractionRedactionWrapper wrapper) {
        DomainResource resource = wrapper.resource();
        Map<String, Set<String>> references = wrapper.references();

        if (resource.hasMeta()) {
            Meta meta = resource.getMeta();
            List<CanonicalType> resourceProfiles = List.of();
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
                resourceProfiles = wrapper.profiles().stream().map(CanonicalType::new).collect(Collectors.toList());
            }


            Set<StructureDefinition> structureDefinitions = CDS.getDefinitions(wrapper.profiles());
            if (structureDefinitions.isEmpty()) {
                logger.error("Unknown Profile in Resource {} {}", resource.getResourceType(), resource.getId());
                throw new RuntimeException("Tryng to handle unknown profiles: " + wrapper.profiles());
            }

            meta.setProfile(resourceProfiles);

            String elementID = String.valueOf(resource.getResourceType());
            return this.redact((Base) resource, Set.of(elementID), 0, structureDefinitions, references);
        }
        throw new RuntimeException("Trying to redact Resource without Meta");
    }


    /**
     * Executes redaction operation on the given base element recursively.
     *
     * @param base                 Base to be redacted (e.g. a Ressource or an Element)
     * @param elementIDs           "Element IDs of parent currently handled initially isEmpty String"
     * @param recursion            "Resurcion depth (for debug purposes)
     * @param structureDefinitions Structure definition of the Resource.
     * @param references
     * @return redacted Base
     */
    public <T> Base redact(Base base, Set<String> elementIDs, int recursion, Set<StructureDefinition> structureDefinitions, Map<String, Set<String>> references) {

        recursion++;
        Set<StructureDefinition.StructureDefinitionSnapshotComponent> snapshots = structureDefinitions.stream().map(StructureDefinition::getSnapshot).collect(Collectors.toSet());
        Set<ElementDefinition> definitions = elementIDs.stream().map(elementId -> snapshots.stream().map(snapshot -> snapshot.getElementById(elementId)).filter(Objects::nonNull).collect(Collectors.toSet())).flatMap(Set::stream).collect(Collectors.toSet());
        if (definitions.isEmpty()) {
            throw new NoSuchElementException("Definiton unknown for" + base.fhirType() + "in Element ID " + elementIDs + "in StructureDefinition " + structureDefinitions.stream().map(StructureDefinition::getUrl));
        }
        Set<ElementDefinition> unslicedElements = definitions.stream().filter(definition -> !definition.hasSlicing()).collect(Collectors.toSet());
        Set<ElementDefinition> slicedElements = Set.of();
        if (!unslicedElements.containsAll(definitions)) {
            slicedElements = Slicing.checkSlicing(base, elementIDs, snapshots).stream().filter(ElementDefinition::hasId).collect(Collectors.toSet());

            /* Slicing could not be resolved, but all elements should be sliced*/
            if (slicedElements.isEmpty() && unslicedElements.isEmpty()) {
                base.children().forEach(child -> {
                    child.getValues().forEach(value -> {
                        base.removeChild(child.getName(), value);
                    });
                });
                if (definitions.stream().anyMatch(definition -> definition.getMin() > 0)) {
                    base.setProperty("extension", createAbsentReasonExtension("masked"));
                }

                return base;
            }
        }

        int finalRecursion = recursion;

        Set<String> finalElementIDs = Stream.concat(unslicedElements.stream(), slicedElements.stream()).map(ElementDefinition::getId).collect(Collectors.toSet());
        base.children().forEach(child -> {


            Set<String> childIDs = finalElementIDs.stream().map(elementId -> elementId + "." + child.getName()).collect(Collectors.toSet());
            Set<ElementDefinition> childDefinitions = Set.of();
            logger.trace("Children to be handled {}", childIDs);
            String type = "";
            int min = 0;
            try {
                childDefinitions = childIDs.stream().map(elementId -> snapshots.stream().map(snapshot -> snapshot.getElementById(elementId)) // May return null
                        .filter(Objects::nonNull) // Keep only non-null elements
                        .collect(Collectors.toSet()) // Collect valid elements
                ).flatMap(Set::stream).collect(Collectors.toSet());

                // Ensure childDefinitions is not empty before extracting elements
                if (childDefinitions.isEmpty()) {
                    throw new NoSuchElementException("No valid elements found in snapshots.");
                }

                // Get type if available, otherwise fallback
                try {
                    type = childDefinitions.stream().flatMap(def -> def.getType().stream()) // Get all TypeRefComponents
                            .map(ElementDefinition.TypeRefComponent::getWorkingCode) // Extract type codes
                            .findFirst().orElseThrow(() -> new NoSuchElementException("No valid type found in any definition"));
                } catch (NoSuchElementException e) {


                    type = child.getTypeCode(); // Fallback only for type
                    logger.trace("{} Standard Type fallback to {} ", child.getName(), type);
                }

                // Get minimum cardinality if available, otherwise fallback
                try {
                    min = childDefinitions.stream().map(ElementDefinition::getMin) // Extract min values
                            .max(Integer::compareTo) // Get the largest one
                            .orElseThrow(() -> new NoSuchElementException("No minimum cardinality found in any definition"));
                } catch (NoSuchElementException e) {

                    min = child.getMinCardinality(); // Fallback only for min
                    logger.trace("{} Standard Cardinality fallback to {} ", child.getName(), min);
                }

            } catch (NoSuchElementException | NullPointerException e) {
                // Case: No valid ElementDefinition at all → full fallback
                type = child.getTypeCode();
                min = child.getMinCardinality();
                logger.trace("{} Standard Type {} with cardinality {} ", child.getName(), type, min);
            }
            if (child.hasValues() && !childDefinitions.isEmpty()) {


                String finalType = type;
                //List Handling
                int finalMin = min;
                if (finalType.equals("Reference")) {

                    List<Base> childReferenceField = child.getValues();

                    Set<String> legalReferences = childIDs.stream().map(references::get).filter(Objects::nonNull).collect(Collectors.toSet()).stream().flatMap(Set::stream).collect(Collectors.toSet());

                    childReferenceField.forEach(referenceValue -> {
                        String reference = ((Reference) referenceValue).getReference();
                        if (!legalReferences.contains(reference)) {
                            referenceValue.setProperty("reference", HapiFactory.create("string").addExtension(createAbsentReasonExtension("unknown")));
                        }

                    });

                }
                child.getValues().forEach(value -> {

                    if (finalMin > 0 && value.isEmpty()) {
                        Element element = HapiFactory.create(finalType).addExtension(createAbsentReasonExtension("masked"));
                        base.setProperty(child.getName(), element);
                    } else if (!value.isPrimitive()) {

                        redact(value, childIDs, finalRecursion, structureDefinitions, references);
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
                        ResourceUtils.setField(base, fieldName, createAbsentReasonExtension("masked"));
                    } else {
                        try {
                            Element element = HapiFactory.create(type).addExtension(createAbsentReasonExtension("masked"));
                            base.setProperty(child.getName(), element);
                        } catch (FHIRException e) {
                            logger.warn("Unresolvable elementID {} in field  {} Standard Type {} with cardinality {} ", finalElementIDs, child.getName(), type, min);
                        }
                    }


                }
            }
        });
        return base;
    }
}
