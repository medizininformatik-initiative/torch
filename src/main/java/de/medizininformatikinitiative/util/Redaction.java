package de.medizininformatikinitiative.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.CDSStructureDefinitionHandler;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Factory;

import java.util.concurrent.atomic.AtomicBoolean;

import static de.medizininformatikinitiative.util.FhirExtensionsUtil.createAbsentReasonExtension;

public class Redaction {
    //TODO Slice Handling
    private StructureDefinition.StructureDefinitionSnapshotComponent snapshot;

    private Factory factory;


    private FhirContext ctx = FhirContext.forR4();
    private IParser parser = ctx.newJsonParser();

    private CDSStructureDefinitionHandler CDS;

    public Redaction(CDSStructureDefinitionHandler CDS) {
        this.CDS = CDS;
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
                CanonicalType url = resource.getMeta().getProfile().get(0);
                System.out.println("URL" + url);
                snapshot = CDS.getSnapshot(String.valueOf(url.getValue()));
            }
            elementID = String.valueOf(resource.getResourceType());


        }
        //System.out.println("Element ID "+elementID+" recursion "+recursion);

        String finalElementID = elementID;
        //System.out.println("ID Base "+base.getIdBase());

        ElementDefinition definition = snapshot.getElementById(elementID);
        //TODO: Handle Slicing

        int finalRecursion = recursion;
        base.children().forEach(child -> {
            //System.out.println("TypeCode"+child.getTypeCode());
            String childID = finalElementID + "." + child.getName();
            //System.out.println("Element ID "+childID+" recursion "+ finalRecursion);
            ElementDefinition childDefinition = null;
            try {
                //System.out.println("Found Snapshot "+childID);
                childDefinition = snapshot.getElementById(childID);
            } catch (NullPointerException e) {
                try {
                    childDefinition = child.getStructure().getSnapshot().getElementById(child.getName());
                    childID = child.getName();

                    //System.out.println("Fallback Snapshot "+childID);
                } catch (NullPointerException ex) {
                    // System.out.println("No Definition Found "+childID);
                }
            }

            //System.out.println(" Value to be handled "+child.getName()+" Min "+child.getMinCardinality());
            if (child.hasValues() && childDefinition != null) {
                childrenEmpty.set(false);
                //System.out.println("HasValue Child  " + childID);
                //List<Base> values = child.getValues();
                //for(Base value:values){
                Base value = child.getValues().get(0);

                System.out.println("Child "+childID+" has Pattern " + childDefinition.getFixedOrPattern());
                //System.out.println(" Value to be handled " + childDefinition.getName() + " Min " + childDefinition.getMin());

                if (childDefinition.getMin() > 0 && value.isEmpty()) {
                    Element element = factory.create(value.fhirType()).addExtension(createAbsentReasonExtension("masked"));
                    //System.out.println("Redacted Element " + element + " " + element.isEmpty());
                    base.setProperty(child.getName(), element);
                } else if (!value.isPrimitive()) {
                    //System.out.println("Recursive Child  " + childID + " value" + value.fhirType() + " Child name " + child.getName() + " Base Name " + base.fhirType());

                    base.setProperty(child.getName(), redact(value, childID, finalRecursion));
                } else {

                    System.out.println("Primitive Value " + value.fhirType() + "  Empty " + value.isEmpty() + " " + value.isPrimitive());
                }


                //}
            } else {
                if (childDefinition != null && childDefinition.getMin() > 0 && child.getTypeCode() != "Extension") {
                    //System.out.println("To be Set to AbsentReasons " + definition.getName() + "TypeCode" + child.getTypeCode());
                    Element element = factory.create(child.getTypeCode()).addExtension(createAbsentReasonExtension("masked"));
                    base.setProperty(child.getName(), element);

                    //System.out.println("Required Child ID " + childID);
                } else if (child.getMinCardinality() > 0) {
                    //System.out.println("Required Child  " + child.getName());
                    Element element = factory.create(child.getTypeCode()).addExtension(createAbsentReasonExtension("masked"));
                    base.setProperty(child.getName(), element);
                }
            }


        });
        return base;
    }


}
