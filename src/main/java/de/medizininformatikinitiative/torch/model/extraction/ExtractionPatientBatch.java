package de.medizininformatikinitiative.torch.model.extraction;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import org.hl7.fhir.r4.model.Bundle;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.stream.Collectors;

public record ExtractionPatientBatch(Map<String, ExtractionResourceBundle> bundles,
                                     ExtractionResourceBundle coreBundle) {

    public ExtractionPatientBatch(Map<String, ExtractionResourceBundle> bundles) {
        this(bundles, new ExtractionResourceBundle());
    }

    public static ExtractionPatientBatch of(PatientBatchWithConsent patientBatch) {
        Map<String, ExtractionResourceBundle> converted =
                patientBatch.bundles().entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> ExtractionResourceBundle.of(e.getValue())   // <-- CORRECT call
                        ));
        ExtractionResourceBundle core = ExtractionResourceBundle.of(patientBatch.coreBundle());
        return new ExtractionPatientBatch(converted, core);
    }

    public Boolean isEmpty() {
        return bundles.values().stream().allMatch(ExtractionResourceBundle::isEmpty);
    }

    public void writeToFhirBundles(FhirContext fhirContext, Writer out, String extractionId) throws IOException {
        for (Bundle fhirBundle : bundles.values().stream().map(bundle -> bundle.toFhirBundle(extractionId)).toList()) {
            fhirContext.newJsonParser().setPrettyPrint(false).encodeResourceToWriter(fhirBundle, out);
            out.append("\n");
        }
    }

    public ExtractionResourceBundle get(String id) {
        return bundles.get(id);
    }
}
