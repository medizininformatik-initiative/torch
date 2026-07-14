package de.medizininformatikinitiative.torch.model.extraction;

import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.extraction.ResourceExtractionInfo;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionPatientBatchTest {

    @Test
    void of_convertsPatientBatchWithConsent() {
        var prb = new PatientResourceBundle("p1");
        var bwc = PatientBatchWithConsent.fromList(java.util.List.of(prb));

        var result = ExtractionPatientBatch.of(bwc);

        assertThat(result.bundles()).containsKey("p1");
        assertThat(result.id()).isEqualTo(bwc.id());
    }

    @Test
    void isEmpty_whenAllBundlesEmpty_returnsTrue() {
        var batch = new ExtractionPatientBatch(Map.of("p1", new ExtractionResourceBundle()), UUID.randomUUID());
        assertThat(batch.isEmpty()).isTrue();
    }

    @Test
    void isEmpty_whenBundleHasResource_returnsFalse() {
        var id = ExtractionId.fromRelativeUrl("Patient/1");
        var infoMap = new ConcurrentHashMap<ExtractionId, ResourceExtractionInfo>();
        infoMap.put(id, new ResourceExtractionInfo(Set.of("G1"), Map.of()));
        var cache = new ConcurrentHashMap<ExtractionId, Optional<Resource>>();
        cache.put(id, Optional.of(new Patient()));
        var bundle = new ExtractionResourceBundle(infoMap, cache);

        var batch = new ExtractionPatientBatch(Map.of("p1", bundle), UUID.randomUUID());

        assertThat(batch.isEmpty()).isFalse();
    }

    @Test
    void get_returnsCorrectBundle() {
        var bundle = new ExtractionResourceBundle();
        var batch = new ExtractionPatientBatch(Map.of("p1", bundle), UUID.randomUUID());

        assertThat(batch.get("p1")).isSameAs(bundle);
        assertThat(batch.get("p2")).isNull();
    }

    @Test
    void totalResources_countsOnlyPresentOptionals() {
        var cache = new ConcurrentHashMap<ExtractionId, Optional<Resource>>();
        cache.put(ExtractionId.fromRelativeUrl("Patient/1"), Optional.of(new Patient()));
        cache.put(ExtractionId.fromRelativeUrl("Patient/2"), Optional.empty());
        var bundle = new ExtractionResourceBundle(new ConcurrentHashMap<>(), cache);

        var batch = new ExtractionPatientBatch(Map.of("p1", bundle), UUID.randomUUID());

        assertThat(batch.totalResources()).isEqualTo(1L);
    }

    @Test
    void totalResources_acrossMultipleBundles() {
        var cache1 = new ConcurrentHashMap<ExtractionId, Optional<Resource>>();
        cache1.put(ExtractionId.fromRelativeUrl("Patient/1"), Optional.of(new Patient()));
        var cache2 = new ConcurrentHashMap<ExtractionId, Optional<Resource>>();
        cache2.put(ExtractionId.fromRelativeUrl("Patient/2"), Optional.of(new Patient()));

        var batch = new ExtractionPatientBatch(Map.of(
                "p1", new ExtractionResourceBundle(new ConcurrentHashMap<>(), cache1),
                "p2", new ExtractionResourceBundle(new ConcurrentHashMap<>(), cache2)
        ), UUID.randomUUID());

        assertThat(batch.totalResources()).isEqualTo(2L);
    }
}
