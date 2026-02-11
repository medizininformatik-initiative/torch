package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.exceptions.ReferenceToPatientException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReferenceBundleLoader {
    private static final Logger logger = LoggerFactory.getLogger(ReferenceBundleLoader.class);
    private final CompartmentManager compartmentManager;
    private final DataStore datastore;
    private final ConsentValidator consentValidator;
    private final int pageCount;
    private final DseMappingTreeBase mappingTree;

    public ReferenceBundleLoader(CompartmentManager compartmentManager,
                                 DataStore datastore, ConsentValidator consentValidator, int pageCount,
                                 DseMappingTreeBase dseMappingTreeBase) {
        this.compartmentManager = compartmentManager;
        this.datastore = datastore;
        this.consentValidator = consentValidator;
        this.pageCount = pageCount;
        this.mappingTree = dseMappingTreeBase;
    }

    public Mono<List<Resource>> fetchUnknownResources(List<String> refsOfLinkedGroup,
                                                      String linkedGroupID,
                                                      Map<String, AnnotatedAttributeGroup> groupMap) {
        var chunkedRefs = chunkRefs(refsOfLinkedGroup, pageCount);
        var bundles = chunkedRefs.stream().map(c -> createBatchBundle(c, linkedGroupID, groupMap));

        return Flux.fromStream(bundles)
                .concatMap(datastore::executeBundle)
                .concatMap(Flux::fromIterable)
                .collectList();
    }

    private Bundle createBatchBundle(Set<String> refs, String linkedGroupID, Map<String, AnnotatedAttributeGroup> groupMap) {
        // Build the batch bundle
        Bundle batchBundle = new Bundle();
        batchBundle.setType(Bundle.BundleType.BATCH);
        batchBundle.getMeta().setLastUpdated(new Date());

        var ag = groupMap.get(linkedGroupID);
        var queryPerFilter = ag.queries(mappingTree, ag.resourceType()).stream().map(query ->
                Query.of(query.type(), query.params()
                        .appendParams(QueryParams.of("_id", QueryParams.multiStringValue(refs.stream().toList())))
                        .appendParams(QueryParams.of("_count", QueryParams.stringValue(String.valueOf(refs.size()))))));

        queryPerFilter.forEach(query -> {
            Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
            entry.setRequest(new Bundle.BundleEntryRequestComponent()
                    .setMethod(Bundle.HTTPVerb.GET)
                    .setUrl(query.toString()));
            batchBundle.addEntry(entry);
        });

        return batchBundle;
    }


    /**
     * Puts a patient resource (according to the compartment manager) into the patient bundle and core resources into
     * the core bundle.
     *
     * @param patientBundle the patient bundle the potential patient resource might be put in
     * @param coreBundle    the core bundle the potential core resource might be put in
     * @param applyConsent  whether to apply consent if it is a patient resource
     * @param resource      the resource to put into the respective bundle
     */
    public void cacheSearchResults(@Nullable PatientResourceBundle patientBundle, ResourceBundle coreBundle, boolean applyConsent, Resource resource) {
        String relativeUrl = ResourceUtils.getRelativeURL(resource);
        boolean isPatientResource = compartmentManager.isInCompartment(relativeUrl);

        if (isPatientResource && patientBundle == null) {
            logger.warn("CoreBundle loaded reference {} belonging to a Patient ", relativeUrl);
            coreBundle.put(relativeUrl);
        }
        if (isPatientResource) {
            try {
                assert patientBundle != null;
                consentValidator.checkPatientIdAndConsent(patientBundle, applyConsent, resource);
                patientBundle.bundle().put(resource);
            } catch (ConsentViolatedException | PatientIdNotFoundException | ReferenceToPatientException e) {
                patientBundle.put(relativeUrl);
            }
        } else {
            coreBundle.put(resource);
        }
    }


    /**
     *
     * @param refsOfGroup a "flat" list of references of resources of the (linked) group
     * @param chunkSize   number of elements each resulting chunk should contain
     * @return list of set where each set represents one chunk (still mapping from group ID to references)
     */
    public List<Set<String>> chunkRefs(List<String> refsOfGroup, int chunkSize) {
        List<Set<String>> chunks = new ArrayList<>();
        Set<String> currentChunk = new HashSet<>();
        int currentChunkSize = 0;

        for (String ref : refsOfGroup) {
            var refId = ref.split("/")[1];
            currentChunk.add(refId);
            currentChunkSize++;

            if (currentChunkSize == chunkSize) {
                chunks.add(currentChunk);
                currentChunk = new HashSet<>();
                currentChunkSize = 0;
            }
        }
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk);
        }

        return chunks;
    }
}
