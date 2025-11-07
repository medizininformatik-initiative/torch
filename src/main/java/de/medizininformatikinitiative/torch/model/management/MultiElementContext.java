package de.medizininformatikinitiative.torch.model.management;

import de.medizininformatikinitiative.torch.util.CompiledStructureDefinition;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.Extension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record MultiElementContext(List<ElementContext> contexts) {

    public MultiElementContext {
        contexts = List.copyOf(contexts);
    }

    public MultiElementContext(String elementId, List<CompiledStructureDefinition> definitions) {
        this(definitions.stream().map(compiledStructureDefinition -> new ElementContext(elementId, compiledStructureDefinition)).toList());
    }

    /**
     * Attempts to resolve slice contexts for the given data element.
     * <p>
     * If matching slices are found, they are passed to the provided {@code sliceConsumer}.
     * If the consumer returns {@code false}, a new {@code MultiElementContext} is returned
     * that includes the matching slices and the existing non-sliced contexts.
     * If the consumer returns {@code true}, no context is returned.
     *
     * @param dataElement   the element to match against slice definitions
     * @param sliceConsumer a predicate to inspect or process matching slices; if it returns {@code true}, result is discarded
     * @return an {@code Optional} containing the updated {@code MultiElementContext}, or empty if suppressed by the consumer
     */
    public Optional<MultiElementContext> resolveSlices(Base dataElement, Predicate<List<ElementContext>> sliceConsumer) {
        List<ElementContext> slices = contexts.stream().filter(ElementContext::hasSlicing)
                .flatMap(ctx -> ctx.matchingSlice(dataElement).stream())
                .toList();
        return sliceConsumer.test(slices) ? Optional.empty() : Optional.of(mergeWithSlices(slices));
    }

    public boolean shouldRedactExtension(Extension extension) {
        return !isDataAbsentReason(extension) && !hasSlice(extension);
    }

    /**
     * Checks if the given element is a sliced element.
     *
     * @param dataElement HAPI Base (Element) which should be checked
     * @return true if any slicing found, false otherwise
     */
    public boolean hasSlice(Base dataElement) {
        return contexts.stream()
                .flatMap(context -> context.matchingSlice(dataElement).stream())
                .findAny()
                .isPresent();
    }

    public Set<String> allowedReferences(Map<String, Set<String>> references) {
        return contexts.stream()
                .map(ElementContext::elementId)
                .flatMap(id -> references.entrySet().stream()
                        .filter(entry -> id.startsWith(entry.getKey()))
                        .flatMap(entry -> entry.getValue().stream())
                )
                .collect(Collectors.toSet());
    }

    private boolean isDataAbsentReason(Extension extension) {
        return "http://hl7.org/fhir/StructureDefinition/data-absent-reason".equals(extension.getUrl());
    }

    public MultiElementContext mergeWithSlices(List<ElementContext> slices) {
        List<ElementContext> all = Stream.concat(
                slices.stream(),
                contexts.stream().filter(ctx -> !ctx.hasSlicing())
        ).toList();
        return new MultiElementContext(all);
    }

    /**
     * Returns a new {@code MultiElementContext} by descending into the specified child element
     * for each contained {@code ElementContext}.
     * <p>
     * This effectively advances all contexts one level deeper along the given {@code childName},
     * preserving any associated references.
     *
     * @param childName the name of the child element to descend into
     * @return a new {@code MultiElementContext} with updated {@code ElementContext}s
     */
    public MultiElementContext descend(String childName) {
        return new MultiElementContext(contexts.stream().map(ctx -> ctx.descend(childName)).toList());
    }

    /**
     * Checks if any element definition has a slicing defined.
     *
     * @return true if slicing present and false otherwise.
     */
    public boolean hasSlicing() {
        return elementDefinitions().anyMatch(ElementDefinition::hasSlicing);
    }

    public Stream<ElementDefinition> elementDefinitions() {
        return contexts.stream().flatMap(elementContext -> elementContext.elementDefinition().stream());
    }

    /**
     * Checks if any element definitions has minimum cardinality >0.
     *
     * @return true if one element definition is required for at least one associated profile other false.
     */
    public boolean required() {
        return elementDefinitions().anyMatch(elementDefinition -> elementDefinition.getMin() > 0);
    }

    public List<String> workingCodes() {
        return elementDefinitions()
                .flatMap(elementDefinition -> elementDefinition.getType().stream()
                        .map(ElementDefinition.TypeRefComponent::getWorkingCode))
                .toList();
    }
}
