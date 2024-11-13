package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.TerserUtil;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.junit.Test;

public class TerserTest {


    FhirContext ctx = FhirContext.forR4();

    @Test
    public void slicingPath() {
        var src = new Observation();
        Coding coding = new Coding();
        coding.setCode("Test");
        coding.setSystem("http://terminology.hl7.org/CodeSystem/v2-0203");
        TerserUtil.setFieldByFhirPath(ctx.newTerser(), "Observation.valueCodeableConcept.coding", src, coding);

    }


}
