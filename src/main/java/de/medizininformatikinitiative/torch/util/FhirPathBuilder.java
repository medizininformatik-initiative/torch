package de.medizininformatikinitiative.torch.util;


import de.medizininformatikinitiative.torch.CdsStructureDefinitionHandler;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Factory;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class FhirPathBuilder {

    Factory factory = new Factory();


    Slicing slicing;

    private static final Logger logger = LoggerFactory.getLogger(FhirPathBuilder.class);
    /**
     * Constructor for Slicing
     *
     * @param handler CDSStructureDefinitionHandler
     */
    public FhirPathBuilder(CdsStructureDefinitionHandler handler) {
    slicing=new Slicing(handler);
    }

    /**
     * For Terser, the base path is the primary concern. This method removes any slicing
     * information that is denoted by colons. For example:
     * - `condition.onset[x]` will not be modified as the method does not handle square brackets.
     * - `Observation.identifier:analyseBefundCode.type.coding` becomes `Observation.identifier.type.coding`.
     *
     * @param input the input string representing the path with potential slicing
     * @param factory an instance of ElementFactory (not currently used in the method)
     * @return a string with slicing removed according to the Terser base path rules
     */
    public  String handleSlicingForTerser(String input) {
        // Remove anything after colons (:) until the next dot (.) or end of the string
        String output = input.replaceAll(":[^\\.]*", "");
        return output;
    }

    // Function to transform the FHIRPath expression
    public  String transformFhirPath(String fhirPath) {
        // Step 1: Remove the ofType clause (e.g., .ofType(CodeableConcept))
        String ofTypeRegex = "\\.ofType\\(([^)]+)\\)";
        fhirPath = fhirPath.replaceAll(ofTypeRegex, "$1");

        // Step 2: Remove the where clause (e.g., .where(condition))
        String whereRegex = "\\.where\\([^)]*\\)";
        fhirPath = fhirPath.replaceAll(whereRegex, "");

        // Step 3: Adjust the 'value' prefix manually
        if (fhirPath.contains("valueCodeableConcept")) {
            fhirPath = fhirPath.replace("value", "valueCodeableConcept");
        }

        return fhirPath;
    }

    public String handleSlicingForFhirPath(String input, StructureDefinition.StructureDefinitionSnapshotComponent snapshot) {
        if (input.contains(":") || input.contains("[x]")) {
            String[] elementIDParts = input.split("\\.");

           // logger.debug("Slicing Found {} size", elementIDParts.length);
            StringBuilder result = new StringBuilder();
            StringBuilder elementIDSoFar = new StringBuilder();
            List<String> conditions = new ArrayList<>();

            boolean isFirstElement = true;  // Flag to track the first element

            for (String e : elementIDParts) {

                if (!isFirstElement) {
                    elementIDSoFar.append(".");
                    result.append(".");  // Add dot only for subsequent elements
                } else {
                    isFirstElement = false;  // After the first iteration, set the flag to false
                }

                elementIDSoFar.append(e);
                logger.debug("Processing {}", elementIDSoFar);

                // Choice Element handling by getting the type slice candidate and then creating a standard fhir type to check if it is known.
                if (e.contains("[x]")) {
                    String[] sliceParts = e.split(":");

                    String path = sliceParts[0].replace("[x]", "");
                    if (sliceParts.length > 1) {
                        String sliceName = sliceParts[1].replace(path, "");
                        Base element;
                        try {
                            element = factory.create(sliceName);
                        } catch (FHIRException upperCaseException) {
                            try {
                                sliceName = sliceName.substring(0, 1).toLowerCase() + sliceName.substring(1);
                                element = factory.create(sliceName);
                            } catch (FHIRException lowerCaseException) {
                                throw new FHIRException("Unsupported Slicing " + sliceName);
                            }
                        }
                        result.append(path).append(".ofType(").append(sliceName).append(")");
                    } else {
                        result.append(path);
                    }
                } else {
                    if (e.contains(":")) {
                        result.append(e.split(":")[0]);
                        //logger.debug("Adding Conditions");
                        conditions.addAll(slicing.generateConditionsForFHIRPath(elementIDSoFar.toString(), snapshot));
                        //logger.debug("Conditions Generated {}",conditions);
                    } else {
                        result.append(e);
                    }
                }
            }

            //logger.debug("Result  {}", result);
            return buildConditions(result.toString(), conditions);
        }
        //logger.debug("Result  {}", input);
        return input;
    }



    // Unified function to handle polymorphic elements [x] and resolve explicit slicing like [x]:valueQuantity
    public static String transformTerserPath(String fhirPath) {
        // Step 1: Replace [x]:slice with just the slice (e.g., value[x]:valueQuantity -> valueQuantity)
        fhirPath = replaceExplicitlySlicedElements(fhirPath);

        // Step 2: Remove any slicing identifiers (e.g., :icd10-gm)
        fhirPath = removeSlicingIdentifiers(fhirPath);

        return fhirPath;
    }

    // Helper function to replace [x]:slice with just the slice (e.g., value[x]:valueQuantity -> valueQuantity)
    private static String replaceExplicitlySlicedElements(String fhirPath) {
        // Regex to match [x]:slice (e.g., value[x]:valueQuantity)
        Pattern pattern = Pattern.compile("(\\[x\\]):([a-zA-Z]+)");
        Matcher matcher = pattern.matcher(fhirPath);

        // Replace [x]:slice with just the slice part
        fhirPath = matcher.replaceAll("$2");  // Keep only the slice type (e.g., valueQuantity)

        return fhirPath;
    }

    // Helper function to remove any slicing components like :icd10-gm
    private static String removeSlicingIdentifiers(String fhirPath) {
        // Regex to match and remove slicing components like :icd10-gm
        Pattern slicingPattern = Pattern.compile(":([a-zA-Z0-9\\-]+)");
        Matcher slicingMatcher = slicingPattern.matcher(fhirPath);

        // Remove any slicing identifier after the colon
        fhirPath = slicingMatcher.replaceAll("");

        return fhirPath;
    }





    /**
     * Builds a FHIRPath expression with a list of where conditions
     *
     * @param path path to be handled
     * @param conditions list of condition strings
     * @return FHIRPath with combined where conditions
     */
    public String buildConditions(String path, List<String> conditions) {
        if(conditions.isEmpty()){
            return path;
        }
        String combinedCondition = conditions.stream()
                .collect(Collectors.joining(" and "));
        return String.format("%s.where(%s)", path, combinedCondition);
    }


}
