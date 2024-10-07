package de.medizininformatikinitiative.torch.model.ccdl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@Builder
public record TermCode(
        @JsonProperty("code") String code,
        @JsonProperty("system") String system,
        @JsonProperty("version") String version,
        @JsonProperty("display") String display
) {

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TermCode termCode = (TermCode) o;
        return Objects.equals(code, termCode.code) && Objects.equals(system,
                termCode.system);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, system);
    }
}
