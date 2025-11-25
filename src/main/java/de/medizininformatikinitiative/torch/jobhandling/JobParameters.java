package de.medizininformatikinitiative.torch.jobhandling;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record JobParameters(AnnotatedCrtdl crtdl, List<String> paramBatch) {
    public JobParameters {
        requireNonNull(crtdl);
        paramBatch = List.copyOf(paramBatch);
    }
}
