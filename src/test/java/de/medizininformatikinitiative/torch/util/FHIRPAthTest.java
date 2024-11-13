package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class FHIRPAthTest {
    FhirContext ctx = FhirContext.forR4();

    @ParameterizedTest
    @ValueSource(strings = {"Observation.identifier.where(type.coding.system='http://terminology.hl7.org/CodeSystem/v2-0203' and type.coding.code='OBI')",
            "Observation.identifier.where(Observation.identifier.type.coding.system='http://terminology.hl7.org/CodeSystem/v2-0203' and Observation.identifier.type.coding.code='OBI').type.coding.userSelected",
            "   Observation.category.coding.where(Observation.category.coding.system='http://loinc.org' and Observation.category.coding.code='26436-6')"})
    public void fhirPAth(String path) {
        var src = new Observation();

        List<Base> elements = ctx.newFhirPath().evaluate(src, path, Base.class);
        System.out.println("elements = " + elements);
        assertThat(elements).isEqualTo(List.of());

    }

}
