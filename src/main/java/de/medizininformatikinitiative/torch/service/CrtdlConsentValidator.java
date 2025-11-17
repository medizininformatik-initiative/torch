package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.databind.JsonNode;
import de.medizininformatikinitiative.torch.exceptions.ConsentFormatException;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.management.TermCode;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class CrtdlConsentValidator {

    /**
     * Extracts all consent codes from the inclusion criteria of a CRDTL,
     * while validating both inclusion and exclusion rules.
     *
     * @param crtdl the CRDTL containing the cohort definition
     * @return an Optional containing all TermCodes if found, otherwise empty
     * @throws ConsentFormatException if inclusion/exclusion rules are violated
     */
    public Optional<Set<TermCode>> extractConsentCodes(Crtdl crtdl) throws ConsentFormatException {
        JsonNode cohortDefinition = crtdl.cohortDefinition();

        // validate exclusions first
        validateExclusionCriteria(cohortDefinition);

        // collect all inclusion term codes
        Set<TermCode> allCodes = collectInclusionTermCodes(cohortDefinition);
        return allCodes.isEmpty() ? Optional.empty() : Optional.of(allCodes);
    }

    /**
     * Collects all TermCodes from inclusion criteria.
     * Streams are used to flatten nested arrays of criteria.
     *
     * @param cohortDefinition the JSON definition of the cohort
     * @return a Set of all valid TermCodes found
     */
    private Set<TermCode> collectInclusionTermCodes(JsonNode cohortDefinition) throws ConsentFormatException {
        JsonNode inclusionCriteria = cohortDefinition.get("inclusionCriteria");
        if (inclusionCriteria == null || !inclusionCriteria.isArray()) {
            return Set.of();
        }

        Set<TermCode> allCodes = new HashSet<>();
        for (JsonNode group : inclusionCriteria) {
            if (!group.isArray()) continue; // skip non-array group
            allCodes.addAll(processInclusionGroup(group).collect(Collectors.toSet()));
        }
        return allCodes;
    }

    /**
     * Processes a single inclusion group, throws ConsentFormatException if multiple Einwilligung contexts exist.
     */
    private Stream<TermCode> processInclusionGroup(JsonNode group) throws ConsentFormatException {
        var einwilligungen = StreamSupport.stream(group.spliterator(), false)
                .filter(criteria -> "Einwilligung".equals(criteria.path("context").path("code").asText(null)))
                .toList();

        if (einwilligungen.size() > 1) {
            throw new ConsentFormatException(
                    "Invalid inclusion criteria: multiple Einwilligung contexts found in the same group.");
        }

        Set<TermCode> codes = new HashSet<>();
        for (JsonNode criteria : einwilligungen) {
            JsonNode termCodes = criteria.path("termCodes");
            if (termCodes.isArray()) {
                for (JsonNode tc : termCodes) {
                    String system = tc.path("system").asText(null);
                    String code = tc.path("code").asText(null);
                    if (system != null && code != null) {
                        codes.add(new TermCode(system, code));
                    }
                }
            }
        }
        return codes.stream();
    }

    /**
     * Validates that exclusion criteria do NOT contain any Einwilligung consent codes.
     *
     * @param cohortDefinition the JSON definition of the cohort
     * @throws ConsentFormatException if any Einwilligung consent codes are found in exclusions
     */
    private void validateExclusionCriteria(JsonNode cohortDefinition) throws ConsentFormatException {
        JsonNode exclusionCriteria = cohortDefinition.get("exclusionCriteria");
        if (exclusionCriteria != null && exclusionCriteria.isArray()) {
            boolean hasConsentCodes = StreamSupport.stream(exclusionCriteria.spliterator(), false)
                    .anyMatch(group ->
                            StreamSupport.stream(group.spliterator(), false)
                                    .anyMatch(criteria ->
                                            "Einwilligung".equals(criteria.path("context").path("code").asText(null))
                                    )
                    );

            if (hasConsentCodes) {
                throw new ConsentFormatException(
                        "Exclusion criteria must not contain Einwilligung consent codes.");
            }
        }
    }


}
