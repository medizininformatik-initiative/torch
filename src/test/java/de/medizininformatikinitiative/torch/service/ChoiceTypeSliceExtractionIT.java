package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.BatchExclusions;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionResourceBundle;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
import org.hl7.fhir.r4.model.Age;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Duration;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates a CRTDL whose attributes select choice-type slices typed as Quantity specialisations (Age, Duration)
 * and extracts matching resources with the resulting annotated attribute groups.
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChoiceTypeSliceExtractionIT {

    private static final String CRTDL = """
            {
              "cohortDefinition": {"inclusionCriteria": []},
              "dataExtraction": {
                "attributeGroups": [
                  {
                    "id": "patient",
                    "groupReference": "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/PatientPseudonymisiert",
                    "attributes": []
                  },
                  {
                    "id": "allergy",
                    "groupReference": "https://gematik.de/fhir/isik/StructureDefinition/ISiKAllergieUnvertraeglichkeit",
                    "attributes": [{"attributeRef": "AllergyIntolerance.onset[x]:onsetAge", "mustHave": false}]
                  },
                  {
                    "id": "medicationStatement",
                    "groupReference": "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/MedicationStatement",
                    "attributes": [{"attributeRef": "MedicationStatement.dosage.timing.repeat.bounds[x]:boundsDuration", "mustHave": false}]
                  }
                ]
              }
            }""";

    private static final String PATIENT = """
            {
              "resourceType": "Patient",
              "id": "p1",
              "meta": {"profile": ["https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/PatientPseudonymisiert"]}
            }""";

    private static final String ALLERGY = """
            {
              "resourceType": "AllergyIntolerance",
              "id": "a1",
              "meta": {"profile": ["https://gematik.de/fhir/isik/StructureDefinition/ISiKAllergieUnvertraeglichkeit"]},
              "patient": {"reference": "Patient/p1"},
              "onsetAge": {"value": 5, "unit": "a", "system": "http://unitsofmeasure.org", "code": "a"}
            }""";

    private static final String MEDICATION_STATEMENT = """
            {
              "resourceType": "MedicationStatement",
              "id": "m1",
              "meta": {"profile": ["https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/MedicationStatement"]},
              "subject": {"reference": "Patient/p1"},
              "dosage": [{"timing": {"repeat": {"boundsDuration": {"value": 7, "unit": "d", "system": "http://unitsofmeasure.org", "code": "d"}}}}]
            }""";

    @Autowired
    CrtdlValidatorService validatorService;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ElementCopier copier;

    @Autowired
    Redaction redaction;

    private final IParser parser = FhirContext.forR4Cached().newJsonParser();

    @Test
    void extractsAgeAndDurationChoiceSlices() throws Exception {
        Map<String, AnnotatedAttributeGroup> groups = validatorService
                .validateAndAnnotate(objectMapper.readValue(CRTDL, Crtdl.class))
                .dataExtraction().attributeGroups().stream()
                .collect(Collectors.toMap(AnnotatedAttributeGroup::id, Function.identity()));

        PatientResourceBundle bundle = new PatientResourceBundle("p1");
        bundle.put(parser.parseResource(Patient.class, PATIENT), "patient", true);
        bundle.put(parser.parseResource(AllergyIntolerance.class, ALLERGY), "allergy", true);
        bundle.put(parser.parseResource(MedicationStatement.class, MEDICATION_STATEMENT), "medicationStatement", true);

        ExtractionResourceBundle result = new BatchCopierRedacter(copier, redaction)
                .transformBundle(ExtractionResourceBundle.of(bundle), groups, BatchExclusions.empty());

        Optional<Resource> allergyResult = result.getResource(ExtractionId.fromRelativeUrl("AllergyIntolerance/a1"));
        assertThat(allergyResult).containsInstanceOf(AllergyIntolerance.class);
        AllergyIntolerance allergy = (AllergyIntolerance) allergyResult.get();
        assertThat(allergy.getOnset()).isInstanceOf(Age.class);
        Age onset = (Age) allergy.getOnset();
        assertThat(onset.getValue()).isEqualByComparingTo("5");
        assertThat(onset.getCode()).isEqualTo("a");
        assertThat(onset.getSystem()).isEqualTo("http://unitsofmeasure.org");

        Optional<Resource> statementResult = result.getResource(ExtractionId.fromRelativeUrl("MedicationStatement/m1"));
        assertThat(statementResult).containsInstanceOf(MedicationStatement.class);
        MedicationStatement statement = (MedicationStatement) statementResult.get();
        assertThat(statement.getDosageFirstRep().getTiming().getRepeat().getBounds()).isInstanceOf(Duration.class);
        Duration bounds = (Duration) statement.getDosageFirstRep().getTiming().getRepeat().getBounds();
        assertThat(bounds.getValue()).isEqualByComparingTo("7");
        assertThat(bounds.getCode()).isEqualTo("d");
        assertThat(bounds.getSystem()).isEqualTo("http://unitsofmeasure.org");
    }
}
