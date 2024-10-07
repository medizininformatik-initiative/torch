package de.medizininformatikinitiative.torch.model.ccdl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@JsonInclude(Include.NON_NULL)
@Builder
public record Unit(
        @JsonProperty("code") String code,
        @JsonProperty("display") String display
) {

}
