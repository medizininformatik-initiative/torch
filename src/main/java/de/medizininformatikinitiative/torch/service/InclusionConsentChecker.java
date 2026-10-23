package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.consent.ConsentCheckOutcome;
import de.medizininformatikinitiative.torch.consent.ConsentCheckResult;
import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionStage;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.mapping.MappingException;
import de.medizininformatikinitiative.torch.model.sq.AttributeFilter;
import de.medizininformatikinitiative.torch.model.sq.Criterion;
import de.medizininformatikinitiative.torch.model.sq.ReferenceFilterPart;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.DomainResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;
import static java.util.Objects.requireNonNull;

/**
 * Removes patients from a batch whose inclusion criteria are only fulfilled by resources outside their consent window.
 *
 * <p>The inclusion criteria of the cohort definition form an AND of OR-groups. Each criterion is translated into the
 * FHIR search queries Flare would use for it, restricted to the batch's patients. A patient is kept if every group
 * has at least one matching resource that passes the {@link ConsentValidator}. Consent criteria
 * ({@code context.code = Einwilligung}) define the window and are not checked; exclusion criteria are ignored, since
 * they can only remove patients. Criteria with reference attribute filters are not supported yet and are rejected.
 */
public class InclusionConsentChecker {

    private static final Logger logger = LoggerFactory.getLogger(InclusionConsentChecker.class);
    private static final String CONSENT_CONTEXT_CODE = "Einwilligung";
    private static final int QUERY_CONCURRENCY = 4;

    private final CriterionQueryTranslator translator;
    private final DataStore dataStore;
    private final ConsentValidator consentValidator;
    private final ObjectMapper mapper;

    public InclusionConsentChecker(CriterionQueryTranslator translator, DataStore dataStore,
                                   ConsentValidator consentValidator, ObjectMapper mapper) {
        this.translator = requireNonNull(translator);
        this.dataStore = requireNonNull(dataStore);
        this.consentValidator = requireNonNull(consentValidator);
        this.mapper = requireNonNull(mapper);
    }

    /**
     * Keeps only the patients of {@code batch} with a consented resource for every inclusion criterion group.
     *
     * <p>Removed patients are recorded as {@link PatientExclusionStage#INCLUSION_CONSENT} exclusions.
     *
     * @param cohortDefinition the CCDL cohort definition of the CRTDL
     * @param batch            the batch with resolved consent windows
     * @return the filtered batch, the unchanged batch if no criterion is checkable, empty if no patient remains, or an
     * error if a criterion cannot be translated
     */
    public Mono<PatientBatchWithConsent> filter(JsonNode cohortDefinition, PatientBatchWithConsent batch) {
        List<List<Criterion>> groups;
        try {
            groups = checkableGroups(cohortDefinition);
        } catch (JsonProcessingException | MappingException e) {
            return Mono.error(e);
        }
        if (groups.isEmpty()) {
            return Mono.just(batch);
        }
        return Flux.fromIterable(groups)
                .concatMap(group -> consentedPatients(group, batch))
                .reduceWith(() -> new HashSet<>(batch.patientIds()), (kept, consented) -> {
                    kept.retainAll(consented);
                    return kept;
                })
                .flatMap(kept -> {
                    batch.patientIds().stream()
                            .filter(id -> !kept.contains(id))
                            .forEach(id -> batch.batchExclusions().addPatientExclusion(PatientExclusionStage.INCLUSION_CONSENT, id));
                    logger.debug("Batch {}: {} of {} patients have consented inclusion criteria",
                            batch.id(), kept.size(), batch.patientIds().size());
                    return kept.isEmpty() ? Mono.empty() : Mono.just(batch.keep(kept));
                });
    }

    /**
     * Checks that every inclusion criterion that {@link #filter} would check can be translated into FHIR search queries.
     *
     * @param cohortDefinition the CCDL cohort definition of the CRTDL
     * @throws ValidationException if a criterion cannot be parsed or translated with the ontology's mappings
     */
    public void validate(JsonNode cohortDefinition) throws ValidationException {
        try {
            for (List<Criterion> group : checkableGroups(cohortDefinition)) {
                for (Criterion criterion : group) {
                    translator.toQuery(criterion);
                }
            }
        } catch (JsonProcessingException | MappingException e) {
            throw new ValidationException("Inclusion criterion cannot be checked against consent: " + e.getMessage());
        }
    }

    /**
     * Parses the inclusion criteria that have to be checked, grouped by AND-group.
     *
     * <p>Criteria with reference attribute filters are rejected: the chained search only returns the referencing
     * resource, so the consent of the referenced resource, which carries the qualifying data, would go unchecked.
     */
    private List<List<Criterion>> checkableGroups(JsonNode cohortDefinition)
            throws JsonProcessingException, MappingException {
        var groups = new ArrayList<List<Criterion>>();
        for (JsonNode group : cohortDefinition.path("inclusionCriteria")) {
            var criteria = new ArrayList<Criterion>();
            for (JsonNode node : group) {
                if (!CONSENT_CONTEXT_CODE.equals(node.path("context").path("code").asText())) {
                    var criterion = mapper.treeToValue(node, Criterion.class);
                    if (hasReferenceFilter(criterion)) {
                        throw new MappingException("Criteria with reference attribute filters are not supported: "
                                + node.path("context").path("code").asText());
                    }
                    criteria.add(criterion);
                }
            }
            if (!criteria.isEmpty()) {
                groups.add(criteria);
            }
        }
        return groups;
    }

    private static boolean hasReferenceFilter(Criterion criterion) {
        return criterion.filters().stream()
                .anyMatch(filter -> filter instanceof AttributeFilter attributeFilter
                        && attributeFilter.filterPart() instanceof ReferenceFilterPart);
    }

    private Mono<Set<String>> consentedPatients(List<Criterion> group, PatientBatchWithConsent batch) {
        return Flux.fromIterable(group)
                .concatMap(this::queries)
                .distinct()
                .flatMap(query -> dataStore.search(restrict(query, batch), DomainResource.class), QUERY_CONCURRENCY)
                .filter(resource -> consentValidator.checkConsent(resource, batch)
                        .map(InclusionConsentChecker::isConsented)
                        .orElse(false))
                .flatMap(resource -> Mono.justOrEmpty(patientId(resource)))
                .collect(Collectors.toSet());
    }

    private Flux<Query> queries(Criterion criterion) {
        try {
            return Flux.fromIterable(translator.toQuery(criterion));
        } catch (MappingException e) {
            return Flux.error(e);
        }
    }

    private static Optional<String> patientId(DomainResource resource) {
        try {
            return Optional.of(ResourceUtils.patientId(resource));
        } catch (PatientIdNotFoundException e) {
            return Optional.empty();
        }
    }

    private static boolean isConsented(ConsentCheckResult result) {
        return result.outcome() == ConsentCheckOutcome.IN_PERIOD || result.outcome() == ConsentCheckOutcome.NO_DATE_FIELD;
    }

    private Query restrict(Query query, PatientBatchWithConsent batch) {
        return Query.of(query.type(), batch.patientBatch().compartmentSearchParam(query.type())
                .appendParams(query.params())
                .appendParam("_elements", stringValue(elements(query.type()))));
    }

    /**
     * The elements needed to attribute a resource to its patient and to check its consent date.
     */
    String elements(String resourceType) {
        var patientElement = switch (resourceType) {
            case "Patient" -> "id";
            case "Consent", "Immunization" -> "patient";
            default -> "subject";
        };
        return Stream.concat(Stream.of(patientElement), consentValidator.consentDateElement(resourceType).stream())
                .collect(Collectors.joining(","));
    }
}
