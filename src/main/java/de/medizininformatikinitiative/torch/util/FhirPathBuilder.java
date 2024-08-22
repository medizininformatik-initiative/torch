package de.medizininformatikinitiative.torch.util;


import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Base;

import static java.lang.String.format;

public class FhirPathBuilder {



    /**
     *
     *
     * @param input
     * @param Terser
     * @param factory
     * @return
     *
     * Type slicing is always coded with choice element e.g. value[x].
     */
    public static String handleChoiceElements(String input, boolean Terser, ElementFactory factory){


        return "Test";
    }

    /**
     * For Terser, the base path is the primary concern. For example, with a choice operator,
     * we need to remove `[x]` and any slicing.
     * For instance, `condition.onset[x]` becomes `condition.onset`, and
     * `Observation.identifier:analyseBefundCode.type.coding:observationInstanceV2` becomes
     * `Observation.identifier.type.coding`.
     *
     * @param input the input string representing the path with potential slicing
     * @param factory an instance of ElementFactory (not currently used in the method)
     * @return a string with slicing removed according to the Terser base path rules
     */
    public static String handleSlicingForTerser(String input) {
        // Remove anything in square brackets, such as [x], and remove any slicing with colons
        String output = input.replaceAll(":[^\\.]*", "");
        return output;
    }


    //Handles Elementdefinition Slicing in a fhir PATH of the form e.g. onset[x]:onsetPeriod
    public static String handleSlicingForFhirPath(String input, boolean Terser, ElementFactory factory){
        if(input.contains(":")) {

            String[] elementIDParts = input.split("\\.");
            StringBuilder result;
            result = new StringBuilder();
            for (String e : elementIDParts) {
                if (e.contains(":")) {
                    String[] sliceParts = e.split(":");
                    String path = sliceParts[0].replace("[x]", "");
                    if(Terser){
                        result.append(".").append(sliceParts[1]);
                    }else {
                        String sliceName = sliceParts[1].replace(path, "");
                        Base element;
                        try{
                            element= factory.createElement(sliceName);
                        }catch(FHIRException upperCaseException){
                            try {
                                sliceName = sliceName.substring(0, 1).toLowerCase() + sliceName.substring(1);
                                element = factory.createElement(sliceName);
                            }catch(FHIRException lowerCaseException){
                                //TODO: get Slicing from StrucutreDefinition and build correct where(....) clause
                                throw new FHIRException("Unsupported Slicing "+sliceName);
                            }
                        }

                        result.append(".").append(path).append(".as(").append(sliceName).append(")");
                    }
                }else{
                    if(result.isEmpty()){
                        result.append(e);
                    }else {
                        result.append(".").append(e);
                    }
                }

            }
            return result.toString();
        }
        return input;
    }



    public static String cleanFhirPath(String input) {
        StringBuilder result = new StringBuilder();
        int length = input.length();
        int i = 0;

        while (i < length) {
            char currentChar = input.charAt(i);
            if (currentChar == '[') {
                // Skip until the closing bracket and the character following it (either a colon or a period)
                while (i < length && input.charAt(i) != ']') {
                    i++;
                }
                i++; // Skip the closing bracket
                if (i < length && (input.charAt(i) == ':' || input.charAt(i) == '.')) {
                    i++; // Skip the colon or period
                }
            } else {
                result.append(currentChar);
                i++;
            }
        }

        return result.toString();
    }
    /**
     * Builds a FHIRPath expression with a simple where condition
     *
     * @param path path to be handled
     * @param condition condition string
     * @return Fhir path with where condition
     */
    public static String build(String path, String condition) {
        return format("%s.where(%s)", path, condition);
    }



}
