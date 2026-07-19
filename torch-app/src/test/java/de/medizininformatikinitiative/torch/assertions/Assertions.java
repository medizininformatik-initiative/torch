package de.medizininformatikinitiative.torch.assertions;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;

public interface Assertions {

    static BundleAssert assertThat(Bundle actual) {
        return new BundleAssert(actual);
    }

    static ResourceAssert assertThat(Resource actual) {
        return new ResourceAssert(actual);
    }
}
