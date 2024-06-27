package de.medizininformatikinitiative.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.CdsStructureDefinitionHandler;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Factory;
import org.hl7.fhir.r4.model.ElementDefinition;

import java.util.concurrent.atomic.AtomicBoolean;

import static de.medizininformatikinitiative.util.FhirExtensionsUtil.createAbsentReasonExtension;

public class Redaction {
    private org.hl7.fhir.r4.model.StructureDefinition.StructureDefinitionSnapshotComponent snapshot;

    private StructureDefinition structureDefinition;
    private Factory factory;


    private FhirContext ctx;
    private IParser parser;

    private String url;

    private CdsStructureDefinitionHandler CDS;


    /**
     * Constructor for Redaction
     *
     * @param CDS CDSStructureDefinitionHandler
     */
    public Redaction(CdsStructureDefinitionHandler CDS) {
        this.CDS = CDS;
        ctx=CDS.ctx;
        parser=ctx.newJsonParser();
        factory = new Factory();
    }


    /**
     * Executes redaction operation on the given base element recursively.
     * @param base
     * @param elementID
     * @param recursion
     * @return
     */
    public Base redact(Base base, String elementID, int recursion) {
        AtomicBoolean childrenEmpty = new AtomicBoolean(true);
        recursion++;

        /**
         * Check if the base is a DomainResource and if it has a profile. Used for initial redaction.
         */
        if (base instanceof DomainResource) {
            recursion = 1;
            DomainResource resource = (DomainResource) base;
            if (resource.hasMeta()) {
                CanonicalType profileurl = resource.getMeta().getProfile().get(0);
                structureDefinition=CDS.getDefinition(String.valueOf(profileurl.getValue()));
                snapshot = structureDefinition.getSnapshot();
                url=String.valueOf(profileurl.getValue());
            }
            elementID = String.valueOf(resource.getResourceType());


        }


        ElementDefinition definition = snapshot.getElementById(elementID);


        //TODO: Handle Slicing
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
                type = childDefinition.getType().get(0).getWorkingCode();
            } catch (NullPointerException e) {
                try {
                    childDefinition = child.getStructure().getSnapshot().getElementById(child.getName());
                    childID = child.getName();
                    type = childDefinition.getType().get(0).getWorkingCode();
                } catch (NullPointerException ex) {
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
                if (childDefinition != null && childDefinition.getMin() > 0 && child.getTypeCode() != "Extension") {
                    //TODO Backbone Element Handling and nested Extensions
                    Element element = factory.create(type).addExtension(createAbsentReasonExtension("masked"));
                    base.setProperty(child.getName(), element);
                }
            }


        });
        return base;
    }


}
