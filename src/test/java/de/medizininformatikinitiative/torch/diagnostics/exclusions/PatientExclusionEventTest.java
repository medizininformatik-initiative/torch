package de.medizininformatikinitiative.torch.diagnostics.exclusions;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatientExclusionEventTest {

    @Test
    void toCsvElements_thenFromCsv_roundTripsAllFields() {
        PatientExclusionEvent event = new PatientExclusionEvent(PatientExclusionStage.CASCADING_DELETE, "pat1");

        String[] csvElements = event.toCsvElements();
        String[] csvRow = ArrayUtils.insert(0, csvElements, "batch1");

        PatientExclusionEvent reconstructed = PatientExclusionEvent.fromCsv(csvRow);

        assertThat(reconstructed).isEqualTo(event);
    }

    @Test
    void getHeaderNames_matchesCsvElementsOrder() {
        String[] headers = PatientExclusionEvent.getHeaderNames();

        assertThat(headers).containsExactly("Stage", "Patient-ID");
    }
}
