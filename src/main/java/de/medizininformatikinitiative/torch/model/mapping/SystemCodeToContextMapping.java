package de.medizininformatikinitiative.torch.model.mapping;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.numcodex.sq2cql.model.common.TermCode;

import java.util.HashMap;
import java.util.Map;

/**
 * A mapping from a combination of a system and a code to a context.
 * <p>
 * In order to save memory, a system-code combination is not directly mapped to a context, but only to a byte key that
 * can be used to look up the corresponding context.
 *
 * @param contextLookup     assigns a key to each context - used to retrieve the context to a given key
 * @param sysCodeToContext  maps the combination of system and code to a key that refers to a context
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SystemCodeToContextMapping(Map<Byte, TermCode> contextLookup, Map<String, Byte> sysCodeToContext) {

    public TermCode getContext(String system, String code) {
        var contextKey = sysCodeToContext.get(sysCodeToKey(system, code));
        return contextLookup.get(contextKey);
    }

    @JsonCreator
    public static SystemCodeToContextMapping fromJson(@JsonProperty("context-lookup") ContextLookupEntry[] contextLookupEntries,
                                                      @JsonProperty("system-code-to-context") SystemCodeEntry[] sysCodeEntries) {
        Map<Byte, TermCode> contextLookup = new HashMap<>();
        for (var entry : contextLookupEntries) {
            contextLookup.put(Byte.parseByte(entry.key()), entry.context());
        }

        Map<String, Byte> sysCodeToContextKey = new HashMap<>();
        for (var entry : sysCodeEntries) {
            sysCodeToContextKey.put(sysCodeToKey(entry.system(), entry.code()), entry.contextKey());
        }

        return new SystemCodeToContextMapping(contextLookup, sysCodeToContextKey);
    }

    private static String sysCodeToKey(String system, String code) {
        return system + code;
    }
}
