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

}
