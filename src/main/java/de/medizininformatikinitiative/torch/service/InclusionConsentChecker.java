package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionStage;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.consent.Period;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryOrJoiner;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.mapping.MappingException;
import de.medizininformatikinitiative.torch.model.sq.Criterion;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * FHIR search queries Flare would use for it; the queries of a group that differ only in one code are OR-joined. Per
 * patient, one batch bundle counts the matches of every query, restricted to the patient and, for resource types with
 * a consent date, to each of the patient's consent periods. A patient is kept if every group has a query with a match.
 *
 * <p>Consent criteria ({@code context.code = Einwilligung}) define the window and are not checked; exclusion criteria
 * are ignored, since they can only remove patients. Criteria with reference attribute filters are checked as chained
 * searches: the consent periods apply to the referencing resource only, which consents to its references as well.
 * Resource types without a consent mapping and search params the FHIR server does not support are rejected.
 */
public class InclusionConsentChecker {

    private static final Logger logger = LoggerFactory.getLogger(InclusionConsentChecker.class);
    private static final String CONSENT_CONTEXT_CODE = "Einwilligung";

    private final CriterionQueryTranslator translator;
    private final DataStore dataStore;
    private final ConsentValidator consentValidator;
    private final ObjectMapper mapper;
    private final int concurrency;
    private final Mono<Map<String, Set<String>>> supportedSearchParams;

    public InclusionConsentChecker(CriterionQueryTranslator translator, DataStore dataStore,
                                   ConsentValidator consentValidator, ObjectMapper mapper, int concurrency) {
        this.translator = requireNonNull(translator);
        this.dataStore = requireNonNull(dataStore);
        this.consentValidator = requireNonNull(consentValidator);
        this.mapper = requireNonNull(mapper);
        this.concurrency = concurrency;
        this.supportedSearchParams = Mono.defer(dataStore::capabilityStatement)
                .map(InclusionConsentChecker::searchParamsByType)
                .cache(params -> Duration.ofMillis(Long.MAX_VALUE), error -> Duration.ZERO, () -> Duration.ZERO);
    }

