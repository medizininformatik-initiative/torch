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
        @JsonProperty String kickOffUrl
) {
    public JobParameters {
        requireNonNull(crtdl);
        paramBatch = List.copyOf(paramBatch);
    }
}
