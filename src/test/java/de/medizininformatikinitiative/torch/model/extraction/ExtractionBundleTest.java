package de.medizininformatikinitiative.torch.model.extraction;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;


class ExtractionBundleToFhirResourceBundleTest {

    @Test
    void createsOneProvenancePerGroup() {

        ConcurrentHashMap<String, ResourceExtractionInfo> infoMap = new ConcurrentHashMap<>(
                Map.of(
                        "R1", new ResourceExtractionInfo(Set.of("G1", "G2"), Map.of()),
                        "R2", new ResourceExtractionInfo(Set.of("G1"), Map.of()),
                        "R3", new ResourceExtractionInfo(Set.of("G3"), Map.of())
                )
        );

        ExtractionResourceBundle bundle = new ExtractionResourceBundle(infoMap, new ConcurrentHashMap<>());

        List<Provenance> prov = bundle.buildProvenance("EX123");

        // We expect 3 groups: G1, G2, G3
        assertThat(prov).hasSize(3);

        Provenance pG1 = prov.stream()
                .filter(p -> p.getId().contains("G1"))
                .findFirst().orElseThrow();

        Provenance pG2 = prov.stream()
                .filter(p -> p.getId().contains("G2"))
                .findFirst().orElseThrow();

        Provenance pG3 = prov.stream()
                .filter(p -> p.getId().contains("G3"))
                .findFirst().orElseThrow();

        // Check targets
        assertThat(pG1.getTarget())
                .extracting(Reference::getReference)
                .containsExactlyInAnyOrder("R1", "R2");

        assertThat(pG2.getTarget())
                .extracting(Reference::getReference)
                .containsExactly("R1");

        assertThat(pG3.getTarget())
                .extracting(Reference::getReference)
                .containsExactly("R3");
    }

    @Test
    void toFhirBundle_addsResourcesThenProvenance_inDeterministicOrder() {

        // -------------------------
        // 1) Setup ExtractionBundle
        // -------------------------
        ConcurrentHashMap<String, Optional<org.hl7.fhir.r4.model.Resource>> cache =
                new ConcurrentHashMap<>();

        // Add cached resources in deliberately scrambled order
        Patient p2 = new Patient();
        p2.setId("Patient/2");
        Patient p1 = new Patient();
        p1.setId("Patient/1");
        Patient p3 = new Patient();
        p3.setId("Patient/3");

        cache.put("Patient/3", Optional.of(p3));
        cache.put("Patient/1", Optional.of(p1));
        cache.put("Patient/2", Optional.of(p2));

        // Extraction info:
        // P1 in groups G1,G2
        // P2 in group G1
        // P3 in group G3
        Map<String, ResourceExtractionInfo> info = Map.of(
                "Patient/1", new ResourceExtractionInfo(Set.of("G1", "G2"), Map.of()),
                "Patient/2", new ResourceExtractionInfo(Set.of("G1"), Map.of()),
                "Patient/3", new ResourceExtractionInfo(Set.of("G3"), Map.of())
        );

        ExtractionResourceBundle bundle = new ExtractionResourceBundle(new ConcurrentHashMap<>(info), cache);

        // -------------------------
        // 2) Execute conversion
        // -------------------------
        Bundle fhir = bundle.toFhirBundle("EX123");

        // Basic metadata
        assertThat(fhir.getType()).isEqualTo(Bundle.BundleType.TRANSACTION);
        assertThat(fhir.getId()).isNotNull();

        // -------------------------
        // 3) Verify ordering
        // -------------------------
        // Expected order:
        //   1) Patient/1
        //   2) Patient/2
        //   3) Patient/3
        //   4) Provenance/G1
        //   5) Provenance/G2
        //   6) Provenance/G3

        assertThat(fhir.getEntry())
                .hasSize(6);

        // Extract entry URLs for ordering check
        var urls = fhir.getEntry().stream()
                .map(e -> e.getRequest().getUrl())
                .toList();

        assertThat(urls).containsExactly(
                "Patient/1",
                "Patient/2",
                "Patient/3",
                "Provenance/torch-G1",
                "Provenance/torch-G2",
                "Provenance/torch-G3"
        );

        // -------------------------
        // 4) Verify Provenance correctness
        // -------------------------
        Provenance pG1 = (Provenance) fhir.getEntry().get(3).getResource();
        Provenance pG2 = (Provenance) fhir.getEntry().get(4).getResource();
        Provenance pG3 = (Provenance) fhir.getEntry().get(5).getResource();

        assertThat(pG1.getTarget())
                .extracting(Reference::getReference)
                .containsExactlyInAnyOrder("Patient/1", "Patient/2");

        assertThat(pG2.getTarget())
                .extracting(Reference::getReference)
                .containsExactly("Patient/1");

        assertThat(pG3.getTarget())
                .extracting(Reference::getReference)
                .containsExactly("Patient/3");
    }

    @Test
    void emptyExtractionBundleProducesEmptyTransactionBundle() {
        ExtractionResourceBundle bundle = new ExtractionResourceBundle(
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>()
        );

        Bundle fhir = bundle.toFhirBundle("EX123");

        assertThat(fhir.getEntry()).isEmpty();
        assertThat(fhir.getType()).isEqualTo(Bundle.BundleType.TRANSACTION);
    }

