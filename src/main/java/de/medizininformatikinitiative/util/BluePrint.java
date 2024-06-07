package de.medizininformatikinitiative.util;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.*;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import static de.medizininformatikinitiative.util.Reflector.getClassForDataType;

public class BluePrint {
    private final FhirContext ctx;
    private Map<String, BluePrintElement> elementInfoMap = new HashMap<>();
    DomainResource resource;



    Factory factory = new Factory();


    public BluePrint(FhirContext ctx, StructureDefinition structureDefinition) {
        this.ctx = ctx;
        createMinimalResource(structureDefinition);
    }

    public void createMinimalResource(StructureDefinition structureDefinition) {


        // Create a resource instance based on the StructureDefinition
        String resourceTypeName = structureDefinition.getType();
        Class<? extends DomainResource> resourceClass = (Class<? extends DomainResource>) ctx.getResourceDefinition(resourceTypeName).getImplementingClass();
        try {
            resource = resourceClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Extension masked = FhirExtensionsUtil.createAbsentReasonExtension("masked");
        System.out.println("StructureDefinition: "+structureDefinition.getUrl());
        System.out.println("StructureDefinition: "+structureDefinition.getName());

        // Iterate through the snapshot element definitions to find required fields at primitive level
        for (ElementDefinition element : structureDefinition.getSnapshot().getElement()) {



            String path = element.getPath();
            BluePrintElement elementInfo = new BluePrintElement();
            elementInfo.path = path;
            elementInfo.dataType = element.getType().isEmpty() ? "N/A" : element.getType().get(0).getWorkingCode();
            elementInfo.extensions = element.getExtension();
            elementInfo.minCardinality = element.getMin();


            elementInfoMap.put(path, elementInfo);




            //set data AbsentReason masked to the resource for required elements at base level
         /*
          TODO: Find a good reflection strategy to set the required fields as empty fields in the resource.
          Problem is that classnames and typenames are not neccessarily equivalent.
          E.g. URI has Type uri but class is called "URIType".
          */
          if(element.getMin() > 0 ){
                System.out.println("Path: "+path+" Min: "+element.getMin()+" Type: "+elementInfo.dataType);
                String[] pathArray = path.split("\\.");
                Element required = factory.create(elementInfo.dataType);
                required.addExtension(masked);
                //resource.setProperty(path,required.addExtension(masked));



            }
        }
    }

    public  Map<String, BluePrintElement> getElementInfoMap() {
        return elementInfoMap;
    }

    // Print ElementInfoMap
    public void printElementInfoMap() {
        for (Map.Entry<String, BluePrintElement> entry : elementInfoMap.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
    }

    public DomainResource getResource() {
        return resource;
    }
}
