package de.medizininformatikinitiative.torch.diagnostics;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;

public final class CriterionKeys {

    private CriterionKeys() {
    } // prevent instantiation

    // -------------------------
    // MUST HAVE (attribute-level)
    // -------------------------

    public static CriterionKey mustHaveAttribute(
            AnnotatedAttributeGroup group,
            AnnotatedAttribute attribute
    ) {
        return new CriterionKey(
                ExclusionKind.MUST_HAVE,
                attribute.fhirPath(),
                group.groupReference(),
                attribute.fhirPath()
        );
    }

    // -------------------------
    // MUST HAVE (group-level patient removal)
    // -------------------------

    public static CriterionKey mustHaveGroup(
            AnnotatedAttributeGroup group
    ) {
        return new CriterionKey(
                ExclusionKind.MUST_HAVE,
                group.id(),
                group.groupReference(),
                null
        );
    }

    // -------------------------
    // CONSENT
    // -------------------------

    public static CriterionKey consentNoData() {
        return new CriterionKey(
                ExclusionKind.CONSENT,
                "No data due to consent",
                null,
                null
        );
    }

    public static CriterionKey consentResourceBlocked() {
        return new CriterionKey(
                ExclusionKind.CONSENT,
                "Resource blocked by consent",
                null,
                null
        );
    }

    // -------------------------
    // REFERENCE RESOLUTION
    // -------------------------

    public static CriterionKey referenceNotFound(String resourceType) {
        return new CriterionKey(
                ExclusionKind.REFERENCE_NOT_FOUND,
                "Reference target not found",
                resourceType,
                null
        );
    }

    public static CriterionKey referenceOutsideBatch(String resourceType) {
        return new CriterionKey(
                ExclusionKind.REFERENCE_OUTSIDE_BATCH,
                "Reference outside patient batch",
                resourceType,
                null
        );
    }

    public static CriterionKey referenceInvalid(String resourceType) {
        return new CriterionKey(
                ExclusionKind.REFERENCE_INVALID,
                "Invalid reference format",
                resourceType,
                null
        );
    }
}
