package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.util.CompiledStructureDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class StandardAttributeGenerator {

    private final StructureDefinitionHandler profileHandler;
    private final CompartmentManager compartmentManager;

    private static final Set<String> PATIENT_REF_FIELDS = Set.of("patient", "subject");

    public StandardAttributeGenerator(CompartmentManager compartmentManager,
                                      StructureDefinitionHandler profileHandler) {
        this.compartmentManager = compartmentManager;
        this.profileHandler = profileHandler;
    }

    /**
     * Enriches a given attributeGroup with standard attributes required for extraction.
     * These cover technically necessary fields (id, meta.profile), Patient-specific fields
     * (identifier), and patient-compartment reference fields (patient, subject) needed for
     * referential integrity.
     * <p>
     * Standard attributes that duplicate an attribute already present in the CRTDL definition
     * are merged via {@link AnnotatedAttributeGroup#addAttributes}.
     *
     * @param attributeGroup attribute group to be handled
     * @param patientGroupId patient group id used for generated patient references
     * @return attribute group with added standard attributes
     */
    public AnnotatedAttributeGroup generate(AttributeGroup attributeGroup,
                                            String patientGroupId) throws ValidationException {

        CompiledStructureDefinition definition = profileHandler.getDefinition(attributeGroup.groupReference())
                .orElseThrow(() -> new ValidationException(
                        "No StructureDefinition found for: " + attributeGroup.groupReference()
                ));

        String resourceType = definition.type();

        List<AnnotatedAttribute> standardAttributes = new ArrayList<>();
        standardAttributes.add(new AnnotatedAttribute(resourceType + ".id", resourceType + ".id", false));
        standardAttributes.add(new AnnotatedAttribute(resourceType + ".meta.profile", resourceType + ".meta.profile", false));

        if ("Patient".equals(resourceType)) {
            standardAttributes.add(new AnnotatedAttribute(resourceType + ".identifier", resourceType + ".identifier", false));
        }

        if (compartmentManager.isInCompartment(resourceType)) {
            for (String field : PATIENT_REF_FIELDS) {
                String fieldString = resourceType + "." + field;
                if (definition.elementDefinitionById(fieldString).isPresent()) {
                    standardAttributes.add(new AnnotatedAttribute(fieldString, fieldString, false, List.of(patientGroupId)));
                }
            }
        }

        AnnotatedAttributeGroup group = new AnnotatedAttributeGroup(
                attributeGroup.name(),
                attributeGroup.id(),
                resourceType,
                attributeGroup.groupReference(),
                standardAttributes,
                attributeGroup.filter(),
                attributeGroup.includeReferenceOnly()
        );

        List<AnnotatedAttribute> crtdlAttributes = attributeGroup.attributes().stream()
                .map(a -> new AnnotatedAttribute(a.attributeRef(), a.attributeRef(), a.mustHave(), a.linkedGroups()))
                .toList();

        return group.addAttributes(crtdlAttributes);
    }
}
