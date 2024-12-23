package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.PatientBatch;
import de.medizininformatikinitiative.torch.model.consent.ConsentInfo;
import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import de.medizininformatikinitiative.torch.model.consent.PatientConsentInfo;
import de.medizininformatikinitiative.torch.model.consent.Provisions;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.service.DataStore;
import de.medizininformatikinitiative.torch.util.*;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static de.medizininformatikinitiative.torch.model.consent.Provisions.merge;
import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;

/**
 * The {@code ConsentHandler} class is responsible for managing and verifying patient consents
 * within the Torch application.
 * <p>Key functionalities include:
 * <ul>
 *     <li>Checking patient consent based on FHIR resources.</li>
 *     <li>Building consent information for a batch of patients.</li>
 *     <li>Updating consent provisions based on patient encounters.</li>
 * </ul>
 *
 * @see DataStore
 * @see ConsentCodeMapper
 * @see ConsentProcessor
 */
public class ConsentHandler {

    private static final Logger logger = LoggerFactory.getLogger(ConsentHandler.class);
    public static final String CDS_CONSENT_PROFILE_URL = "https://www.medizininformatik-initiative.de/fhir/modul-consent/StructureDefinition/mii-pr-consent-einwilligung";
    public static final String CDS_ENCOUNTER_PROFILE_URL = "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung";
    private final DataStore dataStore;
    private final ConsentCodeMapper mapper;
    private final JsonNode mappingProfiletoDateField;
    private final FhirContext ctx;
    private final FhirPathBuilder fhirPathBuilder;
    private final CdsStructureDefinitionHandler cdsStructureDefinitionHandler;
    private final ConsentProcessor consentProcessor;

    /**
     * Constructs a new {@code ConsentHandler} with the specified dependencies.
     *
     * @param dataStore                     The {@link DataStore} service for Server Calls.
     * @param mapper                        The {@link ConsentCodeMapper} for mapping consent codes.
     * @param profilePath                   The file system path to the consent profile mapping configuration.
     * @param cdsStructureDefinitionHandler The {@link CdsStructureDefinitionHandler} for handling structure definitions.
     * @throws IOException If an error occurs while reading the mapping profile file.
     */
    public ConsentHandler(DataStore dataStore, ConsentCodeMapper mapper, String profilePath, CdsStructureDefinitionHandler cdsStructureDefinitionHandler, FhirContext ctx, ObjectMapper objectMapper) throws IOException {
        this.dataStore = dataStore;
        this.mapper = mapper;
        this.ctx = ctx;
        this.fhirPathBuilder = new FhirPathBuilder(new Slicing(ctx));
        this.cdsStructureDefinitionHandler = cdsStructureDefinitionHandler;
        this.consentProcessor = new ConsentProcessor(ctx);
        mappingProfiletoDateField = objectMapper.readTree(new File(profilePath).getAbsoluteFile());
    }

    /**
     * Returns a Mono which will emit a {@code ConsentInfo} for given consent key and batch.
     * <p>
     * This methods fetches Consent resources from a FHIR server and extracts provisions according to consent key.
     *
     * @param consentKey Consent consentKey for which the ConsentInfo should be build
     * @param batch      Batch of patient IDs
     * @return Consentinfo containing all required provisions by Patient with valid times
     */
    public Mono<ConsentInfo> fetchAndBuildConsentInfo(String consentKey, PatientBatch batch) {
        Flux<PatientConsentInfo> consentInfoFlux = buildingConsentInfo(consentKey, batch);
        Mono<List<PatientConsentInfo>> collectedConsentInfo = collectConsentInfo(consentInfoFlux);
        return updateConsentPeriodsByPatientEncounters(collectedConsentInfo, batch)
                .map(consentInfos -> new ConsentInfo(true, consentInfos.stream().collect(Collectors.toMap(PatientConsentInfo::patientId, PatientConsentInfo::provisions))));
    }

