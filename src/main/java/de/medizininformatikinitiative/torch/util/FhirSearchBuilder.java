package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.model.AttributeGroup;

import java.util.ArrayList;
import java.util.List;

import static de.medizininformatikinitiative.torch.util.BatchUtils.splitListIntoBatches;

public class FhirSearchBuilder {

    public static int batchSize = 100;

    // Change global batch size
    public static void setBatchsize(int batchSize) {
        FhirSearchBuilder.batchSize = batchSize;
    }


    public String getSearchBatch(AttributeGroup group, String batch) {
        String filter = "";
        if (group.hasFilter()) {
            filter = "&" + group.getFilterString();
        }
        String parameters;
        if (group.getGroupReferenceURL().contains("patient")) {
            parameters = "identifier=" + batch;
        } else {
            parameters = "patient=" + batch;
        }
        parameters += "&_profile=" + group.getGroupReferenceURL() + filter;
        return parameters;

    }


    public List<String> getSearchBatches(AttributeGroup group, List<String> patients, int size) {
        List<String> batches = splitListIntoBatches(patients, size);
        List<String> searchBatches = new ArrayList<>();
        for (String batch : batches) {
            String parameters = getSearchBatch(group, batch);
            searchBatches.add(parameters);
        }

        return searchBatches;
    }


}
