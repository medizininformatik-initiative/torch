package de.medizininformatikinitiative.torch.service;

import org.hl7.fhir.r4.model.ListResource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CohortQueryServiceFhirListTest {

    @Test
    void toFhirList_withPatientIds_returnsCurrentWorkingListWithPatientReferences() {
        ListResource list = CohortQueryService.toFhirList(List.of("1", "2"));

        assertThat(list.getStatus()).isEqualTo(ListResource.ListStatus.CURRENT);
        assertThat(list.getMode()).isEqualTo(ListResource.ListMode.WORKING);
        assertThat(list.getEntry()).hasSize(2);
        assertThat(list.getEntry().get(0).getItem().getReference()).isEqualTo("Patient/1");
        assertThat(list.getEntry().get(1).getItem().getReference()).isEqualTo("Patient/2");
    }

    @Test
    void toFhirList_withNoPatientIds_returnsListWithNoEntries() {
        ListResource list = CohortQueryService.toFhirList(List.of());

        assertThat(list.getEntry()).isEmpty();
    }
}
