package de.medizininformatikinitiative.torch.assertions;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.ListAssert;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Resource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static de.medizininformatikinitiative.torch.TestUtils.nodeFromTreeString;

public class ResourceAssert extends AbstractAssert<ResourceAssert, Resource> {

    private static final JsonNode DATA_ABSENT_REASON;
    private static final JsonNode DATA_ABSENT_REASON_EXTENSION;

    static {
        try {
            DATA_ABSENT_REASON = nodeFromTreeString("""
                    {
                        "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
                        "valueCode": ""
                    }
                    """);
            DATA_ABSENT_REASON_EXTENSION = nodeFromTreeString("""
                    {
                    "extension": [
                            {
                                "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
                                "valueCode": ""
                            }
                        ]
                    }
                    """);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private final static FhirContext ctx = FhirContext.forR4();
    private final static IFhirPath fhirPathEngine = FhirContext.forR4().newFhirPath();

    protected ResourceAssert(Resource resource) {
        super(resource, ResourceAssert.class);
    }

    public ListAssert<String> extractChildrenStringsAt(String path) {
        List<Base> found = fhirPathEngine.evaluate(actual, path, Base.class);
        IParser fhirParser = ctx.newJsonParser();

        List<String> parsed = new LinkedList<>();
        for (Base base : found) {
            if (base.isPrimitive()) {
                parsed.add(fhirParser.encodeToString(base));
            } else {
                failWithMessage("Expected a primitive value but found %s", base);
            }
        }

        return new ListAssert<>(parsed);
    }

    public ListAssert<JsonNode> extractElementsAt(String path) {
        IParser fhirParser = ctx.newJsonParser();
        ObjectMapper mapper = new ObjectMapper();
        List<Base> found = fhirPathEngine.evaluate(actual, path, Base.class);

        List<JsonNode> parsed = new LinkedList<>();
        for (Base base : found) {
            var encoded = fhirParser.encodeToString(base);

            if (base.isPrimitive()) {
                parsed.add(mapper.valueToTree(encoded));
            } else {
                try {
                    parsed.add(mapper.readTree(encoded));
                } catch (JsonProcessingException e) {
                    failWithMessage("Could not parse value to JsonNode: %s", encoded);
                }
            }
        }

        return new ListAssert<>(parsed);
    }

    public ListAssert<String> extractTopElementNames() {
        ObjectMapper mapper = new ObjectMapper();
        var encoded = ctx.newJsonParser().encodeResourceToString(actual);

        try {
            JsonNode obj = mapper.readTree(encoded);
            return new ListAssert<>(iteratorToList(obj.fieldNames()));
        } catch (JsonProcessingException e) {
            failWithMessage("Could not parse value to JsonNode: %s", encoded);
            return new ListAssert<>(List.of());
        }
    }

    private List<String> iteratorToList(Iterator<String> it) {
        var list = new ArrayList<String>();
        it.forEachRemaining(list::add);
        return list;
    }

    public ResourceAssert hasDataAbsentReasonAt(String path, String absentReason) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        IParser fhirParser = ctx.newJsonParser();
        List<Base> found = fhirPathEngine.evaluate(actual, path, Base.class);
        if (found.size() != 1)
            failWithMessage("Expected one element at '%s', but found %s", path, found.size());

        var base = found.getFirst();
        var encoded = fhirParser.encodeToString(base);

        if (base.isPrimitive()) {
            if (!encoded.isEmpty()) {
                failWithMessage("Expected a primitive value but found: %s", encoded);
            }

            var extensionPath = "(%s).extension".formatted(path);
            found = fhirPathEngine.evaluate(actual, extensionPath, Base.class);
            if (found.size() != 1 || found.getFirst().isPrimitive())
                failWithMessage("Expected extension at '%s', but did not find one", path);

            ((ObjectNode) DATA_ABSENT_REASON).set("valueCode", mapper.valueToTree(absentReason));
            if (!mapper.readTree(fhirParser.encodeToString(found.getFirst())).equals(DATA_ABSENT_REASON)) {
                failWithMessage("Expected data absent reason at '%s', but did not fine one", path);
            }

        } else {
            try {
                ((ObjectNode) DATA_ABSENT_REASON_EXTENSION.get("extension").get(0)).set("valueCode", mapper.valueToTree(absentReason));
                if (!mapper.readTree(encoded).equals(DATA_ABSENT_REASON_EXTENSION)) {
                    failWithMessage("Expected data absent reason at '%s', but found: '%s'", path, encoded);
                }
                ;
            } catch (JsonProcessingException e) {
                failWithMessage("Could not parse value to JsonNode: %s", encoded);
            }

        }

        return myself;
    }
}
