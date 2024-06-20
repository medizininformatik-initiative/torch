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

    private HashMap<String, HashMap<String, String>> extensionsMap = new HashMap<>();

    public FhirContext ctx;

    public IParser jsonParser;

    public CDSStructureDefinitionHandler(FhirContext ctx) {
        this.ctx = ctx;
        this.jsonParser = ctx.newJsonParser();
    }


    /**
     * Reads a StructureDefinition from a file and stores it in the definitionsMap
     *
     * @param filePath
     * @throws IOException
     */
    public void readStructureDefinition(String filePath) throws IOException {
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(filePath);
        } catch (FileNotFoundException e) {
            throw new IOException(e);
        }

        StructureDefinition structureDefinition = (StructureDefinition) ResourceReader.readResource(filePath);
        definitionsMap.put(structureDefinition.getUrl(), structureDefinition);
    }

    /**
     * Returns HAPI JSON Parser
     */
    public IParser getJsonParser() {
        return jsonParser;
    }

    /**
     * Returns the StructureDefinition with the given URL
     * TODO Advanced Version handling?
     *
     * @param url
     * @return StructureDefinition
     */
    public StructureDefinition getDefinition(String url) {
        String[] versionsplit = url.split("\\|");
        return definitionsMap.get(versionsplit[0]);
    }

    public StructureDefinition.StructureDefinitionSnapshotComponent getSnapshot(String url) {
        return (definitionsMap.get(url)).getSnapshot();

    }
}
