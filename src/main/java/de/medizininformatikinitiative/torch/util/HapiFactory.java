package de.medizininformatikinitiative.torch.util;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Factory;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;

public class HapiFactory {

    static final Factory FACTORY = new Factory();

    /**
     * The super create method that distinguishes between complex and standard types.
     * For complex types, it returns an empty instance. For other types, it falls back to `create`.
     */
    public static Type create(String name) throws FHIRException {
        return switch (name) {
            case "*" ->
                //Any type allowed
                    new StringType();
            case "Extension" -> new Extension();
            case "Narrative" -> new Narrative();
            default ->
                // For standard types not considered complex, delegate to the standard create method
                    FACTORY.create(name);
        };
    }
}
