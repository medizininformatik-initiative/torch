import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.util.FHIRNavigator;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationComponentComponent;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class NavigatorTest {

    private FhirContext ctx = FhirContext.forR4();

    @Test
    public void testPatient() {
        FHIRNavigator navigator = new FHIRNavigator();

        String patientJson = "{ \"resourceType\": \"Patient\", \"address\": [ { \"line\": [ \"123 Main St\" ] } ] }";
        IParser parser = ctx.newJsonParser();
        Patient patient = parser.parseResource(Patient.class, patientJson);
        Base element = navigator.navigateToElement(patient, "Patient.address.line");
        assertEquals("123 Main St", element.primitiveValue());
    }

    @Test
    public void testSlicing(){
        FileInputStream fis;
        try {
            fis = new FileInputStream("src/test/resources/InputResources/Observation/slicing.json");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        FHIRNavigator navigator = new FHIRNavigator();
        IParser parser = ctx.newJsonParser();
        Observation observation = (Observation) parser.parseResource(fis);
        Base  element1 = navigator.navigateToElement(observation, "Observation.effectiveDateTime");
        assertNotNull(element1);
        assertEquals("2023-06-01T12:00:00Z", element1.primitiveValue());
        Base  element2 = navigator.navigateToElement(observation, "Observation.effectivePeriod.start");
        assertNotNull(element2);
        assertEquals("2023-05-01T00:00:00Z", element2.primitiveValue());

    }
    @Test
    public void testSlicing2() {
        FHIRNavigator navigator = new FHIRNavigator();

        String observationJson = "{ \"resourceType\": \"Observation\", " +
                "\"component\": [ " +
                "{ \"code\": { \"coding\": [ { \"system\": \"http://loinc.org\", \"code\": \"8480-6\", \"display\": \"Systolic blood pressure\" } ] }, " +
                "\"valueQuantity\": { \"value\": 120, \"unit\": \"mmHg\" } }, " +
                "{ \"code\": { \"coding\": [ { \"system\": \"http://loinc.org\", \"code\": \"8462-4\", \"display\": \"Diastolic blood pressure\" } ] }, " +
                "\"valueQuantity\": { \"value\": 80, \"unit\": \"mmHg\" } } " +
                "] }";
        IParser parser = ctx.newJsonParser();
        Observation observation = parser.parseResource(Observation.class, observationJson);

        // Test navigating to the systolic blood pressure value
        Base  element1 = navigator.navigateToElement(observation, "Observation.component.valueQuantity.value");
        assertNotNull(element1);
        assertEquals("120.0", element1.primitiveValue());

        // Test navigating to the diastolic blood pressure value
        Base  element2 = navigator.navigateToElement(observation, "Observation.component.valueQuantity.value");
        assertNotNull(element2);
        assertEquals("80.0", element2.primitiveValue());
    }

}