package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.DecodedCRTDLContent;
import de.medizininformatikinitiative.torch.model.crtdl.DecodedContent;
import org.hl7.fhir.r4.model.Base64BinaryType;
import org.hl7.fhir.r4.model.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class CrtdlParser {
    private static final Logger logger = LoggerFactory.getLogger(CrtdlParser.class);
    private final FhirContext fhirContext;
    private final ObjectMapper objectMapper;

    public CrtdlParser(FhirContext fhirContext, ObjectMapper objectMapper) {
        this.fhirContext = fhirContext;
        this.objectMapper = objectMapper;
    }

    private static DecodedContent decodeCrtdlContent(Parameters parameters) {
        byte[] crtdlContent = null;
        List<String> patientIds = new ArrayList<>();

        for (var parameter : parameters.getParameter()) {
            if (parameter.hasValue()) {
                var value = parameter.getValue();
                if ("crtdl".equals(parameter.getName()) && value.hasType("base64Binary")) {
                    logger.debug("Found crtdl content for parameter '{}'", parameter.getName());
                    crtdlContent = ((Base64BinaryType) parameter.getValue()).getValue();
                }
                if ("patient".equals(parameter.getName()) && value.hasType("string")) {
                    logger.debug("Found Patient content for parameter '{}' {}", parameter.getName(), parameter.getValue());
                    patientIds.add(value.primitiveValue());
                }
            }
        }

        if (crtdlContent == null) {
            throw new IllegalArgumentException("No base64 encoded CRDTL content found in Parameters resource");
        }

        return new DecodedContent(crtdlContent, patientIds);
    }

    private Crtdl parseCrtdlContent(byte[] content) throws IOException {
        return objectMapper.readValue(content, Crtdl.class);
    }

    public Mono<DecodedCRTDLContent> parseCrtdl(String body) {

        var parameters = fhirContext.newJsonParser().parseResource(Parameters.class, body);
        if (parameters.isEmpty()) {
            logger.debug("Empty Parameters");
            throw new IllegalArgumentException("Empty Parameters");
        }

        try {
            var decodedContent = decodeCrtdlContent(parameters);
            byte[] crtdlBytes = decodedContent.crtdl();
            List<String> patientIds = decodedContent.patientIds();

            // Process crtdl content, potentially using patientIds
            return Mono.just(new DecodedCRTDLContent(parseCrtdlContent(crtdlBytes), patientIds));
        } catch (IOException e) {
            return Mono.error(e);
        }
    }

}