    /**
     * Keeps only the patients of {@code batch} with a consented resource for every inclusion criterion group.
     *
     * <p>Sends one count batch bundle per patient, at most {@code concurrency} at a time, since Blaze processes
     * the entries of a batch bundle sequentially. Removed patients are recorded as
     * {@link PatientExclusionStage#INCLUSION_CONSENT} exclusions.
     *
     * @param cohortDefinition the CCDL cohort definition of the CRTDL
     * @param batch            the batch with resolved consent windows
     * @return the filtered batch, the unchanged batch if no criterion is checkable, empty if no patient remains, or an
     * error if a criterion cannot be translated, the FHIR server does not support a search param or a count fails
     */
    public Mono<PatientBatchWithConsent> filter(JsonNode cohortDefinition, PatientBatchWithConsent batch) {
        List<List<GroupQuery>> groups;
        try {
            groups = groupQueries(cohortDefinition);
        } catch (JsonProcessingException | MappingException e) {
            return Mono.error(e);
        }
        if (groups.isEmpty()) {
            return Mono.just(batch);
        }
        return supportedSearchParams
                .flatMapMany(supported -> Flux.fromIterable(batch.patientIds())
                        .flatMap(id -> fulfillsAllGroups(groups, id, batch, supported)
                                .filter(Boolean::booleanValue)
                                .map(fulfilled -> id), concurrency))
                .collect(Collectors.toSet())
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
     * @throws ValidationException if a criterion cannot be parsed or translated with the ontology's mappings, or
     *                             queries a resource type without a consent date search param
     */
    public void validate(JsonNode cohortDefinition) throws ValidationException {
        try {
            groupQueries(cohortDefinition);
        } catch (JsonProcessingException | MappingException e) {
            throw new ValidationException("Inclusion criterion cannot be checked against consent: " + e.getMessage());
        }
    }

    /**
     * A translated query and the search param of its consent date, if its resource type has one.
     */
    private record GroupQuery(Query query, Optional<String> dateParam) {
    }

    /**
     * Translates the inclusion criteria that have to be checked into OR-joined queries, grouped by AND-group.
     */
    private List<List<GroupQuery>> groupQueries(JsonNode cohortDefinition)
            throws JsonProcessingException, MappingException {
        var groups = new ArrayList<List<GroupQuery>>();
        for (JsonNode group : cohortDefinition.path("inclusionCriteria")) {
            var queries = new ArrayList<Query>();
            for (JsonNode node : group) {
                if (!CONSENT_CONTEXT_CODE.equals(node.path("context").path("code").asText())) {
                    queries.addAll(translator.toQuery(mapper.treeToValue(node, Criterion.class)));
                }
            }
            if (!queries.isEmpty()) {
                var groupQueries = new ArrayList<GroupQuery>();
                for (Query query : QueryOrJoiner.join(queries)) {
                    groupQueries.add(new GroupQuery(query, dateSearchParam(query.type())));
                }
                groups.add(groupQueries);
            }
        }
        return groups;
    }

    private Optional<String> dateSearchParam(String resourceType) throws MappingException {
        var mapping = consentValidator.consentDateMapping(resourceType)
                .orElseThrow(() -> new MappingException("No consent date mapping for resource type " + resourceType));
        return Optional.ofNullable(mapping.dateSearchParam());
    }


    private static Map<String, Set<String>> searchParamsByType(CapabilityStatement capabilityStatement) {
        var params = new HashMap<String, Set<String>>();
        capabilityStatement.getRest().forEach(rest -> rest.getResource().forEach(resource ->
                params.computeIfAbsent(resource.getType(), type -> new HashSet<>()).addAll(resource.getSearchParam().stream()
                        .map(CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent::getName)
                        .toList())));
        return params;
    }

    private Mono<Boolean> fulfillsAllGroups(List<List<GroupQuery>> groups, String patientId,
                                            PatientBatchWithConsent batch, Map<String, Set<String>> supported) {
        var patient = PatientBatch.of(patientId);
        var periods = batch.bundles().get(patientId).consentPeriods().periods();
        var queries = new ArrayList<Query>();
        var groupOfQuery = new ArrayList<Integer>();
        for (int group = 0; group < groups.size(); group++) {
            for (GroupQuery groupQuery : groups.get(group)) {
                for (Query query : patientQueries(groupQuery, patient, periods)) {
                    queries.add(query);
                    groupOfQuery.add(group);
                }
            }
        }
        var unsupported = queries.stream().flatMap(query -> unsupportedParams(query, supported)).findFirst();
        if (unsupported.isPresent()) {
            return Mono.error(new MappingException("The FHIR server does not support the search param " + unsupported.get()));
        }
        return dataStore.countBatch(queries).map(counts -> {
            var fulfilled = new HashSet<Integer>();
            for (int i = 0; i < counts.size(); i++) {
                if (counts.get(i) > 0) {
                    fulfilled.add(groupOfQuery.get(i));
                }
            }
            return fulfilled.size() == groups.size();
        });
    }

    /**
     * Restricts {@code groupQuery} to the patient and, if its resource type has a consent date, to each consent period.
     *
     * <p>The {@code sa}/{@code eb} prefixes only match dates that lie completely inside the period, like
     * {@link ConsentValidator#checkConsent}. Unlike there, the FHIR server compares the dates as instants against UTC
     * days, so a resource timestamped within its UTC offset of a period boundary may be judged differently.
     *
     * <p>The restrictions come before the criterion params, since the criterion values are not escaped: a value
     * containing {@code #} would cut off all following params of the entry URL.
     */
    private static List<Query> patientQueries(GroupQuery groupQuery, PatientBatch patient, List<Period> periods) {
        var query = groupQuery.query();
        var restriction = patient.compartmentSearchParam(query.type());
        return groupQuery.dateParam()
                .map(dateParam -> periods.stream()
                        .map(period -> Query.of(query.type(), restriction
                                .appendParam(dateParam, stringValue("sa" + period.start().minusDays(1)))
                                .appendParam(dateParam, stringValue("eb" + period.end().plusDays(1)))
                                .appendParams(query.params())))
                        .toList())
                .orElse(List.of(Query.of(query.type(), restriction.appendParams(query.params()))));
    }

    /**
     * Blaze silently ignores unknown search params in batch bundle entries, which would make every patient fulfil
     * the criterion, so params the FHIR server does not declare are reported. Of chained params
     * ({@code medication.code}) only the reference param is checked; modifiers and special params ({@code _id}, ...)
     * are not checked.
     */
    private static Stream<String> unsupportedParams(Query query, Map<String, Set<String>> supported) {
        var supportedByType = supported.getOrDefault(query.type(), Set.of());
        return query.params().params().stream()
                .map(param -> param.name().split("[.:]")[0])
                .filter(name -> !name.startsWith("_") && !supportedByType.contains(name))
                .map(name -> query.type() + "." + name);
    }
}
