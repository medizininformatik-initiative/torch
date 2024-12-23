package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.CdsStructureDefinitionHandler;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NoSuchElementException;
import java.util.Objects;

import static de.medizininformatikinitiative.torch.util.FhirUtil.createAbsentReasonExtension;

/**
 * Redaction operations on copied Ressources based on the Structuredefinition
 */
public class Redaction {
    private static final Logger logger = LoggerFactory.getLogger(Redaction.class);
    private final CdsStructureDefinitionHandler CDS;
    private final Slicing slicing;

    /**
     * Constructor for Redaction
     *
     * @param cds CDSStructureDefinitionHandler
     */
    public Redaction(CdsStructureDefinitionHandler cds, Slicing slicing) {
        this.CDS = cds;
        this.slicing = slicing;
    }

    /**
     * @param base - Resource after Data Selection and Extraction process with possibly missing required fields
     * @return Base with fulfilled required fields using Data Absent Reasons
     */
    public Base redact(Base base) {
        StructureDefinition structureDefinition;
        String elementID;
        if (base instanceof DomainResource resource) {
            if (resource.hasMeta()) {

                structureDefinition = CDS.getDefinition(resource.getMeta().getProfile());
                if (structureDefinition == null) {
                    logger.error("Unknown Profile in Resource {} {}", resource.getResourceType(), resource.getId());
                    throw new RuntimeException("Trying to redact Base Element that is not a KDS resource");
                }
                resource.getMeta().setProfile(CDS.legalUrls(resource.getMeta().getProfile()));
                elementID = String.valueOf(resource.getResourceType());
                return redact(base, elementID, 0, structureDefinition);
            }
        }
        throw new RuntimeException("Trying to redact Base Element that is not a resource");
    }


    /**
     * Executes redaction operation on the given base element recursively.
     *
     * @param base                Base to be redacted (e.g. a Ressource or an Element)
     * @param elementID           "Element ID of parent currently handled initially isEmpty String"
     * @param recursion           "Resurcion depth (for debug purposes)
     * @param structureDefinition Structure definition of the Resource.
     * @return redacted Base
     */
    public Base redact(Base base, String elementID, int recursion, StructureDefinition structureDefinition) {

        recursion++;
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();
        ElementDefinition definition = snapshot.getElementById(elementID);

        if (definition == null) {
            throw new NoSuchElementException("Definiton unknown for" + base.fhirType() + "in Element ID " + elementID + "in StructureDefinition " + structureDefinition.getUrl());

        } else if (definition.hasSlicing()) {
            ElementDefinition slicedElement = slicing.checkSlicing(base, elementID, structureDefinition);

            if (slicedElement != null) {
                logger.trace("Sliced Element {}", slicedElement.getName());
                if (slicedElement.hasId()) {
                    elementID = slicedElement.getIdElement().toString();

                } else {
                    throw new NoSuchElementException("Sliced Element has no valid ID " + elementID + "in StructureDefinition " + structureDefinition.getUrl());
                }

            } else {
                base.children().forEach(child -> {
                    child.getValues().forEach(value -> {
                        base.removeChild(child.getName(), value);
                    });
                });
                if (definition.getMin() > 0) {
                    base.setProperty("extension", createAbsentReasonExtension("masked"));
                }

                return base;
            }
        }

        int finalRecursion = recursion;
        String finalElementID = elementID;

        base.children().forEach(child -> {

            String childID = finalElementID + "." + child.getName();
            ElementDefinition childDefinition = null;
            logger.trace("Child to be handled {}", childID);
            String type = "";
            int min = 0;
            try {
                childDefinition = snapshot.getElementById(childID);
                type = childDefinition.getType().getFirst().getWorkingCode();
                min = childDefinition.getMin();
            } catch (NoSuchElementException | NullPointerException e) {
                //Case Element not fully defined in Structure Definition, fallback to Base Resource
                type = child.getTypeCode();
                min = child.getMinCardinality();
                logger.trace("{} Standard Type {} with cardinality {} ", child.getName(), type, min);
            }
            if (child.hasValues() && childDefinition != null) {

                String finalType = type;
                //List Handling
                int finalMin = min;
                child.getValues().forEach(value -> {

                    if (finalMin > 0 && value.isEmpty()) {
                        Element element = HapiFactory.create(finalType).addExtension(createAbsentReasonExtension("masked"));
                        base.setProperty(child.getName(), element);
                    } else if (!value.isPrimitive()) {

                        redact(value, childID, finalRecursion, structureDefinition);
                    }
                });


            } else {
                if (min > 0 && !Objects.equals(child.getTypeCode(), "Extension")) {
                    //TODO Backbone Element Handling and nested Extensions
                    Element element = HapiFactory.create(type).addExtension(createAbsentReasonExtension("masked"));
                    base.setProperty(child.getName(), element);
                }
            }


        });
        return base;
    }


}
