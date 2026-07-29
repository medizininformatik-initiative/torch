package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.extraction.ExtractedReferences;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.extraction.IdentifierReference;
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
    private final ThreadLocal<IFhirPath> fhirPathEngine;

    public ReferenceExtractor(FhirContext ctx) {
        this.fhirPathEngine = FhirPathEngines.threadLocal(ctx);
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
                            ExtractedReferences extracted = getReferences(resource, refAttribute);
                            ExtractionId relativeUrl = ResourceUtils.getRelativeURL(resource);

                            // ReferenceWrapper should handle its own null-safety, but we pass safe values
                            return new ReferenceWrapper(refAttribute, extracted.references(), extracted.identifierReferences(), groupId, relativeUrl);
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

    ExtractedReferences getReferences(Resource resource, AnnotatedAttribute annotatedAttribute) throws MustHaveViolatedException {
        if (resource == null || annotatedAttribute == null) return new ExtractedReferences(List.of(), List.of());

        // Evaluate FHIRPath - library usually returns empty list, but we stream it safely
        List<Base> elements = fhirPathEngine.get().evaluate(resource, annotatedAttribute.fhirPath(), Base.class);

        List<Reference> collectedReferences = Optional.ofNullable(elements).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .flatMap(element -> collectReferences(element).stream())
                .toList();

        List<ExtractionId> references = collectedReferences.stream()
                .filter(Reference::hasReference)
                .flatMap(ref -> {
                    String refStr = ref.getReference();
                    try {
                        return Stream.of(ExtractionId.fromRelativeUrl(refStr));
                    } catch (IllegalArgumentException ex) {
                        logger.debug("Ignoring invalid reference '{}' in {} (attr={}, fhirPath={} due to {})",
                                refStr, resource.getIdElement().getValue(), annotatedAttribute.attributeRef(),
                                annotatedAttribute.fhirPath(), ex.getMessage());
                        return Stream.empty();
                    }
                })
                .toList();

        List<IdentifierReference> identifierReferences = collectedReferences.stream()
                .filter(ref -> !ref.hasReference())
                .map(ref -> new IdentifierReference(ref.getIdentifier().getSystem(), ref.getIdentifier().getValue()))
                .toList();

        if (annotatedAttribute.mustHave() && references.isEmpty() && identifierReferences.isEmpty()) {
            throw new MustHaveViolatedException(
                    "No Reference found in required field " + annotatedAttribute.attributeRef() +
                            " in resource " + resource.getIdElement().getValue()
            );
        }

        return new ExtractedReferences(references, identifierReferences);
    }

    /**
     * Recursively collect all references (literal or identifier-only) from any Base element.
     * Hardened against null elements and null reference strings.
     */
    public List<Reference> collectReferences(Base element) {
        if (element == null) {
            return List.of();
        }

        // Logic check: Pattern matching ensures 'ref' is non-null
        if (element instanceof Reference ref) {
            // A literal reference with a null string (bypassing hasReference()) and an identifier without a
            // value are both treated as no usable reference at all.
            if (ref.hasReference() && ref.getReference() != null) {
                return List.of(ref);
            }
            if (ref.hasIdentifier() && ref.getIdentifier().hasValue()) {
                return List.of(ref);
            }
            return List.of();
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
