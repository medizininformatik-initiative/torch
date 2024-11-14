package de.medizininformatikinitiative.torch.util;

import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.DateTimeType;

/**
 * Factory Class for Empty Base Elements. Can be expanded to BackBoneElements if needed
 */
public class ElementFactory {

    public static Base stringtoPrimitive(String string, String type) {
        if (type.equals("dateTime")) {
            return new DateTimeType(string);
            // Add any other FHIR primitives here as needed
        }
        throw new IllegalArgumentException("Unknown type: " + type);


    }
}
