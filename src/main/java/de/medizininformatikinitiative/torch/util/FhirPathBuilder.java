package de.medizininformatikinitiative.torch.util;


import org.hl7.fhir.exceptions.FHIRException;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * Class for building FHIR and Terser Paths from Element Ids for Slicing, Copying and finding
 */
@Component
public class FhirPathBuilder {


    public static String[] handleSlicingForFhirPath(String input, CompiledStructureDefinition definition) throws FHIRException {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Fhir Path must not be empty");
        }
        if (!input.contains(":") && !input.contains("[x]")) {
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
                String path = e.split("\\[x]")[0];  // Remove [x] for FHIRPath expression

                fhirPath.append(path);

                // Check if slicing is present in the choice element
                if (e.contains(":")) {
                    String[] sliceParts = e.split(":");
                    String sliceName = sliceParts[1].replace(path, "").trim();

                    String typeName;
                    try {
                        typeName = HapiFactory.create(sliceName).fhirType();
                    } catch (FHIRException unknownType) {
                        throw new FHIRException("Unsupported Choice Slicing " + sliceName);
                    }

                    fhirPath.append(".ofType(").append(typeName).append(")");
                    terserPath.append(sliceParts[1]);

                } else {
                    terserPath.append(e);
                }
            } else if (e.contains(":")) {
                String basePath = e.substring(0, e.indexOf(":")).trim();
                fhirPath.append(basePath);
                terserPath.append(basePath);
                List<String> conditions = Slicing.generateConditionsForFHIRPath(String.valueOf(elementIDSoFar), definition);
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


    public String[] resolve(String input, CompiledStructureDefinition definition) throws FHIRException {
        return handleSlicingForFhirPath(input, definition);
    }

    /**
     * Builds a FHIRPath expression with a list of where conditions
     *
     * @param path       path to be handled
     * @param conditions list of condition strings
     * @return FHIRPath with combined where conditions
     */
    public static String buildConditions(String path, List<String> conditions) {
        if (path == null) return null;
        if (conditions == null || conditions.isEmpty()) {
            return path;
        }
        String combinedCondition = String.join(" and ", conditions);
        return String.format("%s.where(%s)", path, combinedCondition);
    }


}
