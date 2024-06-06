package de.medizininformatikinitiative.util;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.*;

import java.util.HashMap;
import java.util.Map;

public class BluePrint {
    private final FhirContext ctx;
    private Map<String, ElementInfo> elementInfoMap = new HashMap<>();
    DomainResource resource;






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
            ElementInfo elementInfo = new ElementInfo();
            elementInfo.path = path;
            elementInfo.dataType = element.getType().isEmpty() ? "N/A" : element.getType().get(0).getWorkingCode();
            elementInfo.extensions = element.getExtension();
            elementInfo.minCardinality = element.getMin();

            elementInfoMap.put(path, elementInfo);

            System.out.println("Path: " + path + " Min: " + element.getMin() + " Type: " + elementInfo.dataType);



            //set data AbsentReason masked to the resource for required elements at base level
         /*
          TODO: Find a good reflection strategy to set the required fields as empty fields in the resource.
          Problem is that classnames and typenames are not neccessarily equivalent.
          E.g. URI has Type uri but class is called "URIType".
          */

          /*
          if(element.getMin() > 0 ){
                System.out.println("Path: "+path+" Min: "+element.getMin()+" Type: "+elementInfo.dataType);
                String[] pathArray = path.split("\\.");
                try {
                    Class<?> clazz = Class.forName("org.hl7.fhir.r4.model." + elementInfo.dataType);
                    System.out.println("ClassName "+"org.hl7.fhir.r4.model." + elementInfo.dataType+"clazz result "+clazz.getName());
                    System.out.println("Constructors "+clazz.getConstructors().toString());
                    //Element required = (Element) clazz.getDeclaredConstructor().newInstance();
                    //resource.setProperty(path,required.addExtension(masked));
                } catch (ClassNotFoundException //| NoSuchMethodException | InstantiationException |IllegalAccessException | InvocationTargetException
                 e) {
                    throw new RuntimeException(e);
                }


            }*/
        }
    }

    public  Map<String, ElementInfo> getElementInfoMap() {
        return elementInfoMap;
    }

    // Print ElementInfoMap
    public void printElementInfoMap() {
        for (Map.Entry<String, ElementInfo> entry : elementInfoMap.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
    }

    public DomainResource getResource() {
        return resource;
    }
}
