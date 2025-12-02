package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionPatientBatch;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionResourceBundle;
import de.medizininformatikinitiative.torch.model.extraction.ResourceExtractionInfo;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class ResultFileManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void writesExtractionResourceBundleWithProvenanceAndResourceMetadata() throws IOException {
        // -------------------------------------------------------
        // 1) Setup FHIR context + ResultFileManager
        // -------------------------------------------------------
        FhirContext ctx = FhirContext.forR4();
        ResultFileManager manager = new ResultFileManager(tempDir.toString(), ctx);

        // -------------------------------------------------------
        // 2) Create a ResourceExtractionInfo for a single resource
        // -------------------------------------------------------
        var extractionInfo = new ResourceExtractionInfo(
                Set.of("GroupA", "GroupB"),           // groups this resource belongs to
                Map.of("Condition.encounter", Set.of("Encounter/123"))
        );

        // This is the extraction info map
        var infoMap = new ConcurrentHashMap<String, ResourceExtractionInfo>();
        infoMap.put("Condition/1", extractionInfo);

        // -------------------------------------------------------
        // 3) Create the cache map
        // -------------------------------------------------------
        var cache = new ConcurrentHashMap<String, Optional<Resource>>();

        // -------------------------------------------------------
        // 4) Create the ExtractionResourceBundle
        // -------------------------------------------------------
        var extractionBundle = new ExtractionResourceBundle(infoMap, cache);

        // -------------------------------------------------------
        // 5) Add one actual resource into a ResourceBundle
        // -------------------------------------------------------
        Condition c1 = new Condition();
        c1.setId("Condition/1");


        extractionBundle.put("Condition/1", Optional.of(c1));

        Map<String, ExtractionResourceBundle> bundles = new ConcurrentHashMap<>();
        bundles.put("CORE", extractionBundle);

        ExtractionPatientBatch patientBatch = new ExtractionPatientBatch(bundles);

        // -------------------------------------------------------
        // 7) Write NDJSON
        // -------------------------------------------------------
        manager.saveBatchToNDJSON("jobX", patientBatch);

        Path ndjson = tempDir.resolve("jobX").resolve("core.ndjson");
        assertThat(ndjson).exists();

        // Only one line: one bundle
        var lines = Files.readAllLines(ndjson);
        assertThat(lines).hasSize(1);

        // -------------------------------------------------------
        // 8) Parse Bundle
        // -------------------------------------------------------
        Bundle bundle = (Bundle) ctx.newJsonParser().parseResource(lines.get(0));

        var provenanceList = bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(Provenance.class::isInstance)
                .map(r -> (Provenance) r)
                .toList();

        // Provenance IDs must match provenance prefix + groupId
        assertThat(provenanceList).hasSize(2)
                .anySatisfy(p -> assertThat(p.getId()).endsWith("GroupA"))
                .anySatisfy(p -> assertThat(p.getId()).endsWith("GroupB"));

        // Each provenance should have the correct entity for its group
        for (Provenance p : provenanceList) {
            String provJson = ctx.newJsonParser().encodeResourceToString(p);

            // Has correct group ID
            if (p.getId().endsWith("GroupA")) {
                assertThat(provJson).contains("GroupA");
                assertThat(provJson).doesNotContain("GroupB");
            } else {
                assertThat(provJson).contains("GroupB");
                assertThat(provJson).doesNotContain("GroupA");
            }

            // Each provenance must contain the extractionId entity
            assertThat(provJson).contains("jobX");

            // Each provenance must include the target resource ID
            assertThat(provJson).contains("Condition/1");
        }

        // 1 Condition resource in the bundle
        long conditionCount = bundle.getEntry().stream()
                .filter(e -> e.getResource() instanceof Condition)
                .count();
        assertThat(conditionCount).isEqualTo(1);

        assertThat(provenanceList)
                .allSatisfy(p ->
                        assertThat(p.getTarget())
                                .extracting(Reference::getReference)
                                .containsExactly("Condition/1")
                );

    }
}
