package de.medizininformatikinitiative.torch.exceptions;

import java.util.UUID;

public class JobNotFoundException extends Exception {

    public JobNotFoundException(UUID jobId) {
        super("Unknown job " + jobId);
    }

    public JobNotFoundException(String jobId) {
        super("Unknown job " + jobId);
    }
}
