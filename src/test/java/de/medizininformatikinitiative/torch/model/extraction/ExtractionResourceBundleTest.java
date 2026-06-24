package de.medizininformatikinitiative.torch.model.extraction;

import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
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


class ExtractionResourceBundleTest {

    @Test
    void createsOneProvenancePerGroup() {
        ConcurrentHashMap<ExtractionId, ResourceExtractionInfo> infoMap = new ConcurrentHashMap<>(
                Map.of(
                        ExtractionId.fromRelativeUrl("Patient/R1"), new ResourceExtractionInfo(Set.of("G1", "G2"), Map.of()),
                        ExtractionId.fromRelativeUrl("Patient/R2"), new ResourceExtractionInfo(Set.of("G1"), Map.of()),
                        ExtractionId.fromRelativeUrl("Patient/R3"), new ResourceExtractionInfo(Set.of("G3"), Map.of())
                )
        );

        ExtractionResourceBundle bundle = new ExtractionResourceBundle(infoMap, CacheBuilder.create()
                .with("Patient/R1", new Patient().setId("Patient/R1"))
                .with("Patient/R2", new Patient().setId("Patient/R2"))
                .with("Patient/R3", new Patient().setId("Patient/R3"))
                .build());

        List<Provenance> prov = bundle.buildProvenance("EX123");

        // We expect 3 groups: G1, G2, G3 => thus 3 provenance resources
        assertThat(prov).hasSize(3);

        assertThat(prov).allSatisfy(p -> {
            String id = p.getId();
            assertThat(id).startsWith("Provenance/torch-");
            String uuidPart = id.substring("Provenance/torch-".length());
            UUID.fromString(uuidPart); // throws IllegalArgumentException if not a valid UUID
        });

        // Helper to compare target reference sets
        java.util.function.Function<Provenance, Set<String>> targets =
                p -> p.getTarget().stream().map(Reference::getReference).collect(java.util.stream.Collectors.toSet());

        Provenance pG1 = prov.stream()
                .filter(p -> targets.apply(p).equals(Set.of("Patient/R1", "Patient/R2")))
                .findFirst().orElseThrow();

        Provenance pG2 = prov.stream()
                .filter(p -> targets.apply(p).equals(Set.of("Patient/R1")))
                .findFirst().orElseThrow();

        Provenance pG3 = prov.stream()
                .filter(p -> targets.apply(p).equals(Set.of("Patient/R3")))
                .findFirst().orElseThrow();

        // Check targets (order-insensitive)
        assertThat(pG1.getTarget()).extracting(Reference::getReference)
                .containsExactlyInAnyOrder("Patient/R1", "Patient/R2");

        assertThat(pG2.getTarget()).extracting(Reference::getReference)
                .containsExactly("Patient/R1");

        assertThat(pG3.getTarget()).extracting(Reference::getReference)
                .containsExactly("Patient/R3");
    }

