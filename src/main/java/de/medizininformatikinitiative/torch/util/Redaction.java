package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.CdsStructureDefinitionHandler;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static de.medizininformatikinitiative.torch.util.FhirUtil.createAbsentReasonExtension;

public class Redaction {


    private static final Logger logger = LoggerFactory.getLogger(Redaction.class);
    private final Factory factory;
    private final CdsStructureDefinitionHandler CDS;
    Slicing slicing;

    /**
     * Constructor for Redaction
     *
     * @param cds CDSStructureDefinitionHandler
     */
    public Redaction(CdsStructureDefinitionHandler cds, Slicing slicing) {
        this.CDS = cds;
        factory = new Factory();
        this.slicing = slicing;
    }

    /**
     * @param base - Resource after Data Selection and Extraction process with possibly missing required fields
     * @return Base with fulfilled required fields using Data Absent Reasons
     */
    public Base redact(Base base) {
        /*
         * Check if the base is a DomainResource and if it has a profile. Used for initial redaction.
         */
        StructureDefinition structureDefinition;
        String elementID = "";
        if (base instanceof DomainResource resource) {
            if (resource.hasMeta()) {

                structureDefinition = CDS.getDefinition(resource.getMeta().getProfile());
                if (structureDefinition == null) {
                    logger.error("Unknown Profile in Resource {} {}", resource.getResourceType(), resource.getId());
                    throw new RuntimeException("Trying to Redact Base Element that is not a ressource");
                }

                elementID = String.valueOf(resource.getResourceType());
                return redact(base, elementID, 0, structureDefinition);
            }
        }
        return null;
    }


    /**
     * Executes redaction operation on the given base element recursively.
     *
     * @param base                Base to be redacted (e.g. a Ressource or an Element)
     * @param elementID           "Element ID of parent currently handled initially empty String"
     * @param recursion           "Resurcion depth (for debug purposes)
     * @param structureDefinition
     * @return redacted Base
     */
    public Base redact(Base base, String elementID, int recursion, StructureDefinition structureDefinition) {

        AtomicBoolean childrenEmpty = new AtomicBoolean(true);
        recursion++;

        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = structureDefinition.getSnapshot();


        ElementDefinition definition = snapshot.getElementById(elementID);

        if (definition == null) {
            logger.warn("Definiton unknown {} {}  Element ID {} {}", base.fhirType(), base.getIdBase(), elementID, structureDefinition.getUrl());
        } else if (definition.hasSlicing()) {


            ElementDefinition slicedElement = slicing.checkSlicing(base, elementID, structureDefinition);
            if (slicedElement != null) {
                if (slicedElement.hasId()) {
                    //logger.warn("Found Sliced Element {}", slicedElement.getIdElement().toString());
                    elementID = slicedElement.getIdElement().toString();

                } else {
                    logger.warn("Sliced Element has no valid ID {}", elementID);
                }

            } else {
                logger.warn("Sliced Element is null {}", elementID);
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
            } catch (NullPointerException e) {

                try {
                    type = child.getTypeCode();
                    min = child.getMinCardinality();
                    logger.trace("{} Standard Type {} with cardinality {} ", child.getName(), type, min);
                } catch (NullPointerException ex) {

                    logger.error(" Child  Type Unknown {} {}", childID, child.getName());
                }
            }
            if (child.hasValues() && childDefinition != null) {
                childrenEmpty.set(false);


                String finalChildID = childID;
                String finalType = type;
                //List Handling
                int finalMin = min;
                child.getValues().forEach(value -> {

                    if (finalMin > 0 && value.isEmpty()) {
                        Element element = factory.create(finalType).addExtension(createAbsentReasonExtension("masked"));
                        base.setProperty(child.getName(), element);
                    } else if (!value.isPrimitive()) {

                        redact(value, finalChildID, finalRecursion, structureDefinition);
                    }
                });


            } else {
                if (min > 0 && !Objects.equals(child.getTypeCode(), "Extension")) {
                    //TODO Backbone Element Handling and nested Extensions
                    Element element = factory.create(type).addExtension(createAbsentReasonExtension("masked"));
                    base.setProperty(child.getName(), element);
                }
            }


        });
        return base;
    }


}
