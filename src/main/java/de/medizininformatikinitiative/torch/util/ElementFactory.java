package de.medizininformatikinitiative.torch.util;

import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Factory;

/**
 * Factory Class for Empty Base Elements. Can be expanded to BackBoneElements if needed
 */
public class ElementFactory {

    Factory factory = new Factory();

    /**
     * Creates an empty Base of a given Type.
     * Currently, it only calls the factory.create method.
     *
     * @param type
     * @return Base object of the given type
     */
    public Base createElement(String type) {
        if (type != "BackboneElement") {
            return (Base) factory.create(type);
        } else {
            //TODO: If needed custom Backbone Element handler
            return null;
        }

    }

    public Base stringtoPrimitive(String string, String type) {
        switch (type) {
            case "dateTime":
                return new DateTimeType(string);

            // Add any other FHIR primitives here as needed
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }


    }
}
