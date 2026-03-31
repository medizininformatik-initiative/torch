package de.medizininformatikinitiative.torch.jobhandling;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record JobParameters(@JsonProperty AnnotatedCrtdl crtdl, @JsonProperty List<String> paramBatch) {
    public JobParameters {
        requireNonNull(crtdl);
        paramBatch = List.copyOf(paramBatch);
    }
}
