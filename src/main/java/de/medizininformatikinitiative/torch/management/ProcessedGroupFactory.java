package de.medizininformatikinitiative.torch.management;

import de.medizininformatikinitiative.torch.model.management.GroupsToProcess;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ProcessedGroupFactory {

    private final CompartmentManager compartment;

    public ProcessedGroupFactory(CompartmentManager compartment) {
        this.compartment = compartment;
    }

    /**
     * @param crtdl Annotaded CRTDL to be handled
     * @return GroupsToProcess which splits crtdl into attribute groups to be handled directly (insides and outside Patient Compartment) and all groups
     */

    public GroupsToProcess create(AnnotatedCrtdl crtdl) {
        List<AnnotatedAttributeGroup> directLoadPatientCompartment = new ArrayList<>();
        List<AnnotatedAttributeGroup> directLoadNotPatientCompartment = new ArrayList<>();
        Map<String, AnnotatedAttributeGroup> allGroups = new HashMap<>();

        crtdl.dataExtraction().attributeGroups().forEach(group -> {
            if (!group.includeReferenceOnly()) {
                if (compartment.isInCompartment(group.resourceType())) {
                    directLoadPatientCompartment.add(group);
                } else {
                    directLoadNotPatientCompartment.add(group);
                }
            }
            allGroups.put(group.id(), group);
        });

        return new GroupsToProcess(directLoadPatientCompartment, directLoadNotPatientCompartment, allGroups);
    }

}
