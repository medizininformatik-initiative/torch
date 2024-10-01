package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.rest.FhirController;
import de.medizininformatikinitiative.torch.util.ConsentCodeMapper;
import de.medizininformatikinitiative.torch.util.ConsentPeriod;
import de.medizininformatikinitiative.torch.util.ResourceReader;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/*@Testcontainers
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AppConfig.class)
@SpringBootTest(classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
*/

@SpringBootTest(classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ConsentHandlerIT extends AbstractIT {

    private static final String RESOURCE_PATH_PREFIX = "src/test/resources/";

    @LocalServerPort
    private int port;

    @Autowired
    FhirController fhirController;

    @Autowired ConsentHandler consentHandler;

    @Autowired
    public ConsentHandlerIT(@Qualifier("fhirClient") WebClient webClient,
                            @Qualifier("flareClient") WebClient flareClient, ResourceTransformer transformer, DataStore dataStore, CdsStructureDefinitionHandler cds, FhirContext context, BundleCreator bundleCreator, ObjectMapper objectMapper) {
        super(webClient, flareClient, transformer, dataStore, cds, context, bundleCreator, objectMapper);
    }

    @Test
    public void testHandler() {
        List<String> strings = new ArrayList<>();
        strings.add("VHF00006");

        // Reading resource
        Resource observation = null;
        try {
            observation = ResourceReader.readResource("src/test/resources/InputResources/Observation/Observation_lab.json");
            Resource consent = ResourceReader.readResource("src/test/resources/InputResources/Consent/VHF006_Consent.json");

            // Build consent information as a Flux
            Flux<Map<String, Map<String, List<ConsentPeriod>>>> consentInfoFlux = consentHandler.buildingConsentInfo("yes-yes-yes-yes", strings);

            // Collect the Flux into a List of Maps, without altering its structure
            List<Map<String, Map<String, List<ConsentPeriod>>>> consentInfoList = consentInfoFlux.collectList().block();

            // Assuming you need the first element from the list
            Map<String, Map<String, List<ConsentPeriod>>> consentInfo = consentInfoList.get(0);

            // Now pass the Map (instead of the Flux) to checkConsent
            Boolean consentInfoResult = consentHandler.checkConsent((DomainResource) observation, consentInfo);

            assertTrue(consentInfoResult);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    @Test
    public void testHandlerFail() {
        List<String> strings = new ArrayList<>();
        strings.add("VHF00006");

        // Reading resource
        Resource observation = null;
        try {
            observation = ResourceReader.readResource("src/test/resources/InputResources/Observation/Observation_lab.json");
            Resource consent = ResourceReader.readResource("src/test/resources/InputResources/Consent/VHF006_Consent.json");
            DateTimeType time= new DateTimeType("2020-01-01T00:00:00+01:00");
            ((Observation)observation).setEffective(time);
            // Build consent information as a Flux
            Flux<Map<String, Map<String, List<ConsentPeriod>>>> consentInfoFlux = consentHandler.buildingConsentInfo("yes-yes-yes-yes", strings);

            // Collect the Flux into a List of Maps, without altering its structure
            List<Map<String, Map<String, List<ConsentPeriod>>>> consentInfoList = consentInfoFlux.collectList().block();

            // Assuming you need the first element from the list
            Map<String, Map<String, List<ConsentPeriod>>> consentInfo = consentInfoList.get(0);

            // Now pass the Map (instead of the Flux) to checkConsent
            Boolean consentInfoResult = consentHandler.checkConsent((DomainResource) observation, consentInfo);

            assertFalse(consentInfoResult);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }






}
