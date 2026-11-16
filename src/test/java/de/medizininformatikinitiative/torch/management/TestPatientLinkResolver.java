package de.medizininformatikinitiative.torch.management;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Provides a {@link PatientLinkResolver} built from the production compartment definition and search parameters,
 * parsed once per test JVM.
 */
public final class TestPatientLinkResolver {

    private static PatientLinkResolver instance;

    private TestPatientLinkResolver() {
    }

    public static synchronized PatientLinkResolver get() {
        if (instance == null) {
            try {
                instance = new PatientLinkResolver(new CompartmentManager("compartmentdefinition-patient.json"),
                        "search-parameters.json");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return instance;
    }
}
