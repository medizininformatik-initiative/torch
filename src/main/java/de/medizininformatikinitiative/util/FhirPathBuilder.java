package de.medizininformatikinitiative.util;


import static java.lang.String.format;

public class FhirPathBuilder {

    //TODO Expand as needed
    public static String build(String path) {
        return path;
    }

    //Handles Elementdefinition Slicing in a fhir PATH of the form e.g. onset[x]:onsetPeriod
    public static String handleSlicingForFhirPath(String input, boolean Terser){
        if(input.contains(":")) {

            String[] elementIDParts = input.split("\\.");
            String result;
            result = "";
            for (String e : elementIDParts) {
                if (e.contains(":")) {
                    String[] sliceParts = e.split(":");
                    String path = sliceParts[0].replace("[x]", "");
                    if(Terser){
                        result+="."+sliceParts[1];
                    }else {
                        String sliceName = sliceParts[1].replace(path, "");
                        String toLowerCase = sliceName.substring(0, 1).toLowerCase() + sliceName.substring(1);
                        result += "." + path + ".as(" +toLowerCase  + ")";
                    }
                }else{
                    if(result.isEmpty()){
                        result+=e;
                    }else {
                        result += "." + e;
                    }
                }

            }
            return result;
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
     * @param path
     * @param condition
     * @return
     */
    public static String build(String path, String condition) {
        return format("%s.where(%s)", path, condition);
    }



}
