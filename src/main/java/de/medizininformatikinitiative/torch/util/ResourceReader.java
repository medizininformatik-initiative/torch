package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Resource;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;


/**
 * Util class for reading FHIR resources from File
 */
public class ResourceReader {


    public ResourceReader(FhirContext ctx) {
        this.ctx = ctx;
    }

    private final FhirContext ctx;

    public Resource readResource(String path) throws IOException {
        FileInputStream fis;
        try {
            fis = new FileInputStream(path);
        } catch (FileNotFoundException e) {
            throw new IOException(e);
        }
        return (Resource) ctx.newJsonParser().parseResource(fis);
    }
}
