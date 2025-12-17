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
import java.util.stream.Collectors;

@Component
public class ReferenceExtractor {
    private static final Logger logger = LoggerFactory.getLogger(ReferenceExtractor.class);


    private final IFhirPath fhirPathEngine;


    public ReferenceExtractor(FhirContext ctx) {
        this.fhirPathEngine = ctx.newFhirPath();
    }

    /**
     * Extracts for a given group all references from a resource.
     *
     * @param resource containing the resource from which the references should be extracted
     * @param groupMap Map of group IDs to their corresponding AnnotatedAttributeGroup
     * @param groupId  group to be extracted from
     * @return List of ReferenceWrapper containing the references and associated attributes of a resource
     * @throws MustHaveViolatedException if a must-have field has no reference at all
     */
    public List<ReferenceWrapper> extract(Resource resource, Map<String, AnnotatedAttributeGroup> groupMap, String groupId) throws MustHaveViolatedException {
        try {
            AnnotatedAttributeGroup group = groupMap.get(groupId);
            logger.debug("Group Reference: '{}'", group.groupReference());
            return group.refAttributes().stream()
                    .map(refAttribute -> {
                        try {
                            return new ReferenceWrapper(refAttribute, getReferences(resource, refAttribute), groupId, ResourceUtils.getRelativeURL(resource));
                        } catch (MustHaveViolatedException e) {
                            throw new RuntimeException(e); // Wrapping the exception first
                        }
                    })
                    .collect(Collectors.toList());
        } catch (RuntimeException e) {
            e.printStackTrace();
            Throwable cause = e.getCause();
            if (cause instanceof MustHaveViolatedException) {
                throw (MustHaveViolatedException) cause; // Unwrapping and rethrowing
            }
            throw e; // Rethrow the original RuntimeException if it's not MustHaveViolatedException
        }
    }


    List<String> getReferences(Resource resource,
                               AnnotatedAttribute annotatedAttribute) throws MustHaveViolatedException {

        List<Base> elements = fhirPathEngine.evaluate(resource, annotatedAttribute.fhirPath(), Base.class);

        // Collect all references recursively
        List<String> references = elements.stream()
                .flatMap(element -> collectReferences(element).stream())
                .toList();

        // Enforce must-have after collection
        if (annotatedAttribute.mustHave() && references.isEmpty()) {
            throw new MustHaveViolatedException(
                    "No Reference found in required field " + annotatedAttribute.attributeRef() +
                            " in resource " + resource.getId()
            );
        }

        return references;
    }

    /**
     * Recursively collect all references from any Base element
     */
    public List<String> collectReferences(Base element) {
        if (element instanceof Reference ref && ref.hasReference()) {
            return List.of(ref.getReference());
        } else if (!element.isPrimitive()) {
            return element.children().stream()
                    .flatMap(child -> child.getValues().stream())
                    .flatMap(childElement -> collectReferences(childElement).stream())
                    .toList();
        }
        return List.of();
    }
}
