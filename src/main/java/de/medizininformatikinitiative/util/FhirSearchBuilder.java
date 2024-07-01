package de.medizininformatikinitiative.util;

import de.medizininformatikinitiative.CdsStructureDefinitionHandler;
import de.medizininformatikinitiative.model.*;

import java.io.UnsupportedEncodingException;
import java.lang.ref.Reference;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static de.medizininformatikinitiative.util.ListUtils.splitListIntoBatches;

public class FhirSearchBuilder {

    public static int batchSize = 100;

    // Change global batch size
    public static void setBatchsize(int batchSize) {
        FhirSearchBuilder.batchSize = batchSize;
    }

    private CdsStructureDefinitionHandler cdsStructureDefinitionHandler;

    public FhirSearchBuilder(CdsStructureDefinitionHandler cds) {
        this.cdsStructureDefinitionHandler = cds;
    }

    public List<MultiValueMap<String, String>> buildSearchBatches(Crtdl crtdl, ArrayList<String> patients) {
        return buildSearchBatches(crtdl, patients, batchSize);
    }

    public List<MultiValueMap<String, String>> buildSearchBatches(Crtdl crtdl, ArrayList<String> patients, int batchSize) {
        return getSearchBatches(crtdl, patients, batchSize);
    }

    public List<String> getSearchBatchesAsUrls(Crtdl crtdl, ArrayList<String> patients, int size) {
        List<MultiValueMap<String, String>> searchBatches = getSearchBatches(crtdl, patients, size);

        return searchBatches.stream()
                .map(this::exportParametersAsString)
                .collect(Collectors.toList());
    }

    public List<MultiValueMap<String, String>> getSearchBatches(Crtdl crtdl, ArrayList<String> patients, int size) {
        DataExtraction extraction = crtdl.getCohortDefinition().getDataExtraction();
        List<String> batches = splitListIntoBatches(patients, size);
        List<AttributeGroup> attributeGroups = extraction.getAttributeGroups();
        List<MultiValueMap<String, String>> searchBatches = new LinkedList<>();
        for (String batch : batches) {
            for (AttributeGroup group : attributeGroups) {
                String resourceType = cdsStructureDefinitionHandler.getDefinition(group.getGroupReference()).getType();
                MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
                parameters.add("patient", batch);
                //parameters.add("resourceType", resourceType);
                //System.out.println("Reference"+group.getGroupReference());
                //parameters.add("_profile", group.getGroupReference());
                if (group.hasFilter()) {
                    Map<String, String> filters = group.getFiltersAsMap();
                    for (Map.Entry<String, String> entry : filters.entrySet()) {
                        System.out.println(entry.getKey()+" Param " +entry.getValue());
                        parameters.add(entry.getKey(), entry.getValue());
                    }
                }
                searchBatches.add(parameters);
            }
        }
        return searchBatches;
    }

    public String exportParametersAsString(MultiValueMap<String, String> parameters) {
        return parameters.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(value -> {
                            try {
                                return URLEncoder.encode(entry.getKey(), "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8");
                            } catch (UnsupportedEncodingException e) {
                                throw new RuntimeException(e);
                            }
                        }))
                .collect(Collectors.joining("&"));
    }
}
