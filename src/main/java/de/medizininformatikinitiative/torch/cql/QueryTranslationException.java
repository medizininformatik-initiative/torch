package de.medizininformatikinitiative.torch.cql;

/**
 * Thrown when a query translation fails.
 */
public class QueryTranslationException extends Exception {

    /**
     * Constructs a new {@link QueryTranslationException} with the specified detail message.
     *
     * @param message The detail message.
     */
    public QueryTranslationException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@link QueryTranslationException} with the specified detail message and cause.
     *
     * @param message The detail message.
     * @param cause   The cause.
     */
    public QueryTranslationException(String message, Throwable cause) {
        super(message, cause);
    }
}
