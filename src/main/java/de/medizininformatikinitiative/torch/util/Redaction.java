package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.CdsStructureDefinitionHandler;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static de.medizininformatikinitiative.torch.util.FhirExtensionsUtil.createAbsentReasonExtension;

public class Redaction {


    private static final Logger logger = LoggerFactory.getLogger(Redaction.class);

    private org.hl7.fhir.r4.model.StructureDefinition.StructureDefinitionSnapshotComponent snapshot;

    private StructureDefinition structureDefinition;
    private final Factory factory;
    private final CdsStructureDefinitionHandler CDS;


    /**
     * Constructor for Redaction
     *
     * @param CDS CDSStructureDefinitionHandler
     */
    public Redaction(CdsStructureDefinitionHandler CDS) {
        this.CDS = CDS;
        factory = new Factory();
    }


    /**
     * Executes redaction operation on the given base element recursively.
     * @param base Base to be redacted (e.g. a Ressource or an Element)
     * @param elementID "Element ID of parent currently handled initially empty String"
     * @param recursion "Resurcion depth (for debug purposes)
     * @return redacted Base
     */
    public Base redact(Base base, String elementID, int recursion) {
        AtomicBoolean childrenEmpty = new AtomicBoolean(true);
        recursion++;

        /*
         * Check if the base is a DomainResource and if it has a profile. Used for initial redaction.
         */
        if (base instanceof DomainResource resource) {
            recursion = 1;
            if (resource.hasMeta()) {
                CanonicalType profileurl = resource.getMeta().getProfile().getFirst();
                structureDefinition=CDS.getDefinition(String.valueOf(profileurl.getValue()));
                snapshot = structureDefinition.getSnapshot();
            }
            elementID = String.valueOf(resource.getResourceType());


        }


        ElementDefinition definition = snapshot.getElementById(elementID);

        if (definition.hasSlicing()) {
              Slicing slicing = new Slicing(CDS);
            ElementDefinition slicedElement = slicing.checkSlicing(base, elementID, structureDefinition);
            if(slicedElement.hasId()){
                elementID=slicedElement.getIdElement().toString();

            }
        }

        int finalRecursion = recursion;
        String finalElementID = elementID;

        base.children().forEach(child -> {

            String childID = finalElementID + "." + child.getName();
            ElementDefinition childDefinition = null;
            String type = "";
            try {
                childDefinition = snapshot.getElementById(childID);
                type = childDefinition.getType().getFirst().getWorkingCode();
            } catch (NullPointerException e) {
                try {
                    childDefinition = child.getStructure().getSnapshot().getElementById(child.getName());
                    childID = child.getName();
                    type = childDefinition.getType().getFirst().getWorkingCode();
                } catch (NullPointerException ex) {
                    //Not necessarily a real error, since the list contains all possible standard children initialized or not.
                    logger.debug("", ex);
                }
            }


            if (child.hasValues() && childDefinition != null) {
                childrenEmpty.set(false);
                ElementDefinition finalChildDefinition = childDefinition;
                String finalChildID = childID;
                String finalType = type;
                //List Handling
                child.getValues().forEach(value -> {

                    if (finalChildDefinition.getMin() > 0 && value.isEmpty()) {
                        Element element = factory.create(finalType).addExtension(createAbsentReasonExtension("masked"));
                        base.setProperty(child.getName(), element);
                    } else if (!value.isPrimitive()) {

                       redact(value, finalChildID, finalRecursion);
                    }
                });


            } else {
                if (childDefinition != null && childDefinition.getMin() > 0 && !Objects.equals(child.getTypeCode(), "Extension")) {
                    //TODO Backbone Element Handling and nested Extensions
                    Element element = factory.create(type).addExtension(createAbsentReasonExtension("masked"));
                    base.setProperty(child.getName(), element);
                }
            }


        });
        return base;
    }


}
