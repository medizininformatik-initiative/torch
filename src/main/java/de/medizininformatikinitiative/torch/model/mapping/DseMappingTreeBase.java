package de.medizininformatikinitiative.torch.model.mapping;


import java.util.List;
import java.util.stream.Stream;

public record DseMappingTreeBase(List<DseTreeRoot> moduleRoots) {
    public Stream<String> expand(String system, String code) {

        return moduleRoots.stream().flatMap(moduleRoot ->
                moduleRoot.isModuleMatching(system, code) ? moduleRoot.expand(code) : Stream.empty());
    }
}
