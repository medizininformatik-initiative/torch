package de.medizininformatikinitiative.torch.model.ccdl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public enum Comparator {
    @JsonProperty("eq")
    EQUAL,
    @JsonProperty("ue")
    UNEQUAL,
    @JsonProperty("le")
    LESS_EQUAL,
    @JsonProperty("lt")
    LESS_THAN,
    @JsonProperty("ge")
    GREATER_EQUAL,
    @JsonProperty("gt")
    GREATER_THAN
}
