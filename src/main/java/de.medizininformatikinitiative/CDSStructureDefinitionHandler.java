package de.medizininformatikinitiative;

import de.medizininformatikinitiative.util.ResourceReader;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

@Component
public class CDSStructureDefinitionHandler {


    private HashMap<String, StructureDefinition> definitionsMap = new HashMap<>();

    private HashMap<String, HashMap<String,String>> extensionsMap = new HashMap<>();

    public FhirContext ctx;

    public IParser jsonParser;

    public CDSStructureDefinitionHandler(FhirContext ctx) {
        this.ctx = ctx;
        this.jsonParser = ctx.newJsonParser();
    }




    public void readStructureDefinition(String filePath) throws IOException {
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(filePath);
        } catch (FileNotFoundException e) {
            throw new IOException(e);
        }

        StructureDefinition structureDefinition = (StructureDefinition) ResourceReader.readResource(filePath);
        definitionsMap.put(structureDefinition.getUrl(),structureDefinition);
    }

    public IParser getJsonParser() {
        return jsonParser;
    }

    public StructureDefinition getDefinition(String url){
        return definitionsMap.get(url);

    }

    public StructureDefinition.StructureDefinitionSnapshotComponent getSnapshot(String url){
        return (definitionsMap.get(url)).getSnapshot();

    }
}
