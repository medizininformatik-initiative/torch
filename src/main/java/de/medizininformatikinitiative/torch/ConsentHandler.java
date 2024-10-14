package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.util.*;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * The {@code ConsentHandler} class is responsible for managing and verifying patient consents
 * within the Torch application.
 * <p>Key functionalities include:
 * <ul>
 *     <li>Checking patient consent based on FHIR resources.</li>
 *     <li>Building consent information for a batch of patients.</li>
 *     <li>Updating consent periods based on patient encounters.</li>
 * </ul>
 *
 * @see DataStore
 * @see ConsentCodeMapper
 * @see ConsentProcessor
 */
@Component
public class ConsentHandler {

    private static final Logger logger = LoggerFactory.getLogger(ConsentHandler.class);
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
     * @param dataStore                    The {@link DataStore} service for Server Calls.
     * @param mapper                       The {@link ConsentCodeMapper} for mapping consent codes.
     * @param profilePath                  The file system path to the consent profile mapping configuration.
     * @param cdsStructureDefinitionHandler The {@link CdsStructureDefinitionHandler} for handling structure definitions.
     * @throws IOException If an error occurs while reading the mapping profile file.
     */
    @Autowired
    public ConsentHandler(DataStore dataStore, ConsentCodeMapper mapper, String profilePath, CdsStructureDefinitionHandler cdsStructureDefinitionHandler) throws IOException {
        this.dataStore = dataStore;
        this.mapper = mapper;
        this.ctx = ResourceReader.ctx;
        this.fhirPathBuilder = new FhirPathBuilder(cdsStructureDefinitionHandler);
        this.cdsStructureDefinitionHandler = cdsStructureDefinitionHandler;
        this.consentProcessor = new ConsentProcessor(ctx);
        ObjectMapper objectMapper = new ObjectMapper();
        mappingProfiletoDateField = objectMapper.readTree(new File(profilePath).getAbsoluteFile());
    }

