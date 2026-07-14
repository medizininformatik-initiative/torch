package de.medizininformatikinitiative.torch.model.crtdl;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FieldConditionTest {


    @Test
    void simplePathWithDots() {
        AnnotatedAttribute attr = new AnnotatedAttribute(
                "",
                "Patient.name.given",
                false
        );

        List<FieldCondition> result = FieldCondition.splitFhirPath(attr);

        assertThat(result).containsExactly(
                new FieldCondition("Patient", ""),
                new FieldCondition("name", ""),
                new FieldCondition("given", "")
        );
    }

    @Test
    void singleElement() {
        AnnotatedAttribute attr = new AnnotatedAttribute(
                "", "Observation",
                false
        );

        List<FieldCondition> result = FieldCondition.splitFhirPath(attr);

        assertThat(result).containsExactly(
                new FieldCondition("Observation", "")
        );
    }

    @Test
    void multipleWhereClauses() {
        AnnotatedAttribute attr = new AnnotatedAttribute(
                "",
                "Observation.where(status='final').value.where(exists()).unit",
                false
        );

        List<FieldCondition> result = FieldCondition.splitFhirPath(attr);

        assertThat(result).containsExactly(
                new FieldCondition("Observation", ".where(status='final')"),
                new FieldCondition("value", ".where(exists())"),
                new FieldCondition("unit", "")
        );
    }


    @Test
    void dotInWhereClause() {
        AnnotatedAttribute attr = new AnnotatedAttribute(
                "",
                "Observation.where(value.unit='mg').code",
                false
        );

        List<FieldCondition> result = FieldCondition.splitFhirPath(attr);

        assertThat(result).containsExactly(
                new FieldCondition("Observation", ".where(value.unit='mg')"),
                new FieldCondition("code", "")
        );
    }

    @Test
    void consecutiveWhereClauses() {
        AnnotatedAttribute attr = new AnnotatedAttribute(
                "",
                "Resource.where(type='A').id",
                false
        );

        List<FieldCondition> result = FieldCondition.splitFhirPath(attr);

        assertThat(result).containsExactly(
                new FieldCondition("Resource", ".where(type='A')"),
                new FieldCondition("id", "")
        );
    }

    @Test
    void choiceElement() {
        AnnotatedAttribute attr = new AnnotatedAttribute(
                "",
                "Resource.value.ofType(\"CodeAbleConcept\")",
                false
        );

        List<FieldCondition> result = FieldCondition.splitFhirPath(attr);

        assertThat(result).containsExactly(
                new FieldCondition("Resource", ""),
                new FieldCondition("value", ".ofType(\"CodeAbleConcept\")")
        );
    }

    @Test
    void parseSegment_noFunction() {
        FieldCondition fc = FieldCondition.parseSegment("value");
        assertThat(fc.fieldName()).isEqualTo("value");
        assertThat(fc.condition()).isEmpty();
    }

    @Test
    void parseSegment_singleFunction() {
        FieldCondition fc = FieldCondition.parseSegment("value.where(value > 5)");
        assertThat(fc.fieldName()).isEqualTo("value");
        assertThat(fc.condition()).isEqualTo(".where(value > 5)");
    }

    @Test
    void parseSegment_multipleFunctions() {
        FieldCondition fc = FieldCondition.parseSegment("value.ofType(Quantity).where(value > 5)");
        // The first function is .ofType(Quantity), should split before it
        assertThat(fc.fieldName()).isEqualTo("value");
        assertThat(fc.condition()).isEqualTo(".ofType(Quantity).where(value > 5)");
    }

}
