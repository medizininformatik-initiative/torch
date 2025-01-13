package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class CrtdlValidatorService {

    private final StructureDefinitionHandler profileHandler;
    private final Set<String> codes;


    public CrtdlValidatorService(StructureDefinitionHandler profileHandler) throws IOException {
        this.profileHandler = profileHandler;
        this.codes = extractCodes("compartmentdefinition-patient.json");
    }

    /**
     * Validates crtdl and modifies the attribute groups by adding standard attributes and modifiers
     *
     * @param crtdl to be validated
     * @return modified crtdl or illegalArgumentException if a profile is unknown.
     * <p>
     * MedicationStatement.medication -> Attributgruppe Medication
     * TODO: attribut in snapshot nachschlagen und wenn referenz -> potenzielle profile identifizieren
     * und dann nur diese hinzufügen.
     */
    public void validate(Crtdl crtdl) {
        Set<String> profiles = new HashSet<>();
        profiles.addAll(profileHandler.knownProfiles());


        crtdl.dataExtraction().attributeGroups().forEach(attributeGroup -> {
            StructureDefinition definition = profileHandler.getDefinition(attributeGroup.groupReference());
            if (definition != null) {
                if (!attributeGroup.includeReferenceOnly() && codes.contains(definition.getType())) {
                    attributeGroup.attributes().forEach(attribute -> {
                        ElementDefinition elementDefinition = definition.getSnapshot().getElementById(attribute.attributeRef());
                        if (elementDefinition == null) {
                            throw new IllegalArgumentException("Unknown Attributes in " + attributeGroup.groupReference());
                        }
                    });

                } else {
                    throw new IllegalArgumentException("Attribute would lead to direct extraction of a Resources outside the patient compartment " + attributeGroup.groupReference());
                }
            } else {
                throw new IllegalArgumentException("Unknown Profile: " + attributeGroup.groupReference());
            }
        });
    }

    /**
     * Reads a JSON file from the resources folder, extracts codes in the resource fields, and returns them as a list.
     * Used to read the compartment definition.
     *
     * @param fileName The name of the JSON file in the resources' folder.
     * @return A Set of extracted codes.
     * @throws IOException If the file cannot be read.
     */
    public Set<String> extractCodes(String fileName) throws IOException {
        // Load the file from resources
        File file = new File(Objects.requireNonNull(getClass().getClassLoader().getResource(fileName)).getFile());

        // Parse JSON using Jackson
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(file);

        // Extract codes from the "resource" array
        Set<String> codes = new HashSet<>();
        if (rootNode.has("resource")) {
            for (JsonNode resourceNode : rootNode.get("resource")) {
                if (resourceNode.has("code")) {
                    codes.add(resourceNode.get("code").asText());
                }
            }
        }
        return codes;
    }

}
