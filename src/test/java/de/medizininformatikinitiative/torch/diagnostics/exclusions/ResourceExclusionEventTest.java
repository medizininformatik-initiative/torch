package de.medizininformatikinitiative.torch.diagnostics.exclusions;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceExclusionEventTest {

    @Test
    void toCsvElements_thenFromCsv_roundTripsAllFields() {
        ResourceExclusionEvent event = new ResourceExclusionEvent(
                ResourceExclusionReason.MUST_HAVE, "grp", "Observation/1", "pat1", "Obs.code");

        String[] csvElements = event.toCsvElements();
        String[] csvRow = ArrayUtils.insert(0, csvElements, "batch1");

        ResourceExclusionEvent reconstructed = ResourceExclusionEvent.fromCsv(csvRow);

        assertThat(reconstructed).isEqualTo(event);
    }

    @Test
    void getHeaderNames_matchesCsvElementsOrder() {
        String[] headers = ResourceExclusionEvent.getHeaderNames();

        assertThat(headers).containsExactly("Reason", "Group", "Attribute", "Resource-ID", "Patient-ID");
    }
}
