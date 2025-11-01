package de.medizininformatikinitiative.torch.model.crtdl;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

public record FieldCondition(String fieldName, String condition) {

    public FieldCondition {
        requireNonNull(fieldName, "fieldName must not be null");
        requireNonNull(condition, "condition must not be null");
    }


    public String fhirPath() {
        return fieldName + condition;
    }


    /**
     * Splits a FHIR path segment into a field and a condition (e.g., `.where(...)`, `.ofType(...)` or `.type(...)`).
     * <p>
     * If the segment contains a function call, the field will be everything before the first function,
     * and the condition will include the function call itself (including parentheses and nested content).
     *
     * @param segment the FHIR path segment to parse
     * @return a {@link FieldCondition} object with field and condition separated
     */
    private static FieldCondition parseSegment(String segment) {
        String[] functions = {"where(", "ofType(", "type("};

        int firstFuncIndex = -1;

        for (String func : functions) {
            int idx = segment.indexOf("." + func);
            if (idx >= 0 && (firstFuncIndex == -1 || idx < firstFuncIndex)) {
                firstFuncIndex = idx;
            }
        }

        if (firstFuncIndex >= 0) {
            // Field is everything before the first function
            String field = segment.substring(0, firstFuncIndex).trim();
            // Condition is everything from the first function onwards
            String cond = segment.substring(firstFuncIndex).trim();
            return new FieldCondition(field, cond);
        } else {
            return new FieldCondition(segment.trim(), "");
        }
    }

    /**
     * Splits a FHIRPath expression into sequential segments as {@link FieldCondition} objects.
     * <p>
     * Rules for splitting:
     * <ul>
     *     <li>Splits after each {@code where} clause, keeping the clause as part of the condition.</li>
     *     <li>Splits at each {@code .} (dot) when not inside parentheses and not immediately followed by a function call like {@code where()} or {@code ofType()}.</li>
     *     <li>Keeps nested function calls intact.</li>
     * </ul>
     * Example:
     * <pre>
     * Input: "Observation.value.ofType(Quantity).where(value &gt; 5).unit"
     * Output: [
     *     FieldCondition(field="Observation", condition=""),
     *     FieldCondition(field="value", condition=".ofType(Quantity)"),
     *     FieldCondition(field="", condition=".where(value &gt; 5)"),
     *     FieldCondition(field="unit", condition="")
     * ]
     * </pre>
     *
     * @return a non-null {@link List} of {@link FieldCondition} objects representing each segment of the FHIRPath
     */
    public static List<FieldCondition> splitFhirPath(AnnotatedAttribute attr) {
        String fhirPath = attr.fhirPath();
        List<FieldCondition> result = new ArrayList<>();
        int start = 0;
        int parenDepth = 0;

        for (int i = 0; i < fhirPath.length(); i++) {
            char c = fhirPath.charAt(i);
            if (c == '(') parenDepth++;
            else if (c == ')') parenDepth--;
            else if (c == '.' && parenDepth == 0) {
                // Look ahead to check for function calls: where(), ofType(), type()
                String remaining = fhirPath.substring(i + 1);
                if (remaining.startsWith("where(") || remaining.startsWith("ofType(") || remaining.startsWith("type(")) {
                    continue; // skip splitting for this dot
                }
                result.add(parseSegment(fhirPath.substring(start, i)));
                start = i + 1;
            }
        }

        if (start < fhirPath.length()) {
            result.add(parseSegment(fhirPath.substring(start)));
        }

        return result;
    }
}
