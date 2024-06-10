package de.medizininformatikinitiative.util.CRTDL;

public abstract class FilterElement {
    private String type;
    private String name;

    public FilterElement(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }
}