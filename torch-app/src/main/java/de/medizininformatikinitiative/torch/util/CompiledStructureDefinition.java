package de.medizininformatikinitiative.torch.util;

import com.google.common.base.Functions;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Helpeing structure for improving the performance of working with enrolled structure definitions
 * by keeping the element definitions from the snapshots in a map.
 *
 * @param structureDefinition FHIR structure definition
 * @param elementDefinitions  map of element definitions from the snapshot of the structure definition
 */
public record CompiledStructureDefinition(StructureDefinition structureDefinition,
                                          Map<String, ElementDefinition> elementDefinitions
) {
    public CompiledStructureDefinition {
        Objects.requireNonNull(structureDefinition);
        Objects.requireNonNull(elementDefinitions);
    }

    public static CompiledStructureDefinition fromStructureDefinition(StructureDefinition structureDefinition) {
        return new CompiledStructureDefinition(structureDefinition, structureDefinition.getSnapshot().getElement().stream().collect(Collectors.toMap(ElementDefinition::getId, Functions.identity())));
    }

    public Optional<ElementDefinition> elementDefinitionById(String id) {
        return Optional.ofNullable(elementDefinitions.get(requireNonNull(id)));
    }

    Stream<ElementDefinition> elementDefinitionByPath(String path) {
        String pathX = requireNonNull(path) + "[x]";
        return structureDefinition.getSnapshot().getElement().stream().filter(ed -> path.equals(ed.getPath()) || pathX.equals(ed.getPath()));
    }

    public String type() {
        return structureDefinition.getType();
    }
}
