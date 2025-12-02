package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.config.TorchProperties;
import de.medizininformatikinitiative.torch.exceptions.ConsentFormatException;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.model.crtdl.ExtractDataParameters;
import de.medizininformatikinitiative.torch.service.CrtdlValidatorService;
import de.medizininformatikinitiative.torch.service.ExtractDataService;
import de.medizininformatikinitiative.torch.service.JobPersistenceService;
import de.medizininformatikinitiative.torch.util.CrtdlFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FhirControllerTest {
    @Mock
    ExtractDataParametersParser extractDataParametersParser;

    @Mock
    ExtractDataService extractDataService;

    @Mock
    CrtdlValidatorService validator;

    @Mock
    JobPersistenceService jobPersistenceService;

    @Mock
    ObjectMapper objectMapper;

    TorchProperties properties = new TorchProperties(
            new TorchProperties.Base("http://base-url"),
            new TorchProperties.Output(new TorchProperties.Output.File(new TorchProperties.Output.File.Server("http://server-url"))),
            new TorchProperties.Profile("/profile-dir"),
            new TorchProperties.Mapping("consent", "typeToConsent"),
            new TorchProperties.Flare(null),
            new TorchProperties.Results("BASE_DIR", "persistence"),
            10, 5, 100,
            "mappingsFile", "conceptTreeFile", "dseMappingTreeFile",
            "search-parameters.json",
            true
    );

    WebTestClient client;

    @BeforeEach
    void setup() {
        FhirContext fhirContext = FhirContext.forR4();
        FhirController fhirController = new FhirController(fhirContext, extractDataParametersParser, validator, jobPersistenceService, properties, objectMapper);
        client = WebTestClient.bindToRouterFunction(fhirController.queryRouter()).build();
    }

    @Nested
    class ExtractDataErrorBranchTests {

        @Test
        void emptyRequestBodyTriggersBadRequest() {
            var response = client.post().uri("/fhir/$extract-data").exchange();

            response.expectStatus().isBadRequest()
                    .expectHeader().contentType("application/fhir+json")
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome");
        }

        @Test
        void blankRequestBodyTriggersBadRequest() {
            var response = client.post().uri("/fhir/$extract-data").contentType(MediaType.APPLICATION_JSON).bodyValue("  ").exchange();

            response.expectStatus().isBadRequest().expectHeader().contentType("application/fhir+json")
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome");
        }
    }

    @Nested
    class Validator {
        @Test
        void invalidCrtdlTriggersBadRequest() throws ValidationException, ConsentFormatException {
            ExtractDataParameters params = new ExtractDataParameters(CrtdlFactory.empty(), Collections.emptyList());
            when(extractDataParametersParser.parseParameters(any())).thenReturn(params);
            when(validator.validateAndAnnotate(any())).thenThrow(new ValidationException("Invalid CRTDL"));

            var response = client.post().uri("/fhir/$extract-data").contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange();

            response.expectStatus().isBadRequest().expectHeader().contentType("application/fhir+json")
                    .expectBody()
                    .jsonPath("$.resourceType").isEqualTo("OperationOutcome")
                    .jsonPath("$.issue[0].diagnostics").isEqualTo("Invalid CRTDL");
        }
    }
}
