package de.medizininformatikinitiative.torch.exception;

public class ConsentViolatedException extends Exception {

    public ConsentViolatedException(String errorMessage) {
        super(errorMessage);
    }

}
