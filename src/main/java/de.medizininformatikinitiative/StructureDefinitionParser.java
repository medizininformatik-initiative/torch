package de.medizininformatikinitiative;

import de.medizininformatikinitiative.util.BluePrint;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class StructureDefinitionParser {

    public Map<String, BluePrint> resourceMap = new HashMap<>();



    public FhirContext ctx= FhirContext.forR4();;

    public IParser jsonParser = ctx.newJsonParser();


    public void run() {
        try {

            readStructureDefinition( "src/test/resources/patient-structure-definition.json");
            BluePrint min = resourceMap.get("http://fhir.de/StructureDefinition/mii-pr-person-patient");
            // Print the minimal resource as JSON
            String resourceJson = jsonParser.setPrettyPrint(true).encodeResourceToString(min.getResource());
            System.out.println(resourceJson);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void  readStructureDefinition(String filePath) throws IOException {
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(filePath);
        } catch (FileNotFoundException e) {
            throw new IOException(e);
        }

        StructureDefinition structureDefinition = jsonParser.parseResource(StructureDefinition.class, fileReader);
        System.out.println("StructureDefinition: " + structureDefinition.getUrl());
        resourceMap.put(structureDefinition.getUrl(), new BluePrint(ctx, structureDefinition));
    }



}
