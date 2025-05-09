package de.medizininformatikinitiative.torch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.Base64BinaryType;
import org.hl7.fhir.r4.model.Parameters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

public class TestUtils {

    public static JsonNode nodeFromTreeString(String s) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(s);
    }

    public static JsonNode nodeFromValueString(String s) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.valueToTree(s);
    }

    private static String slurp(String path) throws IOException {
        return Files.readString(Path.of("src/test/resources/CrtdlItTests/" + path));
    }

    public static Parameters loadCrtdl(String path) throws IOException {
        var crtdl = Base64.getEncoder().encodeToString(slurp(path).getBytes(StandardCharsets.UTF_8));

        var parameters = new Parameters();
        parameters.setParameter("crtdl", new Base64BinaryType(crtdl));
        return parameters;
    }
}
