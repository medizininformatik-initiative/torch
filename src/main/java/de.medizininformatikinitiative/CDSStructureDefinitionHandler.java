package de.medizininformatikinitiative;

import de.medizininformatikinitiative.util.CRTDL.Attribute;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

@Component
public class CDSStructureDefinitionHandler {


    private HashMap<String, CDSStructureDefinition> definitionsMap = new HashMap<>();

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
        definitionsMap.put(structureDefinition.getUrl(),new CDSStructureDefinition(structureDefinition));

    }

    public IParser getJsonParser() {
        return jsonParser;
    }

    public StructureDefinition getDefinition(String url){
        return definitionsMap.get(url).getStructureDefinition();

    }

    public HashMap<String, ElementDefinition> getElementMap(String url){
        return (definitionsMap.get(url)).getElementMap();

    }
}
