package de.medizininformatikinitiative.util;


import static java.lang.String.format;

public class FHIRPATHbuilder {

    //TODO Expand as needed
    public static String build(String path) {
        return path;
    }


    public static String cleanFHIRPATH(String input) {
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
