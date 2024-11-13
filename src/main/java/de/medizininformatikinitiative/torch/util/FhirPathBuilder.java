package de.medizininformatikinitiative.torch.util;


import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Factory;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


/**
 * Class for building FHIR and Terser Paths from Element Ids for Slicing, Copying and finding
 */
public class FhirPathBuilder {

    Factory factory = new Factory();


    Slicing slicing;

    private static final Logger logger = LoggerFactory.getLogger(FhirPathBuilder.class);

    /**
     * Constructor for FHIR PATH builder
     *
     * @param slicing Slicing Class that handles slicing resolving
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
     * @param input the input string representing the path with potential slicing
     * @return a string with slicing removed according to the Terser base path rules
     */
    public String handleSlicingForTerser(String input) {
        if (input == null) {
            return null;
        }
        // Remove anything after colons (:) until the next dot (.) or end of the string
        return input.replaceAll(":[^.]*", "");
    }

    public String[] handleSlicingForFhirPath(String input, StructureDefinition.StructureDefinitionSnapshotComponent snapshot) throws FHIRException {
        if (input == null || (!input.contains(":") && !input.contains("[x]"))) {
            return new String[]{input, input};
        }

        String[] elementIDParts = input.split("\\.");
        StringBuilder fhirPath = new StringBuilder();
        StringBuilder terserPath = new StringBuilder();
        StringBuilder elementIDSoFar = new StringBuilder();

        boolean isFirstElement = true;

        for (String e : elementIDParts) {
            if (!isFirstElement) {
                fhirPath.append(".");
                elementIDSoFar.append(".");
                terserPath.append(".");
            } else {
                isFirstElement = false;
            }

            elementIDSoFar.append(e);

            if (e.contains("[x]")) {
                String path = e.split("\\[x\\]")[0];  // Remove [x] for FHIRPath expression

                fhirPath.append(path);

                // Check if slicing is present in the choice element
                if (e.contains(":")) {
                    String[] sliceParts = e.split(":");
                    String sliceName = sliceParts[1].replace(path, "").trim();

                    Base element;
                    try {
                        element = factory.create(sliceName);
                    } catch (FHIRException upperCaseException) {
                        try {
                            sliceName = sliceName.substring(0, 1).toLowerCase() + sliceName.substring(1);
                            element = factory.create(sliceName);
                        } catch (FHIRException lowerCaseException) {
                            throw new FHIRException("Unsupported Choice Slicing " + sliceName);
                        }
                    }
                    if (element == null) {
                        logger.trace("Valid slicing element for {}", sliceName);
                    }

                    fhirPath.append(".ofType(").append(sliceName).append(")");
                    terserPath.append(sliceParts[1]);

                } else {
                    terserPath.append(e);
                }
            } else if (e.contains(":")) {
                String basePath = e.substring(0, e.indexOf(":")).trim();
                fhirPath.append(basePath);
                terserPath.append(basePath);
                List<String> conditions = slicing.generateConditionsForFHIRPath(String.valueOf(elementIDSoFar), snapshot);
                if (!conditions.isEmpty()) {
                    String combinedConditions = String.join(" and ", conditions);
                    fhirPath.append(".where(").append(combinedConditions).append(")");
                }
            } else {
                fhirPath.append(e);
                terserPath.append(e);
            }
        }

        return new String[]{String.valueOf(fhirPath), String.valueOf(terserPath)};
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
        String combinedCondition = String.join(" and ", conditions);
        return String.format("%s.where(%s)", path, combinedCondition);
    }


}
