package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.util.ResourceReader;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

/**
 * Structure for loading and serving the CDS structrue definitions
 */

@Component
public class CdsStructureDefinitionHandler {


    private HashMap<String, StructureDefinition> definitionsMap = new HashMap<>();
    protected ResourceReader resourceReader;

    public CdsStructureDefinitionHandler(String fileDirectory, ResourceReader resourceReader) {
        try {
            this.resourceReader = resourceReader;
            processDirectory(fileDirectory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Reads a StructureDefinition from a file and stores it in the definitionsMap
     *
     * @param filePath
     * @throws IOException
     */
    public void readStructureDefinition(String filePath) throws IOException {
        StructureDefinition structureDefinition = (StructureDefinition) resourceReader.readResource(filePath);
        definitionsMap.put(structureDefinition.getUrl(), structureDefinition);
    }

    /**
     * Returns the StructureDefinition with the given URL.
     * Handles versioned URLs by splitting on the '|' character.
     *
     * @param url The URL of the StructureDefinition, possibly including a version.
     * @return The StructureDefinition corresponding to the base URL (ignores version).
     */
    public StructureDefinition getDefinition(String url) {
        String[] versionSplit = url.split("\\|");
        return definitionsMap.get(versionSplit[0]);
    }

    /**
     * Returns the first non-null StructureDefinition from a list of URLs.
     * Iterates over the list of URLs, returning the first valid StructureDefinition.
     *
     * @param urls A list of URLs for which to find the corresponding StructureDefinition.
     * @return The first non-null StructureDefinition found, or null if none are found.
     */
    public StructureDefinition getDefinition(List<CanonicalType> urls) {
        for (CanonicalType url : urls) {
            StructureDefinition definition = getDefinition(url.getValue());
            if (definition != null) {
                return definition;
            }
        }
        return null;
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
