package de.medizininformatikinitiative.torch.util;


import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class FHIRPAthTest {
    FhirContext ctx = FhirContext.forR4();


    @Test
    public void fhirPath() {
        var src = new Observation();
        var path = "Observation.value";

        List<Base> elements = ctx.newFhirPath().evaluate(src, path, Base.class);

        assertThat(elements).isEqualTo(List.of());

    }
}
