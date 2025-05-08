package de.medizininformatikinitiative.torch.model.management;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;

import java.util.List;
import java.util.Map;


public record GroupsToProcess(List<AnnotatedAttributeGroup> directPatientCompartmentGroups,
                              List<AnnotatedAttributeGroup> directNoPatientGroups,
                              Map<String, AnnotatedAttributeGroup> allGroups) {

    public GroupsToProcess {
        directPatientCompartmentGroups = List.copyOf(directPatientCompartmentGroups);
        directNoPatientGroups = List.copyOf(directNoPatientGroups);
        allGroups = Map.copyOf(allGroups);
    }
}
