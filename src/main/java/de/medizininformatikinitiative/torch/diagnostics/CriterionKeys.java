package de.medizininformatikinitiative.torch.diagnostics;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;

/**
 * Factory methods for the standard {@link CriterionKey} instances used in extraction diagnostics.
 */
public final class CriterionKeys {

    private CriterionKeys() {
    } // prevent instantiation

    // -------------------------
    // MUST HAVE (attribute-level)
    // -------------------------

    /**
     * Creates a {@link CriterionKey} for a missing must-have attribute within a group.
     *
     * @param group     the attribute group that owns the must-have attribute
     * @param attribute the specific attribute that was absent
     * @return a {@link CriterionKey} of kind {@link ExclusionKind#MUST_HAVE} scoped to the attribute
     */
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

    /**
     * Creates a {@link CriterionKey} for a patient excluded because a must-have group had no results.
     *
     * @param group the attribute group that caused the patient exclusion
     * @return a {@link CriterionKey} of kind {@link ExclusionKind#MUST_HAVE} scoped to the group
     */
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

    /**
     * Creates a {@link CriterionKey} for a patient who has no data due to a consent restriction.
     *
     * @return a {@link CriterionKey} of kind {@link ExclusionKind#CONSENT}
     */
    public static CriterionKey consentNoData() {
        return new CriterionKey(
                ExclusionKind.CONSENT,
                "No data due to consent",
                null,
                null
        );
    }

    /**
     * Creates a {@link CriterionKey} for a resource that was blocked by a consent policy.
     *
     * @return a {@link CriterionKey} of kind {@link ExclusionKind#CONSENT}
     */
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

    /**
     * Creates a {@link CriterionKey} for a FHIR reference whose target could not be found.
     *
     * @param resourceType the FHIR resource type of the unresolved reference target
     * @return a {@link CriterionKey} of kind {@link ExclusionKind#REFERENCE_NOT_FOUND}
     */
    public static CriterionKey referenceNotFound(String resourceType) {
        return new CriterionKey(
                ExclusionKind.REFERENCE_NOT_FOUND,
                "Reference target not found",
                resourceType,
                null
        );
    }

    /**
     * Creates a {@link CriterionKey} for a FHIR reference whose target belongs to a patient outside the current batch.
     *
     * @param resourceType the FHIR resource type of the out-of-batch reference target
     * @return a {@link CriterionKey} of kind {@link ExclusionKind#REFERENCE_OUTSIDE_BATCH}
     */
    public static CriterionKey referenceOutsideBatch(String resourceType) {
        return new CriterionKey(
                ExclusionKind.REFERENCE_OUTSIDE_BATCH,
                "Reference outside patient batch",
                resourceType,
                null
        );
    }

    /**
     * Creates a {@link CriterionKey} for a FHIR reference that had an unrecognised or malformed format.
     *
     * @param resourceType the FHIR resource type associated with the invalid reference
     * @return a {@link CriterionKey} of kind {@link ExclusionKind#REFERENCE_INVALID}
     */
    public static CriterionKey referenceInvalid(String resourceType) {
        return new CriterionKey(
                ExclusionKind.REFERENCE_INVALID,
                "Invalid reference format",
                resourceType,
                null
        );
    }
}
