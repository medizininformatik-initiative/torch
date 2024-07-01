package de.medizininformatikinitiative.util;

import de.medizininformatikinitiative.CdsStructureDefinitionHandler;
import de.medizininformatikinitiative.model.*;

import java.io.UnsupportedEncodingException;
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

    public List<MultiValueMap<String, String>> buildSearchBatches(AttributeGroup group, ArrayList<String> patients) {
        return buildSearchBatches(group, patients, batchSize);
    }

    public List<MultiValueMap<String, String>> buildSearchBatches(AttributeGroup group, ArrayList<String> patients, int batchSize) {
        return getSearchBatches(group, patients, batchSize );
    }

    public List<String> getSearchBatchesAsUrls(AttributeGroup group, ArrayList<String> patients, int size) {
        List<MultiValueMap<String, String>> searchBatches = getSearchBatches(group, patients, size);

        return searchBatches.stream()
                .map(this::exportParametersAsString)
                .collect(Collectors.toList());
    }

    public List<MultiValueMap<String, String>> getSearchBatches(AttributeGroup group, ArrayList<String> patients, int size) {

        List<String> batches = splitListIntoBatches(patients, size);

        List<MultiValueMap<String, String>> searchBatches = new LinkedList<>();
        for (String batch : batches) {

            String resourceType = cdsStructureDefinitionHandler.getDefinition(group.getGroupReference()).getType();
            MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
            parameters.add("patient", batch);
            if (group.hasFilter()) {
                Map<String, String> filters = group.getFiltersAsMap();
                for (Map.Entry<String, String> entry : filters.entrySet()) {
                    parameters.add(entry.getKey(), entry.getValue());
                }
            }
            searchBatches.add(parameters);

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
