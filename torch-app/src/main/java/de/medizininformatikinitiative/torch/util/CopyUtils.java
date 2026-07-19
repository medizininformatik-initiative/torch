package de.medizininformatikinitiative.torch.util;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.PrimitiveType;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Util class for copying and redacting.
 * Contains some basic string manipulations and setter reflection
 */
public class CopyUtils {


    private static final Map<String, String> RESERVED_WORDS = Map.of(
            "class", "Class_",
            "enum", "Enum_",
            "abstract", "Abstract_",
            "default", "Default_"
    );

    private static final Map<CacheKey, SetterEntry> setterCache = new ConcurrentHashMap<>();

    /**
     * Sets a field on a FHIR resource. Falls back to reflection + cached setter if Base.setProperty fails.
     *
     * @param tgt       the resource to modify
     * @param fieldName field name to set
     * @param values    list of values to set
     */
    public static void setFieldReflectively(Base tgt, String fieldName, List<? extends Base> values) throws ReflectiveOperationException {
        if (values == null || values.isEmpty()) return;

        String mappedField = RESERVED_WORDS.getOrDefault(fieldName, fieldName);
        try {
            for (Base value : values) {

                tgt.setProperty(mappedField, value);
            }
        } catch (IllegalArgumentException | FHIRException e) {
            // fast path failed, fallback to reflection
            if (values.size() == 1) {
                invokeSetterWithCache(tgt, mappedField, values.getFirst());
            } else {
                throw new ReflectiveOperationException("Set Property not sufficient for list in " + fieldName + " for Class" + tgt.getClass());
            }

        }
    }

    private static void invokeSetterWithCache(Base tgt, String fieldName, Base value) throws ReflectiveOperationException {
        CacheKey key = new CacheKey(tgt.getClass(), fieldName, value.getClass());
        SetterEntry entry = setterCache.computeIfAbsent(key, k -> {
            Method method = findSetter(k.clazz, k.fieldName, value);
            boolean unwrap = method != null && value instanceof PrimitiveType<?> &&
                    !method.getParameterTypes()[0].isAssignableFrom(value.getClass());
            return method != null ? new SetterEntry(method, unwrap) : null;
        });

        if (entry == null) {
            throw new ReflectiveOperationException("No setter found for field " + fieldName + " with value type " + value.getClass());
        }

        try {
            Object arg = entry.useUnwrapped && value instanceof PrimitiveType<?> p ? p.getValue() : value;
            entry.method.invoke(tgt, arg);
        } catch (Exception e) {
            throw new ReflectiveOperationException("Failed to invoke setter for field " + fieldName, e);
        }
    }

    /**
     * Finds setter for the value as a fallback.
     * For primitive Values it also checks for the unwrapped primitive due to HAPI not always having a setter for the
     * primitive Value, but only for the internal value.
     * <p>
     * Unwrapped example identifier.system takes string and not Stringtype
     * deceased is not a fieldname for a property in patient
     *
     * @param clazz     class of the resource to modify
     * @param fieldName field name to set
     * @param value     value to bes set
     * @return setter found for the value
     */
    private static Method findSetter(Class<?> clazz, String fieldName, Base value) {
        String cap = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        Class<?> valueClazz = value.getClass();

        Object unwrapped = null;
        if (value instanceof PrimitiveType<?> primitiveType) {
            unwrapped = primitiveType.getValue();
        }

        // --- exact match on wrapped type
        Method setter = Arrays.stream(clazz.getMethods())
                .filter(m -> m.getName().equals("set" + cap))
                .filter(m -> m.getParameterCount() == 1)
                .filter(m -> m.getParameterTypes()[0].isAssignableFrom(valueClazz))
                .findFirst()
                .orElse(null);

        // --- fallback on unwrapped primitive
        if (setter == null && unwrapped != null) {
            Class<?> unwrappedClass = unwrapped.getClass();
            setter = Arrays.stream(clazz.getMethods())
                    .filter(m -> m.getName().equals("set" + cap))
                    .filter(m -> m.getParameterCount() == 1)
                    .filter(m -> m.getParameterTypes()[0].isAssignableFrom(unwrappedClass))
                    .findFirst()
                    .orElse(null);
        }

        return setter;
    }

    private record CacheKey(Class<?> clazz, String fieldName, Class<?> valueClass) {
    }

    private record SetterEntry(Method method, boolean useUnwrapped) {
    }
}
