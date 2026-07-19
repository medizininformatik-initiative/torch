package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractDataParametersParserTest {

    private ExtractDataParametersParser parser;

    @BeforeEach
    void setup() {
        FhirContext fhirContext = FhirContext.forR4();
        parser = new ExtractDataParametersParser(fhirContext, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void parseParameters_emptyParameters_throws() {
        String emptyParamsJson = "{\"resourceType\":\"Parameters\",\"parameter\":[]}";

        assertThatThrownBy(() -> parser.parseParameters(emptyParamsJson))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Empty Parameters");
    }

    @Test
    void parseCrtdl_missingParametersParameter_returnsErrorMono() {
        String noCrtdlJson = """
                {
                  "resourceType": "Parameters",
                  "parameter": [
                    {
                      "name": "patient",
                      "valueString": "patient-123"
                    }
                  ]
                }
                """;

        assertThatThrownBy(() -> parser.parseParameters(noCrtdlJson))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No base64 encoded CRDTL content found in Parameters resource");
    }

    @Test
    void parseParameters_invalidJson_returnsErrorMono() {
        String invalidJson = "this is not valid JSON";

        assertThatThrownBy(() -> parser.parseParameters(invalidJson))
                .isInstanceOf(IllegalArgumentException.class)
                .hasCauseInstanceOf(DataFormatException.class);
    }

    @Test
    void parseParameters_crtdl_ioException_returnsErrorMono() {
        String validParametersInvalidCrtdl = """
                {
                  "resourceType": "Parameters",
                  "parameter": [
                    {
                      "name": "crtdl",
                      "valueBase64Binary": "ew=="
                    }
                  ]
                }
                """;

        assertThatThrownBy(() -> parser.parseParameters(validParametersInvalidCrtdl))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Reading CRTDL Failed with IO Exception");
    }
}
