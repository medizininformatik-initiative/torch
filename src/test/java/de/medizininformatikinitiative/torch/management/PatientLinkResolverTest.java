package de.medizininformatikinitiative.torch.management;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import org.hl7.fhir.r4.model.Account;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.BodyStructure;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.FamilyMemberHistory;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.RelatedPerson;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientLinkResolverTest {

    private static CompartmentManager compartmentManager;
    private static PatientLinkResolver resolver;

    @BeforeAll
    static void setUp() throws IOException {
        compartmentManager = new CompartmentManager("compartmentdefinition-patient.json");
        resolver = new PatientLinkResolver(compartmentManager, "search-parameters.json");
    }

    @Nested
    class SearchParam {

        @ParameterizedTest
        @CsvSource({
                "Patient, _id",
                "Observation, patient",
                "Encounter, patient",
                "Consent, patient",
                "Coverage, patient",
                "AdverseEvent, subject",
                "SupplyRequest, subject",
                "Group, member",
                "Schedule, actor"
        })
        void searchParam(String resourceType, String expected) {
            assertThat(resolver.searchParam(resourceType)).isEqualTo(expected);
        }

        @Test
        void everyCompartmentTypeHasSearchParam() {
            assertThat(compartmentManager.getCompartment()).allSatisfy(resourceType ->
                    assertThat(resolver.searchParam(resourceType)).isNotBlank());
        }

        /**
         * Before, every type except {@code Patient} was searched by {@code patient}; only types lacking that parameter
         * may differ.
         */
        @Test
        void keepsPatientParamWhereItExists() {
            var typesWithoutPatientParam = Set.of("Patient", "AdverseEvent", "Group", "Schedule", "SupplyRequest");

            assertThat(compartmentManager.getCompartment())
                    .filteredOn(resourceType -> !typesWithoutPatientParam.contains(resourceType))
                    .isNotEmpty()
                    .allSatisfy(resourceType -> assertThat(resolver.searchParam(resourceType)).isEqualTo("patient"));
        }

        @Test
        void typeOutsideCompartment() {
            assertThatThrownBy(() -> resolver.searchParam("Medication"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Medication");
        }

        @Test
        void compartmentTypeWithoutPatientReferenceParamFailsConstruction() throws IOException {
            var compartment = new CompartmentManager("compartmentdefinition-observation-adverse-event.json");

            assertThatThrownBy(() -> new PatientLinkResolver(compartment, "search-parameters-observation-only.json"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageEndingWith(": [AdverseEvent]");
        }
    }

    @Nested
    class ElementPaths {

        /**
         * Account has a relative expression shared by several base types, Encounter an expression that is not a plain
         * element path and Observation an expression without a part for the type; Condition is usable.
         */
        @Test
        void unusableExpressionsFailConstruction() throws IOException {
            var compartment = new CompartmentManager("compartmentdefinition-unusable-expressions.json");

            assertThatThrownBy(() -> new PatientLinkResolver(compartment, "search-parameters-unusable-expressions.json"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageEndingWith(": [Account, Encounter, Observation]");
        }

        @ParameterizedTest
        @CsvSource({
                "Observation, Observation.subject",
                "Coverage, Coverage.beneficiary",
                "Account, Account.subject",
                "BodyStructure, BodyStructure.patient",
                "AdverseEvent, AdverseEvent.subject",
                "SupplyRequest, SupplyRequest.deliverTo",
                "Group, Group.member.entity",
                "Appointment, Appointment.participant.actor"
        })
        void singlePath(String resourceType, String expected) {
            assertThat(resolver.elementPaths(resourceType)).containsExactly(expected);
        }

        @Test
        void unionOfSeveralPaths() {
            assertThat(resolver.elementPaths("AuditEvent"))
                    .containsExactly("AuditEvent.agent.who", "AuditEvent.entity.what");
        }

        @Test
        void patientHasNoPath() {
            assertThat(resolver.elementPaths("Patient")).isEmpty();
        }

        /**
         * The previous lookup read a single-valued {@code subject} via reflection; every type it handled must keep
         * resolving through {@code subject}.
         */
        @Test
        void keepsSubjectOfEveryTypeWithSingleSubjectReference() {
            FhirContext context = FhirContext.forR4Cached();
            var typesWithSubject = compartmentManager.getCompartment().stream()
                    .filter(type -> {
                        try {
                            Class<?> resourceClass = context.getResourceDefinition(type).getImplementingClass();
                            return Reference.class.equals(resourceClass.getMethod("getSubject").getReturnType());
                        } catch (NoSuchMethodException e) {
                            return false;
                        }
                    })
                    .toList();

            assertThat(typesWithSubject).isNotEmpty().allSatisfy(type ->
                    assertThat(resolver.elementPaths(type)).contains(type + ".subject"));
        }
    }

    @Nested
    class PatientIds {

        @Test
        void patientIsItsOwnPatient() throws PatientIdNotFoundException {
            Patient patient = new Patient();
            patient.setId("123");

            assertThat(resolver.patientId(patient)).isEqualTo("123");
        }

        @Test
        void patientWithoutIdIsNoPatient() {
            assertThat(resolver.patientIds(new Patient())).isEmpty();
            assertThatThrownBy(() -> resolver.patientId(new Patient())).isInstanceOf(PatientIdNotFoundException.class);
        }

        @Test
        void consent() throws PatientIdNotFoundException {
            Consent consent = new Consent();
            consent.setPatient(new Reference("Patient/123"));

            assertThat(resolver.patientId(consent)).isEqualTo("123");
        }

        @Test
        void observationSubject() throws PatientIdNotFoundException {
            Observation observation = new Observation();
            observation.setSubject(new Reference("Patient/123"));

            assertThat(resolver.patientId(observation)).isEqualTo("123");
        }

        @Test
        void coverageBeneficiary() throws PatientIdNotFoundException {
            Coverage coverage = new Coverage();
            coverage.setBeneficiary(new Reference("Patient/123"));

            assertThat(resolver.patientId(coverage)).isEqualTo("123");
        }

        @Test
        void patientElement() throws PatientIdNotFoundException {
            BodyStructure bodyStructure = new BodyStructure();
            bodyStructure.setPatient(new Reference("Patient/1"));
            FamilyMemberHistory familyMemberHistory = new FamilyMemberHistory();
            familyMemberHistory.setPatient(new Reference("Patient/2"));
            RelatedPerson relatedPerson = new RelatedPerson();
            relatedPerson.setPatient(new Reference("Patient/3"));

            assertThat(resolver.patientId(bodyStructure)).isEqualTo("1");
            assertThat(resolver.patientId(familyMemberHistory)).isEqualTo("2");
            assertThat(resolver.patientId(relatedPerson)).isEqualTo("3");
        }

        @Test
        void nestedPathSkipsNonPatientReferences() {
            Appointment appointment = new Appointment();
            appointment.addParticipant().setActor(new Reference("Practitioner/p"));
            appointment.addParticipant().setActor(new Reference("Patient/1"));
            appointment.addParticipant();

            assertThat(resolver.patientIds(appointment)).containsExactly("1");
        }

        @Test
        void severalPatientsInDocumentOrder() {
            Account account = new Account();
            account.addSubject(new Reference("Patient/2"));
            account.addSubject(new Reference("Device/d"));
            account.addSubject(new Reference("Patient/1"));
            account.addSubject(new Reference("Patient/2"));

            assertThat(resolver.patientIds(account)).containsExactly("2", "1");
        }

        @Test
        void severalPatientsAreNotAttributed() {
            Account account = new Account();
            account.addSubject(new Reference("Patient/2"));
            account.addSubject(new Reference("Patient/1"));

            assertThatThrownBy(() -> resolver.patientId(account)).isInstanceOf(PatientIdNotFoundException.class)
                    .hasMessageContaining("several patients");
        }

        @Test
        void samePatientTwiceIsOnePatient() throws PatientIdNotFoundException {
            Account account = new Account();
            account.addSubject(new Reference("Patient/1"));
            account.addSubject(new Reference("Patient/1"));

            assertThat(resolver.patientId(account)).isEqualTo("1");
        }

        @Test
        void noPatientReference() {
            Coverage coverage = new Coverage();
            coverage.setBeneficiary(new Reference("RelatedPerson/r"));

            assertThat(resolver.patientIds(coverage)).isEmpty();
            assertThatThrownBy(() -> resolver.patientId(coverage)).isInstanceOf(PatientIdNotFoundException.class)
                    .hasMessageContaining("Coverage");
        }

        @Test
        void versionedReferenceIsItsPatient() throws PatientIdNotFoundException {
            Coverage coverage = new Coverage();
            coverage.setBeneficiary(new Reference("Patient/123/_history/4"));

            assertThat(resolver.patientId(coverage)).isEqualTo("123");
        }

        @Test
        void referenceWithoutIdOrAbsoluteIsNoPatient() {
            Coverage withoutId = new Coverage();
            withoutId.setBeneficiary(new Reference("Patient/"));
            Coverage absolute = new Coverage();
            absolute.setBeneficiary(new Reference("http://other.example/fhir/Patient/123"));

            assertThat(resolver.patientIds(withoutId)).isEmpty();
            assertThat(resolver.patientIds(absolute)).isEmpty();
        }

        @Test
        void typeOutsideCompartment() {
            assertThat(resolver.patientIds(new Medication())).isEmpty();
        }

        @Test
        void missingOrIncompleteReference() {
            Coverage withoutBeneficiary = new Coverage();
            Coverage withEmptyBeneficiary = new Coverage();
            withEmptyBeneficiary.setBeneficiary(new Reference());
            Coverage withDisplayOnly = new Coverage();
            withDisplayOnly.setBeneficiary(new Reference().setDisplay("Jane Doe"));

            assertThat(resolver.patientIds(withoutBeneficiary)).isEmpty();
            assertThat(resolver.patientIds(withEmptyBeneficiary)).isEmpty();
            assertThat(resolver.patientIds(withDisplayOnly)).isEmpty();
        }

        @Test
        void dataAbsentReferenceIsNoPatient() {
            Observation observation = new Observation();
            observation.setSubject(new Reference().setReferenceElement(dataAbsent()));

            assertThat(resolver.patientIds(observation)).isEmpty();
            assertThatThrownBy(() -> resolver.patientId(observation)).isInstanceOf(PatientIdNotFoundException.class);
        }

        @Test
        void dataAbsentReferenceNextToPatientReference() {
            Appointment appointment = new Appointment();
            appointment.addParticipant().setActor(new Reference().setReferenceElement(dataAbsent()));
            appointment.addParticipant().setActor(new Reference("Patient/1"));

            assertThat(resolver.patientIds(appointment)).containsExactly("1");
        }

        private static StringType dataAbsent() {
            StringType reference = new StringType();
            reference.addExtension("http://hl7.org/fhir/StructureDefinition/data-absent-reason", new CodeType("unknown"));
            return reference;
        }
    }
}
