package de.medizininformatikinitiative.torch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.medizininformatikinitiative.torch.consent.ConsentEvaluator;
import de.medizininformatikinitiative.torch.consent.ConsentFormatException;
import de.medizininformatikinitiative.torch.consent.mii.ConsentAdjuster;
import de.medizininformatikinitiative.torch.consent.mii.ConsentCalculator;
import de.medizininformatikinitiative.torch.consent.mii.ConsentFetcher;
import de.medizininformatikinitiative.torch.consent.mii.CrtdlConsentValidator;
import de.medizininformatikinitiative.torch.consent.mii.MiiConsentEvaluator;
import de.medizininformatikinitiative.torch.consent.mii.model.ConsentCodeConfig;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.Code;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.DataExtraction;
import de.medizininformatikinitiative.torch.model.crtdl.Filter;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import de.medizininformatikinitiative.torch.util.CompiledStructureDefinition;
import de.medizininformatikinitiative.torch.util.FhirPathBuilder;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StructureDefinition;
import ca.uhn.fhir.context.FhirContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class CrtdlValidatorServiceTest {
    private final IntegrationTestSetup itSetup = new IntegrationTestSetup();
    private final CrtdlValidatorService validatorService = new CrtdlValidatorService(itSetup.structureDefinitionHandler(),
            new StandardAttributeGenerator(new CompartmentManager("compartmentdefinition-patient.json"), itSetup.structureDefinitionHandler()),
            consentEvaluator(new ConsentCodeConfig(List.of())), itSetup.fhirPathBuilder(), itSetup.fhirContext());

    /**
     * A real {@link MiiConsentEvaluator} wired with the given code config; only {@code validate()} is
     * exercised by this test class, so the fetch/adjust/calculate collaborators are unused mocks.
     */
    private static ConsentEvaluator consentEvaluator(ConsentCodeConfig consentCodeConfig) {
        return new MiiConsentEvaluator(new CrtdlConsentValidator(), consentCodeConfig,
                org.mockito.Mockito.mock(ConsentFetcher.class),
                org.mockito.Mockito.mock(ConsentAdjuster.class),
                org.mockito.Mockito.mock(ConsentCalculator.class));
    }

    @Mock
    StructureDefinitionHandler mockHandler;
    @Mock
    StandardAttributeGenerator mockAttributeGenerator;
    @Mock
    FhirPathBuilder mockFhirPathBuilder;
    JsonNode node = JsonNodeFactory.instance.objectNode();

    AttributeGroup patientGroup = new AttributeGroup("patientGroupId", "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/PatientPseudonymisiert", List.of(), List.of());

    CrtdlValidatorServiceTest() throws IOException {
    }


    @Test
    void consentViolated() throws JsonProcessingException {
        String json = """
                {
                  "exclusionCriteria": [
                    [
                      {
                        "context": {
                          "code": "Einwilligung",
                          "system": "fdpg.mii.cds"
                        },
                        "termCodes": [
                          { "system": "s1", "code": "A2" }
                        ]
                      }
                    ]
                  ]
                }""";
        ObjectMapper mapper = new ObjectMapper();
        JsonNode cohortDefinition = mapper.readTree(json);
        Crtdl crtdl = new Crtdl(cohortDefinition, new DataExtraction(List.of(patientGroup, new AttributeGroup("test", "unknown.test", List.of(), List.of()))));

        assertThatThrownBy(() -> validatorService.validateAndAnnotate(crtdl)).isInstanceOf(ConsentFormatException.class)
                .hasMessageContaining("Exclusion criteria must not contain Einwilligung consent codes.");
    }

    @Test
    void nonDomainResourceProfile() {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup,
                new AttributeGroup("binaryGroup", "https://gematik.de/fhir/isik/StructureDefinition/ISiKBinary", List.of(), List.of()))));

        assertThatThrownBy(() -> validatorService.validateAndAnnotate(crtdl)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("Binary")
                .hasMessageContaining("not a DomainResource");
    }

    @Test
    void moreThanOnePatientGroup() {
        AttributeGroup secondPatientGroup = new AttributeGroup("patientGroupId2", "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/PatientPseudonymisiert", List.of(), List.of());
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup, secondPatientGroup)));

        assertThatThrownBy(() -> validatorService.validateAndAnnotate(crtdl)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("More than one Patient Attribute Group");
    }

    @Test
    void unknownProfile() {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup, new AttributeGroup("test", "unknown.test", List.of(), List.of()))));

        assertThatThrownBy(() -> validatorService.validateAndAnnotate(crtdl)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unknown Profile: unknown.test");

    }


    @Test
    void unknownAttribute() {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup, new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab", List.of(new Attribute("Condition.unknown", false)), List.of()))));

        assertThatThrownBy(() -> validatorService.validateAndAnnotate(crtdl)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unknown Attribute Condition.unknown in group test");

    }

    @Test
    void referenceWithoutLinkedGroups() {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup, new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab", List.of(new Attribute("Observation.encounter", false)), List.of()))));

        assertThatThrownBy(() -> validatorService.validateAndAnnotate(crtdl)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("Reference Attribute Observation.encounter without linked Groups in group test");

    }

    @Test
    void BackBoneElementWithReference() throws ValidationException, ConsentFormatException {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup, new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung", List.of(new Attribute("Encounter.diagnosis", false, List.of("patientGroupId"))), List.of()))));

        var validatedCrtdl = validatorService.validateAndAnnotate(crtdl);

        assertThat(validatedCrtdl).isNotNull();
        assertThat(validatedCrtdl.dataExtraction().attributeGroups().get(1).attributes()).isEqualTo(
                List.of(
                        new AnnotatedAttribute("Encounter.id", "Encounter.id", false),
                        new AnnotatedAttribute("Encounter.meta.profile", "Encounter.meta.profile", false),
                        new AnnotatedAttribute("Encounter.subject", "Encounter.subject", false, List.of("patientGroupId")),
                        new AnnotatedAttribute("Encounter.diagnosis", "Encounter.diagnosis", false, List.of("patientGroupId"))
                ));

    }

    @Test
    void validInput_withoutFilter() throws ValidationException, ConsentFormatException {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup)));

        var validatedCrtdl = validatorService.validateAndAnnotate(crtdl);

        assertThat(validatedCrtdl).isNotNull();
        assertThat(validatedCrtdl.dataExtraction().attributeGroups().getFirst().attributes()).isEqualTo(
                List.of(
                        new AnnotatedAttribute("Patient.id", "Patient.id", false),
                        new AnnotatedAttribute("Patient.meta.profile", "Patient.meta.profile", false)
                ));
    }

    @Test
    void duplicateAttribute() {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup,
                new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab",
                        List.of(new Attribute("Observation.status", false), new Attribute("Observation.status", false)), List.of()))));

        assertThatThrownBy(() -> validatorService.validateAndAnnotate(crtdl)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("Duplicate attribute Observation.status in group test");
    }

    @Test
    void standardAttributeExplicitlySpecified_isSkipped() throws ValidationException, ConsentFormatException {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup,
                new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab",
                        List.of(new Attribute("Observation.id", false)), List.of()))));

        var result = validatorService.validateAndAnnotate(crtdl);

        assertThat(result.dataExtraction().attributeGroups().get(1).attributes()).contains(
                new AnnotatedAttribute("Observation.id", "Observation.id", false));
    }

    @Test
    void standardCompartmentRefAttributeExplicitlySpecified_isSkipped() throws ValidationException, ConsentFormatException {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup,
                new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab",
                        List.of(new Attribute("Observation.subject", false, List.of("patientGroupId"))), List.of()))));

        var result = validatorService.validateAndAnnotate(crtdl);

        assertThat(result.dataExtraction().attributeGroups().get(1).attributes()).contains(
                new AnnotatedAttribute("Observation.subject", "Observation.subject", false, List.of("patientGroupId")));
    }

    @Test
    void standardAttributeExplicitlySpecifiedWithMustHaveTrue_failsValidation() {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup,
                new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab",
                        List.of(new Attribute("Observation.id", true)), List.of()))));

        assertThatThrownBy(() -> validatorService.validateAndAnnotate(crtdl)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("Standard attribute Observation.id in group test cannot be declared with mustHave: true");
    }

    @Test
    void sliceSubElement_addsDiscriminatorAttribute() throws ValidationException, ConsentFormatException {
        AttributeGroup patientGroupWithAddress = new AttributeGroup(
                "patientGroupId",
                "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/PatientPseudonymisiert",
                List.of(new Attribute("Patient.address:Strassenanschrift.postalCode", false)),
                List.of()
        );
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroupWithAddress)));

        var validatedCrtdl = validatorService.validateAndAnnotate(crtdl);

        List<String> refs = validatedCrtdl.dataExtraction().attributeGroups().getFirst().attributes()
                .stream().map(AnnotatedAttribute::attributeRef).toList();
        assertThat(refs).contains(
                "Patient.address:Strassenanschrift.postalCode",
                "Patient.address:Strassenanschrift.type"
        );
    }

    @Test
    void sliceDiscriminatorAttributes_parentDefMissing_skips() throws Exception {
        // SD has the sliced attribute but NOT the parent element — parentDef.isEmpty() → continue
        ElementDefinition attrEd = new ElementDefinition();
        attrEd.setId("Observation.component:someSlice.code");
        attrEd.addType(new ElementDefinition.TypeRefComponent().setCode("CodeableConcept"));

        StructureDefinition sd = new StructureDefinition();
        sd.setType("Observation");
        sd.getSnapshot().addElement(attrEd);
        CompiledStructureDefinition csd = new CompiledStructureDefinition(sd,
                Map.of("Observation.component:someSlice.code", attrEd));

        StructureDefinition patientSd = new StructureDefinition();
        patientSd.setType("Patient");
        CompiledStructureDefinition patientCsd = CompiledStructureDefinition.fromStructureDefinition(patientSd);

        when(mockHandler.getDefinition("patient-profile")).thenReturn(Optional.of(patientCsd));
        when(mockHandler.getDefinition("obs-profile")).thenReturn(Optional.of(csd));

        AnnotatedAttributeGroup stub = new AnnotatedAttributeGroup("g", "Observation", "obs-profile", List.of(), List.of());
        when(mockAttributeGenerator.generate(any(), any())).thenReturn(stub);
        when(mockFhirPathBuilder.resolve(eq("Observation.component:someSlice.code"), any()))
                .thenReturn(new String[]{"Observation.component", "Observation.component"});

        CrtdlValidatorService svc = new CrtdlValidatorService(mockHandler, mockAttributeGenerator, consentEvaluator(new ConsentCodeConfig(List.of())), mockFhirPathBuilder, FhirContext.forR4());

        AttributeGroup patientGroup = new AttributeGroup("patientGroupId", "patient-profile", List.of(), List.of());
        AttributeGroup obsGroup = new AttributeGroup("obsGroupId", "obs-profile",
                List.of(new Attribute("Observation.component:someSlice.code", false)), List.of());
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup, obsGroup)));

        var result = svc.validateAndAnnotate(crtdl);
        List<String> refs = result.dataExtraction().attributeGroups().stream()
                .flatMap(g -> g.attributes().stream())
                .map(AnnotatedAttribute::attributeRef)
                .toList();
        // discriminator attribute for Observation.component is not added since parentDef is missing
        assertThat(refs).contains("Observation.component:someSlice.code");
        assertThat(refs).doesNotContain("Observation.component");
    }

    @Test
    void sliceDiscriminatorAttributes_parentHasNoSlicing_skips() throws Exception {
        // SD has both the parent and the sliced attribute, but parent has no slicing → !hasSlicing() → continue
        ElementDefinition parentEd = new ElementDefinition();
        parentEd.setId("Observation.component");
        parentEd.addType(new ElementDefinition.TypeRefComponent().setCode("BackboneElement"));
        // intentionally no slicing set

        ElementDefinition attrEd = new ElementDefinition();
        attrEd.setId("Observation.component:someSlice.code");
        attrEd.addType(new ElementDefinition.TypeRefComponent().setCode("CodeableConcept"));

        StructureDefinition sd = new StructureDefinition();
        sd.setType("Observation");
        sd.getSnapshot().addElement(parentEd);
        sd.getSnapshot().addElement(attrEd);
        CompiledStructureDefinition csd = CompiledStructureDefinition.fromStructureDefinition(sd);

        StructureDefinition patientSd = new StructureDefinition();
        patientSd.setType("Patient");
        CompiledStructureDefinition patientCsd = CompiledStructureDefinition.fromStructureDefinition(patientSd);

        when(mockHandler.getDefinition("patient-profile")).thenReturn(Optional.of(patientCsd));
        when(mockHandler.getDefinition("obs-profile")).thenReturn(Optional.of(csd));

        AnnotatedAttributeGroup stub = new AnnotatedAttributeGroup("g", "Observation", "obs-profile", List.of(), List.of());
        when(mockAttributeGenerator.generate(any(), any())).thenReturn(stub);
        when(mockFhirPathBuilder.resolve(eq("Observation.component:someSlice.code"), any()))
                .thenReturn(new String[]{"Observation.component", "Observation.component"});

        CrtdlValidatorService svc = new CrtdlValidatorService(mockHandler, mockAttributeGenerator, consentEvaluator(new ConsentCodeConfig(List.of())), mockFhirPathBuilder, FhirContext.forR4());

        AttributeGroup patientGroup = new AttributeGroup("patientGroupId", "patient-profile", List.of(), List.of());
        AttributeGroup obsGroup = new AttributeGroup("obsGroupId", "obs-profile",
                List.of(new Attribute("Observation.component:someSlice.code", false)), List.of());
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup, obsGroup)));

        var result = svc.validateAndAnnotate(crtdl);
        List<String> refs = result.dataExtraction().attributeGroups().stream()
                .flatMap(g -> g.attributes().stream())
                .map(AnnotatedAttribute::attributeRef)
                .toList();
        assertThat(refs).contains("Observation.component:someSlice.code");
        assertThat(refs).doesNotContain("Observation.component");
    }

    @Test
    void sliceDiscriminatorAttributes_fhirPathThrows_logsAndSkips() throws Exception {
        // FhirPathBuilder throws when resolving the discriminator field — caught, logged, discriminator is omitted
        CrtdlValidatorService svc = new CrtdlValidatorService(
                itSetup.structureDefinitionHandler(),
                new StandardAttributeGenerator(new CompartmentManager("compartmentdefinition-patient.json"), itSetup.structureDefinitionHandler()),
                consentEvaluator(new ConsentCodeConfig(List.of())),
                mockFhirPathBuilder, itSetup.fhirContext());

        String postalCode = "Patient.address:Strassenanschrift.postalCode";
        String discriminator = "Patient.address:Strassenanschrift.type";
        when(mockFhirPathBuilder.resolve(eq(postalCode), any()))
                .thenReturn(new String[]{"Patient.address.where(type = 'postal').postalCode", "Patient.address.postalCode"});
        when(mockFhirPathBuilder.resolve(eq(discriminator), any()))
                .thenThrow(new RuntimeException("simulated FhirPath failure"));

        AttributeGroup patientGroupWithAddress = new AttributeGroup(
                "patientGroupId",
                "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/PatientPseudonymisiert",
                List.of(new Attribute(postalCode, false)),
                List.of()
        );
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroupWithAddress)));

        var result = svc.validateAndAnnotate(crtdl);
        List<String> refs = result.dataExtraction().attributeGroups().getFirst().attributes()
                .stream().map(AnnotatedAttribute::attributeRef).toList();
        assertThat(refs).contains(postalCode);
        assertThat(refs).doesNotContain(discriminator);
    }

    @Test
    void validInput_withFilter() throws ValidationException, ConsentFormatException {
        Crtdl crtdl = new Crtdl(node, new DataExtraction(List.of(patientGroup,
                new AttributeGroup("test", "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab",
                        List.of(), List.of(new Filter("token", "code", List.of(new Code("some-system", "some-code"))))))));

        var validatedCrtdl = validatorService.validateAndAnnotate(crtdl);

        assertThat(validatedCrtdl).isNotNull();
        assertThat(validatedCrtdl.dataExtraction().attributeGroups().get(1).attributes()).isEqualTo(
                List.of(
                        new AnnotatedAttribute("Observation.id", "Observation.id", false),
                        new AnnotatedAttribute("Observation.meta.profile", "Observation.meta.profile", false),
                        new AnnotatedAttribute("Observation.subject", "Observation.subject", false, List.of("patientGroupId"))
                ));
    }


}
