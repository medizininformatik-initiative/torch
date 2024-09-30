package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.Attribute;
import de.medizininformatikinitiative.torch.model.AttributeGroup;
import de.medizininformatikinitiative.torch.model.Crtdl;
import de.medizininformatikinitiative.torch.util.*;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Consent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Component
public class ConsentHandler {

    private static final Logger logger = LoggerFactory.getLogger(ConsentHandler.class);
    private final DataStore dataStore;



    private final ConsentCodeMapper mapper;
    private final ConsentProcessor processor;

    @Autowired
    public ConsentHandler(DataStore dataStore, CdsStructureDefinitionHandler cds, ConsentCodeMapper mapper) {
        this.dataStore = dataStore;
        this.mapper = mapper;
        this.processor=new ConsentProcessor(cds.ctx);
    }



    public Flux<Map<String, Map<String, List<ConsentPeriod>>>> buildingConsentInfo(String key, List<String> batch) {
        Set<String> codes = mapper.getRelevantCodes(key);

        // Offload the HTTP call to a bounded elastic scheduler to handle blocking I/O
        Flux<Resource> resources = dataStore.getResources("Consent", FhirSearchBuilder.getConsent(batch))
                .subscribeOn(Schedulers.boundedElastic())  // Ensure HTTP requests are offloaded
                // Error handling in case the HTTP request fails
                .onErrorResume(e -> {
                    logger.error("Error fetching resources for parameters: {}", FhirSearchBuilder.getConsent(batch), e);
                    return Flux.empty();  // Return an empty Flux to continue processing without crashing the pipeline
                });

        // Map to store the patient's consent periods by patient ID
        ConcurrentMap<String, Map<String, List<ConsentPeriod>>> patientConsentMap = new ConcurrentHashMap<>();

        // Process each resource
        return resources.map(resource -> {
                    try {
                        DomainResource domainResource = (DomainResource) resource;

                        // Extract consent periods for the domain resource and valid codes
                        Map<String, List<ConsentPeriod>> consents = processor.transformToConsentPeriodByCode(domainResource, codes);
                        String patient = ResourceUtils.getPatientId(domainResource);

                        // Update the map with the patient's consent periods (handling multiple periods per code)
                        patientConsentMap.computeIfAbsent(patient, k -> new ConcurrentHashMap<>());

                        // Iterate through the consents (codes and their lists of ConsentPeriods)
                        consents.forEach((code, newConsentPeriods) -> {
                            // For each patient and code, either update the existing list of periods or create a new list
                            patientConsentMap.get(patient)
                                    .computeIfAbsent(code, x -> new ArrayList<>())
                                    .addAll(newConsentPeriods); // Add all new periods for this code
                        });

                        return patientConsentMap;
                    } catch (Exception e) {
                        logger.error("Error processing resource", e);
                        throw new RuntimeException(e);
                    }
                }).collectList() // Collect all the maps into a list
                .flatMapMany(list -> Flux.fromIterable(list)) // Convert list back to Flux
                .map(map -> new HashMap<>(map));  // Return the final map in the desired format
    }















}


