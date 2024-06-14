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
    //TODO Slice Handling
    private org.hl7.fhir.r4.model.StructureDefinition.StructureDefinitionSnapshotComponent snapshot;

    private StructureDefinition structureDefinition;
    private Factory factory;


    private FhirContext ctx = FhirContext.forR4();
    private IParser parser = ctx.newJsonParser();

    private CDSStructureDefinitionHandler CDS;

    private String url;

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
                CanonicalType profileurl = resource.getMeta().getProfile().get(0);
                System.out.println("URL" + profileurl);
                structureDefinition=CDS.getDefinition(String.valueOf(profileurl.getValue()));
                snapshot = structureDefinition.getSnapshot();
                url=String.valueOf(profileurl.getValue());
            }
            elementID = String.valueOf(resource.getResourceType());


        }
        //System.out.println("Element ID "+elementID+" recursion "+recursion);

        String finalElementID = elementID;
        //System.out.println("ID Base "+base.getIdBase());

        ElementDefinition definition = snapshot.getElementById(elementID);
        //TODO: Handle Slicing


        //System.out.println("Definition " + definition.getPath() + " Slicing " + definition.hasSlicing() + " Constraining " + definition.getSliceIsConstraining());

        if (definition.hasSlicing()) {
            System.out.println("Definition " + definition.getPath() + " Slicing " + definition.hasSlicing() + " Constraining " + definition.getSliceIsConstraining());
            /*ElementDefinition.ElementDefinitionSlicingComponent slicing = definition.getSlicing();

            List<ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent> slicingDiscriminator = slicing.getDiscriminator();
            slicing.children().forEach(child -> {
                System.out.println("Slicing Child " + child.getName() + " Type " + child.getTypeCode());
                        child.getValues().forEach(values->{
                            System.out.println("Discriminator Child Value "+values);
                        });
            });
            slicingDiscriminator.forEach(discriminator -> {
                System.out.println( " Path " + discriminator.getPathElement().getValue());
                
                discriminator.children().forEach(child -> {
                    if (child.hasValues()) {
                        System.out.println("Discriminator Child " + child.getName() + " Type " + child.getTypeCode());
                        child.getValues().forEach(values->{
                            System.out.println("Discriminator Child Value "+values);
                        });
                    }
                    
                });

            });*/
            Slicing slicing = new Slicing(CDS);
            slicing.checkSlicing(base, elementID, structureDefinition);
        }

        int finalRecursion = recursion;
        base.children().forEach(child -> {
            //System.out.println("TypeCode"+child.getTypeCode());
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
                    type = childDefinition.getType().get(0).getWorkingCode();
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

                //System.out.println("Child " + childID + " has Pattern " + childDefinition.getFixedOrPattern());


                if (childDefinition.getMin() > 0 && value.isEmpty()) {
                    Element element = factory.create(type).addExtension(createAbsentReasonExtension("masked"));
                    //System.out.println("Redacted Element " + element + " " + element.isEmpty());
                    base.setProperty(child.getName(), element);
                } else if (!value.isPrimitive()) {
                    //System.out.println("Recursive Child  " + childID + " value" + value.fhirType() + " Child name " + child.getName() + " Base Name " + base.fhirType());

                    base.setProperty(child.getName(), redact(value, childID, finalRecursion));
                } else {

                  //  System.out.println("Primitive Value " + value.fhirType() + "  Empty " + value.isEmpty() + " " + value.isPrimitive());
                }


                //}
            } else {
                if (childDefinition != null && childDefinition.getMin() > 0 && child.getTypeCode() != "Extension") {

                    //System.out.println("To be Set to AbsentReasons " + child.getName() + " TypeCode " + child.getTypeCode());
                    Element element = factory.create(type).addExtension(createAbsentReasonExtension("masked"));
                    base.setProperty(child.getName(), element);

                    //System.out.println("Required Child ID " + childID);
                } else if (child.getMinCardinality() > 0) {
                    //System.out.println("Required Child  " + child.getName());
                    Element element = factory.create(childDefinition.fhirType()).addExtension(createAbsentReasonExtension("masked"));
                    base.setProperty(child.getName(), element);
                }
            }


        });
        return base;
    }


}
