package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.model.AttributeGroup;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FhirSearchBuilder {

    private final DseMappingTreeBase mappingTreeBase;

    public FhirSearchBuilder(DseMappingTreeBase mappingTreeBase) {
        this.mappingTreeBase = mappingTreeBase;
    }

    public String getSearchParam(AttributeGroup group, List<String> batch) {
        String filter = "";
        if (group.hasFilter()) {
            filter = "&" + group.getFilterString(mappingTreeBase);
        }
        String parameters;
        if (group.getGroupReferenceURL().contains("patient")) {
            parameters = "identifier=" + String.join(",",batch);
        } else {
            parameters = "patient=" + String.join(",",batch);
        }
        parameters += "&_profile=" + group.getGroupReferenceURL() + filter;
        return parameters;

    }

    public String getConsent(List<String> batch) {
        String parameters;

        parameters = "patient=" + String.join(",",batch);

        parameters += "&_profile=https://www.medizininformatik-initiative.de/fhir/modul-consent/StructureDefinition/mii-pr-consent-einwilligung";
        return parameters;

    }

    public static String getEncounter(List<String> batch) {
        String parameters;

        parameters = "patient=" + String.join(",",batch);

        parameters += "&_profile=https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung";
        return parameters;

    }





}
