package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferenceExtractorTest {

    public static final AnnotatedAttribute ATTRIBUTE = new AnnotatedAttribute("Condition.subject", "Condition.subject", "Condition.subject", true, List.of("SubjectGroup"));
    public static final AnnotatedAttribute ATTRIBUTE_2 = new AnnotatedAttribute("Condition.asserter", "Condition.asserter", "Condition.asserter", true, List.of("AssertionGroup"));
    private final FhirContext fhirContext = FhirContext.forR4();
    private final IFhirPath fhirPathEngine = fhirContext.newFhirPath();
    static AnnotatedAttributeGroup GROUP_TEST = new AnnotatedAttributeGroup("Test", "test", List.of(ATTRIBUTE, ATTRIBUTE_2), List.of());
    static Map<String, AnnotatedAttributeGroup> GROUPS = new HashMap<>();


    @BeforeAll
    static void setup() {
        GROUPS.put("Test", GROUP_TEST);

    }

    @Nested
    class getReference {
        @Test
        void getReference() throws MustHaveViolatedException {
            ReferenceExtractor referenceExtractor = new ReferenceExtractor(fhirPathEngine, GROUPS);
            Condition condition = new Condition();
            condition.setId("Condition1");
            condition.setSubject(new Reference("Patient1"));

            assertThat(referenceExtractor.getReferences(condition, ATTRIBUTE)).containsExactly("Patient1");
        }

        @Test
        void getReferenceViolated() throws MustHaveViolatedException {
            ReferenceExtractor referenceExtractor = new ReferenceExtractor(fhirPathEngine, GROUPS);
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

            ReferenceExtractor referenceExtractor = new ReferenceExtractor(fhirPathEngine, GROUPS);

            Condition condition = new Condition();
            condition.setId("Condition1");
            condition.setSubject(new Reference("Patient1"));
            condition.setAsserter(new Reference("Asserter1"));
            ResourceGroupWrapper wrapper = new ResourceGroupWrapper(condition, Set.of("Test"));


            assertThat(referenceExtractor.extract(wrapper)).containsExactly(new ReferenceWrapper("Condition1", "Test", ATTRIBUTE, List.of("Patient1")), new ReferenceWrapper("Condition1", "Test", ATTRIBUTE_2, List.of("Asserter1")));
        }

        @Test
        void violated() throws MustHaveViolatedException {
            ReferenceExtractor referenceExtractor = new ReferenceExtractor(fhirPathEngine, GROUPS);
            Condition condition = new Condition();
            condition.setId("Condition1");
            ResourceGroupWrapper wrapper = new ResourceGroupWrapper(condition, Set.of("Test"));
            assertThatThrownBy(() -> {
                referenceExtractor.extract(wrapper);
            }).isInstanceOf(MustHaveViolatedException.class);
        }


    }


}