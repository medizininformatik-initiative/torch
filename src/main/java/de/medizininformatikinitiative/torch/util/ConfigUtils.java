package de.medizininformatikinitiative.torch.util;

public class ConfigUtils {

    private static final String EMPTY_QUOTES = "\"\"";

    public static boolean isNotSet(String variable) {
        return variable == null || variable.isBlank() || EMPTY_QUOTES.equals(variable);
    }
}
