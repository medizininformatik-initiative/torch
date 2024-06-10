package de.medizininformatikinitiative.util.CRTDL;


class Code {
    private String code;
    private String system;
    private String display;

    public Code(String code, String system, String display) {
        this.code = code;
        this.system = system;
        this.display = display;
    }

    public String getCode() {
        return code;
    }

    public String getSystem() {
        return system;
    }

    public String getDisplay() {
        return display;
    }
}