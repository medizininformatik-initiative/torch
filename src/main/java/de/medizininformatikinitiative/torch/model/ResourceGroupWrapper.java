package de.medizininformatikinitiative.torch.model;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import org.hl7.fhir.r4.model.Resource;

import java.util.Objects;
import java.util.Set;

public record ResourceGroupWrapper(
        Resource resource,
        Set<AnnotatedAttributeGroup> groupSet
) {

    public ResourceGroupWrapper {
        Objects.requireNonNull(resource);
        groupSet = Set.copyOf(groupSet);
    }

}