    /**
     * Checks whether the provided {@link DomainResource} complies with the patient's consents.
     *
     * <p>This method evaluates the resource against the consent information to determine if access
     * should be granted based on the defined consent provisions.
     *
     * @param resource    The FHIR {@link DomainResource} to check for consent compliance.
     * @param consentInfo A map containing consent information structured by patient ID and consent codes.
     * @return {@code true} if the resource complies with the consents; {@code false} otherwise.
     */
    public boolean checkConsent(DomainResource resource, ConsentInfo consentInfo) {
        logger.trace("Checking consent for {} {}", resource.getResourceType(), resource.getId());
        Iterator<CanonicalType> profileIterator = resource.getMeta().getProfile().iterator();
        JsonNode fieldValue = null;
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = null;

        logger.trace("Checking consent for resource of type: {} with {} profiles", resource.getResourceType(), resource.getMeta().getProfile().size());

        while (profileIterator.hasNext()) {
            String profile = profileIterator.next().asStringValue();
            logger.trace("Evaluating profile: {}", profile);

            if (mappingProfiletoDateField.has(profile)) {
                logger.trace("Handling the following Profile {}", profile);
                fieldValue = mappingProfiletoDateField.get(profile);
                logger.trace("Fieldvalue {}", fieldValue);
                snapshot = cdsStructureDefinitionHandler.getSnapshot(profile);
                logger.trace("Profile matched. FieldValue for profile {}: {}", profile, fieldValue);
                break; // Exit after finding the first match
            }
        }

        if (fieldValue == null) {
            logger.warn("No matching profile found for resource {} of type: {}", resource.getId(), resource.getResourceType());
            return false;
        }
        if (fieldValue.asText().isEmpty()) {
            logger.trace("Field value is isEmpty, consent is automatically granted if patient has consents in general.");
            return true;
        } else {
            logger.trace("Fieldvalue to be handled {} as FhirPath", fieldValue.asText());

            String fhirPath = fhirPathBuilder.handleSlicingForFhirPath(fieldValue.asText(), snapshot)[0];
            List<Base> values = ctx.newFhirPath().evaluate(resource, fhirPath, Base.class);

            logger.trace("Evaluated FHIRPath expression, found {} values.", values.size());

            for (Base value : values) {
                de.medizininformatikinitiative.torch.model.consent.Period period;
                if (value instanceof Period) {
                    period = de.medizininformatikinitiative.torch.model.consent.Period.fromHapi((Period) value);
                } else if (value instanceof DateTimeType) {
                    period = de.medizininformatikinitiative.torch.model.consent.Period.fromHapi((DateTimeType) value);
                } else {
                    throw new IllegalArgumentException("No valid Date Time Value found");
                }
                String patientID;
                try {
                    patientID = ResourceUtils.patientId(resource);
                } catch (PatientIdNotFoundException e) {
                    return false;
                }

                boolean hasValidConsent = Optional.ofNullable(consentInfo.provisions().get(patientID))
                        .map(provisions -> provisions.periods().entrySet().stream()
                                .allMatch(innerEntry -> {
                                    NonContinuousPeriod consentPeriods = innerEntry.getValue();
                                    return consentPeriods.within(period);
                                }))
                        .orElse(false);
                if (hasValidConsent) {
                    return true;
                }
            }
            return false;  // No matching consent period found
        }
    }


    /**
     * Builds consent information for a batch of patients based on the provided key and patient IDs.
     *
     * <p>This method retrieves relevant consent resources, processes them, and structures the consent
     * information in a map organized by patient ID and consent codes.
     *
     * @param key   A string key used to retrieve relevant consent codes from the {@link ConsentCodeMapper}.
     * @param batch A list of patient IDs to process in this batch.
     * @return A {@link Flux} emitting maps containing consent information structured by patient ID and consent codes.
     */
    public Flux<PatientConsentInfo> buildingConsentInfo(String key, PatientBatch batch) {
        Objects.requireNonNull(batch, "Patient batch cannot be null");
        // Retrieve the relevant codes for the given key
        Set<String> codes = mapper.getRelevantCodes(key);

        logger.trace("Starting to build consent info for key: {} with batch size: {}", key, batch.ids().size());
        String type = "Consent";
        Query query = Query.of(type, QueryParams.of("_profile", stringValue(CDS_CONSENT_PROFILE_URL))
                .appendParams(batch.compartmentSearchParam(type)));

        return dataStore.search(query)
                .cast(Consent.class)
                .doOnSubscribe(subscription -> logger.debug("Fetching resources for batch: {}", batch.ids()))
                .doOnNext(resource -> logger.trace("consent resource with id {} fetched for ConsentBuild", resource.getIdPart()))
                .flatMap(consent -> {
                    try {
                        String patientId = ResourceUtils.patientId(consent);
                        logger.trace("Processing consent for patient {}", patientId);
                        Provisions consents = consentProcessor.transformToConsentPeriodByCode(consent, codes);
                        return Mono.just(new PatientConsentInfo(patientId, consents));
                    } catch (ConsentViolatedException | PatientIdNotFoundException e) {
                        return Mono.error(e);
                    }
                });
    }

