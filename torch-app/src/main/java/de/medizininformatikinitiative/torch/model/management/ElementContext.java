package de.medizininformatikinitiative.torch.model.management;

import de.medizininformatikinitiative.torch.util.CompiledStructureDefinition;
import de.medizininformatikinitiative.torch.util.Slicing;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.ElementDefinition;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ElementContext(String elementId, CompiledStructureDefinition definition) {

    public ElementContext {
        requireNonNull(elementId);
        requireNonNull(definition);
    }

    Optional<ElementDefinition> elementDefinition() {
        return definition.elementDefinitionById(elementId);
    }

    /**
     * Creates a new ElementContext by descending to the child with {@code childName}.
     * <p>
     *
     * @param childName name of the field to which next should be descended.
     * @return ElementContext with elementIds updated by one step.
     */
    ElementContext descend(String childName) {
        return new ElementContext(elementId + "." + childName, definition);
    }

    /**
     * Checks if the element definition exists and has a slicing defined.
     *
     * @return true if element definition present and has slicing, false otherwise.
     */
    public boolean hasSlicing() {
        return elementDefinition().map(ElementDefinition::hasSlicing).orElse(false);
    }

    public Optional<ElementContext> matchingSlice(Base dataElement) {
        Optional<ElementDefinition> slice = Slicing.resolveSlicing(dataElement, elementId, definition);
        return slice.map(elementDefinition -> new ElementContext(elementDefinition.getId(), definition));
    }
}