    @Test
    void optionalEmptyResourcesAreSkipped() {
        ConcurrentHashMap<String, Optional<org.hl7.fhir.r4.model.Resource>> cache =
                new ConcurrentHashMap<>();

        cache.put("Patient/1", Optional.empty());
        cache.put("Patient/2", Optional.empty());

        ExtractionResourceBundle bundle = new ExtractionResourceBundle(
                new ConcurrentHashMap<>(Map.of(
                        "Patient/1", new ResourceExtractionInfo(Set.of("G1"), Map.of()),
                        "Patient/2", new ResourceExtractionInfo(Set.of("G1"), Map.of())
                )),
                cache
        );

        Bundle fhir = bundle.toFhirBundle("EX123");

        // No actual resources added
        // But provenance still appears because groups exist.
        assertThat(fhir.getEntry()).hasSize(1);

        assertThat(fhir.getEntry().getFirst().getRequest().getUrl())
                .isEqualTo("Provenance/torch-G1");

        Provenance prov = (Provenance) fhir.getEntry().get(0).getResource();
        assertThat(prov.getTarget())
                .extracting(Reference::getReference)
                .containsExactlyInAnyOrder("Patient/1", "Patient/2");
    }

    @Test
    void noGroupsMeansNoProvenance() {
        Patient p = new Patient();
        p.setId("Patient/1");

        ConcurrentHashMap<String, Optional<org.hl7.fhir.r4.model.Resource>> cache = new ConcurrentHashMap<>();
        cache.put("Patient/1", Optional.of(p));

        ExtractionResourceBundle bundle = new ExtractionResourceBundle(
                new ConcurrentHashMap<>(Map.of("Patient/1", new ResourceExtractionInfo(Set.of(), Map.of()))),
                cache
        );

        Bundle fhir = bundle.toFhirBundle("EX123");

        assertThat(fhir.getEntry()).hasSize(1);
        assertThat(fhir.getEntry().getFirst().getRequest().getUrl())
                .isEqualTo("Patient/1");
    }

    @Test
    void provenanceContainsExtractionIdEntity() {
        Map<String, ResourceExtractionInfo> info = Map.of(
                "R1", new ResourceExtractionInfo(Set.of("GroupX"), Map.of())
        );

        ExtractionResourceBundle bundle = new ExtractionResourceBundle(new ConcurrentHashMap<>(info), new ConcurrentHashMap<>());

        var provenanceList = bundle.buildProvenance("EX999");

        assertThat(provenanceList).hasSize(1);

        Provenance p = provenanceList.getFirst();

        // Check extractionId entity
        assertThat(
                p.getEntity().stream()
                        .flatMap(e -> Optional.ofNullable(e.getWhat().getIdentifier()).stream())
                        .filter(id -> "https://www.medizininformatik-initiative.de/fhir/fdpg/NamingSystem/extraction_id"
                                .equals(id.getSystem()))
                        .map(Identifier::getValue)
        ).containsExactly("EX999");
    }

    @Test
    void deterministicOutput_evenIfCalledTwice() {
        ConcurrentHashMap<String, Optional<org.hl7.fhir.r4.model.Resource>> cache =
                new ConcurrentHashMap<>();

        Patient p1 = new Patient();
        p1.setId("Patient/2");
        Patient p2 = new Patient();
        p2.setId("Patient/1");

        cache.put("Patient/2", Optional.of(p1));
        cache.put("Patient/1", Optional.of(p2));

        Map<String, ResourceExtractionInfo> info = Map.of(
                "Patient/1", new ResourceExtractionInfo(Set.of("G1"), Map.of()),
                "Patient/2", new ResourceExtractionInfo(Set.of("G1"), Map.of())
        );

        ExtractionResourceBundle bundle = new ExtractionResourceBundle(new ConcurrentHashMap<>(info), cache);

        Bundle f1 = bundle.toFhirBundle("EX123");
        Bundle f2 = bundle.toFhirBundle("EX123");

        // The entry order must be exactly identical
        List<String> urls1 = f1.getEntry().stream()
                .map(e -> e.getRequest().getUrl()).toList();

        List<String> urls2 = f2.getEntry().stream()
                .map(e -> e.getRequest().getUrl()).toList();

        assertThat(urls1).containsExactlyElementsOf(urls2);
    }

    @Test
    void provenanceTargetsAreSortedDeterministically() {
        Map<String, ResourceExtractionInfo> infoMap = Map.of(
                "B", new ResourceExtractionInfo(Set.of("G1"), Map.of()),
                "A", new ResourceExtractionInfo(Set.of("G1"), Map.of()),
                "C", new ResourceExtractionInfo(Set.of("G1"), Map.of())
        );

        ExtractionResourceBundle bundle = new ExtractionResourceBundle(new ConcurrentHashMap<>(infoMap), new ConcurrentHashMap<>());

        Provenance prov = bundle.buildProvenance("EX123").getFirst();

        assertThat(
                prov.getTarget().stream()
                        .map(Reference::getReference)
                        .toList()
        ).containsExactlyInAnyOrder("A", "B", "C"); // order within provenance is set-of-values (no guaranteed order)
    }
}

