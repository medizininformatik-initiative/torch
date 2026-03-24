package de.medizininformatikinitiative.torch.exceptions;

public class VersionConflictException extends RuntimeException {
    public VersionConflictException(String message) {
        super(message);
    }
}
