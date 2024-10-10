package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.util.*;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.hl7.fhir.r4.model.*;
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

@Component
public class ConsentInfoBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ConsentInfoBuilder.class);
    private final DataStore dataStore;


    private final ConsentCodeMapper mapper;
    private final JsonNode mappingProfiletoDateField;
    private final FhirContext ctx;
    private final FhirPathBuilder fhirPathBuilder;
    private final CdsStructureDefinitionHandler cdsStructureDefinitionHandler;
    private final ConsentProcessor consentProcessor;

    @Autowired
    public ConsentInfoBuilder(DataStore dataStore, ConsentCodeMapper mapper, String profilePath, CdsStructureDefinitionHandler cdsStructureDefinitionHandler) throws IOException {
        this.dataStore = dataStore;
        this.mapper = mapper;
        this.ctx = ResourceReader.ctx;
        this.fhirPathBuilder = new FhirPathBuilder(cdsStructureDefinitionHandler);
        this.cdsStructureDefinitionHandler = cdsStructureDefinitionHandler;
        this.consentProcessor=new ConsentProcessor(ctx);
        ObjectMapper objectMapper = new ObjectMapper();
        mappingProfiletoDateField = objectMapper.readTree(new File(profilePath).getAbsoluteFile());

    }


    public Boolean checkConsent(DomainResource resource, Map<String, Map<String, List<ConsentPeriod>>> consentInfo) {
        logger.debug("Checking Consent for {}", resource.getResourceType());
        Iterator<CanonicalType> profileIterator = resource.getMeta().getProfile().iterator();
        JsonNode fieldValue = null;
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot = null;

        logger.debug("Checking consent for resource of type: {} with {} profiles", resource.getResourceType(), resource.getMeta().getProfile().size());

        // Iterate over the profiles
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
            return false; // No profile match, thus no consent
        }

        if (fieldValue.asText().isEmpty()) {
            logger.debug("Field value is empty, consent is automatically granted if patient has consents in general.");
            return true; // Empty field means automatic consent
        } else {
            // Evaluate the field value using FHIRPath
            logger.debug("Fieldvalue to be handled {} as FhirPath", fieldValue.asText());
            List<Base> values = ctx.newFhirPath().evaluate(resource, fhirPathBuilder.handleSlicingForFhirPath(fieldValue.asText(), snapshot), Base.class);
            logger.debug("Evaluated FHIRPath expression, found {} values.", values.size());

            // Loop through the values to check against consent periods
            for (Base value : values) {
                DateTimeType dateTimeStart;
                DateTimeType dateTimeEnd;

                // Handle DateTimeType and Period values
                if (value instanceof DateTimeType) {
                    dateTimeStart = (DateTimeType) value;
                    dateTimeEnd = (DateTimeType) value;
                    logger.debug("Evaluated value is DateTimeType: start {}, end {}", dateTimeStart, dateTimeEnd);
                } else if (value instanceof Period) {
                    dateTimeStart = ((Period) value).getStartElement();
                    dateTimeEnd = ((Period) value).getEndElement();
                    logger.debug("Evaluated value is Period: start {}, end {}", dateTimeStart, dateTimeEnd);
                } else {
                    logger.error("No valid Date Time Value found. Value: {}", value);
                    throw new IllegalArgumentException("No valid Date Time Value found");
                }

                boolean hasValidConsent = Boolean.TRUE.equals(
                        consentInfo.entrySet().parallelStream()
                                .allMatch(entry -> {
                                    String patientId = entry.getKey();
                                    Map<String, List<ConsentPeriod>> consentPeriodMap = entry.getValue();
                                    logger.debug("Checking consent periods for patient: {}", patientId);

                                    // Check all codes for the patient
                                    return consentPeriodMap.entrySet().stream()
                                            .allMatch(innerEntry -> {
                                                String code = innerEntry.getKey();
                                                List<ConsentPeriod> consentPeriods = innerEntry.getValue();
                                                logger.debug("Checking {} consent periods for code: {}", consentPeriods.size(), code);

                                                // Check if at least one consent period is valid
                                                return consentPeriods.stream()
                                                        .anyMatch(period -> {
                                                            logger.debug("Evaluating ConsentPeriod: start {}, end {} vs {} and {}", dateTimeStart, dateTimeEnd, period.getStart(), period.getEnd());
                                                            logger.debug("Result {}", dateTimeStart.after(period.getStart()) && dateTimeEnd.before(period.getEnd()));
                                                            return dateTimeStart.after(period.getStart()) && dateTimeEnd.before(period.getEnd());
                                                        });
                                            });
                                })
                );

                // Return if a valid consent period was found
                if (hasValidConsent) {
                    logger.info("Valid consent period found for evaluated values.");
                    return true;
                }
            }
            logger.warn("No valid consent period found for any value.");
            return false;  // No matching consent period found
        }
    }


    public Flux<Map<String, Map<String, List<ConsentPeriod>>>> buildingConsentInfo(String key, List<String> batch) {
        // Retrieve the relevant codes for the given key
        Set<String> codes = mapper.getRelevantCodes(key);

        logger.info("Starting to build consent info for key: {} with batch size: {}", key, batch.size());

        // Fetch resources using a bounded elastic scheduler for offloading blocking HTTP I/O
        return dataStore.getResources("Consent", FhirSearchBuilder.getConsent(batch))
                .subscribeOn(Schedulers.boundedElastic())  // Offload the HTTP requests
                .doOnSubscribe(subscription -> logger.info("Fetching resources for batch: {}", batch))
                .doOnNext(resource -> logger.debug("Resource fetched for ConsentBuild: {}", resource.getIdElement().getIdPart()))  // Log each resource fetched
                .onErrorResume(e -> {
                    logger.error("Error fetching resources for parameters: {}", FhirSearchBuilder.getConsent(batch), e);
                    return Flux.empty();  // Return an empty Flux if there's an error to prevent the pipeline from crashing
                })
                // Map over the Flux to process each resource and build the consent information
                .map(resource -> {
                    try {
                        // Cast the resource to a DomainResource
                        DomainResource domainResource = (DomainResource) resource;
                        String patient = ResourceUtils.getPatientId(domainResource);

                        logger.debug("Processing resource for patient: {} {}", patient, resource.getResourceType());

                        // Extract the consent periods for the relevant codes
                        Map<String, List<ConsentPeriod>> consents = consentProcessor.transformToConsentPeriodByCode(domainResource, codes);

                        // Create a map to store the patient's consent periods
                        Map<String, Map<String, List<ConsentPeriod>>> patientConsentMap = new HashMap<>();
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
                // Collect all the maps into a single Flux containing a list of maps
                .collectList()
                .doOnSuccess(list -> logger.info("Successfully processed {} resources", list.size()))  // Log success after processing all resources
                // Flatten the list of maps into a Flux again if you want to continue processing in a reactive manner
                .flatMapMany(Flux::fromIterable);
    }



    public Flux<Map<String, Map<String, List<ConsentPeriod>>>> updateConsentInfo(
            Flux<Map<String, Map<String, List<ConsentPeriod>>>> consentInfoFlux, List<String> batch) {

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
        Mono<Map<String, List<Encounter>>> encountersByPatientMono = allEncountersFlux
                .flatMap(encounter -> {
                    try {
                        String patientId = ResourceUtils.getPatientId(encounter);
                        return Mono.just(Tuples.of(patientId, encounter));
                    } catch (PatientIdNotFoundException e) {
                        logger.error("Patient ID not found in encounter resource", e);
                        return Mono.empty();
                    }
                })
                .collectMultimap(tuple -> tuple.getT1(), tuple -> tuple.getT2());

        // Step 3: Process each patient's consent info individually
        return encountersByPatientMono.flatMapMany(encountersByPatientMap ->
                consentInfoFlux.flatMap(patientConsentInfo -> {
                    // Extract the patient ID from the consent info map
                    String patientId = extractPatientId(patientConsentInfo);

                    if (patientId == null) {
                        logger.warn("Patient ID not found in consent info");
                        return Mono.just(patientConsentInfo); // Or handle as appropriate
                    }

                    // Get the encounters for this patient
                    List<Encounter> patientEncounters = encountersByPatientMap.get(patientId);

                    if (patientEncounters == null || patientEncounters.isEmpty()) {
                        logger.info("No encounters found for patient {}", patientId);
                        // No encounters for this patient, return the consent info as is
                        return Mono.just(patientConsentInfo);
                    }

                    // Process the encounters and update the consent periods
                    return Mono.fromCallable(() -> {
                        // Update the consent periods for this patient
                        updateConsentPeriodsForPatient(patientConsentInfo, patientEncounters);
                        return patientConsentInfo;
                    }).subscribeOn(Schedulers.boundedElastic());
                })
        );
    }

    // Helper method to extract patient ID from the consent info map
    private String extractPatientId(Map<String, Map<String, List<ConsentPeriod>>> patientConsentInfo) {
        // Assuming the patient ID is the key of the outermost map
        // Adjust this logic based on the actual structure of your consent info map
        return patientConsentInfo.keySet().stream().findFirst().orElse(null);
    }

    // Helper method to update consent periods for a patient based on their encounters
    private void updateConsentPeriodsForPatient(
            Map<String, Map<String, List<ConsentPeriod>>> patientConsentInfo, List<Encounter> encounters) {

        String patientId = extractPatientId(patientConsentInfo);
        Map<String, List<ConsentPeriod>> consentFields = patientConsentInfo.get(patientId);

        // Iterate over each encounter for the patient
        for (Encounter encounter : encounters) {
            Period encounterPeriod = encounter.getPeriod();

            // Iterate over each consent field and update the consent periods
            for (Map.Entry<String, List<ConsentPeriod>> entry : consentFields.entrySet()) {
                String fieldName = entry.getKey();
                List<ConsentPeriod> consentPeriods = entry.getValue();

                for (ConsentPeriod consentPeriod : consentPeriods) {
                    // Check if the consent period needs to be updated
                    if (isWithinEncounterPeriod(consentPeriod, encounterPeriod)) {
                        updateConsentPeriod(consentPeriod, encounterPeriod);
                    }
                }
            }
        }
    }

    // Helper method to check if the consent period overlaps with the encounter period
    private boolean isWithinEncounterPeriod(ConsentPeriod consentPeriod, Period encounterPeriod) {
        Date consentStart = consentPeriod.getStart();
        Date consentEnd = consentPeriod.getEnd();
        Date encounterStart = encounterPeriod.getStart();
        Date encounterEnd = encounterPeriod.getEnd();

        // Adjust the logic based on your specific requirements
        return (consentStart.before(encounterEnd) || consentStart.equals(encounterEnd)) &&
                (consentEnd.after(encounterStart) || consentEnd.equals(encounterStart));
    }

    // Helper method to update the consent period based on the encounter period
    private void updateConsentPeriod(ConsentPeriod consentPeriod, Period encounterPeriod) {
        Date encounterStart = encounterPeriod.getStart();
        Date encounterEnd = encounterPeriod.getEnd();

        // Update the consent period's start and end dates if necessary
        if (encounterStart.before(consentPeriod.getStart())) {
            consentPeriod.setStart(encounterStart);
        }
        if (encounterEnd.after(consentPeriod.getEnd())) {
            consentPeriod.setEnd(encounterEnd);
        }
    }





}


