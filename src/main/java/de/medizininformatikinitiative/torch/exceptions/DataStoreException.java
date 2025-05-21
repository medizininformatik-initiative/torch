package de.medizininformatikinitiative.torch.exceptions;

public class DataStoreException extends Exception {

    public DataStoreException(String errorMessage) {
        super(errorMessage);
    }

    public DataStoreException(String message, Throwable cause) {
        super(message, cause);
    }

}
