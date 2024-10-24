package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS) // Required to use @BeforeAll non-static method in instance context
public class BaseTest {

    protected static final Logger logger = LoggerFactory.getLogger(BaseTest.class);

    protected static FhirContext ctx;
    protected static CdsStructureDefinitionHandler cds;
    protected static ElementCopier copier;
    protected static ObjectMapper objectMapper;
    protected static Redaction redaction;

    @BeforeAll
    public static void setUp() {
        objectMapper = new ObjectMapper();
        ctx = FhirContext.forR4();
        cds = new CdsStructureDefinitionHandler("src/main/resources/StructureDefinitions/");
        copier = new ElementCopier(cds);
        redaction = new Redaction(cds);
    }
}
