package de.medizininformatikinitiative.util.CRTDL;

public class Attribute {

    public String AttributeRef;
    public Boolean mustHave;

    public Attribute(String s, boolean b) {
        this.AttributeRef = s;
        this.mustHave = b;
    }
}