    /**
     * Checks whether the provided {@link DomainResource} complies with the patient's consents.
     *
     * <p>This method evaluates the resource against the consent information to determine if access
     * should be granted based on the defined consent periods.
     *
     * @param resource    The FHIR {@link DomainResource} to check for consent compliance.
     * @param consentInfo A map containing consent information structured by patient ID and consent codes.
     * @return {@code true} if the resource complies with the consents; {@code false} otherwise.
     */
    public Boolean checkConsent(@NotNull DomainResource resource, Map<String, Map<String, List<Period>>> consentInfo) {
        logger.debug("Checking Consent for {}", resource.getResourceType());
        Iterator<CanonicalType> profileIterator = resource.getMeta().getProfile().iterator();
        JsonNode fieldValue = null;
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = null;

        logger.debug("Checking consent for resource of type: {} with {} profiles", resource.getResourceType(), resource.getMeta().getProfile().size());

        while (profileIterator.hasNext()) {
            String profile = profileIterator.next().asStringValue();
            logger.debug("Evaluating profile: {}", profile);

            if (mappingProfiletoDateField.has(profile)) {
                logger.debug("Handling the following Profile {}", profile);
                fieldValue = mappingProfiletoDateField.get(profile);
                logger.debug("Fieldvalue {}", fieldValue);
                snapshot = cdsStructureDefinitionHandler.getSnapshot(profile);
                logger.debug("Profile matched. FieldValue for profile {}: {}", profile, fieldValue);
                break; // Exit after finding the first match
            }
        }

        if (fieldValue == null) {
            logger.warn("No matching profile found for resource of type: {}", resource.getResourceType());
            return false;
        }

        if (fieldValue.asText().isEmpty()) {
            logger.debug("Field value is empty, consent is automatically granted if patient has consents in general.");
            return true;
        } else {
            logger.debug("Fieldvalue to be handled {} as FhirPath", fieldValue.asText());
            List<Base> values = ctx.newFhirPath().evaluate(resource, fhirPathBuilder.handleSlicingForFhirPath(fieldValue.asText(), snapshot), Base.class);
            logger.debug("Evaluated FHIRPath expression, found {} values.", values.size());

            for (Base value : values) {
                DateTimeType resourceStart;
                DateTimeType resourceEnd;

                if (value instanceof DateTimeType) {
                    resourceStart = (DateTimeType) value;
                    resourceEnd = (DateTimeType) value;
                    logger.debug("Evaluated value is DateTimeType: start {}, end {}", resourceStart, resourceEnd);
                } else if (value instanceof Period) {
                    resourceStart = ((Period) value).getStartElement();
                    resourceEnd = ((Period) value).getEndElement();
                    logger.debug("Evaluated value is Period: start {}, end {}", resourceStart, resourceEnd);
                } else {
                    logger.error("No valid Date Time Value found. Value: {}", value);
                    throw new IllegalArgumentException("No valid Date Time Value found");
                }

                String patientID = null;
                try {
                    patientID = ResourceUtils.getPatientId(resource);
                } catch (PatientIdNotFoundException e) {
                    logger.warn("Resource does not Contain any Patient Reference", resource.getIdElement());
                    return false;
                }

                logger.debug("Patient ID {} and Set {} ", patientID, consentInfo.keySet());
                logger.debug("Get Result {}", consentInfo.get(patientID));

                boolean hasValidConsent = Optional.ofNullable(consentInfo.get(patientID))
                        .map(consentPeriodMap -> consentPeriodMap.entrySet().stream()
                                .allMatch(innerEntry -> {
                                    String code = innerEntry.getKey();
                                    List<Period> consentPeriods = innerEntry.getValue();
                                    logger.debug("Checking {} consent periods for code: {}", consentPeriods.size(), code);

                                    // Check if at least one consent period is valid for the current code
                                    return consentPeriods.stream()
                                            .anyMatch(period -> {
                                                logger.debug("Evaluating ConsentPeriod: start {}, end {} vs {} and {}",
                                                        resourceStart, resourceEnd, period.getStart(), period.getEnd());
                                                logger.debug("Result: {}", resourceStart.after(period.getStartElement()) && resourceEnd.before(period.getEndElement()));
                                                return resourceStart.after(period.getStartElement()) && resourceEnd.before(period.getEndElement());
                                            });
                                }))
                        .orElse(false);

                if (hasValidConsent) {
                    logger.info("Valid consent period found for evaluated values.");
                    return true;
                }
            }
            logger.warn("No valid consent period found for any value.");
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
    public Flux<Map<String, Map<String, List<Period>>>> buildingConsentInfo(String key, @NotNull List<String> batch) {
        // Retrieve the relevant codes for the given key
        Set<String> codes = mapper.getRelevantCodes(key);

        logger.info("Starting to build consent info for key: {} with batch size: {}", key, batch.size());

        // Fetch resources using a bounded elastic scheduler for offloading blocking HTTP I/O
        return dataStore.getResources("Consent", FhirSearchBuilder.getConsent(batch))
                .subscribeOn(Schedulers.boundedElastic())  // Offload the HTTP requests
                .doOnSubscribe(subscription -> logger.info("Fetching resources for batch: {}", batch))
                .doOnNext(resource -> logger.debug("Resource fetched for ConsentBuild: {}", resource.getIdElement().getIdPart()))
                .onErrorResume(e -> {
                    logger.error("Error fetching resources for parameters: {}", FhirSearchBuilder.getConsent(batch), e);
                    return Flux.empty();
                })

                .map(resource -> {
                    try {
                        DomainResource domainResource = (DomainResource) resource;
                        String patient = ResourceUtils.getPatientId(domainResource);

                        logger.debug("Processing resource for patient: {} {}", patient, resource.getResourceType());

                        Map<String, List<Period>> consents = consentProcessor.transformToConsentPeriodByCode(domainResource, codes);

                        Map<String, Map<String, List<Period>>> patientConsentMap = new HashMap<>();
                        patientConsentMap.put(patient, new HashMap<>());

                        // Log consent periods transformation
                        logger.debug("Transformed resource into {} consent periods for patient: {}", consents.size(), patient);

                        // Iterate over the consent periods and add them to the patient's map
                        consents.forEach((code, newConsentPeriods) -> {
                            patientConsentMap.get(patient)
                                    .computeIfAbsent(code, k -> new ArrayList<>())
                                    .addAll(newConsentPeriods);
                        });

                        logger.debug("Consent periods updated for patient: {} with {} codes", patient, consents.size());

                        // Return the map containing the patient's consent periods
                        return patientConsentMap;
                    } catch (Exception e) {
                        logger.error("Error processing resource", e);
                        throw new RuntimeException(e);
                    }
                })

                .collectList()
                .doOnSuccess(list -> logger.info("Successfully processed {} resources", list.size()))

                .flatMapMany(Flux::fromIterable);
    }

    /**
     * Updates consent periods based on patient encounters for a given batch.
     *
     * <p>This method retrieves all encounters associated with the patients in the batch and updates
     * their consent periods accordingly. It ensures that consents are valid in the context of the
     * patient's encounters.
     *
     * @param consentInfoFlux A {@link Flux} emitting maps of consent information structured by patient ID and consent codes.
     * @param batch           A list of patient IDs to process in this batch.
     * @return A {@link Flux} emitting updated maps of consent information.
     */
    public Flux<Map<String, Map<String, List<Period>>>> updateConsentPeriodsByPatientEncounters(
            Flux<Map<String, Map<String, List<Period>>>> consentInfoFlux, @NotNull List<String> batch) {

        logger.info("Starting to update consent info with batch size: {}", batch.size());

        // Step 1: Fetch all encounters for the batch of patients
        Flux<Encounter> allEncountersFlux = dataStore.getResources("Encounter", FhirSearchBuilder.getEncounter(batch))
                .subscribeOn(Schedulers.boundedElastic())
                .cast(Encounter.class)
                .doOnSubscribe(subscription -> logger.info("Fetching encounters for batch: {}", batch))
                .doOnNext(encounter -> logger.debug("Encounter fetched: {}", encounter.getIdElement().getIdPart()))
                .onErrorResume(e -> {
                    logger.error("Error fetching encounters for batch: {}", batch, e);
                    return Flux.empty();
                });

        // Step 2: Group the encounters by patient ID
        Mono<Map<String, Collection<Encounter>>> encountersByPatientMono = allEncountersFlux
                .flatMap(encounter -> {
                    try {
                        String patientId = ResourceUtils.getPatientId(encounter);
                        return Mono.just(new AbstractMap.SimpleEntry<>(patientId, encounter));
                    } catch (PatientIdNotFoundException e) {
                        logger.error("Patient ID not found in encounter resource", e);
                        return Mono.empty();
                    }
                })
                .collectMultimap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                );

        // Step 3: Process each patient's consent info individually
        return encountersByPatientMono.flatMapMany(encountersByPatientMap ->
                consentInfoFlux.flatMap(patientConsentInfo -> {

                    String patientId = patientConsentInfo.keySet().stream().findFirst().orElse(null);

                    if (patientId == null) {
                        logger.warn("Patient ID not found in consent info");
                        return Mono.just(patientConsentInfo); // Or handle as appropriate
                    }

                    List<Encounter> patientEncounters = (List<Encounter>) encountersByPatientMap.get(patientId);

                    if (patientEncounters == null || patientEncounters.isEmpty()) {
                        logger.info("No encounters found for patient {}", patientId);
                        // No encounters for this patient, return the consent info as is
                        return Mono.just(patientConsentInfo);
                    }

                    return Mono.fromCallable(() -> {
                        updateConsentPeriodsByPatientEncounters(patientConsentInfo.get(patientId), patientEncounters);
                        return patientConsentInfo;
                    }).subscribeOn(Schedulers.boundedElastic());
                })
        );
    }

    /**
     * Helper method to update consent periods for a patient based on their encounters.
     *
     * <p>This method adjusts the start dates of consent periods to align with the start dates of the patient's
     * encounters, ensuring that consents are valid during the periods of active encounters.
     *
     * @param patientConsentInfo A map of consent codes to their corresponding periods for a specific patient.
     * @param encounters         A list of {@link Encounter} resources associated with the patient.
     */
    private void updateConsentPeriodsByPatientEncounters(
            Map<String, List<Period>> patientConsentInfo, @NotNull List<Encounter> encounters) {

        for (Encounter encounter : encounters) {
            Period encounterPeriod = encounter.getPeriod();

            for (Map.Entry<String, List<Period>> entry : patientConsentInfo.entrySet()) {
                List<Period> consentPeriods = entry.getValue();

                for (Period consentPeriod : consentPeriods) {
                    if (encounterPeriod.getStartElement().before(consentPeriod.getStartElement()) &&
                            encounterPeriod.getEndElement().after(consentPeriod.getStartElement())) {
                        consentPeriod.setStartElement(encounterPeriod.getStartElement());
                    }
                }
            }
        }
    }
}
