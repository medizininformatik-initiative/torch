package de.medizininformatikinitiative.util;

import java.lang.reflect.Method;

public class CopyUtils {

    public static String getElementName(String path) {
        String[] parts = path.split("\\.");
        return parts[parts.length - 1];
    }

    public static Method reflectListSetter(Class<?> clazz, String fieldName) {
        try {
            return clazz.getMethod("set"+capitalizeFirstLetter(fieldName), java.util.List.class);
        } catch ( NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }


    public static String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        // Get the first character and convert it to uppercase
        char firstChar = Character.toUpperCase(str.charAt(0));
        // Get the rest of the string
        String restOfString = str.substring(1);
        // Concatenate the uppercase first character with the rest of the string
        return firstChar + restOfString;
    }

}
