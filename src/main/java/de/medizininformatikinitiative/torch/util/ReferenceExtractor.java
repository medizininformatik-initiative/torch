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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReferenceExtractor {

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
            return groupMap.get(groupId).refAttributes().stream()
                    .map(refAttribute -> {
                        try {
                            return new ReferenceWrapper(refAttribute, getReferences(resource, refAttribute), groupId, ResourceUtils.getRelativeURL(resource));
                        } catch (MustHaveViolatedException e) {
                            throw new RuntimeException(e); // Wrapping the exception first
                        }
                    })
                    .collect(Collectors.toList());
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof MustHaveViolatedException) {
                throw (MustHaveViolatedException) cause; // Unwrapping and rethrowing
            }
            throw e; // Rethrow the original RuntimeException if it's not MustHaveViolatedException
        }
    }


    List<String> getReferences(Resource resource, AnnotatedAttribute annotatedAttribute) throws MustHaveViolatedException {

        List<String> elements;
        elements = fhirPathEngine.evaluate(resource, annotatedAttribute.fhirPath(), Base.class).stream()
                .filter(Reference.class::isInstance) // Ensure it's a Reference
                .map(Reference.class::cast)
                .map(Reference::getReference)
                .collect(Collectors.toList());

        if (elements.isEmpty() && annotatedAttribute.mustHave()) {
            throw new MustHaveViolatedException("No Reference in " + annotatedAttribute.attributeRef() + " in " + resource.getId());
        }
        return elements;
    }

}