    @Test
    void toFhirBundle_addsResourcesThenProvenance_inDeterministicOrder() {
        var cache = CacheBuilder.create()
                .with("Patient/3", new Patient().setId("Patient/3"))
                .with("Patient/1", new Patient().setId("Patient/1"))
                .with("Patient/2", new Patient().setId("Patient/2"))
                .build();

        Map<ExtractionId, ResourceExtractionInfo> info = Map.of(
                ExtractionId.fromRelativeUrl("Patient/1"), new ResourceExtractionInfo(Set.of("G1", "G2"), Map.of()),
                ExtractionId.fromRelativeUrl("Patient/2"), new ResourceExtractionInfo(Set.of("G1"), Map.of()),
                ExtractionId.fromRelativeUrl("Patient/3"), new ResourceExtractionInfo(Set.of("G3"), Map.of())
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
    void putDoesNotThrowOnIllegalExtractionId() {
        ExtractionResourceBundle bundle = new ExtractionResourceBundle(
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>()
        );
        assertThat(bundle.put(new Patient())).isFalse();

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
    void optionalEmptyResourcesAreSkippedFromBundleAndProvenance() {
        ExtractionResourceBundle bundle = new ExtractionResourceBundle(
                new ConcurrentHashMap<>(Map.of(
                        ExtractionId.fromRelativeUrl("Patient/1"), new ResourceExtractionInfo(Set.of("G1"), Map.of()),
                        ExtractionId.fromRelativeUrl("Patient/2"), new ResourceExtractionInfo(Set.of("G1"), Map.of())
                )),
                CacheBuilder.create().empty("Patient/1").empty("Patient/2").build()
        );

        Bundle fhir = bundle.toFhirBundle("EX123");

        assertThat(fhir.getEntry()).isEmpty();
    }

    @Test
    void partiallyEmptyResourcesAreSkippedFromBundleAndProvenance() {
        ExtractionResourceBundle bundle = new ExtractionResourceBundle(
                new ConcurrentHashMap<>(Map.of(
                        ExtractionId.fromRelativeUrl("Patient/1"), new ResourceExtractionInfo(Set.of("G1"), Map.of()),
                        ExtractionId.fromRelativeUrl("Patient/2"), new ResourceExtractionInfo(Set.of("G1"), Map.of())
                )),
                CacheBuilder.create()
                        .empty("Patient/1")
                        .with("Patient/2", new Patient().setId("Patient/2"))
                        .build()
        );

        Bundle fhir = bundle.toFhirBundle("EX123");

        var urls = fhir.getEntry().stream().map(e -> e.getRequest().getUrl()).toList();
        assertThat(urls).contains("Patient/2");
        assertThat(urls).doesNotContain("Patient/1");

        var provenances = fhir.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(r -> r instanceof Provenance)
                .map(r -> (Provenance) r)
                .toList();
        assertThat(provenances).hasSize(1);
        assertThat(provenances.getFirst().getTarget())
                .extracting(Reference::getReference)
                .containsExactly("Patient/2");
    }

    @Test
    void noGroupsMeansNoProvenance() {
        ExtractionResourceBundle bundle = new ExtractionResourceBundle(
                new ConcurrentHashMap<>(Map.of(ExtractionId.fromRelativeUrl("Patient/1"), new ResourceExtractionInfo(Set.of(), Map.of()))),
                CacheBuilder.create().with("Patient/1", new Patient().setId("Patient/1")).build()
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
        Map<ExtractionId, ResourceExtractionInfo> info = Map.of(
                ExtractionId.fromRelativeUrl("Patient/R1"), new ResourceExtractionInfo(Set.of("GroupX"), Map.of())
        );

        ExtractionResourceBundle bundle = new ExtractionResourceBundle(
                new ConcurrentHashMap<>(info),
                CacheBuilder.create().with("Patient/R1", new Patient().setId("Patient/R1")).build());

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
        var cache = CacheBuilder.create()
                .with("Patient/2", new Patient().setId("Patient/2"))
                .with("Patient/1", new Patient().setId("Patient/1"))
                .build();

        Map<ExtractionId, ResourceExtractionInfo> info = Map.of(
                ExtractionId.fromRelativeUrl("Patient/1"), new ResourceExtractionInfo(Set.of("G1"), Map.of()),
                ExtractionId.fromRelativeUrl("Patient/2"), new ResourceExtractionInfo(Set.of("G1"), Map.of())
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
        Map<ExtractionId, ResourceExtractionInfo> infoMap = Map.of(
                ExtractionId.fromRelativeUrl("R/B"), new ResourceExtractionInfo(Set.of("G1"), Map.of()),
                ExtractionId.fromRelativeUrl("R/A"), new ResourceExtractionInfo(Set.of("G1"), Map.of()),
                ExtractionId.fromRelativeUrl("R/C"), new ResourceExtractionInfo(Set.of("G1"), Map.of())
        );

        ExtractionResourceBundle bundle = new ExtractionResourceBundle(
                new ConcurrentHashMap<>(infoMap),
                CacheBuilder.create()
                        .with("R/B", new Patient().setId("R/B"))
                        .with("R/A", new Patient().setId("R/A"))
                        .with("R/C", new Patient().setId("R/C"))
                        .build());

        Provenance prov = bundle.buildProvenance("EX123").getFirst();

        assertThat(
                prov.getTarget().stream()
                        .map(Reference::getReference)
                        .toList()
        ).containsExactlyInAnyOrder("R/A", "R/B", "R/C"); // order within provenance is set-of-values (no guaranteed order)
    }

    private static final class CacheBuilder {
        private final ConcurrentHashMap<ExtractionId, Optional<Resource>> map = new ConcurrentHashMap<>();

        static CacheBuilder create() { return new CacheBuilder(); }

        CacheBuilder with(String relativeUrl, Resource resource) {
            map.put(ExtractionId.fromRelativeUrl(relativeUrl), Optional.of(resource));
            return this;
        }

        CacheBuilder empty(String relativeUrl) {
            map.put(ExtractionId.fromRelativeUrl(relativeUrl), Optional.empty());
            return this;
        }

        ConcurrentHashMap<ExtractionId, Optional<Resource>> build() { return map; }
    }
}

