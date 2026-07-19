package de.medizininformatikinitiative.torch.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JobDiagnosticsMapperTest {

    static final UUID JOB_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    JobDiagnosticsMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new JobDiagnosticsMapper(new ObjectMapper());
    }

    @Nested
    class ToOperationOutcome {

        @Test
        void setsResourceTypeAndJobExtensions() {
            var diag = new JobDiagnostics(JOB_ID, 50, 40, List.of());

            ObjectNode result = mapper.toOperationOutcome(diag);

            assertThat(result.get("resourceType").asText()).isEqualTo("OperationOutcome");
            var ext = result.get("extension");
            assertThat(ext.get(0).get("url").asText()).isEqualTo("jobId");
            assertThat(ext.get(0).get("valueString").asText()).isEqualTo(JOB_ID.toString());
            assertThat(ext.get(1).get("url").asText()).isEqualTo("cohortPatientsTotal");
            assertThat(ext.get(1).get("valueInteger").asLong()).isEqualTo(50);
            assertThat(ext.get(2).get("url").asText()).isEqualTo("finalPatientsTotal");
            assertThat(ext.get(2).get("valueInteger").asLong()).isEqualTo(40);
        }

        @Test
        void emptyDiagnostics_producesEmptyIssueArray() {
            var diag = new JobDiagnostics(JOB_ID, 0, 0, List.of());

            ObjectNode result = mapper.toOperationOutcome(diag);

            assertThat(result.get("issue")).isEmpty();
        }

        @Test
        void criterion_producesIssueWithCodingAndCounts() {
            var key = new CriterionKey(ExclusionKind.MUST_HAVE, "Obs.code", "obs-group", "Observation.code");
            var counts = new CriterionCounts(2, 3, 1000L, 1L);
            var diag = new JobDiagnostics(JOB_ID, 10, 8, List.of(new CriterionEntry(key, counts)));

            ObjectNode result = mapper.toOperationOutcome(diag);

            var issue = result.get("issue").get(0);
            assertThat(issue.get("severity").asText()).isEqualTo("information");
            assertThat(issue.get("code").asText()).isEqualTo("business-rule");
            assertThat(issue.get("details").get("coding").get(0).get("code").asText()).isEqualTo("MUST_HAVE");
            assertThat(issue.get("details").get("text").asText()).isEqualTo("Obs.code");
            assertThat(issue.get("expression").get(0).asText()).isEqualTo("Observation.code");
            var issueExt = issue.get("extension");
            assertThat(issueExt.get(0).get("url").asText()).isEqualTo("elementId");
            assertThat(issueExt.get(1).get("url").asText()).isEqualTo("groupRef");
            assertThat(issueExt.get(2).get("valueInteger").asLong()).isEqualTo(2);
            assertThat(issueExt.get(3).get("valueInteger").asLong()).isEqualTo(3);
            assertThat(issueExt.get(4).get("url").asText()).isEqualTo("durationMs");
        }

        @Test
        void referenceKind_usesDisplayNameFromKind() {
            var key = new CriterionKey(ExclusionKind.REFERENCE_NOT_FOUND, null, "Observation", null);
            var diag = new JobDiagnostics(JOB_ID, 5, 4, List.of(new CriterionEntry(key, new CriterionCounts(1, 0))));

            ObjectNode result = mapper.toOperationOutcome(diag);

            var issue = result.get("issue").get(0);
            assertThat(issue.get("details").get("text").asText()).isEqualTo("Reference target not found");
            assertThat(issue.has("expression")).isFalse();
        }

        @Test
        void mustHaveKind_withNullId_omitsDetailsText() {
            var key = new CriterionKey(ExclusionKind.MUST_HAVE, null, null, null);
            var diag = new JobDiagnostics(JOB_ID, 5, 4, List.of(new CriterionEntry(key, new CriterionCounts(1, 0))));

            ObjectNode result = mapper.toOperationOutcome(diag);

            var issue = result.get("issue").get(0);
            assertThat(issue.get("details").has("text")).isFalse();
        }

        @Test
        void stageTimings_serializedAsRootExtensions() {
            var diag = new JobDiagnostics(JOB_ID, 10, 10, List.of(),
                    Map.of(PipelineStage.CASCADING_DELETE, new StageCounts(60_000L, 120)), // 60 s in ms
                    0L);

            ObjectNode result = mapper.toOperationOutcome(diag);

            var ext = result.get("extension");
            boolean found = false;
            for (var node : ext) {
                if ("stage.cascading-delete".equals(node.get("url").asText())) {
                    var nested = node.get("extension");
                    assertThat(nested.get(0).get("url").asText()).isEqualTo("durationMs");
                    assertThat(nested.get(1).get("url").asText()).isEqualTo("resourcesProcessed");
                    assertThat(nested.get(2).get("url").asText()).isEqualTo("resourcesPerMinute");
                    assertThat(nested.get(2).get("valueInteger").asLong()).isEqualTo(120);
                    found = true;
                }
            }
            assertThat(found).as("stage.cascading-delete extension present").isTrue();
        }

        @Test
        void cohortQueryDuration_serializedWhenNonZero() {
            var diag = new JobDiagnostics(JOB_ID, 10, 10, List.of(), Map.of(), 5_000L); // 5 s in ms

            ObjectNode result = mapper.toOperationOutcome(diag);

            var ext = result.get("extension");
            boolean found = false;
            for (var node : ext) {
                if ("cohortQueryDurationMs".equals(node.get("url").asText())) {
                    assertThat(node.get("valueInteger").asLong()).isEqualTo(5_000);
                    found = true;
                }
            }
            assertThat(found).as("cohortQueryDurationNanos extension present").isTrue();
        }

        @Test
        void zeroCohortQueryDuration_omitted() {
            var diag = new JobDiagnostics(JOB_ID, 10, 10, List.of(), Map.of(), 0L);

            ObjectNode result = mapper.toOperationOutcome(diag);

            var ext = result.get("extension");
            for (var node : ext) {
                assertThat(node.get("url").asText()).isNotEqualTo("cohortQueryDurationMs");
            }
        }

        @ParameterizedTest
        @EnumSource(ExclusionKind.class)
        void allExclusionKindsProduceIssueCode(ExclusionKind kind) {
            var key = new CriterionKey(kind, null, null, null);
            var diag = new JobDiagnostics(JOB_ID, 1, 0, List.of(new CriterionEntry(key, new CriterionCounts(1, 0))));

            ObjectNode result = mapper.toOperationOutcome(diag);

            assertThat(result.get("issue").get(0).get("code").asText()).isNotBlank();
        }
    }
}
