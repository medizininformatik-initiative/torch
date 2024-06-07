package de.medizininformatikinitiative.util;

import org.hl7.fhir.r4.model.Element;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class Reflector {

    private static final Map<String, String> specialCasesMap = new HashMap<>();

    static {
        // Add special cases here
        specialCasesMap.put("base64binary", "Base64BinaryType");
        specialCasesMap.put("basedatetime", "BaseDateTimeType");
        specialCasesMap.put("boolean", "BooleanType");
        specialCasesMap.put("decimal", "DecimalType");
        specialCasesMap.put("enumeration", "Enumeration");
        specialCasesMap.put("integer64", "Integer64Type");
        specialCasesMap.put("integer", "IntegerType");
        specialCasesMap.put("string", "StringType");
        specialCasesMap.put("time", "TimeType");
        specialCasesMap.put("uri", "UriType");
        specialCasesMap.put("xhtml", "XhtmlType");
        specialCasesMap.put("code", "CodeType");
    }
    static Element getClassForDataType(String dataType) throws ClassNotFoundException, NoSuchMethodException {
        String className = "org.hl7.fhir.r4.model." + dataType;
        Class clazz;
        try {
            clazz=Class.forName(className);
            return (Element) clazz.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            // Try with the "Type" suffix if it fails
            if (specialCasesMap.containsKey(dataType)) {
                className = "org.hl7.fhir.r4.model." + specialCasesMap.get(dataType);
                clazz=Class.forName(className);
                try {
                    return (Element) clazz.getDeclaredConstructor().newInstance();
                } catch (InstantiationException ex) {
                    throw new RuntimeException(ex);
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                } catch (InvocationTargetException ex) {
                    throw new RuntimeException(ex);
                }
            } else {
                throw e; // Rethrow if no special case found
            }

        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
