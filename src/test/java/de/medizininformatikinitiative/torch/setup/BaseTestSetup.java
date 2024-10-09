package de.medizininformatikinitiative.torch.setup;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.CdsStructureDefinitionHandler;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
import de.medizininformatikinitiative.torch.util.ResourceReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseTestSetup {

    private static final Logger logger = LoggerFactory.getLogger(BaseTestSetup.class);

    private final FhirContext ctx;
    private final CdsStructureDefinitionHandler cds;
    private final ElementCopier copier;
    private final ObjectMapper objectMapper;
    private final Redaction redaction;

    // Constructor initializes all fields
    public BaseTestSetup() {
        this.objectMapper = new ObjectMapper();
        this.ctx = ResourceReader.ctx;
        this.cds = new CdsStructureDefinitionHandler("src/main/resources/StructureDefinitions/");
        this.copier = new ElementCopier(cds);
        this.redaction = new Redaction(cds);
        logger.info("Base test setup complete with immutable configurations.");
    }

    // Provide getter methods for accessing the initialized objects
    public FhirContext getFhirContext() {
        return ctx;
    }

    public CdsStructureDefinitionHandler getCds() {
        return cds;
    }

    public ElementCopier getCopier() {
        return copier;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public Redaction getRedaction() {
        return redaction;
    }
}