    /**
     * Updates consent provisions based on patient encounters for a given batch.
     *
     * <p>This method retrieves all encounters associated with the patients in the batch and updates
     * their consent provisions accordingly. It ensures that consents are valid in the context of the
     * patient's encounters.
     *
     * @param consentInfos A {@link Flux} emitting maps of consent information structured by patient ID and consent codes.
     * @param batch        A list of patient IDs to process in this batch.
     * @return A {@link Flux} emitting updated maps of consent information.
     */
    public Mono<List<PatientConsentInfo>> updateConsentPeriodsByPatientEncounters(
            Mono<List<PatientConsentInfo>> consentInfos, PatientBatch batch) {
        Objects.requireNonNull(batch, "Patient batch cannot be null");
        logger.debug("Starting to update consent info with batch size: {}", batch.ids().size());
        String type = "Encounter";

        Query query = Query.of(type, QueryParams.of("_profile", stringValue(CDS_ENCOUNTER_PROFILE_URL))
                .appendParams(batch.compartmentSearchParam(type)));
        Flux<Encounter> allEncountersFlux = dataStore.search(query)
                .cast(Encounter.class)
                .doOnSubscribe(subscription -> logger.debug("Fetching encounters for batch: {}", batch.ids()))
                .doOnNext(encounter -> logger.trace("Encounter fetched: {}", encounter.getIdElement().getIdPart()));

        // Step 2: Group the encounters by patient ID
        Mono<Map<String, Collection<Encounter>>> encountersByPatientMono = groupEncounterByPatient(allEncountersFlux);

        // Step 3: Process each patient's consent info individually
        return encountersByPatientMono.flatMap(encountersByPatientMap ->
                consentInfos.map(patientConsentInfos ->
                        patientConsentInfos.stream().map(
                                consentInfo -> {
                                    Collection<Encounter> patientEncounters = encountersByPatientMap.get(consentInfo.patientId());
                                    if (patientEncounters == null || patientEncounters.isEmpty()) {
                                        logger.warn("No encounters found for patient {}", consentInfo.patientId());
                                        return consentInfo;
                                    }
                                    return consentInfo.updateConsentPeriodsByPatientEncounters(patientEncounters);
                                }
                        ).toList()

                )
        );
    }

    private static Mono<Map<String, Collection<Encounter>>> groupEncounterByPatient(Flux<Encounter> allEncountersFlux) {
        return allEncountersFlux
                .flatMap(encounter -> {
                    try {
                        String patientId = ResourceUtils.patientId(encounter);
                        return Mono.just(Tuples.of(patientId, encounter));
                    } catch (PatientIdNotFoundException e) {
                        return Mono.error(e);
                    }
                }).collectMultimap(Tuple2::getT1, Tuple2::getT2);
    }


    Mono<List<PatientConsentInfo>> collectConsentInfo(Flux<PatientConsentInfo> consentInfoFlux) {
        return consentInfoFlux.collectMultimap(PatientConsentInfo::patientId, PatientConsentInfo::provisions)
                .map(map ->
                        map.entrySet().stream().map(
                                entry -> new PatientConsentInfo(entry.getKey(), merge(entry.getValue()))
                        ).toList()
                );
    }


}
