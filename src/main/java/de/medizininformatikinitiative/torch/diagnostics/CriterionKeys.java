package de.medizininformatikinitiative.torch.diagnostics;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;

/**
 * Factory methods for the standard {@link CriterionKey} instances used in extraction diagnostics.
 */
public final class CriterionKeys {

    private CriterionKeys() {
    }

    public static CriterionKey mustHaveAttribute(
            AnnotatedAttributeGroup group,
            AnnotatedAttribute attribute
    ) {
        return new CriterionKey(
                ExclusionKind.MUST_HAVE,
                attribute.attributeRef(),
                attribute.attributeRef(),
                group.groupReference(),
                attribute.fhirPath()
        );
    }

    public static CriterionKey mustHaveGroup(
            AnnotatedAttributeGroup group
    ) {
        return new CriterionKey(
                ExclusionKind.MUST_HAVE,
                group.id(),
                group.name(),
                group.groupReference(),
                null
        );
    }

    public static CriterionKey consentNoData() {
        return new CriterionKey(
                ExclusionKind.CONSENT,
                null,
                "No data due to consent",
                null,
                null
        );
    }

    public static CriterionKey consentResourceBlocked() {
        return new CriterionKey(
                ExclusionKind.CONSENT,
                null,
                "Resource blocked by consent",
                null,
                null
        );
    }

    public static CriterionKey referenceNotFound(String resourceType) {
        return new CriterionKey(
                ExclusionKind.REFERENCE_NOT_FOUND,
                null,
                "Reference target not found",
                resourceType,
                null
        );
    }

    public static CriterionKey referenceOutsideBatch(String resourceType) {
        return new CriterionKey(
                ExclusionKind.REFERENCE_OUTSIDE_BATCH,
                null,
                "Reference outside patient batch",
                resourceType,
                null
        );
    }

    public static CriterionKey referenceInvalid(String resourceType) {
        return new CriterionKey(
                ExclusionKind.REFERENCE_INVALID,
                null,
                "Invalid reference format",
                resourceType,
                null
        );
    }
}
