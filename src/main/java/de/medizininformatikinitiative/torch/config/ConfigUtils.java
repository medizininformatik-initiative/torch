package de.medizininformatikinitiative.torch.config;

public class ConfigUtils {


    private static final String EMPTY_QUOTES = "\"\"";

    public static boolean isNotSet(String variable) {
        return variable == null || variable.isBlank() || EMPTY_QUOTES.equals(variable);
    }

    public static String removeTrailingSlashes(String url) {
        url = url.strip();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
