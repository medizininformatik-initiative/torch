package de.medizininformatikinitiative.torch.model;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;

import java.util.List;
import java.util.Map;


public record ProcessedGroups(List<AnnotatedAttributeGroup> directPatientCompartmentGroups,
                              List<AnnotatedAttributeGroup> directNoPatientGroups,
                              Map<String, AnnotatedAttributeGroup> allGroups) {
    public ProcessedGroups(List<AnnotatedAttributeGroup> directPatientCompartmentGroups, List<AnnotatedAttributeGroup> directNoPatientGroups, Map<String, AnnotatedAttributeGroup> allGroups) {
        this.directPatientCompartmentGroups = List.copyOf(directPatientCompartmentGroups);
        this.directNoPatientGroups = List.copyOf(directNoPatientGroups);
        this.allGroups = Map.copyOf(allGroups);
    }
}
