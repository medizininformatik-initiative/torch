package de.medizininformatikinitiative.torch.util;


import de.medizininformatikinitiative.torch.CdsStructureDefinitionHandler;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class FhirPathBuilder {


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



    public String handleSlicingForFhirPath(String input, ElementFactory factory, StructureDefinition.StructureDefinitionSnapshotComponent snapshot) {
        if (input.contains(":") || input.contains("[x]")) {
            String[] elementIDParts = input.split("\\.");

            logger.info("Slicing Found {} size", elementIDParts.length);
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
                logger.info("Processing {}", elementIDSoFar);

                // Choice Element handling
                if (e.contains("[x]")) {
                    String[] sliceParts = e.split(":");

                    String path = sliceParts[0].replace("[x]", "");
                    if (sliceParts.length > 1) {
                        String sliceName = sliceParts[1].replace(path, "");
                        Base element;
                        try {
                            element = factory.createElement(sliceName);
                        } catch (FHIRException upperCaseException) {
                            try {
                                sliceName = sliceName.substring(0, 1).toLowerCase() + sliceName.substring(1);
                                element = factory.createElement(sliceName);
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
                        logger.info("Adding Conditions");
                        conditions.addAll(slicing.generateConditionsForFHIRPath(elementIDSoFar.toString(), snapshot));
                        logger.info("Conditions Generated {}",conditions);
                    } else {
                        result.append(e);
                    }
                }
            }

            logger.info("Result  {}", result);
            return buildConditions(result.toString(), conditions);
        }
        logger.info("Result  {}", input);
        return input;
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
