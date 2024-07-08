package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS) // Required to use @BeforeAll non-static method in instance context
public class BaseTest {

    protected static FhirContext ctx;
    protected static IParser parser;
    protected static CdsStructureDefinitionHandler cds;
    protected static ElementCopier copier;

    final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    public static void setUp() {
        ctx = FhirContext.forR4();
        parser = ctx.newJsonParser();
        cds = new CdsStructureDefinitionHandler(ctx,"src/test/resources/StructureDefinitions/");
        copier=new ElementCopier(cds);
    }


}
