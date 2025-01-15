package de.medizininformatikinitiative.torch.management;

import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AttributeGroupProcessor {

    private final CompartmentManager compartment;

    public AttributeGroupProcessor(CompartmentManager compartment) {
        this.compartment = compartment;
    }

    public ProcessedGroups process(Crtdl crtdl) {
        List<AttributeGroup> firstPass = new ArrayList<>();
        List<AttributeGroup> secondPass = new ArrayList<>();
        Map<String, AttributeGroup> groups = new HashMap<>();

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

    public record ProcessedGroups(List<AttributeGroup> firstPass, List<AttributeGroup> secondPass,
                                  Map<String, AttributeGroup> groups) {
        public ProcessedGroups(List<AttributeGroup> firstPass, List<AttributeGroup> secondPass, Map<String, AttributeGroup> groups) {
            this.firstPass = List.copyOf(firstPass); // Immutable lists
            this.secondPass = List.copyOf(secondPass);
            this.groups = Map.copyOf(groups);       // Immutable map
        }
    }
}
