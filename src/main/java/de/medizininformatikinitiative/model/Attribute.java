package de.medizininformatikinitiative.util.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Attribute {

    @JsonProperty("attributeRef")
    private String attributeRef;

    @JsonProperty("mustHave")
    private boolean mustHave;

    public Attribute(String s, boolean b) {
        this.attributeRef=s;
        this.mustHave=b;
    }

    // No-argument constructor
    public Attribute() {
    }


    // Getters and Setters
    public String getAttributeRef() {
        return attributeRef;
    }

    public void setAttributeRef(String attributeRef) {
        this.attributeRef = attributeRef;
    }

    public boolean isMustHave() {
        return mustHave;
    }

    public void setMustHave(boolean mustHave) {
        this.mustHave = mustHave;
    }
}
