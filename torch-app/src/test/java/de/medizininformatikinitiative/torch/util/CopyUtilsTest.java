package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CopyUtilsTest {

    @org.junit.jupiter.api.Nested
    class SetFieldReflectively {

        @Test
        void setsSingleElementFieldReflectively() throws ReflectiveOperationException {
            // Given a Patient and a single name
            Patient patient = new Patient();
            HumanName name = new HumanName().setFamily("Smith");

            // When setting it reflectively
            CopyUtils.setFieldReflectively(patient, "name", List.of(name));

            // Then it should appear in the patient
            assertThat(patient.getName()).hasSize(1);
            assertThat(patient.getNameFirstRep().getFamily()).isEqualTo("Smith");
        }

        @Test
        void testChoiceSlicingField() throws ReflectiveOperationException {
            // Given a Patient and a single name
            Patient patient = new Patient();
            BooleanType deceased = new BooleanType(true);
            // When setting it reflectively
            CopyUtils.setFieldReflectively(patient, "deceased", List.of(deceased));
            assertThat(patient.getDeceased().getClass()).isEqualTo(BooleanType.class);
        }


        @Test
        void setReservedKeywordsFallback() throws ReflectiveOperationException {
            Encounter encounter = new Encounter();

            // --- 1️⃣ Reserved word field 'class' (Coding) ---
            Coding encounterClass = new Coding().setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode").setCode("inpatient");
            CopyUtils.setFieldReflectively(encounter, "class", List.of(encounterClass));
            assertNotNull(encounter.getClass_());
            assertEquals("inpatient", encounter.getClass_().getCode());
        }

        @Test
        void setMeta() throws ReflectiveOperationException {
            // Given a Patient and a single name
            Patient patient = new Patient();
            Meta meta = new Meta().setProfile(List.of(new CanonicalType("Test")));

            // When setting it reflectively
            CopyUtils.setFieldReflectively(patient, "meta", List.of(meta));

            assertThat(patient.getMeta().getProfile())
                    .usingElementComparator((CanonicalType a, CanonicalType b) -> a.equalsDeep(b) ? 0 : 1)
                    .containsExactly(new CanonicalType("Test"));
        }

        @Test
        void setsMultipleElementsFieldReflectively() throws ReflectiveOperationException {
            // Given a Patient and multiple identifiers
            Patient patient = new Patient();
            Identifier id1 = new Identifier().setValue("A1");
            Identifier id2 = new Identifier().setValue("B2");

            // When setting reflectively with multiple elements
            CopyUtils.setFieldReflectively(patient, "identifier", List.of(id1, id2));

            // Then the list setter should have been used
            assertThat(patient.getIdentifier()).hasSize(2);
            assertThat(patient.getIdentifier().get(0).getValue()).isEqualTo("A1");
            assertThat(patient.getIdentifier().get(1).getValue()).isEqualTo("B2");
        }

        @Test
        void setsBackboneElementReflectively() throws ReflectiveOperationException {
            // Given an Observation and a single component (BackboneElement)
            Observation observation = new Observation();
            Observation.ObservationComponentComponent component =
                    new Observation.ObservationComponentComponent()
                            .setCode(new CodeableConcept().addCoding(new Coding().setCode("heart-rate")));

            // When setting reflectively
            CopyUtils.setFieldReflectively(observation, "component", List.of(component));

            // Then it should correctly assign the component list
            assertThat(observation.getComponent()).hasSize(1);
            assertThat(observation.getComponentFirstRep().getCode().getCodingFirstRep().getCode())
                    .isEqualTo("heart-rate");
        }

        @Test
        void doesNothingForEmptyList() throws ReflectiveOperationException {
            Patient patient = new Patient();
            CopyUtils.setFieldReflectively(patient, "name", List.of());
            assertThat(patient.getName()).isEmpty();
        }

        @Test
        void failsOnUnknownField() {
            Patient patient = new Patient();
            HumanName name = new HumanName().setFamily("Test");

            assertThatThrownBy(() -> CopyUtils.setFieldReflectively(patient, "nonExistingField", List.of(name)))
                    .isInstanceOf(ReflectiveOperationException.class)
                    .hasMessageContaining("No setter found for field nonExistingField with value type class org.hl7.fhir.r4.model.HumanName");
        }

        @Test
        void setFieldReflectively_dateTimeType() throws ReflectiveOperationException {
            // --- Given ---
            Patient target = new Patient();
            DateTimeType dt = new DateTimeType("1980-12-25T00:00:00Z");

            // --- When ---
            // Wrap in a list, like your method expects
            CopyUtils.setFieldReflectively(target, "birthDate", List.of(dt));

            // --- Then ---
            DateType copied = target.getBirthDateElement();
            assertThat(copied).isNotNull();
            assertThat(copied.getValueAsString()).isEqualTo("1980-12-25");
        }

        @Test
        void testSetFieldReflectively_Primitive() throws ReflectiveOperationException {
            Identifier identifier = new Identifier();

            // Set a primitive field via reflection
            StringType system = new StringType("urn:example");
            CopyUtils.setFieldReflectively(identifier, "system", List.of(system));

            assertEquals("urn:example", identifier.getSystem());
        }

        @Test
        void testSetFieldReflectively_ListOfBackbone() throws ReflectiveOperationException {
            Patient patient = new Patient();

            // Set HumanName list reflectively
            HumanName name1 = new HumanName().setFamily("Smith");
            HumanName name2 = new HumanName().setFamily("Jones");

            CopyUtils.setFieldReflectively(patient, "name", List.of(name1, name2));

            List<HumanName> names = patient.getName();
            assertEquals(2, names.size());
            assertEquals("Smith", names.get(0).getFamily());
            assertEquals("Jones", names.get(1).getFamily());
        }

        @Test
        void testSetFieldReflectively_ListOfBackboneElement_Contact() throws ReflectiveOperationException {
            Patient patient = new Patient();

            // Patient.ContactComponent is another BackboneElement example
            Patient.ContactComponent contact1 = new Patient.ContactComponent()
                    .addTelecom(new ContactPoint().setValue("555-1234"));
            Patient.ContactComponent contact2 = new Patient.ContactComponent()
                    .addTelecom(new ContactPoint().setValue("555-5678"));

            CopyUtils.setFieldReflectively(patient, "contact", List.of(contact1, contact2));

            List<Patient.ContactComponent> contacts = patient.getContact();
            assertEquals(2, contacts.size());
            assertEquals("555-1234", contacts.get(0).getTelecom().getFirst().getValue());
            assertEquals("555-5678", contacts.get(1).getTelecom().getFirst().getValue());
        }

        @Test
        void testSetFieldReflectively_BackbonePrimitive() throws ReflectiveOperationException {
            HumanName name = new HumanName();

            // Add a given element (primitive) via reflection
            StringType given = new StringType("John");
            CopyUtils.setFieldReflectively(name, "given", List.of(given));

            assertEquals("John", name.getGiven().getFirst().getValue());
        }

        @Test
        void setFieldReflectively_shouldSetPrimitiveListAndObjects() throws ReflectiveOperationException {
            Patient patient = new Patient();

            // --- Multiple primitive elements (list) ---
            Identifier id1 = new Identifier();
            Identifier id2 = new Identifier();
            id2.setValue("John");
            id1.setValue("Bob");
            List<Identifier> identifiers = List.of(id1, id2);

            CopyUtils.setFieldReflectively(patient, "identifier", identifiers);

            assertThat(patient.getIdentifier()).hasSize(2);
            assertThat(patient.getIdentifier()).containsExactly(id1, id2);

            // --- Setting an empty list should do nothing ---
            CopyUtils.setFieldReflectively(patient, "identifier", List.of());
            assertThat(patient.getIdentifier()).hasSize(2); // still 2
        }

        @Nested
        class typeSlicing {
            @Test
            void setFieldReflectively_valueDateTimeType() throws ReflectiveOperationException {
                Observation obs = new Observation();
                DateTimeType dt = new DateTimeType("2025-11-01T12:00:00");
                dt.setPrecision(TemporalPrecisionEnum.MINUTE);

                CopyUtils.setFieldReflectively(obs, "value", List.of(dt));

                assertThat(obs.getValueDateTimeType().getValueAsString())
                        .isEqualTo("2025-11-01T12:00");
                assertThat(obs.getValueDateTimeType().getPrecision())
                        .isEqualTo(TemporalPrecisionEnum.MINUTE);
            }

            @Test
            void skipsEmpty() throws ReflectiveOperationException {
                Observation obs = new Observation();
                Observation obsBackUp = obs.copy();
                CopyUtils.setFieldReflectively(obs, "value", List.of());

                assertThat(obs.equalsDeep(obsBackUp)).isTrue();
            }

            @Test
            void valueQuantity() throws ReflectiveOperationException {
                Observation obs = new Observation();
                Quantity quantity = new Quantity().setValue(5.4).setUnit("mmol/L");

                CopyUtils.setFieldReflectively(obs, "value", List.of(quantity));

                assertThat(obs.getValueQuantity().getValue()).isEqualTo(BigDecimal.valueOf(5.4));
                assertThat(obs.getValueQuantity().getUnit()).isEqualTo("mmol/L");
            }

            @Test
            void valueCodeableConcept() throws ReflectiveOperationException {
                Observation obs = new Observation();
                CodeableConcept cc = new CodeableConcept()
                        .addCoding(new Coding("http://loinc.org", "1234-5", "Example"));

                CopyUtils.setFieldReflectively(obs, "value", List.of(cc));

                assertThat(obs.getValueCodeableConcept().getCodingFirstRep().getCode())
                        .isEqualTo("1234-5");
            }
        }
    }
}
