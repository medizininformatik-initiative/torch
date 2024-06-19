package de.medizininformatikinitiative.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.CDSStructureDefinitionHandler;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Factory;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StructureDefinition.StructureDefinitionSnapshotComponent;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static de.medizininformatikinitiative.util.FhirExtensionsUtil.createAbsentReasonExtension;

public class Redaction {
    private org.hl7.fhir.r4.model.StructureDefinition.StructureDefinitionSnapshotComponent snapshot;

    private StructureDefinition structureDefinition;
    private Factory factory;


    private FhirContext ctx;
    private IParser parser;

    private String url;

    private CDSStructureDefinitionHandler CDS;


    public Redaction(CDSStructureDefinitionHandler CDS) {
        this.CDS = CDS;
        ctx=CDS.ctx;
        parser=ctx.newJsonParser();
        factory = new Factory();
    }

    public Base redact(Base base, String elementID, int recursion) {
        AtomicBoolean childrenEmpty = new AtomicBoolean(true);
        recursion++;
        //System.out.println("Redact Call "+elementID+" recursion "+recursion);
        if (base instanceof DomainResource) {
            recursion = 1;
            DomainResource resource = (DomainResource) base;
            if (resource.hasMeta()) {
                CanonicalType profileurl = resource.getMeta().getProfile().get(0);
                System.out.println("URL" + profileurl);
                structureDefinition=CDS.getDefinition(String.valueOf(profileurl.getValue()));
                snapshot = structureDefinition.getSnapshot();
                url=String.valueOf(profileurl.getValue());
            }
            elementID = String.valueOf(resource.getResourceType());


        }


        ElementDefinition definition = snapshot.getElementById(elementID);


        //System.out.println("Definition " + definition.getPath() + " Slicing " + definition.hasSlicing() + " Constraining " + definition.getSliceIsConstraining());
        //TODO: Handle Slicing
        if (definition.hasSlicing()) {
              Slicing slicing = new Slicing(CDS);
            ElementDefinition slicedElement = slicing.checkSlicing(base, elementID, structureDefinition);
            if(slicedElement.hasId()){
                elementID=slicedElement.getIdElement().toString();
                //System.out.println("Found Slicing " + slicedElement.getPath() + " Element "+elementID);

            }
        }

        int finalRecursion = recursion;
        String finalElementID = elementID;

        base.children().forEach(child -> {
            //System.out.println("TypeCode"+child.getTypeCode());
            if(child.isList()){
                //System.out.println("Found List "+child.getName()+" path "+ finalElementID +" Nr. of Values "+child.getValues().size() );
            }

            if(child.getName()=="coding"){
               child.getValues().forEach(
                       value -> {
                               System.out.println("Coding Value "+value.fhirType()+"  Empty? "+value.isEmpty()+" Value "+value.toString());

                       }
               );
            }
            String childID = finalElementID + "." + child.getName();
            //System.out.println("Element ID "+childID+" recursion "+ finalRecursion);
            ElementDefinition childDefinition = null;
            String type = "";
            try {
                //System.out.println("Found Snapshot "+childID);
                childDefinition = snapshot.getElementById(childID);
                type = childDefinition.getType().get(0).getWorkingCode();
            } catch (NullPointerException e) {
                try {
                    childDefinition = child.getStructure().getSnapshot().getElementById(child.getName());
                    childID = child.getName();
                    //For redaction we can be greedy and take the first type?
                    type = childDefinition.getType().get(0).getWorkingCode();
                } catch (NullPointerException ex) {
                    // System.out.println("No Definition Found "+childID);
                }
            }


            if (child.hasValues() && childDefinition != null) {
                childrenEmpty.set(false);
                //System.out.println("HasValue Child  " + childID);
                ElementDefinition finalChildDefinition = childDefinition;
                String finalChildID = childID;
                String finalType = type;
                child.getValues().forEach(value -> {

                    if (finalChildDefinition.getMin() > 0 && value.isEmpty()) {
                        Element element = factory.create(finalType).addExtension(createAbsentReasonExtension("masked"));
                        //System.out.println("Redacted Element " + element + " " + element.isEmpty());
                        base.setProperty(child.getName(), element);
                    } else if (!value.isPrimitive()) {
                        //System.out.println("Recursive Child  " + childID + " value" + value.fhirType() + " Child name " + child.getName() + " Base Name " + base.fhirType());

                       redact(value, finalChildID, finalRecursion);
                    }
                });


            } else {
                if (childDefinition != null && childDefinition.getMin() > 0 && child.getTypeCode() != "Extension") {
                    //System.out.println("To be Set to AbsentReasons " + child.getName() + " TypeCode " + child.getTypeCode());
                    //TODO Backbone Element Handling and nested Extensions
                    Element element = factory.create(type).addExtension(createAbsentReasonExtension("masked"));
                    base.setProperty(child.getName(), element);
                }
            }


        });
        return base;
    }


}
