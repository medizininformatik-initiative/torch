package de.medizininformatikinitiative.torch.exception;

public class MustHaveViolatedException extends Exception {

    public MustHaveViolatedException(String errorMessage) {
        super(errorMessage);
    }

}
