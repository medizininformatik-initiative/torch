package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Enumerations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CapabilityStatementController {

    private static final Logger logger = LoggerFactory.getLogger(CapabilityStatementController.class);

    @Autowired
    FhirContext fhirContext;

    @Operation(
            summary = "GET /fhir/metadata — CapabilityStatement",
            description = "Returns the server CapabilityStatement (FHIR R4).",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "FHIR CapabilityStatement",
                            content = @Content(
                                    mediaType = "application/fhir+json",
                                    schema = @Schema(type = "object")
                            )
                    )
            }
    )
    @GetMapping(value = "/fhir/metadata", produces = "application/fhir+json")
    public org.springframework.http.ResponseEntity<String> getCapabilityStatement() {
        var cs = createCapabilityStatement();
        String json = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(cs);
        return org.springframework.http.ResponseEntity.ok(json);
    }

    private CapabilityStatement createCapabilityStatement() {
        logger.debug("Creating CapabilityStatement");

        CapabilityStatement capabilityStatement = new CapabilityStatement();

        // Basic Metadata
        capabilityStatement.setPublisher("Your Organization");
        capabilityStatement.setDate(new java.util.Date());
        capabilityStatement.setFhirVersion(Enumerations.FHIRVersion._4_0_1);
        capabilityStatement.setKind(CapabilityStatement.CapabilityStatementKind.INSTANCE);
        capabilityStatement.getSoftware().setName("Torch FHIR Server").setVersion("1.0.0-alpha.18");
        capabilityStatement.getImplementation().setDescription("Torch FHIR Server Implementation");
        logger.trace("Created basic metadata for CapabilityStatement");


        CapabilityStatement.CapabilityStatementRestComponent rest = new CapabilityStatement.CapabilityStatementRestComponent();
        rest.setMode(CapabilityStatement.RestfulCapabilityMode.SERVER);
        logger.trace("Configured RESTful capabilities");

        // Define supported resources and operations
        CapabilityStatement.CapabilityStatementRestResourceComponent resource = new CapabilityStatement.CapabilityStatementRestResourceComponent();
        resource.setType("CRTDL");
        resource.getInteraction().add(new CapabilityStatement.ResourceInteractionComponent().setCode(CapabilityStatement.TypeRestfulInteraction.READ));
        resource.getInteraction().add(new CapabilityStatement.ResourceInteractionComponent().setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE));
        rest.getResource().add(resource);

        capabilityStatement.getRest().add(rest);

        logger.debug("CapabilityStatement creation completed");
        return capabilityStatement;
    }
}
