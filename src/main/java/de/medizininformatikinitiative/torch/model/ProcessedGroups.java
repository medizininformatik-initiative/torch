package de.medizininformatikinitiative.torch.model;

import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;

import java.util.List;
import java.util.Map;


public record ProcessedGroups(List<AttributeGroup> patientCompartmentGroups, List<AttributeGroup> noPatientGroups,
                              Map<String, AttributeGroup> allGroups) {
    public ProcessedGroups(List<AttributeGroup> patientCompartmentGroups, List<AttributeGroup> noPatientGroups, Map<String, AttributeGroup> allGroups) {
        this.patientCompartmentGroups = List.copyOf(patientCompartmentGroups);
        this.noPatientGroups = List.copyOf(noPatientGroups);
        this.allGroups = Map.copyOf(allGroups);
    }
}
