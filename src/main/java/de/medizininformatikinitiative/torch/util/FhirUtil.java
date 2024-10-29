package de.medizininformatikinitiative.torch.util;


import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Extension;

public class FhirUtil {

    /**
     * Creates an Extension for DataAbsentReason with a predefined reason code.
     *
     * @param reasonCode The predefined reason code for the absence of data (e.g., "unknown", "masked").
     * @return An Extension object representing the DataAbsentReason.
     */
    public static Extension createAbsentReasonExtension(String reasonCode) {
        return new Extension()
                .setUrl("http://hl7.org/fhir/StructureDefinition/data-absent-reason")
                .setValue(new CodeType(reasonCode));
    }




}
