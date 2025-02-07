package de.medizininformatikinitiative.torch.management;

import de.medizininformatikinitiative.torch.model.ProcessedGroups;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AttributeGroupProcessor {

    private final CompartmentManager compartment;

    public AttributeGroupProcessor(CompartmentManager compartment) {
        this.compartment = compartment;
    }

    public ProcessedGroups process(AnnotatedCrtdl crtdl) {
        List<AnnotatedAttributeGroup> firstPass = new ArrayList<>();
        List<AnnotatedAttributeGroup> secondPass = new ArrayList<>();
        Map<String, AnnotatedAttributeGroup> groups = new HashMap<>();

        crtdl.dataExtraction().attributeGroups().forEach(group -> {
            if (!group.includeReferenceOnly()) {
                if (compartment.isInCompartment(group.resourceType())) {
                    firstPass.add(group);
                } else {
                    secondPass.add(group);
                }
            }
            groups.put(group.groupReference(), group);
        });

        return new ProcessedGroups(firstPass, secondPass, groups);
    }

}
