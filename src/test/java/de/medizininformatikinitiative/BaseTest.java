package de.medizininformatikinitiative;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS) // Required to use @BeforeAll non-static method in instance context
public class BaseTest {

    protected static FhirContext ctx;
    protected static IParser parser;
    protected static CDSStructureDefinitionHandler CDS;

    final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    public static void setUp() {
        ctx = FhirContext.forR4();
        parser = ctx.newJsonParser();
        CDS = new CDSStructureDefinitionHandler(ctx,"src/test/resources/StructureDefinitions/");
    }


}
