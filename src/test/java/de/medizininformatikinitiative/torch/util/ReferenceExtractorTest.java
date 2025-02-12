package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferenceExtractorTest {

    private final FhirContext fhirContext = FhirContext.forR4();
    private final IFhirPath fhirPathEngine = fhirContext.newFhirPath();


    @Test
    void extractReference() thrgows MustHaveViolatedException {
        ReferenceExtractor referenceExtractor = new ReferenceExtractor(fhirPathEngine);
        AnnotatedAttribute attribute = new AnnotatedAttribute("Condition.subject", "Condition.subject", "Condition.subject", true, List.of("Organization1"));
        Condition condition = new Condition();
        condition.setId("Condition1");
        condition.setSubject(new Reference("Patient1"));

        assertThat(referenceExtractor.getReferences(condition, attribute)).containsExactly("Patient1");
    }

    @Test
    void extractReferenceViolated() throws MustHaveViolatedException {
        ReferenceExtractor referenceExtractor = new ReferenceExtractor(fhirPathEngine);
        AnnotatedAttribute attribute = new AnnotatedAttribute("Condition.subject", "Condition.subject", "Condition.subject", true, List.of("Organization1"));
        Condition condition = new Condition();
        condition.setId("Condition1");

        assertThatThrownBy(() -> {
            referenceExtractor.getReferences(condition, attribute);
        }).isInstanceOf(MustHaveViolatedException.class);
    }


}