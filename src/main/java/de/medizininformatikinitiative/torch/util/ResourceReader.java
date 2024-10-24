package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Resource;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;


public class ResourceReader {



    public ResourceReader(FhirContext ctx){
    this.ctx=ctx;
    }

    protected FhirContext ctx = null;

    public Resource readResource(String path) throws IOException {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(path);
        } catch (FileNotFoundException e) {
            throw new IOException(e);
        }
        return (Resource) ctx.newJsonParser().parseResource(fis);
    }
}
