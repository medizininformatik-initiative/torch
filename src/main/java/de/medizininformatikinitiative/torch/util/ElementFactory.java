package de.medizininformatikinitiative.torch.util;

import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Factory;

public class ElementFactory {

    Factory factory = new Factory();

    /**
     * Creates an empty Base of a given Type.
     * Currently it only calls the factory.create method.
     *
     * @param type
     * @return Base object of the given type
     */
    public Base createElement(String type) {
        if (type != "BackboneElement") {
            return (Base) factory.create(type);
        } else {
            //TODO Custom Backbone Element handler
            return null;
        }

    }

}
