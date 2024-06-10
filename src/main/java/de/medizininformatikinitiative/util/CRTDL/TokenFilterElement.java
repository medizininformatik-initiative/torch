package de.medizininformatikinitiative.util.CRTDL;

import java.util.List;

class TokenFilterElement extends FilterElement {
    private List<Code> codes;

    public TokenFilterElement(String name, List<Code> codes) {
        super("token", name);
        this.codes = codes;
    }

    public List<Code> getCodes() {
        return codes;
    }
}