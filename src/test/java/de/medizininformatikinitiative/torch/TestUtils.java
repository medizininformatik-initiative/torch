package de.medizininformatikinitiative.torch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    public record TorchBundleUrls(String coreBundle, List<String> patientBundles) {

    }
}
