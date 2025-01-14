package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class CrtdlValidatorService {

    private final StructureDefinitionHandler profileHandler;


    public CrtdlValidatorService(StructureDefinitionHandler profileHandler) throws IOException {
        this.profileHandler = profileHandler;
    }

    /**
     * Validates crtdl and modifies the attribute groups by adding standard attributes and modifiers
     *
     * @param crtdl to be validated
     * @return modified crtdl or illegalArgumentException if a profile is unknown.
     * <p>
     * MedicationStatement.medication -> Attributgruppe Medication
     * und dann nur diese hinzufügen.
     */
    public void validate(Crtdl crtdl) throws IllegalArgumentException {
        Set<String> profiles = new HashSet<>();
        profiles.addAll(profileHandler.knownProfiles());


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
