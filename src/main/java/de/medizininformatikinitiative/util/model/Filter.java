package de.medizininformatikinitiative.util.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.util.model.Code;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Filter {

    // No-argument constructor
    public Filter() {
    }
    @JsonProperty("type")
    private String type;

    @JsonProperty("name")
    private String name;

    @JsonProperty("codes")
    private List<Code> codes;

    @JsonProperty("start")
    private String start;

    @JsonProperty("end")
    private String end;

    // Getters and Setters
}
