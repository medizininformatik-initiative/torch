package de.medizininformatikinitiative.util;

import de.medizininformatikinitiative.CDSStructureDefinitionHandler;
import de.medizininformatikinitiative.util.model.AttributeGroup;
import de.medizininformatikinitiative.util.model.CRTDL;
import de.medizininformatikinitiative.util.model.DataExtraction;
import de.medizininformatikinitiative.util.model.Filter;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static de.medizininformatikinitiative.util.ListUtils.splitListIntoBatches;

public class FHIRSearchBuilder {

    public static int batchSize=100;


    //Change global batch size
    public static void setBatchsize(int batchsize) {
        batchSize=batchsize;
    }

    public DataExtraction extraction;

    private CDSStructureDefinitionHandler cdsStructureDefinitionHandler;


    public FHIRSearchBuilder(CRTDL extraction,CDSStructureDefinitionHandler cds) {
        this.extraction = extraction.getCohortDefinition().getDataExtraction();;
        this.cdsStructureDefinitionHandler = cds;
    }

    public List<String> getSearchBatches(ArrayList<String> Patients, String serverUrl){
        return getSearchBatches(Patients, serverUrl, batchSize);
    }


        public List<String> getSearchBatches(ArrayList<String> Patients, String serverUrl,int size){

        List<String> batches = splitListIntoBatches(Patients, size);
        List<AttributeGroup> attributeGroups = extraction.getAttributeGroups();
        List<String> modifiedFilters = new ArrayList<>();
        for (AttributeGroup group : attributeGroups) {
            String resourceType = (cdsStructureDefinitionHandler.getDefinition(group.getGroupReference())).getType();
            String groupString = "/"+resourceType+"?patient={patient}&_profile=" + URLEncoder.encode(group.getGroupReference());
            if(group.hasFilter()) {
                modifiedFilters.addAll(group.getFilters().stream()
                        .map(f -> serverUrl + groupString+"&" + f)
                        .toList());
            }else{
                modifiedFilters.add(serverUrl + groupString);
            }

        }
        List<String> searchBatches = new LinkedList<>();
        for(String batch: batches){
            searchBatches.addAll(modifiedFilters.stream().map(f -> f.replace("{patient}", batch)).toList());
        }
        return searchBatches;
    }


}
