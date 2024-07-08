package de.medizininformatikinitiative.flare.model.sq;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A terminology code, coding a concept.
 * <p>
 * Instances are immutable and implement {@code equals} and {@code hashCode} based on {@link #system() system} and
 * {@link #code() code}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TermCode(String system, String code, String display) {

    public TermCode {
        requireNonNull(system);
        requireNonNull(code);
        requireNonNull(display);
    }

    /**
     * Returns a terminology code.
     *
     * @param system  the terminology to use (mostly represented by an URL)
     * @param code    the code within the terminology
     * @param display a human-readable string of the concept coded
     * @return the terminology code
     */
    @JsonCreator
    public static TermCode of(@JsonProperty("system") String system, @JsonProperty("code") String code,
                              @JsonProperty("display") String display) {
        return new TermCode(system, code, display);
    }

    public static TermCode fromJsonNode(JsonNode node) {
        return TermCode.of(node.get("system").asText(), node.get("code").asText(), node.get("display").asText());
    }

    /**
     * Returns a string that can be used as FHIR search parameter value.
     *
     * @return a string that can be used as FHIR search parameter value
     */
    public String searchValue() {
        return system + "|" + code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TermCode termCode = (TermCode) o;
        return system.equals(termCode.system) && code.equals(termCode.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(system, code);
    }
}
