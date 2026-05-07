package de.medizininformatikinitiative.torch.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
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
            var key = new CriterionKey(ExclusionKind.MUST_HAVE, "Obs.code", "obs label", "obs-group", "Observation.code");
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
        }

        @Test
        void nullId_useNameAsDetailsText() {
            var key = new CriterionKey(ExclusionKind.CONSENT, null, "some-name", null, null);
            var diag = new JobDiagnostics(JOB_ID, 5, 4, List.of(new CriterionEntry(key, new CriterionCounts(1, 0))));

            ObjectNode result = mapper.toOperationOutcome(diag);

            var issue = result.get("issue").get(0);
            assertThat(issue.get("details").get("text").asText()).isEqualTo("some-name");
            assertThat(issue.has("expression")).isFalse();
        }

        @Test
        void nullIdAndNullName_omitsDetailsText() {
            var key = new CriterionKey(ExclusionKind.REFERENCE_NOT_FOUND, null, null, null, null);
            var diag = new JobDiagnostics(JOB_ID, 5, 4, List.of(new CriterionEntry(key, new CriterionCounts(1, 0))));

            ObjectNode result = mapper.toOperationOutcome(diag);

            var issue = result.get("issue").get(0);
            assertThat(issue.get("details").has("text")).isFalse();
        }

        @ParameterizedTest
        @EnumSource(ExclusionKind.class)
        void allExclusionKindsProduceIssueCode(ExclusionKind kind) {
            var key = new CriterionKey(kind, null, null, null, null);
            var diag = new JobDiagnostics(JOB_ID, 1, 0, List.of(new CriterionEntry(key, new CriterionCounts(1, 0))));

            ObjectNode result = mapper.toOperationOutcome(diag);

            assertThat(result.get("issue").get(0).get("code").asText()).isNotBlank();
        }
    }
}
