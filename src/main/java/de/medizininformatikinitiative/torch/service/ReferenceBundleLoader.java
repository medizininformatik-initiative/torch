package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.exceptions.ReferenceToPatientException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.extraction.IdentifierReference;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReferenceBundleLoader {
    private static final Logger logger = LoggerFactory.getLogger(ReferenceBundleLoader.class);

    /**
     * Unlike an {@code _id=} search, where each id matches at most one resource, a {@code identifier=} search can
     * return more than one match per identifier since identifiers are not guaranteed unique in a FHIR store. The
     * requested {@code _count} is scaled by this margin so a chunk's response isn't truncated before ambiguous
     * (more than one match) identifiers can be detected.
     */
    private static final int IDENTIFIER_SEARCH_COUNT_MARGIN = 4;

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

    public Mono<List<Resource>> fetchUnknownResources(List<ExtractionId> refsOfLinkedGroup,
                                                      String linkedGroupID,
                                                      Map<String, AnnotatedAttributeGroup> groupMap) {
        var chunkedRefs = chunkRefs(refsOfLinkedGroup, pageCount);
        var bundles = chunkedRefs.stream().map(c -> createBatchBundle(c, linkedGroupID, groupMap));

        return Flux.fromStream(bundles)
                .concatMap(datastore::executeBundle)
                .concatMap(Flux::fromIterable)
                .filter(r -> {
                    try {
                        ResourceUtils.getRelativeURL(r);
                        return true;
                    } catch (RuntimeException e) {
                        logger.warn("Skipping malformed fetched resource (no usable id). type={}, idElement={}",
                                r.fhirType(), r.getId());
                        return false;
                    }
                })
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

    private static String toSearchTokenValue(IdentifierReference ref) {
        return ref.system() == null ? ref.value() : ref.system() + "|" + ref.value();
    }

    private static <T> List<Set<T>> chunk(Collection<T> items, int chunkSize) {
        List<Set<T>> chunks = new ArrayList<>();
        Set<T> currentChunk = new HashSet<>();

        for (T item : items) {
            currentChunk.add(item);

            if (currentChunk.size() == chunkSize) {
                chunks.add(currentChunk);
                currentChunk = new HashSet<>();
            }
        }
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk);
        }

        return chunks;
    }

    /**
     * Fetches resources referenced only by a {@code Reference.identifier} (a logical reference), by searching for
     * their {@code identifier} against the same configured FHIR source, batched per linked group the same way
     * {@link #fetchUnknownResources} batches {@code _id=} searches.
     *
     * @param identifierRefs unresolved logical references of the (linked) group
     * @param linkedGroupID  ID of the linked group, used to apply its filter and resource type during the search
     * @param groupMap       map from group ID to the corresponding {@link AnnotatedAttributeGroup}
     * @return resources matching at least one of the searched identifiers
     */
    public Mono<List<Resource>> fetchByIdentifier(List<IdentifierReference> identifierRefs,
                                                  String linkedGroupID,
                                                  Map<String, AnnotatedAttributeGroup> groupMap) {
        var chunkedRefs = chunkIdentifierRefs(identifierRefs, pageCount);
        var bundles = chunkedRefs.stream().map(c -> createIdentifierBatchBundle(c, linkedGroupID, groupMap));

        return Flux.fromStream(bundles)
                .concatMap(datastore::executeBundle)
                .concatMap(Flux::fromIterable)
                .filter(r -> {
                    try {
                        ResourceUtils.getRelativeURL(r);
                        return true;
                    } catch (RuntimeException e) {
                        logger.warn("Skipping malformed fetched resource (no usable id). type={}, idElement={}",
                                r.fhirType(), r.getId());
                        return false;
                    }
                })
                .collectList();
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
        ExtractionId relativeUrl = ResourceUtils.getRelativeURL(resource);
        boolean isPatientResource = compartmentManager.isInCompartment(relativeUrl);

        if (isPatientResource && patientBundle == null) {
            logger.warn("CoreBundle loaded reference {} belonging to a Patient ", relativeUrl);
            coreBundle.put(relativeUrl);
            return;
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

    private Bundle createIdentifierBatchBundle(Set<IdentifierReference> refs, String linkedGroupID, Map<String, AnnotatedAttributeGroup> groupMap) {
        Bundle batchBundle = new Bundle();
        batchBundle.setType(Bundle.BundleType.BATCH);
        batchBundle.getMeta().setLastUpdated(new Date());

        var ag = groupMap.get(linkedGroupID);
        var identifierValues = refs.stream().map(ReferenceBundleLoader::toSearchTokenValue).toList();
        var count = refs.size() * IDENTIFIER_SEARCH_COUNT_MARGIN;
        var queryPerFilter = ag.queries(mappingTree, ag.resourceType()).stream().map(query ->
                Query.of(query.type(), query.params()
                        .appendParams(QueryParams.of("identifier", QueryParams.multiStringValue(identifierValues)))
                        .appendParams(QueryParams.of("_count", QueryParams.stringValue(String.valueOf(count))))));

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
     *
     * @param refsOfGroup a "flat" list of references of resources of the (linked) group
     * @param chunkSize   number of elements each resulting chunk should contain
     * @return list of set where each set represents one chunk (still mapping from group ID to references)
     */
    public List<Set<String>> chunkRefs(@MonotonicNonNull List<ExtractionId> refsOfGroup, int chunkSize) {
        return chunk(refsOfGroup.stream().map(ExtractionId::id).toList(), chunkSize);
    }

    /**
     * Same chunking as {@link #chunkRefs}, but for unresolved logical references.
     *
     * @param refsOfGroup a "flat" list of identifier references of resources of the (linked) group
     * @param chunkSize   number of elements each resulting chunk should contain
     * @return list of sets where each set represents one chunk
     */
    public List<Set<IdentifierReference>> chunkIdentifierRefs(List<IdentifierReference> refsOfGroup, int chunkSize) {
        return chunk(refsOfGroup, chunkSize);
    }
}
