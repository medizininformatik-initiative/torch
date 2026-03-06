package de.medizininformatikinitiative.torch.diagnostics;

public record CriterionKey(
        ExclusionKind kind,        // e.g. MUST_HAVE, REF_NOT_FOUND, ...
        String name,
        String groupRef,
        String attributeRef
) {
}
