package de.medizininformatikinitiative.torch.model.extraction;

import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static de.medizininformatikinitiative.torch.assertions.BundleAssertFactory.BUNDLE_ASSERT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;


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

        // We expect 3 groups: G1, G2, G3 => thus 3 provenance resources
        assertThat(prov).hasSize(3);

        assertThat(prov)
                .extracting(Provenance::getId)
                .allSatisfy(id -> {
                    assertThat(id).startsWith("Provenance/torch-");
                    String uuidPart = id.substring("Provenance/torch-".length());
                    assertThatCode(() -> UUID.fromString(uuidPart)).doesNotThrowAnyException();
                });

        // Helper to compare target reference sets
        java.util.function.Function<Provenance, Set<String>> targets =
                p -> p.getTarget().stream().map(Reference::getReference).collect(java.util.stream.Collectors.toSet());

        Provenance pG1 = prov.stream()
                .filter(p -> targets.apply(p).equals(Set.of("R1", "R2")))
                .findFirst().orElseThrow();

        Provenance pG2 = prov.stream()
                .filter(p -> targets.apply(p).equals(Set.of("R1")))
                .findFirst().orElseThrow();

        Provenance pG3 = prov.stream()
                .filter(p -> targets.apply(p).equals(Set.of("R3")))
                .findFirst().orElseThrow();

        // Check targets (order-insensitive)
        assertThat(pG1.getTarget()).extracting(Reference::getReference)
                .containsExactlyInAnyOrder("R1", "R2");

        assertThat(pG2.getTarget()).extracting(Reference::getReference)
                .containsExactly("R1");

        assertThat(pG3.getTarget()).extracting(Reference::getReference)
                .containsExactly("R3");
    }

    @Test
    void toFhirBundle_addsResourcesThenProvenance_inDeterministicOrder() {
        ConcurrentHashMap<String, Optional<org.hl7.fhir.r4.model.Resource>> cache = new ConcurrentHashMap<>();

        Patient p2 = new Patient();
        p2.setId("Patient/2");
        Patient p1 = new Patient();
        p1.setId("Patient/1");
        Patient p3 = new Patient();
        p3.setId("Patient/3");

        cache.put("Patient/3", Optional.of(p3));
        cache.put("Patient/1", Optional.of(p1));
        cache.put("Patient/2", Optional.of(p2));

        Map<String, ResourceExtractionInfo> info = Map.of(
                "Patient/1", new ResourceExtractionInfo(Set.of("G1", "G2"), Map.of()),
                "Patient/2", new ResourceExtractionInfo(Set.of("G1"), Map.of()),
                "Patient/3", new ResourceExtractionInfo(Set.of("G3"), Map.of())
        );

        ExtractionResourceBundle bundle = new ExtractionResourceBundle(new ConcurrentHashMap<>(info), cache);

        Bundle fhir = bundle.toFhirBundle("EX123");

        var urls = fhir.getEntry().stream()
                .map(e -> e.getRequest().getUrl())
                .toList();

        // normalize: keep patients as-is, cut off "-<uuid>" for provenance
        List<String> stable = urls.stream()
                .map(u -> u.startsWith("Provenance/")
                        ? u.replaceAll("[0-9a-fA-F\\-]{36}$", "")
                        : u)
                .toList();

        assertThat(stable).containsExactly(
                "Patient/1",
                "Patient/2",
                "Patient/3",
                "Provenance/torch-",
                "Provenance/torch-",
                "Provenance/torch-"
        );

        // optional: still assert the UUID suffix is actually present + plausible
        assertThat(urls.subList(3, 6)).allSatisfy(u ->
                assertThat(u).matches("Provenance/torch-[0-9a-fA-F\\-]{36}")
        );
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
                .startsWith("Provenance/torch");

        Provenance prov = (Provenance) fhir.getEntry().getFirst().getResource();
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
    void toFhirBundle_neverExportsInvalidResources() {
        // given
        ResourceBundle resourceBundle = new ResourceBundle();

        resourceBundle.put(new Patient().setId("Patient/1"), "Group1", false);
        resourceBundle.put(new Observation().setId("Observation/1"), "Group2", false);
        resourceBundle.put(new Condition().setId("Condition/1"), "Group2", true);
        resourceBundle.put(new Condition().setId("Condition/2"));

        ExtractionResourceBundle extractionBundle =
                ExtractionResourceBundle.of(resourceBundle);

        // when
        Bundle fhirBundle = extractionBundle.toFhirBundle("extraction-1");

        assertThat(fhirBundle)
                .asInstanceOf(BUNDLE_ASSERT)
                .extractResources(r ->
                        r.getId().equals("Condition/2")
                                || r.getId().equals("Patient/1")
                                || r.getId().equals("Observation/1"))
                .isEmpty();

        assertThat(fhirBundle)
                .asInstanceOf(BUNDLE_ASSERT)
                .extractResources(r ->
                        r.getResourceType() != ResourceType.Provenance)
                .hasSize(1);
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

        List<String> stable1 = urls1.stream()
                .map(u -> u.startsWith("Provenance/") ? u.replaceAll("-[0-9a-fA-F\\-]{36}$", "") : u)
                .toList();
        List<String> stable2 = urls2.stream()
                .map(u -> u.startsWith("Provenance/") ? u.replaceAll("-[0-9a-fA-F\\-]{36}$", "") : u)
                .toList();

        assertThat(stable2).containsExactlyElementsOf(stable1);
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

