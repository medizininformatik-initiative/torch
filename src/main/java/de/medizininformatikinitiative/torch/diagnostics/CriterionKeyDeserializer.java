package de.medizininformatikinitiative.torch.diagnostics;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;

import java.io.IOException;

/**
 * Jackson {@link KeyDeserializer} that parses a pipe-delimited string back into a {@link CriterionKey}.
 *
 * <p>Inverse of {@link CriterionKeySerializer}. Empty segments are restored as {@code null}.
 */
public class CriterionKeyDeserializer extends KeyDeserializer {

    /**
     * Deserializes a pipe-delimited key string into a {@link CriterionKey}.
     *
     * @param key  the encoded string produced by {@link CriterionKeySerializer}
     * @param ctxt the Jackson deserialization context
     * @return the reconstructed {@link CriterionKey}
     * @throws IOException if the string does not have exactly four pipe-separated segments
     *                     or the kind segment does not match a known {@link ExclusionKind}
     */
    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
        String[] parts = key.split("\\|", -1);
        if (parts.length != 4) {
            throw new IOException("Invalid CriterionKey map key: " + key);
        }

        ExclusionKind kind;
        try {
            kind = ExclusionKind.valueOf(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new IOException("Unknown ExclusionKind '" + parts[0] + "' in CriterionKey map key: " + key, e);
        }
        String name = emptyToNull(parts[1]);
        String groupRef = emptyToNull(parts[2]);
        String attributeRef = emptyToNull(parts[3]);

        return new CriterionKey(kind, name, groupRef, attributeRef);
    }

    private String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
