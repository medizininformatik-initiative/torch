package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class StandardAttributeGenerator {

    /**
     * Enriches a given attributeGroup by hardcoded Standard attributes to be extracted.
     * These attributes are a mix of technically  necessary ones such as id and meta.profile and
     * other ones that come from patient compartment to ensure referential integrity
     *
     * @param attributeGroup attribute group to be handled
     * @param resourceType   resource Type to be applied to attribute group
     * @param patientGroups
     * @return attribute group with added standard attributes
     */

    static Set<String> standardPatientFields = Set.of("patient", "subject");

    public static AnnotatedAttributeGroup generate(AttributeGroup attributeGroup, String resourceType, CompartmentManager manager, List<String> patientGroups) {
        List<AnnotatedAttribute> tempAttributes = new ArrayList<>();

        String id = resourceType + ".id";
        tempAttributes.add(new AnnotatedAttribute(id, id, id, false));
        String profile = resourceType + ".meta.profile";
        tempAttributes.add(new AnnotatedAttribute(profile, profile, profile, false));


        if (manager.isInCompartment(resourceType)) {
            List<String> params = manager.getParams(resourceType);
            params.forEach(param -> {
                if (standardPatientFields.contains(param)) {
                    String attributeString = resourceType + "." + param;
                    tempAttributes.add(new AnnotatedAttribute(attributeString, attributeString, attributeString, false, patientGroups));
                }

            });
        }

        return new AnnotatedAttributeGroup(attributeGroup.name(), attributeGroup.id(), attributeGroup.groupReference(), tempAttributes, attributeGroup.filter(), null, attributeGroup.includeReferenceOnly());
    }
}
