package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.util.ResourceReader;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

@Component


public class CdsStructureDefinitionHandler {


    private HashMap<String, StructureDefinition> definitionsMap = new HashMap<>();


    public FhirContext ctx;

    public IParser jsonParser;

    public CdsStructureDefinitionHandler(FhirContext ctx, String fileDirectory) {
        try {
            processDirectory(fileDirectory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.ctx = ctx;
        this.jsonParser = ctx.newJsonParser();
    }

    public CdsStructureDefinitionHandler(FhirContext ctx) {

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

    /**
     * Reads all JSON files in a directory and stores their StructureDefinitions in the definitionsMap
     *
     * @param directoryPath the path to the directory containing JSON files
     * @throws IOException
     */
    private void processDirectory(String directoryPath) throws IOException {
        File directory = new File(directoryPath);
        if (directory.isDirectory()) {
            File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    readStructureDefinition(file.getAbsolutePath());
                }
            }
        }
    }
}
