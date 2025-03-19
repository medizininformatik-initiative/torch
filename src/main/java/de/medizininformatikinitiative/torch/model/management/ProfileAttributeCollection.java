package de.medizininformatikinitiative.torch.model.management;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;

import java.util.Set;

public record ProfileAttributeCollection(Set<String> profiles, Set<AnnotatedAttribute> attributes) {

    public ProfileAttributeCollection {
        profiles = Set.copyOf(profiles);
        attributes = Set.copyOf(attributes);
    }
    
}
