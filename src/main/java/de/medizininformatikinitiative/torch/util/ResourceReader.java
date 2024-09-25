package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Resource;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class ResourceReader {

    // TODO: the FhirContext should be given via dependency injection
    public static FhirContext ctx = FhirContext.forR4();

    public static Resource readResource(String path) throws IOException {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(path);
        } catch (FileNotFoundException e) {
            throw new IOException(e);
        }
        return (Resource) ctx.newJsonParser().parseResource(fis);
    }
}
