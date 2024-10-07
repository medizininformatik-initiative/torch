package de.medizininformatikinitiative.torch.model.ccdl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public enum ValueFilterType {
    @JsonProperty("concept")
    CONCEPT,
    @JsonProperty("quantity-comparator")
    QUANTITY_COMPARATOR,
    @JsonProperty("quantity-range")
    QUANTITY_RANGE,
    @JsonProperty("reference")
    REFERENCE
}
