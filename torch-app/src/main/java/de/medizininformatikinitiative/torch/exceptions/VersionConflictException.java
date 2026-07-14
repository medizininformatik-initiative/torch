package de.medizininformatikinitiative.torch.exceptions;

import java.util.UUID;

public class VersionConflictException extends RuntimeException {

    public VersionConflictException(UUID jobId, long expected, long actual) {
        super("Version conflict for Task/%s: expected %d but current is %d".formatted(jobId, expected, actual));
    }
}
