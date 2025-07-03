package de.medizininformatikinitiative.torch.management;

import de.medizininformatikinitiative.torch.util.CompiledStructureDefinition;
import de.medizininformatikinitiative.torch.util.ResourceReader;
import jakarta.annotation.PostConstruct;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Handler for loading and serving FHIR structure definitions.
 */
public class StructureDefinitionHandler {

    private final Map<String, CompiledStructureDefinition> definitions = new HashMap<>();
    private final ResourceReader resourceReader;
    private final File directory;

    /**
     * Creates a new StructureDefinitionHandler.
     *
     * @param directory      the directory containing JSON structure definition files
     * @param resourceReader utility for reading and parsing FHIR resources
     */
    public StructureDefinitionHandler(File directory, ResourceReader resourceReader) {
        this.resourceReader = requireNonNull(resourceReader);
        this.directory = requireNonNull(directory);
    }

    /**
     * Reads all JSON files in a directory and stores their StructureDefinitions.
     *
     * @throws IOException if any file cannot be read or parsed
     */
    @PostConstruct
    public void processDirectory() throws IOException {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    readStructureDefinition(file.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Checks if a profile URL is known to this handler.
     *
     * @param profile the profile URL to check
     * @return true if the profile is known, false otherwise
     */
    public boolean known(String profile) {
        return definitions.containsKey(stripVersion(profile));
    }

    /**
     * Returns the StructureDefinition with the given URL.
     * Handles versioned URLs by splitting on the '|' character.
     *
     * @param url The URL of the StructureDefinition, possibly including a version
     * @return The StructureDefinition corresponding to the base URL (ignores version)
     */
    public Optional<CompiledStructureDefinition> getDefinition(String url) {
        return getDefinition(Set.of(url));
    }

    /**
     * Returns the first found StructureDefinition from a list of URLs.
     * Iterates over the list of URLs, returning the first valid StructureDefinition.
     * Removes version flags making the handling version agnostic.
     *
     * @param urls a list of URLs for which to find the corresponding StructureDefinition
     * @return the first StructureDefinition found, or empty if none are found
     */
    public Optional<CompiledStructureDefinition> getDefinition(Set<String> urls) {
        return urls.stream()
                .map(this::stripVersion)
                .map(definitions::get)
                .filter(Objects::nonNull)
                .findFirst();
    }

    /**
     * Reads a StructureDefinition from a file and stores it in the definitions map.
     *
     * @param filePath the absolute path to the JSON file
     * @throws IOException if the file cannot be read or parsed
     */
    private void readStructureDefinition(String filePath) throws IOException {
        StructureDefinition structureDefinition = (StructureDefinition) resourceReader.readResource(filePath);
        definitions.put(structureDefinition.getUrl(), CompiledStructureDefinition.fromStructureDefinition(structureDefinition));
    }

    /**
     * Strips version information from a FHIR canonical URL.
     *
     * @param url the potentially versioned URL
     * @return the URL with version information removed
     */
    private String stripVersion(String url) {
        int pipeIndex = url.indexOf('|');
        return pipeIndex == -1 ? url : url.substring(0, pipeIndex);
    }
}
