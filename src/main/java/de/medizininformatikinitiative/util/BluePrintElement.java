package de.medizininformatikinitiative.util;

import org.hl7.fhir.r4.model.Element;
import org.hl7.fhir.r4.model.Extension;

import java.util.List;

//node
public class BluePrintElement {
    public String path;
    public String dataType;
    public List<Extension> extensions;
    public int minCardinality;

    public Element element;

    public List<String> children;


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
