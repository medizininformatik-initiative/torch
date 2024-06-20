package de.medizininformatikinitiative.util;

import static java.lang.String.format;

public class FHIRPATHbuilder {

    //TODO Expand as needed
    public static String build(String path) {
        return path;
    }


    /**
     * Builds a FHIRPath expression with a simple where condition
     * @param path
     * @param condition
     * @return
     */
    public static String build(String path, String condition) {
        return format("%s.where(%s)", path, condition);
    }


}
