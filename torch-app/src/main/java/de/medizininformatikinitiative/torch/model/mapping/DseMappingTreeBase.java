package de.medizininformatikinitiative.torch.model.mapping;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.stream.Stream;

public record DseMappingTreeBase(@JsonProperty List<DseTreeRoot> moduleRoots) {
    public Stream<String> expand(String system, String code) {

        return moduleRoots.stream().flatMap(moduleRoot ->
                moduleRoot.isModuleMatching(system, code) ? moduleRoot.expand(code) : Stream.empty());
    }
}
