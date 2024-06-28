package de.medizininformatikinitiative.util;

import de.medizininformatikinitiative.CdsStructureDefinitionHandler;
import de.medizininformatikinitiative.model.*;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static de.medizininformatikinitiative.util.ListUtils.splitListIntoBatches;

public class FhirSearchBuilder {

    public static int batchSize = 100;

    // Change global batch size
    public static void setBatchsize(int batchSize) {
        FhirSearchBuilder.batchSize = batchSize;
    }

    public DataExtraction extraction;

    private CdsStructureDefinitionHandler cdsStructureDefinitionHandler;

    public FhirSearchBuilder(CdsStructureDefinitionHandler cds) {
        this.cdsStructureDefinitionHandler = cds;
    }

    public List<String> buildSearchBatches(Crtdl extraction, ArrayList<String> patients) {
        return buildSearchBatches(extraction, patients, batchSize);
    }

    public List<String> buildSearchBatches(Crtdl extraction, ArrayList<String> patients, int batchSize) {
        this.extraction = extraction.getCohortDefinition().getDataExtraction();
        return getSearchBatchesAsUrls(patients, batchSize);
    }

    public List<String> getSearchBatchesAsUrls(ArrayList<String> patients) {
        return getSearchBatchesAsUrls(patients, batchSize);
    }

    public List<String> getSearchBatchesAsUrls(ArrayList<String> patients, int size) {
        List<Map<String, String>> searchBatches = getSearchBatches(patients, size);
        return searchBatches.stream()
                .map(this::exportParametersAsString)
                .collect(Collectors.toList());
    }

    public List<Map<String, String>> getSearchBatches(ArrayList<String> patients, int size) {
        List<String> batches = splitListIntoBatches(patients, size);
        List<AttributeGroup> attributeGroups = extraction.getAttributeGroups();
        List<Map<String, String>> searchBatches = new LinkedList<>();
        for (String batch : batches) {
            for (AttributeGroup group : attributeGroups) {
                String resourceType = cdsStructureDefinitionHandler.getDefinition(group.getGroupReference()).getType();
                Map<String, String> parameters = new HashMap<>();
                parameters.put("resourceType", resourceType);
                parameters.put("patient", batch);
                parameters.put("_profile", group.getGroupReference());
                if (group.hasFilter()) {
                    parameters.putAll(group.getFiltersAsMap());
                }
                searchBatches.add(parameters);
            }
        }
        return searchBatches;
    }

    public String exportParametersAsString(Map<String, String> parameters) {
        return parameters.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey()) + "=" + URLEncoder.encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }
}
