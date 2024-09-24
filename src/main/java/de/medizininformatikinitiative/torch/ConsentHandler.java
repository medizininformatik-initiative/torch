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


    private FhirContext ctx;



    @Autowired
    public ConsentHandler(DataStore dataStore, CdsStructureDefinitionHandler cds) {
        this.dataStore = dataStore;
        this.ctx=cds.ctx;
    }


    public Flux<Map<String, List<ConsentPeriod>>> buildingConsentInfo(List<String> batch) {

        // Offload the HTTP call to a bounded elastic scheduler to handle blocking I/O
        Flux<Resource> resources = dataStore.getResources("Consent", FhirSearchBuilder.getConsent(batch))
                .subscribeOn(Schedulers.boundedElastic())  // Ensure HTTP requests are offloaded
                // Error handling in case the HTTP request fails
                .onErrorResume(e -> {
                    logger.error("Error fetching resources for parameters: {}", FhirSearchBuilder.getConsent(batch), e);
                    return Flux.empty();  // Return an empty Flux to continue processing without crashing the pipeline
                });

        // Map to store the patient's consent periods
        Map<String, List<ConsentPeriod>> patientConsentMap = new HashMap<>();

        return resources.map(resource -> {
            try {
                DomainResource domainResource = (DomainResource) resource;

                // Extract consent provisions using FHIRPath
                List<Base> provisionList = extractConsentProvisions(domainResource);

                // Transform to extract patient and consent period information
                ConsentPeriod consentPeriod = transformToConsentPeriod(domainResource, provisionList); // Adjusted to include provisions
                String patient = ResourceUtils.getPatientId(domainResource);

                // Update the map with the patient's consent periods
                patientConsentMap.computeIfAbsent(patient, k -> new ArrayList<>()).add(consentPeriod);

                return patientConsentMap;
            } catch (Exception e) {
                logger.error("Error processing resource ", e);
                throw new RuntimeException(e);
            }
        });
    }

    private List<Base> extractConsentProvisions(DomainResource domainResource) {
        try {
            // Using FHIRPath to extract Encounter.provision.provision elements from the resource


            return ctx.newFhirPath().evaluate(domainResource, "Consent.provision.provision.period",Base.class);
        } catch (Exception e) {
            logger.error("Error extracting provisions with FHIRPath ", e);
            return Collections.emptyList();  // Return an empty list in case of errors
        }
    }

    private ConsentPeriod transformToConsentPeriod(DomainResource domainResource, List<Base> provisionPeriodList) {
        LocalDateTime maxStart = null;
        LocalDateTime minEnd = null;

        // Iterate over the provisions to find the maximum start and minimum end
        for (Base provision : provisionPeriodList) {
            try {
                // Assuming provision has a period with start and end dates
                Period period = (Period) provision;
                LocalDateTime start = period.hasStart() ? LocalDateTime.parse(period.getStart().toString()) : null;
                LocalDateTime end = period.hasEnd() ? LocalDateTime.parse(period.getEnd().toString()) : null;

                // Update maxStart and minEnd based on current provision
                if (start != null) {
                    if (maxStart == null || start.isAfter(maxStart)) {
                        maxStart = start;
                    }
                }

                if (end != null) {
                    if (minEnd == null || end.isBefore(minEnd)) {
                        minEnd = end;
                    }
                }
            } catch (Exception e) {
                logger.error("Error processing provision period", e);
            }
        }

        // Handle the case where no valid periods were found
        if (maxStart == null || minEnd == null) {
            throw new IllegalStateException("No valid start or end dates found in provisions");
        }

        // Create and return the ConsentPeriod with the calculated max start and min end
        ConsentPeriod consentPeriod = new ConsentPeriod();
        consentPeriod.setStart(maxStart);
        consentPeriod.setEnd(minEnd);

        return consentPeriod;
    }









}


