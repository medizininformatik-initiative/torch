package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.fhirpath.IFhirPath;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import java.util.List;
import java.util.stream.Collectors;

public class ReferenceExtractor {

    private final IFhirPath fhirPathEngine;


    ReferenceExtractor(IFhirPath fhirPathEngine) {
        this.fhirPathEngine = fhirPathEngine;
    }

    /*
    Idea:
    Extract all attributegroups with linked groups (maybe preprocess that)
    Recursive walk -> kill all unknown references

     */
    List<ReferenceWrapper> extract(ResourceGroupWrapper wrapper) {
        Resource resource = wrapper.resource();
        return wrapper.groupSet().stream()
                .flatMap(group -> group.refAttributes().stream()
                        .map(refAttribute -> {
                            try {
                                return new ReferenceWrapper(resource.getId(), refAttribute, getReferences(resource, refAttribute));
                            } catch (MustHaveViolatedException e) {
                                throw new RuntimeException(e);
                            }
                        }))
                .collect(Collectors.toList());
    }


    List<String> getReferences(Resource resource, AnnotatedAttribute annotatedAttribute) throws MustHaveViolatedException {
        //fhirpath call
        //musthaveviolated if empty and must have ->mark for deletion

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
