package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferenceExtractorTest {

    public static final AnnotatedAttribute ATTRIBUTE = new AnnotatedAttribute("Condition.subject", "Condition.subject", "Condition.subject", true, List.of("SubjectGroup"));
    public static final AnnotatedAttribute ATTRIBUTE_2 = new AnnotatedAttribute("Condition.asserter", "Condition.asserter", "Condition.asserter", true, List.of("AssertionGroup"));
    private final FhirContext fhirContext = FhirContext.forR4();
    static AnnotatedAttributeGroup GROUP_TEST = new AnnotatedAttributeGroup("Test", "Condition", "test", List.of(ATTRIBUTE, ATTRIBUTE_2), List.of(), null);
    static Map<String, AnnotatedAttributeGroup> GROUPS = new HashMap<>();


    @BeforeAll
    static void setup() {
        GROUPS.put("Test", GROUP_TEST);

    }

    @Nested
    class getReference {
        @Test
        void getReference() throws MustHaveViolatedException {
            ReferenceExtractor referenceExtractor = new ReferenceExtractor(fhirContext);
            Condition condition = new Condition();
            condition.setId("Condition1");
            condition.setSubject(new Reference("Patient1"));

            assertThat(referenceExtractor.getReferences(condition, ATTRIBUTE)).containsExactly("Patient1");
        }

        @Test
        void getReferenceViolated() {
            ReferenceExtractor referenceExtractor = new ReferenceExtractor(fhirContext);
            Condition condition = new Condition();
            condition.setId("Condition1");

            assertThatThrownBy(() -> {
                referenceExtractor.getReferences(condition, ATTRIBUTE);
            }).isInstanceOf(MustHaveViolatedException.class);
        }

    }


    @Nested
    class Extract {
        @Test
        void success() throws MustHaveViolatedException {

            ReferenceExtractor referenceExtractor = new ReferenceExtractor(fhirContext);

            Condition condition = new Condition();
            condition.setId("Condition1");
            condition.setSubject(new Reference("Patient1"));
            condition.setAsserter(new Reference("Asserter1"));

            assertThat(referenceExtractor.extract(condition, GROUPS, "Test")).containsExactly(new ReferenceWrapper(ATTRIBUTE, List.of("Patient1"), "Test", "Condition/Condition1"), new ReferenceWrapper(ATTRIBUTE_2, List.of("Asserter1"), "Test", "Condition/Condition1"));
        }

        @Test
        void violated() {
            ReferenceExtractor referenceExtractor = new ReferenceExtractor(fhirContext);
            Condition condition = new Condition();
            condition.setId("Condition1");
            assertThatThrownBy(() -> {
                referenceExtractor.extract(condition, GROUPS, "Test");
            }).isInstanceOf(MustHaveViolatedException.class);
        }


    }


}
