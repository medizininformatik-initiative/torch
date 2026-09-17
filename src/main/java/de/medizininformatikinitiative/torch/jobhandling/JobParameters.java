package de.medizininformatikinitiative.torch.jobhandling;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;

import java.util.List;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JobParameters(
        @JsonProperty AnnotatedCrtdl crtdl,
        @JsonProperty List<String> paramBatch,
        @JsonProperty String kickOffUrl,
        @JsonProperty boolean consentDiagnostics
) {
    public JobParameters {
        requireNonNull(crtdl);
        paramBatch = List.copyOf(paramBatch);
    }

    public JobParameters(AnnotatedCrtdl crtdl, List<String> paramBatch, String kickOffUrl) {
        this(crtdl, paramBatch, kickOffUrl, false);
    }
}
