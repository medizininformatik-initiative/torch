package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.PatientLinkResolver;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.util.CompiledStructureDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class StandardAttributeGenerator {

    private final StructureDefinitionHandler profileHandler;
    private final CompartmentManager compartmentManager;
    private final PatientLinkResolver patientLinkResolver;


    public StandardAttributeGenerator(CompartmentManager compartmentManager, StructureDefinitionHandler profileHandler,
                                      PatientLinkResolver patientLinkResolver) {
        this.compartmentManager = compartmentManager;
        this.profileHandler = profileHandler;
        this.patientLinkResolver = patientLinkResolver;
    }

    /**
     * Enriches a given attributeGroup by hardcoded Standard attributes to be extracted.
     * These attributes are a mix of technically necessary ones such as id and meta.profile and
     * other ones that come from patient compartment to ensure referential integrity.
     * A patient compartment reference is linked to the patient group and to any groups the attribute group declares
     * for the same element, so references to other resource types in that element can still be resolved. It is
     * mustHave if the attribute group declares the same element as mustHave.
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
            for (String fieldString : patientLinkResolver.elementPaths(resourceType)) {
                if (definition.elementDefinitionById(fieldString).isPresent()) {
                    tempAttributes.add(new AnnotatedAttribute(fieldString, fieldString,
                            mustHave(attributeGroup, fieldString), linkedGroups(attributeGroup, fieldString, patientGroupId)));
                }
            }
        }

        return new AnnotatedAttributeGroup(attributeGroup.name(), attributeGroup.id(), resourceType, attributeGroup.groupReference(), tempAttributes, attributeGroup.filter(), attributeGroup.includeReferenceOnly());
    }

    private static List<String> linkedGroups(AttributeGroup attributeGroup, String attributeRef, String patientGroupId) {
        Set<String> linkedGroups = new LinkedHashSet<>();
        linkedGroups.add(patientGroupId);
        attributeGroup.attributes().stream()
                .filter(attribute -> attribute.attributeRef().equals(attributeRef))
                .forEach(attribute -> linkedGroups.addAll(attribute.linkedGroups()));
        return List.copyOf(linkedGroups);
    }

    private static boolean mustHave(AttributeGroup attributeGroup, String attributeRef) {
        return attributeGroup.attributes().stream()
                .anyMatch(attribute -> attribute.attributeRef().equals(attributeRef) && attribute.mustHave());
    }

    public Set<String> standardAttributeRefs(String resourceType, CompiledStructureDefinition definition) {
        Set<String> refs = new HashSet<>();
        refs.add(resourceType + ".id");
        refs.add(resourceType + ".meta.profile");
        if (compartmentManager.isInCompartment(resourceType)) {
            for (String fieldString : patientLinkResolver.elementPaths(resourceType)) {
                if (definition.elementDefinitionById(fieldString).isPresent()) {
                    refs.add(fieldString);
                }
            }
        }
        return Set.copyOf(refs);
    }
}
