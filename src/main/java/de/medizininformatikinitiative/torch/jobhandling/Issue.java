package de.medizininformatikinitiative.torch.jobhandling;

import java.util.Optional;

public record Issue(Severity severity, String msg, Optional<Exception> exception) {

    public Issue(Severity severity, String msg) {
        this(severity, msg, Optional.empty());
    }

    public Issue(Severity severity, String msg, Exception e) {
        this(severity, msg, Optional.ofNullable(e));
    }
}
