package de.medizininformatikinitiative.torch.rest;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.ExtractDataParameters;
import org.hl7.fhir.r4.model.Base64BinaryType;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

@Component
public class ExtractDataParametersParser {

    private final FhirContext fhirContext;
    private final ObjectMapper objectMapper;

    public ExtractDataParametersParser(FhirContext fhirContext, ObjectMapper objectMapper) {
        this.fhirContext = requireNonNull(fhirContext);
        this.objectMapper = requireNonNull(objectMapper);
    }

    /**
     * Parses the CRDTL content from a byte array.
     *
     * @param content the byte array containing the CRDTL content
     * @return the parsed Crtdl object
     * @throws IOException if there is an error during parsing
     */
    private Crtdl parseCrtdlContent(byte[] content) throws IOException {
        return objectMapper.readValue(content, Crtdl.class);
    }

    /**
     * Normalizes a patient parameter value to its bare id part.
     *
     * @param value either a bare FHIR id ({@code 123}) or a relative Patient reference ({@code Patient/123})
     * @return the bare id
     * @throws IllegalArgumentException if the value references another resource type or is neither a bare id nor
     *                                  a relative Patient reference (e.g. absolute or versioned references)
     */
    private static String normalizePatientId(String value) {
        IdType id = new IdType(value);
        if (id.hasResourceType() && !"Patient".equals(id.getResourceType())) {
            throw new IllegalArgumentException("Patient parameter '%s' must reference resource type Patient".formatted(value));
        }
        if (id.hasBaseUrl() || id.hasVersionIdPart() || !id.isIdPartValid()) {
            throw new IllegalArgumentException(
                    "Patient parameter '%s' must be a bare id or a relative Patient reference (Patient/<id>)".formatted(value));
        }
        return id.getIdPart();
    }

    /**
     * Parses a FHIR {@link Parameters} resource containing CRTDL data and optional patient IDs.
     *
     * <p>The input must be a valid JSON representation of a Parameters resource
     * that includes:
     * <ul>
     *     <li>A parameter named {@code crtdl} with a {@code base64Binary} value containing the CRTDL content.</li>
     *     <li>Optional parameters named {@code patient} with {@code string} values for patient IDs, each either a
     *     bare id or a relative Patient reference; they are normalized to distinct bare ids.</li>
     *     <li>An optional parameter named {@code consentDiagnostics} with a {@code boolean} value, enabling
     *     the opt-in raw-provisions/final-periods/consent-considered-resources diagnostics for this job.</li>
     * </ul>
     *
     * @param body the JSON string representing a FHIR Parameters resource
     * @return an {@link ExtractDataParameters} containing the parsed CRTDL content and patient IDs
     * @throws IllegalArgumentException if the input cannot be parsed as a valid Parameters
     *                                  resource, the Parameters resource is empty, no crtdl
     *                                  parameter with base64 encoded content is found, a patient
     *                                  parameter is invalid, or the CRTDL content cannot be read
     *                                  due to an IOException
     */
    public ExtractDataParameters parseParameters(String body) {
        Parameters parameters;
        try {
            parameters = fhirContext.newJsonParser().parseResource(Parameters.class, body);
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("Input is not a valid Parameters Resource", e);
        }
        if (parameters.isEmpty()) {
            throw new IllegalArgumentException("Empty Parameters resource provided");
        }
        try {
            byte[] crtdlContent = null;
            List<String> patientIds = new ArrayList<>();
            boolean consentDiagnostics = false;

            for (var parameter : parameters.getParameter()) {
                if (parameter.hasValue()) {
                    var value = parameter.getValue();
                    if ("crtdl".equals(parameter.getName()) && value.hasType("base64Binary")) {
                        crtdlContent = ((Base64BinaryType) parameter.getValue()).getValue();
                    }
                    if ("patient".equals(parameter.getName()) && value.hasType("string")) {
                        String patientId = normalizePatientId(value.primitiveValue());
                        if (!patientIds.contains(patientId)) {
                            patientIds.add(patientId);
                        }
                    }
                    if ("consentDiagnostics".equals(parameter.getName()) && value.hasType("boolean")) {
                        consentDiagnostics = ((BooleanType) value).booleanValue();
                    }
                }
            }
            if (crtdlContent == null) {
                throw new IllegalArgumentException("No base64 encoded CRDTL content found in Parameters resource");
            }
            // Process crtdl content, potentially using patientIds
            return new ExtractDataParameters(parseCrtdlContent(crtdlContent), patientIds, consentDiagnostics);
        } catch (IOException e) {
            throw new IllegalArgumentException("Reading CRTDL Failed with IO Exception", e);
        }
    }
}

