package de.medizininformatikinitiative;

import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

@Component
public class CDSStructureDefinitionHandler {

    private HashMap<String, StructureDefinition> definitionsMap = new HashMap<>();

    public FhirContext ctx= FhirContext.forR4();;

    public IParser jsonParser = ctx.newJsonParser();


    public void run() {
        try {

            readStructureDefinition( "src/test/resources/patient-structure-definition.json");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void readStructureDefinition(String filePath) throws IOException {
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(filePath);
        } catch (FileNotFoundException e) {
            throw new IOException(e);
        }

        StructureDefinition structureDefinition = jsonParser.parseResource(StructureDefinition.class, fileReader);
        System.out.println("StructureDefinition: " + structureDefinition.getUrl());
        definitionsMap.put(structureDefinition.getUrl(), structureDefinition);

    }

    public IParser getJsonParser() {
        return jsonParser;
    }

    public StructureDefinition getDefinition(String url){
        return definitionsMap.get(url);

    }
}
