package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class StandardAttributeGenerator {

    private final StructureDefinitionHandler profileHandler;
    private final CompartmentManager compartmentManager;


    public StandardAttributeGenerator(CompartmentManager compartmentManager, StructureDefinitionHandler profileHandler) {
        this.compartmentManager = compartmentManager;
        this.profileHandler = profileHandler;
    }

    Set<String> patientRefFields = Set.of("patient", "subject");

    /**
     * Enriches a given attributeGroup by hardcoded Standard attributes to be extracted.
     * These attributes are a mix of technically necessary ones such as id and meta.profile and
     * other ones that come from patient compartment to ensure referential integrity
     *
     * @param attributeGroup attribute group to be handled
     * @return attribute group with added standard attributes
     */
    public AnnotatedAttributeGroup generate(AttributeGroup attributeGroup, String patientGroupId) {
        List<AnnotatedAttribute> tempAttributes = new ArrayList<>();
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = profileHandler.getSnapshot(attributeGroup.groupReference());

        StructureDefinition definition = profileHandler.getDefinition(attributeGroup.groupReference());
        String resourceType = definition.getType();
        String id = resourceType + ".id";
        tempAttributes.add(new AnnotatedAttribute(id, id, id, false));
        String profile = resourceType + ".meta.profile";
        tempAttributes.add(new AnnotatedAttribute(profile, profile, profile, false));


        if (compartmentManager.isInCompartment(resourceType)) {
            for (String field : patientRefFields) {
                String fieldString = resourceType + "." + field;
                ElementDefinition elementDefinition = definition.getSnapshot().getElementById(fieldString);
                if (elementDefinition != null) {
                    tempAttributes.add(new AnnotatedAttribute(fieldString, fieldString, fieldString, true, List.of(patientGroupId)));
                }
            }
        }

        return new AnnotatedAttributeGroup(attributeGroup.name(), attributeGroup.id(), attributeGroup.groupReference(), tempAttributes, attributeGroup.filter(), null, attributeGroup.includeReferenceOnly());
    }
}
