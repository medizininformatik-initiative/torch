package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CrtdlValidatorService {

    private StructureDefinitionHandler profileHandler;
    private final AttributeGroupPopulator attributeGroupPopulator;


    public CrtdlValidatorService(StructureDefinitionHandler profileHandler) {
        this.profileHandler = profileHandler;
        this.attributeGroupPopulator = new AttributeGroupPopulator(profileHandler);
    }

    /**
     * Validates crtdl and modifies the attribute groups by adding standard attributes and modifiers
     *
     * @param crtdl to be validated
     * @return modified crtdl or illegalArgumentException if a profile is unknown.
     * <p>
     * MedicationStatement.medication -> Attributgruppe Medication
     * TODO: attribut in snapshot nachschlagen und wenn referenz -> potenzielle profile identifizieren
     * und dann nur diese hinzufügen.
     */
    public void validate(Crtdl crtdl) {
        Set<String> profiles = new HashSet<>();
        profiles.addAll(profileHandler.knownProfiles());
        List<AttributeGroup> groupList = new ArrayList<>();


        crtdl.dataExtraction().attributeGroups().forEach(attributeGroup -> {
            StructureDefinition definition = profileHandler.getDefinition(attributeGroup.groupReference());
            if (definition != null) {
                attributeGroup.attributes().forEach(attribute -> {
                    ElementDefinition elementDefinition = definition.getSnapshot().getElementById(attribute.attributeRef());
                    if (elementDefinition == null) {
                        throw new IllegalArgumentException("Unknown Attributes in " + attributeGroup.groupReference());
                    }
                });

            } else {
                throw new IllegalArgumentException("Unknown Profile: " + attributeGroup.groupReference());
            }
        });
    }

}
