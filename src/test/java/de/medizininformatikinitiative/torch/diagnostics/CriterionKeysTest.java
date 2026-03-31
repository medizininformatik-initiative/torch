package de.medizininformatikinitiative.torch.diagnostics;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CriterionKeysTest {

    private static final AnnotatedAttributeGroup GROUP = new AnnotatedAttributeGroup(
            "group-id",
            "Observation",
            "http://example.org/StructureDefinition/Observation",
            List.of(),
            List.of()
    );

    private static final AnnotatedAttribute ATTRIBUTE = new AnnotatedAttribute(
            "attr-ref",
            "Observation.status",
            true
    );

    @Nested
    class MustHave {

        @Test
        void mustHaveAttribute_usesAttributeFhirPathAsBothNameAndAttributeRef() {
            var key = CriterionKeys.mustHaveAttribute(GROUP, ATTRIBUTE);

            assertThat(key.kind()).isEqualTo(ExclusionKind.MUST_HAVE);
            assertThat(key.name()).isEqualTo(ATTRIBUTE.fhirPath());
            assertThat(key.groupRef()).isEqualTo(GROUP.groupReference());
            assertThat(key.attributeRef()).isEqualTo(ATTRIBUTE.fhirPath());
        }

        @Test
        void mustHaveGroup_usesGroupIdAsNameAndNullAttributeRef() {
            var key = CriterionKeys.mustHaveGroup(GROUP);

            assertThat(key.kind()).isEqualTo(ExclusionKind.MUST_HAVE);
            assertThat(key.name()).isEqualTo(GROUP.id());
            assertThat(key.groupRef()).isEqualTo(GROUP.groupReference());
            assertThat(key.attributeRef()).isNull();
        }
    }

    @Nested
    class Consent {

        @Test
        void consentNoData_hasExpectedFields() {
            var key = CriterionKeys.consentNoData();

            assertThat(key.kind()).isEqualTo(ExclusionKind.CONSENT);
            assertThat(key.name()).isEqualTo("No data due to consent");
            assertThat(key.groupRef()).isNull();
            assertThat(key.attributeRef()).isNull();
        }

        @Test
        void consentResourceBlocked_hasExpectedFields() {
            var key = CriterionKeys.consentResourceBlocked();

            assertThat(key.kind()).isEqualTo(ExclusionKind.CONSENT);
            assertThat(key.name()).isEqualTo("Resource blocked by consent");
            assertThat(key.groupRef()).isNull();
            assertThat(key.attributeRef()).isNull();
        }
    }

    @Nested
    class Reference {

        @Test
        void referenceNotFound_usesResourceTypeAsGroupRef() {
            var key = CriterionKeys.referenceNotFound("Observation");

            assertThat(key.kind()).isEqualTo(ExclusionKind.REFERENCE_NOT_FOUND);
            assertThat(key.name()).isEqualTo("Reference target not found");
            assertThat(key.groupRef()).isEqualTo("Observation");
            assertThat(key.attributeRef()).isNull();
        }

        @Test
        void referenceOutsideBatch_usesResourceTypeAsGroupRef() {
            var key = CriterionKeys.referenceOutsideBatch("Patient");

            assertThat(key.kind()).isEqualTo(ExclusionKind.REFERENCE_OUTSIDE_BATCH);
            assertThat(key.name()).isEqualTo("Reference outside patient batch");
            assertThat(key.groupRef()).isEqualTo("Patient");
            assertThat(key.attributeRef()).isNull();
        }

        @Test
        void referenceInvalid_usesResourceTypeAsGroupRef() {
            var key = CriterionKeys.referenceInvalid("MedicationRequest");

            assertThat(key.kind()).isEqualTo(ExclusionKind.REFERENCE_INVALID);
            assertThat(key.name()).isEqualTo("Invalid reference format");
            assertThat(key.groupRef()).isEqualTo("MedicationRequest");
            assertThat(key.attributeRef()).isNull();
        }
    }
}
