package de.medizininformatikinitiative.util;

import de.medizininformatikinitiative.model.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;

import static de.medizininformatikinitiative.util.ListUtils.splitListIntoBatches;

public class FhirSearchBuilder {

    public static int batchSize = 100;

    // Change global batch size
    public static void setBatchsize(int batchSize) {
        FhirSearchBuilder.batchSize = batchSize;
    }



    public List<String> getSearchBatches(AttributeGroup group, List<String> patients, int size) {
        List<String> batches = splitListIntoBatches(patients, size);
        List<String> searchBatches = new ArrayList<>();
        String filter="";
        if (group.hasFilter()) {
            filter="&" + group.getFilterString();
        }

        for (String batch : batches) {
            String parameters = "patient=" + batch+"&profile"+group.getGroupReferenceURL()+filter;
            searchBatches.add(parameters);
            }

        return searchBatches;
    }

}
