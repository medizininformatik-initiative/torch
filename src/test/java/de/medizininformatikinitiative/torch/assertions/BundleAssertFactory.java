package de.medizininformatikinitiative.torch.assertions;

import org.assertj.core.api.InstanceOfAssertFactory;
import org.hl7.fhir.r4.model.Bundle;

public final class BundleAssertFactory {
    public static final InstanceOfAssertFactory<Bundle, BundleAssert> BUNDLE_ASSERT = new InstanceOfAssertFactory<>
            (Bundle.class, BundleAssert::new);
}
