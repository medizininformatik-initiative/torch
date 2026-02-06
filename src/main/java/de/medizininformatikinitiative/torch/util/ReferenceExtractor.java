package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class ReferenceExtractor {
    private static final Logger logger = LoggerFactory.getLogger(ReferenceExtractor.class);
    private final IFhirPath fhirPathEngine;

    public ReferenceExtractor(FhirContext ctx) {
        this.fhirPathEngine = ctx.newFhirPath();
    }

    public List<ReferenceWrapper> extract(Resource resource, Map<String, AnnotatedAttributeGroup> groupMap, String groupId) throws MustHaveViolatedException {
        // Guard 1: Basic input validation
        if (resource == null || groupMap == null || groupId == null) {
            return List.of();
        }

        try {
            // Guard 2: Missing group in map
            AnnotatedAttributeGroup group = groupMap.get(groupId);
            if (group == null) {
                logger.warn("No AnnotatedAttributeGroup found for groupId: {}", groupId);
                return List.of();
            }

            return group.refAttributes().stream()
                    .filter(Objects::nonNull) // Ensure attribute list doesn't contain nulls
                    .map(refAttribute -> {
                        try {
                            List<String> refs = getReferences(resource, refAttribute);
                            String relativeUrl = ResourceUtils.getRelativeURL(resource);

                            // ReferenceWrapper should handle its own null-safety, but we pass safe values
                            return new ReferenceWrapper(refAttribute, refs, groupId, relativeUrl);
                        } catch (MustHaveViolatedException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof MustHaveViolatedException cause) {
                throw cause;
            }
            logger.error("Unexpected error during reference extraction", e);
            throw e;
        }
    }

    List<String> getReferences(Resource resource, AnnotatedAttribute annotatedAttribute) throws MustHaveViolatedException {
        if (resource == null || annotatedAttribute == null) return List.of();

        // Evaluate FHIRPath - library usually returns empty list, but we stream it safely
        List<Base> elements = fhirPathEngine.evaluate(resource, annotatedAttribute.fhirPath(), Base.class);

        List<String> references = Optional.ofNullable(elements).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .flatMap(element -> collectReferences(element).stream())
                .filter(Objects::nonNull) // Ensure no null strings leaked through
                .toList();

        if (annotatedAttribute.mustHave() && references.isEmpty()) {
            throw new MustHaveViolatedException(
                    "No Reference found in required field " + annotatedAttribute.attributeRef() +
                            " in resource " + resource.getIdElement().getValue()
            );
        }

        return references;
    }

    /**
     * Recursively collect all references from any Base element.
     * Hardened against null elements and null reference strings.
     */
    public List<String> collectReferences(Base element) {
        if (element == null) {
            return List.of();
        }

        // Logic check: Pattern matching ensures 'ref' is non-null
        if (element instanceof Reference ref && ref.hasReference()) {
            String refString = ref.getReference();
            // List.of(refString) would throw NPE if refString is null.
            // Stream.ofNullable().toList() is the safest way to return 0 or 1 items.
            return Stream.ofNullable(refString).toList();
        }

        // Logic check: FHIR primitives don't have children
        if (!element.isPrimitive()) {
            return element.children().stream()
                    .filter(Objects::nonNull) // The Property object itself
                    .flatMap(child -> child.getValues().stream())
                    .filter(Objects::nonNull) // The Base element in the values list
                    .flatMap(childElement -> collectReferences(childElement).stream())
                    .toList();
        }

        return List.of();
    }
}
