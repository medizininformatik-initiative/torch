package de.medizininformatikinitiative.util;

import org.hl7.fhir.r4.model.Extension;

import java.util.List;

public class ElementInfo {
    public String path;
    public String dataType;
    public List<Extension> extensions;
    public int minCardinality;

    @Override
    public String toString() {
        return "ElementInfo{" +
                "path='" + path + '\'' +
                ", dataType='" + dataType + '\'' +
                ", extensions=" + extensions +
                ", minCardinality=" + minCardinality +
                '}';
    }
}
