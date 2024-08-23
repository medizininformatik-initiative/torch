package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Enumerations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CapabilityStatementController {

    private static final Logger logger = LoggerFactory.getLogger(CapabilityStatementController.class);

    @Autowired
    IParser parser;

    @GetMapping(value = "/fhir/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getCapabilityStatement() {
        logger.info("Received request for /fhir/metadata");
        CapabilityStatement capabilityStatement = createCapabilityStatement();
        String capabilityStatementString = parser.setPrettyPrint(true).encodeResourceToString(capabilityStatement);
        logger.debug("Encoded CapabilityStatement to JSON");
        return capabilityStatementString;
    }

    private CapabilityStatement createCapabilityStatement() {
        logger.debug("Creating CapabilityStatement");

        CapabilityStatement capabilityStatement = new CapabilityStatement();

        // Basic Metadata
        capabilityStatement.setPublisher("Your Organization");
        capabilityStatement.setDate(new java.util.Date());
        capabilityStatement.setFhirVersion(Enumerations.FHIRVersion._4_0_1);
        capabilityStatement.setKind(CapabilityStatement.CapabilityStatementKind.INSTANCE);
        capabilityStatement.getSoftware().setName("Torch FHIR Server").setVersion("1.0.0");
        capabilityStatement.getImplementation().setDescription("Torch FHIR Server Implementation");
        logger.debug("Created basic metadata for CapabilityStatement");

        // Restful Capabilities
        CapabilityStatement.CapabilityStatementRestComponent rest = new CapabilityStatement.CapabilityStatementRestComponent();
        rest.setMode(CapabilityStatement.RestfulCapabilityMode.SERVER);
        logger.debug("Configured RESTful capabilities");

        // Define supported resources and operations
        CapabilityStatement.CapabilityStatementRestResourceComponent resource = new CapabilityStatement.CapabilityStatementRestResourceComponent();
        resource.setType("CRTDL");
        resource.getInteraction().add(new CapabilityStatement.ResourceInteractionComponent().setCode(CapabilityStatement.TypeRestfulInteraction.READ));
        resource.getInteraction().add(new CapabilityStatement.ResourceInteractionComponent().setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE));
        rest.getResource().add(resource);
        logger.debug("Defined supported resources and operations");

        capabilityStatement.getRest().add(rest);

        logger.debug("CapabilityStatement creation completed");
        return capabilityStatement;
    }
}
