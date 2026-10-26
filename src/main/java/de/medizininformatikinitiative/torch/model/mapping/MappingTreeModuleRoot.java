package de.medizininformatikinitiative.torch.model.mapping;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.model.sq.ContextualTermCode;
import de.medizininformatikinitiative.torch.model.sq.TermCode;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.function.Function.identity;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MappingTreeModuleRoot(TermCode context, String system, Map<String, MappingTreeModuleEntry> entries) {
    @JsonCreator
    static MappingTreeModuleRoot fromJson(@JsonProperty("context") TermCode context,
                                          @JsonProperty("system") String system,
                                          @JsonProperty("entries") List<MappingTreeModuleEntry> entries) {
        return new MappingTreeModuleRoot(
                context,
                system,
                entries.stream().collect(Collectors.toMap(MappingTreeModuleEntry::key, identity())));
    }

    public Stream<ContextualTermCode> expand(String key) {
        var newTermCode = new ContextualTermCode(context, new TermCode(system, key, ""));

        return Stream.concat(Stream.of(newTermCode), entries.get(key).children().stream().flatMap(this::expand));
    }

    boolean isModuleMatching(ContextualTermCode contextualTermCode) {
        return context.equals(contextualTermCode.context()) &&
                system.equals(contextualTermCode.termCode().system()) &&
                entries.containsKey(contextualTermCode.termCode().code());
    }
}
