package de.medizininformatikinitiative.torch.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Serializes {@link JobDiagnostics} to an OperationOutcome JSON node.
 *
 * <p>Job-level totals are carried as root extensions; per-criterion counts and timings
 * are carried as issue-level extensions. Extension URLs are short names — no StructureDefinition
 * backing is required.
 */
public class JobDiagnosticsMapper {

    static final String EXCLUSION_KIND_SYSTEM = "https://torch.mii.de/fhir/CodeSystem/exclusion-kind";

    private final ObjectMapper mapper;

    public JobDiagnosticsMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    private static String issueCode(ExclusionKind kind) {
        return switch (kind) {
            case MUST_HAVE -> "business-rule";
            case CONSENT -> "suppressed";
            case REFERENCE_NOT_FOUND -> "not-found";
            case REFERENCE_INVALID -> "structure";
            case REFERENCE_OUTSIDE_BATCH -> "informational";
        };
    }

    public ObjectNode toOperationOutcome(JobDiagnostics diag) {
        ObjectNode root = mapper.createObjectNode();
        root.put("resourceType", "OperationOutcome");

        ArrayNode rootExt = mapper.createArrayNode();
        rootExt.add(ext("jobId", "valueString", diag.jobId().toString()));
        rootExt.add(ext("cohortPatientsTotal", "valueInteger", diag.cohortPatientsTotal()));
        rootExt.add(ext("finalPatientsTotal", "valueInteger", diag.finalPatientsTotal()));
        root.set("extension", rootExt);

        ArrayNode issues = mapper.createArrayNode();
        for (CriterionEntry entry : diag.criteria()) {
            issues.add(toIssue(entry));
        }
        root.set("issue", issues);

        return root;
    }

    private ObjectNode toIssue(CriterionEntry entry) {
        CriterionKey key = entry.key();
        CriterionCounts counts = entry.counts();

        ObjectNode issue = mapper.createObjectNode();
        issue.put("severity", "information");
        issue.put("code", issueCode(key.kind()));

        ObjectNode details = mapper.createObjectNode();
        ArrayNode coding = mapper.createArrayNode();
        coding.add(mapper.createObjectNode()
                .put("system", EXCLUSION_KIND_SYSTEM)
                .put("code", key.kind().name()));
        details.set("coding", coding);
        String text = key.id() != null ? key.id() : key.name();
        if (text != null) details.put("text", text);
        issue.set("details", details);

        if (key.attributeRef() != null) {
            ArrayNode expr = mapper.createArrayNode();
            expr.add(key.attributeRef());
            issue.set("expression", expr);
        }

        ArrayNode issueExt = mapper.createArrayNode();
        if (key.id() != null) issueExt.add(ext("elementId", "valueString", key.id()));
        if (key.groupRef() != null) issueExt.add(ext("groupRef", "valueString", key.groupRef()));
        issueExt.add(ext("patientsExcluded", "valueInteger", counts.patientsExcluded()));
        issueExt.add(ext("resourcesExcluded", "valueInteger", counts.resourcesExcluded()));
        issueExt.add(ext("durationNanos", "valueInteger", counts.totalDurationNanos()));
        issueExt.add(ext("invocations", "valueInteger", counts.invocations()));
        issue.set("extension", issueExt);

        return issue;
    }

    private ObjectNode ext(String url, String valueKey, String value) {
        return mapper.createObjectNode().put("url", url).put(valueKey, value);
    }

    private ObjectNode ext(String url, String valueKey, long value) {
        return mapper.createObjectNode().put("url", url).put(valueKey, value);
    }
}
