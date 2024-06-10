package de.medizininformatikinitiative.util.CRTDL;

class DateFilterElement extends FilterElement {
    private String start;
    private String end;

    public DateFilterElement(String name, String start, String end) {
        super("date", name);
        this.start = start;
        this.end = end;
    }

    public String getStart() {
        return start;
    }

    public String getEnd() {
        return end;
    }
}