package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.TestPatientLinkResolver;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class StandardAttributeGeneratorTest {

    private StandardAttributeGenerator standardAttributeGenerator;

    @BeforeEach
    void setUp() throws IOException {
        var compartmentManager = new CompartmentManager("compartmentdefinition-patient.json");
        var integrationTestSetup = new IntegrationTestSetup();
        standardAttributeGenerator = new StandardAttributeGenerator(compartmentManager, integrationTestSetup.structureDefinitionHandler(),
                TestPatientLinkResolver.get());
    }

    @Test
    void patient() throws ValidationException {
        var attributeGroup = new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/PatientPseudonymisiert", List.of(new Attribute("Patient.name", false)), List.of());

        var standardAddedGroup = standardAttributeGenerator.generate(attributeGroup, "patientGroup");

        assertThat(standardAddedGroup.hasMustHave()).isFalse();
        assertThat(standardAddedGroup.attributes()).containsExactly(
                new AnnotatedAttribute("Patient.id", "Patient.id", false),
                new AnnotatedAttribute("Patient.meta.profile", "Patient.meta.profile", false)
        );
    }

    @Test
    void consent() throws ValidationException {
        var attributeGroup = new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/modul-consent/StructureDefinition/mii-pr-consent-einwilligung", List.of(new Attribute("Consent.identifier", false)), List.of());

        var standardAddedGroup = standardAttributeGenerator.generate(attributeGroup, "patientGroup");

        assertThat(standardAddedGroup.hasMustHave()).isFalse();
        assertThat(standardAddedGroup.attributes()).containsExactly(
                new AnnotatedAttribute("Consent.id", "Consent.id", false),
                new AnnotatedAttribute("Consent.meta.profile", "Consent.meta.profile", false),
                new AnnotatedAttribute("Consent.patient", "Consent.patient", false, List.of("patientGroup"))
        );
    }

    @Test
    void observation() throws ValidationException {
        var attributeGroup = new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab", List.of(new Attribute("Observation.identifier", false)), List.of());

        var standardAddedGroup = standardAttributeGenerator.generate(attributeGroup, "group1");

        assertThat(standardAddedGroup.hasMustHave()).isFalse();
        assertThat(standardAddedGroup.attributes()).containsExactly(
                new AnnotatedAttribute("Observation.id", "Observation.id", false),
                new AnnotatedAttribute("Observation.meta.profile", "Observation.meta.profile", false),
                new AnnotatedAttribute("Observation.subject", "Observation.subject", false, List.of("group1"))
        );
    }

    @Test
    void patientLinkKeepsLinkedGroupsDeclaredInCrtdl() throws ValidationException {
        var attributeGroup = new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab",
                List.of(new Attribute("Observation.subject", false, List.of("deviceGroup", "patientGroup"))), List.of());

        var standardAddedGroup = standardAttributeGenerator.generate(attributeGroup, "patientGroup");

        assertThat(standardAddedGroup.attributes()).contains(
                new AnnotatedAttribute("Observation.subject", "Observation.subject", false, List.of("patientGroup", "deviceGroup")));
    }

    @Test
    void patientLinkKeepsMustHaveDeclaredInCrtdl() throws ValidationException {
        var attributeGroup = new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab",
                List.of(new Attribute("Observation.subject", true)), List.of());

        var standardAddedGroup = standardAttributeGenerator.generate(attributeGroup, "patientGroup");

        assertThat(standardAddedGroup.attributes()).contains(
                new AnnotatedAttribute("Observation.subject", "Observation.subject", true, List.of("patientGroup")));
    }

    @Test
    void patientLinkIsNotMustHaveWhenCrtdlDeclaresItWithoutMustHave() throws ValidationException {
        var attributeGroup = new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab",
                List.of(new Attribute("Observation.subject", false, List.of("patientGroup"))), List.of());

        var standardAddedGroup = standardAttributeGenerator.generate(attributeGroup, "patientGroup");

        assertThat(standardAddedGroup.attributes()).contains(
                new AnnotatedAttribute("Observation.subject", "Observation.subject", false, List.of("patientGroup")));
    }

    @ParameterizedTest
    @CsvSource({
            "https://gematik.de/fhir/isik/StructureDefinition/ISiKVersicherungsverhaeltnisGesetzlich, Coverage.beneficiary",
            "https://gematik.de/fhir/isik/StructureDefinition/ISiKAbrechnungsfall, Account.subject",
            "https://www.medizininformatik-initiative.de/fhir/ext/modul-bildgebung/StructureDefinition/mii-pr-bildgebung-koerperstruktur, BodyStructure.patient",
            "https://www.medizininformatik-initiative.de/fhir/ext/modul-molgen/StructureDefinition/familienanamnese, FamilyMemberHistory.patient",
            "https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/StructureDefinition/mii-pr-onko-nebenwirkung-adverse-event, AdverseEvent.subject"
    })
    void patientLinkFromCompartmentSearchParam(String profile, String patientLink) throws ValidationException {
        var attributeGroup = new AttributeGroup("test", profile, List.of(), List.of());

        var standardAddedGroup = standardAttributeGenerator.generate(attributeGroup, "patientGroup");

        assertThat(standardAddedGroup.attributes())
                .contains(new AnnotatedAttribute(patientLink, patientLink, false, List.of("patientGroup")))
                .filteredOn(attribute -> !attribute.linkedGroups().isEmpty())
                .hasSize(1);
    }

    /**
     * For every loaded profile where the previous fixed {@code patient}/{@code subject} lookup found an element, the
     * patient link derived from the compartment search parameter is the same.
     */
    @Test
    void patientLinkUnchangedWhereSubjectOrPatientWorked() throws IOException {
        var compartmentManager = new CompartmentManager("compartmentdefinition-patient.json");
        var structureDefinitionHandler = new IntegrationTestSetup().structureDefinitionHandler();
        var objectMapper = new ObjectMapper();
        int compared = 0;

        try (var files = Files.list(Path.of("ontology/structureDefinitions"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                var json = objectMapper.readTree(file.toFile());
                String type = json.path("type").asText();
                var definition = structureDefinitionHandler.getDefinition(json.path("url").asText());
                if (definition.isEmpty() || !compartmentManager.isInCompartment(type) || "Patient".equals(type)) {
                    continue;
                }
                Set<String> previous = Stream.of(type + ".patient", type + ".subject")
                        .filter(path -> definition.get().elementDefinitionById(path).isPresent())
                        .collect(Collectors.toSet());
                if (previous.isEmpty()) {
                    continue;
                }

                Set<String> refs = new HashSet<>(standardAttributeGenerator.standardAttributeRefs(type, definition.get()));
                refs.removeAll(Set.of(type + ".id", type + ".meta.profile"));

                assertThat(refs).as(file.getFileName().toString()).isEqualTo(previous);
                compared++;
            }
        }
        assertThat(compared).isPositive();
    }

    @Test
    void coreCase() throws ValidationException {
        var attributeGroup = new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/Medication", List.of(), List.of());

        var standardAddedGroup = standardAttributeGenerator.generate(attributeGroup, "patientGroup");

        assertThat(standardAddedGroup.hasMustHave()).isFalse();
        assertThat(standardAddedGroup.attributes()).containsExactly(
                new AnnotatedAttribute("Medication.id", "Medication.id", false),
                new AnnotatedAttribute("Medication.meta.profile", "Medication.meta.profile", false)
        );
    }

    @Test
    void defaultCase() throws ValidationException {
        var attributeGroup = new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose", List.of(new Attribute("Condition.code", false)), List.of());

        var standardAddedGroup = standardAttributeGenerator.generate(attributeGroup, "patientGroup");

        assertThat(standardAddedGroup.hasMustHave()).isFalse();
        assertThat(standardAddedGroup.attributes()).containsExactly(
                new AnnotatedAttribute("Condition.id", "Condition.id", false),
                new AnnotatedAttribute("Condition.meta.profile", "Condition.meta.profile", false),
                new AnnotatedAttribute("Condition.subject", "Condition.subject", false, List.of("patientGroup"))
        );
    }
}
