package de.medizininformatikinitiative.torch.model.extraction;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import java.io.IOException;
import java.io.Writer;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.hl7.fhir.r4.model.Bundle.BundleType.TRANSACTION;
import static org.hl7.fhir.r4.model.Bundle.HTTPVerb.PUT;

/**
 * Holds the extraction info for a  FHIR resources of a batch or core extraction run.
 *
 * <p>The bundle consists of:</p>
 * <ul>
 *   <li><b>extractionInfoMap</b> – metadata about each resource
 *       (attribute groups and allowed references), used for extraction and later provenance generation.</li>
 *   <li><b>cache</b> – the resolved FHIR resources themselves, stored as
 *       Optional<Resource>. Empty values indicate unresolved references.</li>
 * </ul>
 *
 * <p>The structure is thread-safe (ConcurrentHashMap) and supports merging:
 * other bundles overwrite metadata if present, and only non-empty resources
 * are added to the cache.</p>
 *
 * <p>The bundle can be converted to a FHIR transaction Bundle via
 * {@link #toFhirBundle(String)}, including automatically generated
 * Provenance resources per attribute group.</p>
 *
 * @param extractionInfoMap metadata per resourceId
 * @param cache             resolved FHIR resources per resourceId
 */
public record ExtractionResourceBundle(ConcurrentHashMap<String, ResourceExtractionInfo> extractionInfoMap,
                                       ConcurrentHashMap<String, Optional<Resource>> cache) {

    public static final String PROGRAM_URL = "https://www.medizininformatik-initiative.de/fhir/fdpg/NamingSystem/program";
    public static final String ATTRIBUTE_GROUP_URL = "https://www.medizininformatik-initiative.de/fhir/fdpg/NamingSystem/attribute_group";
    public static final String EXTRACTION_ID_URL = "https://www.medizininformatik-initiative.de/fhir/fdpg/NamingSystem/extraction_id";
    public static final String TORCH = "torch";
    public static final String PROVENANCE_PREFIX = "Provenance/torch-";

    public ExtractionResourceBundle {
        Objects.requireNonNull(extractionInfoMap);
        Objects.requireNonNull(cache);
    }

    public ExtractionResourceBundle() {
        this(new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    public static ExtractionResourceBundle of(ResourceBundle resourceBundle) {
        return new ExtractionResourceBundle(new ConcurrentHashMap<>(ResourceExtractionInfo.toExtractionInfoMap(resourceBundle)), new ConcurrentHashMap<>(resourceBundle.cache()));
    }


    public static ExtractionResourceBundle of(PatientResourceBundle patientResourceBundle) {
        return of(patientResourceBundle.bundle());
    }

    public Optional<Resource> getResource(String id) {
        return cache.getOrDefault(id, Optional.empty());
    }

    public Optional<Resource> get(String id) {
        return cache.get(id);
    }

    public void put(String id, Optional<Resource> resource) {
        cache.put(id, resource);
    }

    public boolean isEmpty() {
        return extractionInfoMap.isEmpty() || cache.isEmpty();
    }

    public ExtractionResourceBundle merge(ExtractionResourceBundle other) {
        Map<String, ResourceExtractionInfo> mergedInfo = new HashMap<>(this.extractionInfoMap);
        mergedInfo.putAll(other.extractionInfoMap); // safe, immutable values

        ConcurrentHashMap<String, Optional<Resource>> newCache = new ConcurrentHashMap<>(this.cache);

        other.cache.forEach((k, v) -> {
            if (v.isPresent()) newCache.put(k, v);
        });

        return new ExtractionResourceBundle(new ConcurrentHashMap<>(mergedInfo),   // deep immutable
                newCache);
    }

    /**
     * Returns all resource IDs that exist in the extraction metadata
     * but have no resolved entry in the cache or contain Optional.empty().
     *
     * @return set of missing resourceIds
     */
    public Set<String> missingCacheEntries() {
        Set<String> keys = Set.copyOf(extractionInfoMap.keySet());

        return keys.stream()
                .filter(id -> {
                    var value = cache.get(id);
                    return value == null || value.isEmpty();
                })
                .collect(Collectors.toSet());
    }

    public Bundle toFhirBundle(String extractionId) {
        Bundle bundle = new Bundle();
        bundle.setType(TRANSACTION);
        bundle.setId(UUID.randomUUID().toString());

        // 1. deterministic cache order
        cache.entrySet().stream()
                .filter(e -> extractionInfoMap.containsKey(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> entry.getValue()
                        .ifPresent(resource ->
                                bundle.addEntry(createBundleEntry(resource))));

        // 2. one Provenance per groupId
        buildProvenance(extractionId).forEach(p ->
                bundle.addEntry(createBundleEntry(p)));

        return bundle;
    }

    private Bundle.BundleEntryComponent createBundleEntry(Resource resource) {
        Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
        entry.setResource(resource);

        Bundle.BundleEntryRequestComponent req = new Bundle.BundleEntryRequestComponent();
        req.setUrl(ResourceUtils.getRelativeURL(resource));
        req.setMethod(PUT);

        entry.setRequest(req);
        return entry;
    }

    // -------------------------------------------------------------------------
    //  PROVENANCE GENERATION
    // -------------------------------------------------------------------------

    /**
     * Creates one Provenance resource per groupId.
     * groupId → set of resourceIds
     */
    public List<Provenance> buildProvenance(String extractionId) {

        // invert extractionInfoMap:
        // resourceId → ResourceExtractionInfo(groups = Set<String>)
        // =>
        // groupId → Set<resourceId>
        Map<String, Set<String>> groupIdToResourceIds =
                extractionInfoMap.entrySet().stream()
                        .flatMap(e -> e.getValue().groups().stream()
                                .map(groupId -> Map.entry(groupId, e.getKey())))
                        .collect(Collectors.groupingBy(
                                Map.Entry::getKey,
                                Collectors.mapping(Map.Entry::getValue, Collectors.toSet())
                        ));

        // one Provenance per group
        return groupIdToResourceIds.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> createProvenance(
                        extractionId,
                        entry.getKey(),
                        entry.getValue()       // Set<String> resourceIds
                ))
                .toList();
    }

    private Provenance createProvenance(
            String extractionId,
            String groupId,
            Set<String> resourceIds
    ) {
        Provenance p = new Provenance();
        p.setId(PROVENANCE_PREFIX + UUID.randomUUID());
        p.setRecorded(new Date());

        // targets
        resourceIds.forEach(rId ->
                p.addTarget(new Reference(rId)));

        // occurred
        Period occurred = new Period();
        occurred.setStartElement(new DateTimeType(new Date()));
        p.setOccurred(occurred);

        // agent
        Provenance.ProvenanceAgentComponent agent = new Provenance.ProvenanceAgentComponent();
        agent.setWho(new Reference().setIdentifier(
                new Identifier().setSystem(PROGRAM_URL).setValue(TORCH)));
        p.addAgent(agent);

        // attribute_group entity
        p.addEntity(new Provenance.ProvenanceEntityComponent()
                .setRole(Provenance.ProvenanceEntityRole.SOURCE)
                .setWhat(new Reference().setIdentifier(
                        new Identifier().setSystem(ATTRIBUTE_GROUP_URL).setValue(groupId)))
        );

        // extraction_id entity
        p.addEntity(new Provenance.ProvenanceEntityComponent()
                .setRole(Provenance.ProvenanceEntityRole.SOURCE)
                .setWhat(new Reference().setIdentifier(
                        new Identifier().setSystem(EXTRACTION_ID_URL).setValue(extractionId)))
        );

        return p;
    }

    public void writeToFhirBundle(FhirContext fhirContext, Writer out, String extractionId) throws IOException {
        fhirContext.newJsonParser().setPrettyPrint(false).encodeResourceToWriter(this.toFhirBundle(extractionId), out);
        out.append("\n");
    }
}
