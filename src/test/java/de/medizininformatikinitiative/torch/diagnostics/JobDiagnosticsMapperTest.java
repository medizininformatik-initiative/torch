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
            var diag = new JobDiagnostics(JOB_ID, 50, 40, Map.of(), 0L);

            ObjectNode result = mapper.toOperationOutcome(diag, List.of());

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
        void emptyExclusions_producesEmptyIssueArray() {
            var diag = new JobDiagnostics(JOB_ID, 0, 0, Map.of(), 0L);

            ObjectNode result = mapper.toOperationOutcome(diag, List.of());

            assertThat(result.get("issue")).isEmpty();
        }

        @Test
        void mustHaveResourceExclusion_producesIssueWithBusinessRuleCode() {
            var diag = new JobDiagnostics(JOB_ID, 10, 8, Map.of(), 0L);
            var exclusions = List.of(
                    new ExclusionRecord("p1", ExclusionKind.MUST_HAVE_RESOURCE, "obs-group", null, "Observation.code"),
                    new ExclusionRecord("p2", ExclusionKind.MUST_HAVE_RESOURCE, "obs-group", null, "Observation.code")
            );

            ObjectNode result = mapper.toOperationOutcome(diag, exclusions);

            var issue = result.get("issue").get(0);
            assertThat(issue.get("severity").asText()).isEqualTo("information");
            assertThat(issue.get("code").asText()).isEqualTo("business-rule");
            assertThat(issue.get("details").get("coding").get(0).get("code").asText()).isEqualTo("MUST_HAVE_RESOURCE");
            var issueExt = issue.get("extension");
            assertThat(issueExt.get(1).get("url").asText()).isEqualTo("patientsExcluded");
            assertThat(issueExt.get(1).get("valueInteger").asLong()).isEqualTo(2);
        }

        @Test
        void consentExclusion_usesSuppressedCode() {
            var diag = new JobDiagnostics(JOB_ID, 5, 4, Map.of(), 0L);
            var exclusions = List.of(
                    new ExclusionRecord("p1", ExclusionKind.CONSENT, null, null, null)
            );

            ObjectNode result = mapper.toOperationOutcome(diag, exclusions);

            assertThat(result.get("issue").get(0).get("code").asText()).isEqualTo("suppressed");
        }

        @Test
        void referenceNotFoundExclusion_usesNotFoundCode() {
            var diag = new JobDiagnostics(JOB_ID, 5, 4, Map.of(), 0L);
            var exclusions = List.of(
                    new ExclusionRecord("p1", ExclusionKind.REFERENCE_NOT_FOUND, "grp", "Observation/o1", null)
            );

            ObjectNode result = mapper.toOperationOutcome(diag, exclusions);

            assertThat(result.get("issue").get(0).get("code").asText()).isEqualTo("not-found");
        }

        @Test
        void exclusionsAggregatedByKey() {
            var diag = new JobDiagnostics(JOB_ID, 10, 7, Map.of(), 0L);
            var exclusions = List.of(
                    new ExclusionRecord("p1", ExclusionKind.CONSENT, null, null, null),
                    new ExclusionRecord("p2", ExclusionKind.CONSENT, null, null, null),
                    new ExclusionRecord("p3", ExclusionKind.MUST_HAVE_RESOURCE, "grp", null, null)
            );

            ObjectNode result = mapper.toOperationOutcome(diag, exclusions);

            assertThat(result.get("issue")).hasSize(2);
        }

        @Test
        void stageTimings_serializedAsRootExtensions() {
            var diag = new JobDiagnostics(JOB_ID, 10, 10,
                    Map.of(PipelineStage.CASCADING_DELETE, new StageCounts(60_000L, 120)), 0L);

            ObjectNode result = mapper.toOperationOutcome(diag, List.of());

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
            var diag = new JobDiagnostics(JOB_ID, 10, 10, Map.of(), 5_000L);

            ObjectNode result = mapper.toOperationOutcome(diag, List.of());

            var ext = result.get("extension");
            boolean found = false;
            for (var node : ext) {
                if ("cohortQueryDurationMs".equals(node.get("url").asText())) {
                    assertThat(node.get("valueInteger").asLong()).isEqualTo(5_000);
                    found = true;
                }
            }
            assertThat(found).as("cohortQueryDurationMs extension present").isTrue();
        }

        @Test
        void zeroCohortQueryDuration_omitted() {
            var diag = new JobDiagnostics(JOB_ID, 10, 10, Map.of(), 0L);

            ObjectNode result = mapper.toOperationOutcome(diag, List.of());

            var ext = result.get("extension");
            for (var node : ext) {
                assertThat(node.get("url").asText()).isNotEqualTo("cohortQueryDurationMs");
            }
        }

        @ParameterizedTest
        @EnumSource(ExclusionKind.class)
        void allExclusionKindsProduceIssueCode(ExclusionKind kind) {
            var diag = new JobDiagnostics(JOB_ID, 1, 0, Map.of(), 0L);
            var exclusions = List.of(new ExclusionRecord("p1", kind, null, null, null));

            ObjectNode result = mapper.toOperationOutcome(diag, exclusions);

            assertThat(result.get("issue").get(0).get("code").asText()).isNotBlank();
        }
    }
}
