package de.medizininformatikinitiative.torch.diagnostics;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * Jackson {@link JsonSerializer} that writes a {@link CriterionKey} as a pipe-delimited string
 * so it can be used as a JSON object key.
 *
 * <p>Format: {@code KIND|name|groupRef|attributeRef}, where {@code null} fields are serialized as
 * empty segments and restored by {@link CriterionKeyDeserializer}.
 */
public class CriterionKeySerializer extends JsonSerializer<CriterionKey> {

    /**
     * Encodes a {@link CriterionKey} to its pipe-delimited string representation.
     *
     * @param key the criterion key to encode
     * @return the encoded string
     */
    static String encode(CriterionKey key) {
        return String.join("|",
                key.kind().name(),
                nullToEmpty(key.name()),
                nullToEmpty(key.groupRef()),
                nullToEmpty(key.attributeRef())
        );
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    @Override
    public void serialize(CriterionKey value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeFieldName(encode(value));
    }
}
