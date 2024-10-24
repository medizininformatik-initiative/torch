package de.medizininformatikinitiative.torch.setup;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.CdsStructureDefinitionHandler;
import de.medizininformatikinitiative.torch.util.*;
import org.hl7.fhir.r4.model.DomainResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class BaseTestSetup {

    private static final Logger logger = LoggerFactory.getLogger(BaseTestSetup.class);

    private final FhirContext ctx;
    private final CdsStructureDefinitionHandler cds;
    private final ElementCopier copier;
    private final ObjectMapper objectMapper;
    private final Redaction redaction;
    private final FhirPathBuilder builder;
    private final ResourceReader resourceReader;

    // Constructor initializes all fields
    public BaseTestSetup() {
        this.ctx = FhirContext.forR4();
        this.resourceReader=new ResourceReader(ctx);
        this.cds = new CdsStructureDefinitionHandler("src/main/resources/StructureDefinitions/", resourceReader);
        Slicing slicing = new Slicing(cds,ctx);
        this.objectMapper = new ObjectMapper();
        this.builder=new FhirPathBuilder(slicing);
        this.copier = new ElementCopier(cds,ctx,builder);
        this.redaction = new Redaction(cds,slicing);
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

    public DomainResource readResource(String path) throws IOException {
        return (DomainResource) resourceReader.readResource(path);
    }
}
