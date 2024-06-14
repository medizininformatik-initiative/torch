package de.medizininformatikinitiative.util;

import static java.lang.String.format;

public class FHIRPATHbuilder {

    //TODO Expand as needed
    public static String build(String path) {
        return path;
    }

    public static String build(String path, String condition) {
        return format("%s.where(%s)", path, condition);
    }


}
