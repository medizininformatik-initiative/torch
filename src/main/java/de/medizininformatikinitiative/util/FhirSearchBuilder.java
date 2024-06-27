package de.medizininformatikinitiative.util;

import de.medizininformatikinitiative.CdsStructureDefinitionHandler;

import de.medizininformatikinitiative.model.*;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static de.medizininformatikinitiative.util.ListUtils.splitListIntoBatches;

public class FhirSearchBuilder {

    public static int batchSize=100;


    //Change global batch size
    public static void setBatchsize(int batchsize) {
        batchSize=batchsize;
    }

    public DataExtraction extraction;

    private CdsStructureDefinitionHandler cdsStructureDefinitionHandler;


    public FhirSearchBuilder(CdsStructureDefinitionHandler cds) {;
        this.cdsStructureDefinitionHandler = cds;
    }

    public List<String> buildSearchBatches(Crtdl extraction, ArrayList<String> Patients){
        return buildSearchBatches(extraction,Patients, batchSize);
    }
    public List<String> buildSearchBatches(Crtdl extraction, ArrayList<String> Patients, int batchSize){
        this.extraction = (extraction.getCohortDefinition()).getDataExtraction();
        return getSearchBatches(Patients, batchSize);
    }

    public List<String> getSearchBatches(ArrayList<String> Patients){
        return getSearchBatches(Patients, batchSize);
    }


        public List<String> getSearchBatches(ArrayList<String> Patients, int size){

        List<String> batches = splitListIntoBatches(Patients, size);
        List<AttributeGroup> attributeGroups = extraction.getAttributeGroups();
        List<String> modifiedFilters = new ArrayList<>();
        for (AttributeGroup group : attributeGroups) {
            String resourceType = (cdsStructureDefinitionHandler.getDefinition(group.getGroupReference())).getType();
            String groupString = "/"+resourceType+"?patient={patient}&_profile=" + URLEncoder.encode(group.getGroupReference());
            if(group.hasFilter()) {
                modifiedFilters.addAll(group.getFilters().stream()
                        .map(f -> groupString+"&" + f)
                        .toList());
            }else{
                modifiedFilters.add(groupString);
            }

        }
        List<String> searchBatches = new LinkedList<>();
        for(String batch: batches){
            searchBatches.addAll(modifiedFilters.stream().map(f -> f.replace("{patient}", batch)).toList());
        }
        return searchBatches;
    }


}
