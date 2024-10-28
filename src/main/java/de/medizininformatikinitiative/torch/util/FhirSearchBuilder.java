package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.model.AttributeGroup;
import de.medizininformatikinitiative.torch.model.Filter;

import java.util.List;

public class FhirSearchBuilder {



    public static String getSearchParam(AttributeGroup group, Filter codeFilter, List<String> batch) {

        String filter = "";
        String codeFilterString = "";
        if (group.hasFilter() && ! group.getNonCodeFilterString().isEmpty() ) {
            filter = "&" + group.getNonCodeFilterString();
        }

        if (codeFilter != null) {
            codeFilterString = "&" + codeFilter.getCodeFilter();
        }

        String parameters;
        if (group.getGroupReferenceURL().contains("patient")) {
            parameters = "identifier=" + String.join(",",batch);
        } else {
            parameters = "patient=" + String.join(",",batch);
        }
        parameters += "&_profile=" + group.getGroupReferenceURL() + filter + codeFilterString;

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

        parameters += "&_profile=https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung";
        return parameters;

    }





}
