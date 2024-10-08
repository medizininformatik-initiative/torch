package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.model.AttributeGroup;

import java.util.List;

public class FhirSearchBuilder {



    public static String getSearchParam(AttributeGroup group, List<String> batch) {
        String filter = "";
        if (group.hasFilter()) {
            filter = "&" + group.getFilterString();
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

    public static String getConsent(List<String> batch) {
        String parameters;

        parameters = "patient=" + String.join(",",batch);

        parameters += "&_profile=https://www.medizininformatik-initiative.de/fhir/modul-consent/StructureDefinition/mii-pr-consent-einwilligung";
        return parameters;

    }

    public static String getEncounter(List<String> batch) {
        String parameters;

        parameters = "patient=" + String.join(",",batch);

        parameters += "&_profile=https://www.medizininformatik-initiative.de/fhir/modul-consent/StructureDefinition/mii-pr-consent-einwilligung";
        return parameters;

    }





}
