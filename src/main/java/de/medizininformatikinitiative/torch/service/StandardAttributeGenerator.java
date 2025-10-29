package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.util.CompiledStructureDefinition;

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
    public AnnotatedAttributeGroup generate(AttributeGroup attributeGroup, String patientGroupId) throws ValidationException {
        List<AnnotatedAttribute> tempAttributes = new ArrayList<>();

        CompiledStructureDefinition definition = profileHandler.getDefinition(attributeGroup.groupReference())
                .orElseThrow(() -> new ValidationException("No StructureDefinition found for: " + attributeGroup.groupReference()));

        String resourceType = definition.type();
        String id = resourceType + ".id";
        tempAttributes.add(new AnnotatedAttribute(id, id, false));
        String profile = resourceType + ".meta.profile";
        tempAttributes.add(new AnnotatedAttribute(profile, profile, false));


        if (compartmentManager.isInCompartment(resourceType)) {
            for (String field : patientRefFields) {
                String fieldString = resourceType + "." + field;
                if (definition.elementDefinitionById(fieldString).isPresent()) {
                    tempAttributes.add(new AnnotatedAttribute(fieldString, fieldString, false, List.of(patientGroupId)));
                }
            }
        }

        return new AnnotatedAttributeGroup(attributeGroup.name(), attributeGroup.id(), resourceType, attributeGroup.groupReference(), tempAttributes, attributeGroup.filter(), null, attributeGroup.includeReferenceOnly());
    }
}
