package de.medizininformatikinitiative.util.CRTDL;

public class Attribute {

    String AttributeRef;
    Boolean mustHave;

    public Attribute(String s, boolean b) {
        this.AttributeRef = s;
        this.mustHave = b;
    }
}
