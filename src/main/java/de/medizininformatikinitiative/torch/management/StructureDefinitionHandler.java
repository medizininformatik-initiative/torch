package de.medizininformatikinitiative.torch.management;

import de.medizininformatikinitiative.torch.util.ResourceReader;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Structure for loading and serving the CDS structure definitions
 */

@Component
public class StructureDefinitionHandler {


    private final HashMap<String, StructureDefinition> definitionsMap = new HashMap<>();
    protected ResourceReader resourceReader;

    public StructureDefinitionHandler(String fileDirectory, ResourceReader resourceReader) {
        try {
            this.resourceReader = resourceReader;
            processDirectory(fileDirectory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return Keyset of the underlying Map managing profiles and their Structure Definitions.
     */
    public Set<String> knownProfiles() {
        return Set.copyOf(definitionsMap.keySet());
    }

    /**
     * Reads a StructureDefinition from a file and stores it in the definitionsMap
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
    public Set<StructureDefinition> getDefinitions(Set<String> urls) {
        Set<StructureDefinition> definitions = new HashSet<>();
        urls.forEach(url -> {
            definitions.add(getDefinition(url));
        });
        return definitions;
    }

    /**
     * Returns the first non-null StructureDefinition from a list of URLs.
     * Iterates over the list of URLs, returning the first valid StructureDefinition.
     *
     * @param urls A list of URLs for which to find the corresponding StructureDefinition.
     * @return The first non-null StructureDefinition found, or null if none are found.
     */
    public Set<StructureDefinition.StructureDefinitionSnapshotComponent> getSnapshots(Set<String> urls) {
        Set<StructureDefinition.StructureDefinitionSnapshotComponent> definitions = new HashSet<>();
        urls.forEach(url -> {
            definitions.add(getSnapshot(url));
        });
        return definitions;
    }


    public StructureDefinition.StructureDefinitionSnapshotComponent getSnapshot(String url) {
        if (definitionsMap.get(url) != null) {
            return (definitionsMap.get(url)).getSnapshot();
        } else {
            throw new IllegalArgumentException("Unknown Profile: " + url);
        }


    }

    public String getResourceType(String url) {
        if (definitionsMap.get(url) != null) {
            return (definitionsMap.get(url)).getType();
        } else {
            throw new IllegalArgumentException("Unknown Profile: " + url);
        }

    }


    /**
     * Reads all JSON files in a directory and stores their StructureDefinitions in the definitionsMap
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
