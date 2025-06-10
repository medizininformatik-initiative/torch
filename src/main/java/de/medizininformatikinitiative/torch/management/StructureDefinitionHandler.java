package de.medizininformatikinitiative.torch.management;

import de.medizininformatikinitiative.torch.util.ResourceReader;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Handler for loading and serving structure definitions.
 */
@Component
public class StructureDefinitionHandler {

    private final Map<String, StructureDefinition> definitions = new HashMap<>();
    private final ResourceReader resourceReader;

    public StructureDefinitionHandler(String fileDirectory, ResourceReader resourceReader) {
        try {
            this.resourceReader = resourceReader;
            processDirectory(fileDirectory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param profile to check if known
     * @return returns if profile is known.
     */
    public Boolean known(String profile) {
        return definitions.containsKey(profile);
    }

    /**
     * Reads a StructureDefinition from a file and stores it in the definitionsMap
     */
    public void readStructureDefinition(String filePath) throws IOException {
        StructureDefinition structureDefinition = (StructureDefinition) resourceReader.readResource(filePath);
        definitions.put(structureDefinition.getUrl(), structureDefinition);
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
        return definitions.get(versionSplit[0]);
    }

    /**
     * Returns the first non-null StructureDefinition from a list of URLs.
     * <p>
     * Iterates over the list of URLs, returning the first valid StructureDefinition.
     *
     * @param urls a list of URLs for which to find the corresponding StructureDefinition.
     * @return The first non-null StructureDefinition found, or empty if none are found.
     */
    public Optional<StructureDefinition> getDefinition(Set<String> urls) {
        return urls.stream().map(definitions::get).filter(Objects::nonNull).findFirst();
    }

    public StructureDefinition.StructureDefinitionSnapshotComponent getSnapshot(String url) {
        if (definitions.get(url) != null) {
            return (definitions.get(url)).getSnapshot();
        } else {
            throw new IllegalArgumentException("Unknown Profile: " + url);
        }
    }

    public String getResourceType(String url) {
        if (definitions.get(url) != null) {
            return (definitions.get(url)).getType();
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
