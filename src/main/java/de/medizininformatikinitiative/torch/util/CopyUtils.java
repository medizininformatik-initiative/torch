package de.medizininformatikinitiative.torch.util;

import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Util class for copying and redacting.
 * Contains some basic string manipulations and setter reflection
 */
public class CopyUtils {


    private static final Logger logger = LoggerFactory.getLogger(CopyUtils.class);


    private static final Map<String, String> RESERVED_WORDS = Map.of(
            "class", "Class_",
            "enum", "Enum_",
            "abstract", "Abstract_",
            "default", "Default_"
    );

    /**
     * Sets a field on a FHIR Base object reflectively, handling primitive types, backbone elements, and lists.
     *
     * @param tgt       Base whose values should be set
     * @param fieldName field that should be set
     * @param values    values that should be set for the field
     */
    public static void setFieldReflectively(Base tgt, String fieldName, List<? extends Base> values) {
        if (values == null || values.isEmpty()) return;

        Class<?> clazz = tgt.getClass();
        String cap = capitalizeOrReplaceReserved(fieldName);

        try {
            // --- Get the getter method
            Method getter = clazz.getMethod("get" + cap);
            Class<?> returnType = getter.getReturnType();
            Object currentValue = getter.invoke(tgt);

            // --- 1️⃣ List field: use list.add()
            if (List.class.isAssignableFrom(returnType) && currentValue instanceof List<?> listRaw) {
                handleList(fieldName, values, (List<Base>) listRaw);
                return;
            }

            Base first = values.getFirst();
            if (setSingleParam(tgt, first, clazz, cap)) return;

            throw new IllegalArgumentException("Could not set field " + fieldName + " on " + clazz.getSimpleName());


        } catch (ReflectiveOperationException e) {
            logger.warn("Could not reflectively set field {} on {}", fieldName, clazz.getSimpleName(), e);
        } catch (IllegalArgumentException e) {
            logger.warn("Could not set field {} on {}", fieldName, tgt.fhirType());
            throw new IllegalArgumentException("Could not set field " + fieldName, e);
        }
    }

    /**
     * Sets a single Parameter by reflecting the Setter.
     * <p>
     * In Hapi classes the primitive value handling is somewhat inconsistent for the one parameter setters.
     * Sometimes they take unwrapped java base classes like strings or in the case of DateTimeType a wrapped one for the wrapped
     * and a multi param one for the unwrapped case.
     * <p>
     * This method takes a greedy approach trying both the wrapped and unwrapped value.
     *
     * @param tgt
     * @param first
     * @param clazz
     * @param cap
     * @return
     */
    private static boolean setSingleParam(Base tgt, Base first, Class<?> clazz, String cap) {
        Object unwrapped = first instanceof PrimitiveType<?> primitive
                ? primitive.getValue()
                : first;

        List<Method> candidateSetters = Arrays.stream(clazz.getMethods())
                .filter(m -> m.getName().equals("set" + cap))
                .toList();

        for (Method setter : candidateSetters) {
            Class<?>[] params = setter.getParameterTypes();

            try {
                // --- single-param setters
                if (params.length == 1) {
                    if (params[0].isAssignableFrom(first.getClass())) {
                        setter.invoke(tgt, first); // wrapped
                        return true;
                    }
                    if (params[0].isAssignableFrom(unwrapped.getClass())) {
                        setter.invoke(tgt, unwrapped); // unwrapped
                        return true;
                    }
                }
            } catch (ReflectiveOperationException e) {
                // continue trying other setters
            }
        }
        return false;
    }

    /**
     * Handles list by adding all values of the mutable list
     *
     * @param fieldName field to be set
     * @param values
     * @param listRaw
     */
    private static void handleList(String fieldName, List<? extends Base> values, List<Base> listRaw) {
        try {
            @SuppressWarnings("unchecked")
            List<Base> list = listRaw;
            list.addAll(values);
            return;
        } catch (ClassCastException e) {
            // List element type is incompatible with Base
            throw new IllegalArgumentException(
                    "Field " + fieldName + " is not a List<Base>", e);
        }
    }

    /**
     * Replaces known reserved java words used in FHIR with their hapi field names.
     *
     * @param fieldName name to be capitalized
     * @return capitalized or replaced field name in case of reserved words such as class.
     */
    private static String capitalizeOrReplaceReserved(String fieldName) {
        String mapped = RESERVED_WORDS.getOrDefault(fieldName, fieldName);
        return capitalizeFirstLetter(mapped);
    }


    /**
     * Relevant for setter methods that are in Camel case, but the field name is in lower case.
     *
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
