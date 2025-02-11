package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;

import java.util.ArrayList;
import java.util.List;

public class StandardAttributeGenerator {

    /**
     * Enriches a given attributeGroup by hardcoded Standard attributes to be extracted.
     * These attributes are a mix of technically  necessary ones such as id and meta.profile and
     * other ones that come from patient compartment to ensure referential integrity
     *
     * @param attributeGroup attribute group to be handled
     * @param resourceType   resource Type to be applied to attribute group
     * @return attribute group with added standard attributes
     */


    public static AttributeGroup generate(AttributeGroup attributeGroup, String resourceType, CompartmentManager manager) {
        List<Attribute> tempAttributes = new ArrayList<>();

        tempAttributes.add(new Attribute(resourceType + ".id", true));
        tempAttributes.add(new Attribute(resourceType + ".meta.profile", true));

        if (manager.isInCompartment(resourceType)) {
            if (!"Patient".equals(resourceType) && !"Consent".equals(resourceType)) {
                tempAttributes.add(new Attribute(resourceType + ".subject", true));
            }
            if ("Consent".equals(resourceType)) {
                tempAttributes.add(new Attribute(resourceType + ".patient.reference", true));
            }
        }

        return attributeGroup.addAttributes(tempAttributes);
    }
}
