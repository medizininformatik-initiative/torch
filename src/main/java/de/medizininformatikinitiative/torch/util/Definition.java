package de.medizininformatikinitiative.torch.util;

import com.google.common.base.Functions;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

record Definition(StructureDefinition structureDefinition, Map<String, ElementDefinition> elementDefinitions) {

    static Definition fromStructureDefinition(StructureDefinition structureDefinition) {
        return new Definition(structureDefinition, structureDefinition.getSnapshot().getElement().stream().collect(Collectors.toMap(ElementDefinition::getId, Functions.identity())));
    }

    ElementDefinition elementDefinitionById(String id) {
        return elementDefinitions.get(requireNonNull(id));
    }

    Stream<ElementDefinition> elementDefinitionByPath(String path) {
        String pathX = requireNonNull(path) + "[x]";
        return structureDefinition.getSnapshot().getElement().stream().filter(ed -> path.equals(ed.getPath()) || pathX.equals(ed.getPath()));
    }
}
