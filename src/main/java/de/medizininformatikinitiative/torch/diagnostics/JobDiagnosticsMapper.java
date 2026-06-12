package de.medizininformatikinitiative.torch.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializes {@link JobDiagnostics} plus an exclusion log to an OperationOutcome JSON node.
 *
 * <p>Job-level totals and stage timings are carried as root extensions.
 * Per-criterion exclusion counts (derived by aggregating the exclusion log) are carried as issues.
 */
public class JobDiagnosticsMapper {

    static final String EXCLUSION_KIND_SYSTEM = "https://torch.mii.de/fhir/CodeSystem/exclusion-kind";
    private static final String VALUE_INTEGER = "valueInteger";
    private static final String EXTENSION = "extension";

    private final ObjectMapper mapper;

    public JobDiagnosticsMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    private static String issueCode(ExclusionKind kind) {
        return switch (kind) {
            case MUST_HAVE_RESOURCE, MUST_HAVE_FIELD, MUST_HAVE_CASCADE -> "business-rule";
            case CONSENT -> "suppressed";
            case REFERENCE_NOT_FOUND -> "not-found";
            case REFERENCE_INVALID -> "structure";
            case REFERENCE_OUTSIDE_BATCH -> "informational";
        };
    }

    public ObjectNode toOperationOutcome(JobDiagnostics diag, List<ExclusionRecord> exclusions) {
        ObjectNode root = mapper.createObjectNode();
        root.put("resourceType", "OperationOutcome");

        ArrayNode rootExt = mapper.createArrayNode();
        rootExt.add(ext("jobId", diag.jobId().toString()));
        rootExt.add(ext("cohortPatientsTotal", diag.cohortPatientsTotal()));
        rootExt.add(ext("finalPatientsTotal", diag.finalPatientsTotal()));
        if (diag.cohortQueryDurationMs() > 0) {
            rootExt.add(ext("cohortQueryDurationMs", diag.cohortQueryDurationMs()));
        }
        for (Map.Entry<PipelineStage, StageCounts> entry : diag.stages().entrySet()) {
            rootExt.add(stageExt(entry.getKey(), entry.getValue()));
        }
        root.set(EXTENSION, rootExt);

        ArrayNode issues = mapper.createArrayNode();
        for (Map.Entry<AggKey, long[]> entry : aggregate(exclusions).entrySet()) {
            issues.add(toIssue(entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
        }
        root.set("issue", issues);

        return root;
    }

    /**
     * Groups exclusions by (reason, groupRef, attributeRef); counts[0]=patients, counts[1]=resources.
     */
    private Map<AggKey, long[]> aggregate(List<ExclusionRecord> exclusions) {
        Map<AggKey, long[]> map = new LinkedHashMap<>();
        for (ExclusionRecord r : exclusions) {
            AggKey key = new AggKey(r.reason(), r.groupRef(), r.attributeRef());
            long[] counts = map.computeIfAbsent(key, k -> new long[2]);
            if (r.resourceId() == null) counts[0]++;
            else counts[1]++;
        }
        return map;
    }

    private ObjectNode toIssue(AggKey key, long patientsExcluded, long resourcesExcluded) {
        ObjectNode issue = mapper.createObjectNode();
        issue.put("severity", "information");
        issue.put("code", issueCode(key.reason()));

        ObjectNode details = mapper.createObjectNode();
        ArrayNode coding = mapper.createArrayNode();
        coding.add(mapper.createObjectNode()
                .put("system", EXCLUSION_KIND_SYSTEM)
                .put("code", key.reason().name()));
        details.set("coding", coding);
        if (key.attributeRef() != null) details.put("text", key.attributeRef());
        else if (key.groupRef() != null) details.put("text", key.groupRef());
        issue.set("details", details);

        if (key.attributeRef() != null) {
            ArrayNode expr = mapper.createArrayNode();
            expr.add(key.attributeRef());
            issue.set("expression", expr);
        }

        ArrayNode issueExt = mapper.createArrayNode();
        if (key.groupRef() != null) issueExt.add(ext("groupRef", key.groupRef()));
        issueExt.add(ext("patientsExcluded", patientsExcluded));
        issueExt.add(ext("resourcesExcluded", resourcesExcluded));
        issue.set(EXTENSION, issueExt);

        return issue;
    }

    private ObjectNode stageExt(PipelineStage stage, StageCounts counts) {
        ObjectNode node = mapper.createObjectNode();
        node.put("url", "stage." + stage.name().toLowerCase().replace('_', '-'));
        ArrayNode nested = mapper.createArrayNode();
        nested.add(ext("durationMs", counts.durationMs()));
        nested.add(ext("resourcesProcessed", counts.resourcesProcessed()));
        nested.add(ext("resourcesPerMinute", counts.resourcesPerMinute()));
        node.set(EXTENSION, nested);
        return node;
    }

    private ObjectNode ext(String url, String value) {
        return mapper.createObjectNode().put("url", url).put("valueString", value);
    }

    private ObjectNode ext(String url, long value) {
        return mapper.createObjectNode().put("url", url).put(VALUE_INTEGER, value);
    }

    private record AggKey(ExclusionKind reason, String groupRef, String attributeRef) {
    }
}
