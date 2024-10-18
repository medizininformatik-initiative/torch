package de.medizininformatikinitiative.torch.model.mapping;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.function.Function.identity;

public record DseTreeRoot(String system, Map<String, DseTreeEntry> entries) {

    @JsonCreator
    static DseTreeRoot fromJson(@JsonProperty("system") String system,
                                @JsonProperty("entries") List<DseTreeEntry> entries) {
        return new DseTreeRoot(
                system,
                entries.stream().collect(Collectors.toMap(DseTreeEntry::key, identity())));
    }

    public Stream<String> expand(String key) {
        return Stream.concat(Stream.of(key), entries.get(key).children().stream().flatMap(this::expand));
    }

    boolean isModuleMatching(String system, String code) {
        return  this.system.equals(system) &&
                this.entries.containsKey(code);
    }
}
