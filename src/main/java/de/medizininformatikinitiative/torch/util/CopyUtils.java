package de.medizininformatikinitiative.torch.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Util class for copying and redacting.
 * Contains some basic string manipulations and setter reflection
 */
public class CopyUtils {


    private static final Logger logger = LoggerFactory.getLogger(CopyUtils.class);

    /**
     * Get the name of the element from the path by getting the last non empty element after a separator (.)
     *
     * @param path
     * @return
     */
    public static String getElementName(String path) {
        String[] parts = path.split("\\.");
        return parts[parts.length - 1];
    }

    /**
     * Reflects a setMethod for a given class and field name.
     * Quite relevant for e.g. Lists, since they cannot be set directly through constructors or makeproperty in Hapi.
     *
     * @param clazz
     * @param fieldName
     * @return reflected Setter Method
     */
    public static Method reflectListSetter(Class<?> clazz, String fieldName) {
        try {
            return clazz.getMethod("set" + capitalizeFirstLetter(fieldName), java.util.List.class);
        } catch (NoSuchMethodException e) {
            logger.warn(" Class {} does not have the method set{}", clazz.getName(), capitalizeFirstLetter(fieldName));
            return null;
        }
    }


    /**
     * Relevant for setter methods that are in Camel case, but the field name is in lower case.
     *
     * @param str
     * @return capitalized String
     */
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
