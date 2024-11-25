package de.medizininformatikinitiative.torch.util;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.*;

public class HapiFactory {

    static final Factory FACTORY = new Factory();

    /**
     * The super create method that distinguishes between complex and standard types.
     * For complex types, it returns an empty instance. For other types, it falls back to `create`.
     */
    public static Type create(String name) throws FHIRException {
        switch (name) {
            case "*":
                //Any type allowed
                return new StringType();
            case "Reference(Organization)":
                return new Reference();
            case "Extension":
                return new Extension();
            case "Narrative":
                return new Narrative();
            case "Identifier":
                return new Identifier();
            default:
                // For standard types not considered complex, delegate to the standard create method
                return FACTORY.create(name);
        }
    }
}
