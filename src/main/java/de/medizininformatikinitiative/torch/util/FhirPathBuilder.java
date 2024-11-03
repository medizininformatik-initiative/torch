package de.medizininformatikinitiative.torch.util;


import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Factory;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class FhirPathBuilder {

    Factory factory = new Factory();


    Slicing slicing;

    private static final Logger logger = LoggerFactory.getLogger(FhirPathBuilder.class);

    /**
     * Constructor for Slicing
     *
     * @param handler CDSStructureDefinitionHandler
     */
    public FhirPathBuilder(Slicing slicing) {
        this.slicing = slicing;
    }

    /**
     * For Terser, the base path is the primary concern. This method removes any slicing
     * information that is denoted by colons. For example:
     * - `condition.onset[x]` will not be modified as the method does not handle square brackets.
     * - `Observation.identifier:analyseBefundCode.type.coding` becomes `Observation.identifier.type.coding`.
     *
     * @param input   the input string representing the path with potential slicing
     * @param factory an instance of ElementFactory (not currently used in the method)
     * @return a string with slicing removed according to the Terser base path rules
     */
    public String handleSlicingForTerser(String input) {
        if (input == null) {
            return null;
        }
        // Remove anything after colons (:) until the next dot (.) or end of the string
        String output = input.replaceAll(":[^\\.]*", "");
        return output;
    }

    public String handleSlicingForFhirPath(String input, StructureDefinition.StructureDefinitionSnapshotComponent snapshot) throws FHIRException {
        // Handle null or input without slicing indicators
        if (input == null || (!input.contains(":") && !input.contains("[x]"))) {
            return input;
        }

        // Split the input path by dots to process each segment
        String[] elementIDParts = input.split("\\.");
        StringBuilder result = new StringBuilder();
        StringBuilder elementIDSoFar = new StringBuilder();

        boolean isFirstElement = true;  // Flag to manage dot separators

        for (String e : elementIDParts) {
            if (!isFirstElement) {
                result.append(".");
                elementIDSoFar.append(".");
            } else {
                isFirstElement = false;
            }

            // Append `e` directly to `elementIDSoFar`, keeping choice and slicing indicators
            elementIDSoFar.append(e);

            // Handle choice elements (contains [x]) and slicing elements (contains :)
            if (e.contains("[x]")) {
                String path = e.split("\\[x\\]")[0];  // Remove [x] for FHIRPath expression

                // Append the base path to result without [x]
                result.append(path);

                // Check if slicing is present in the choice element
                if (e.contains(":")) {
                    String[] sliceParts = e.split(":");
                    String sliceName = sliceParts[1].replace(path, "").trim();

                    // Attempt to create the slice using the factory
                    Base element;
                    try {
                        element = factory.create(sliceName);
                    } catch (FHIRException upperCaseException) {
                        // Attempt with lowercase first letter
                        try {
                            sliceName = sliceName.substring(0, 1).toLowerCase() + sliceName.substring(1);
                            element = factory.create(sliceName);
                        } catch (FHIRException lowerCaseException) {
                            throw new FHIRException("Unsupported Slicing " + sliceName);
                        }
                    }
                    if (element == null) {
                        logger.trace("Valid slicing element for {}", sliceName);
                    }

                    // Append the ofType clause
                    result.append(".ofType(").append(sliceName).append(")");
                }
            } else if (e.contains(":")) { // Handle slicing indicator in non-choice elements
                String basePath = e.substring(0, e.indexOf(":")).trim();
                // Append the base path without slicing
                result.append(basePath);

                // Generate conditions using the complete slicing path
                List<String> conditions = slicing.generateConditionsForFHIRPath(String.valueOf(elementIDSoFar), snapshot);

                // Append the where clause with combined conditions
                if (!conditions.isEmpty()) {
                    String combinedConditions = String.join(" and ", conditions);
                    result.append(".where(").append(combinedConditions).append(")");
                }
            } else {
                // Append the segment as-is if no choice or slicing is present
                result.append(e);
            }
        }

        return result.toString();
    }


    /**
     * Builds a FHIRPath expression with a list of where conditions
     *
     * @param path       path to be handled
     * @param conditions list of condition strings
     * @return FHIRPath with combined where conditions
     */
    public String buildConditions(String path, List<String> conditions) {
        if (path == null) return null;
        if (conditions == null || conditions.isEmpty()) {
            return path;
        }
        String combinedCondition = conditions.stream()
                .collect(Collectors.joining(" and "));
        return String.format("%s.where(%s)", path, combinedCondition);
    }


}
