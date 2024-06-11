package de.medizininformatikinitiative.util.CRTDL;

import java.net.URL;
import java.util.List;

public class AttributeGroup {

    URL GroupReference;
    List<Attribute> attributes;
    List <FilterElement> filterElement;

    public AttributeGroup(URL GroupReference) {
        this.GroupReference = GroupReference;
    }

    public void setAttributes(List<Attribute> attributes) {
        this.attributes = attributes;
    }
}
