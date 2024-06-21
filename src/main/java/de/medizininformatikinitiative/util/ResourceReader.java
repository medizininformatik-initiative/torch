package de.medizininformatikinitiative.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class ResourceReader {

    public static FhirContext ctx= FhirContext.forR4();;

    public static IParser jsonParser = ctx.newJsonParser();

    public static Resource readResource(String path) throws IOException {
        FileInputStream fis=null;
        try {
            fis = new FileInputStream(path);
        } catch (FileNotFoundException e) {
            throw new IOException(e);
        }
        return (Resource) jsonParser.parseResource(fis);
    }
}
