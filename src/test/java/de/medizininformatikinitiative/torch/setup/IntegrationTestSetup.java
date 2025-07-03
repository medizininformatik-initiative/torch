package de.medizininformatikinitiative.torch.setup;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.FhirPathBuilder;
import de.medizininformatikinitiative.torch.util.Redaction;
import de.medizininformatikinitiative.torch.util.ResourceReader;
import org.hl7.fhir.r4.model.DomainResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class IntegrationTestSetup {

    private static final Logger logger = LoggerFactory.getLogger(IntegrationTestSetup.class);

    private final FhirContext ctx;
    private final StructureDefinitionHandler structHandler;
    private final ElementCopier copier;
    private final ObjectMapper objectMapper;
    private final Redaction redaction;
    private final ResourceReader resourceReader;
    private final FhirPathBuilder builder;

    // Constructor initializes all fields
    public IntegrationTestSetup() throws IOException {
        this.ctx = FhirContext.forR4();
        this.resourceReader = new ResourceReader(ctx);
        this.structHandler = new StructureDefinitionHandler(new File("structureDefinitions/"), resourceReader);
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        structHandler.processDirectory();

        builder = new FhirPathBuilder();
        this.copier = new ElementCopier(ctx);
        this.redaction = new Redaction(structHandler);

        logger.info("Base test setup complete with immutable configurations.");
    }

    public FhirPathBuilder fhirPathBuilder() {
        return builder;
    }

    // Provide getter methods for accessing the initialized objects
    public FhirContext fhirContext() {
        return ctx;
    }

    public StructureDefinitionHandler structureDefinitionHandler() {
        return structHandler;
    }

    public ElementCopier copier() {
        return copier;
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public Redaction redaction() {
        return redaction;
    }

    public DomainResource readResource(String path) throws IOException {
        return (DomainResource) resourceReader.readResource(path);
    }
}
