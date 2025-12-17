package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.experimental.runners.Enclosed;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@RunWith(Enclosed.class)
@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ReferenceResolverIT {

    @Autowired
    ReferenceResolver referenceResolver;

    @MockitoBean
    private DataStore dataStore;

    @BeforeEach
    void setUp() {
        reset(dataStore);
    }

    private ResourceGroup rgFromResource(Resource resource, String groupID) {
        return new ResourceGroup(resource.getId(), groupID);
    }

    private boolean containsIdInQuery(Bundle bundle, String requestedResourceId) {
        return bundle.getEntry().stream().anyMatch(e -> e.getRequest().getUrl().contains(requestedResourceId));

    }

    private List<Resource> returnResourcesByQuery(Bundle queryBundle, Resource... potentialResources) {
        return Stream.of(potentialResources).filter(resource -> containsIdInQuery(queryBundle, resource.getIdPart())).toList();
    }

    @Nested
    class ResolveCoreBundle {
        public static final String MEDICATION_PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/Medication";
        public static final String ORGANIZATION_PROFILE = "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Organization";
        public static final String ORG_ID_1 = "Organization/org_1";
        public static final String ORG_ID_2 = "Organization/org_2";
        public static final String MED_ID_1 = "Medication/med_1";
        public static final String MED_GROUP = "med-group";
        public static final String LINKED_ORG_GROUP_1 = "linked-org-1";
        public static final String LINKED_ORG_GROUP_2 = "linked-org-2";
        public static final String AG_1 = "ag-1";
        public static final String AG_2 = "ag-2";
        public static final String AG_3 = "ag-3";
        public static final String MEDICATION_TYPE = "Medication";
        public static final String MANUFACTURER_PATH = "Medication.manufacturer";
        public static final String PARTOF_PATH = "Organization.partOf";
        public static final String ORGNAME_PATH = "Organization.name";
        public static final String ORGANIZATION_TYPE = "Organization";

        private Organization createOrganization(String orgId, String partOfId) {
            var org = (Organization) new Organization()
                    .setId(orgId)
                    .setMeta(new Meta().setProfile(List.of((CanonicalType)new CanonicalType().setValue(ORGANIZATION_PROFILE))));
            if (partOfId != null) {
                org.setPartOf(new Reference(partOfId));
            }
            return org;
        }

        private Medication createMedication(String medId, String orgId) {
            var med = new Medication()
                    .setManufacturer(new Reference(orgId))
                    .setId(medId)
                    .setMeta(new Meta().setProfile(List.of((CanonicalType)new CanonicalType().setValue(MEDICATION_PROFILE))));

            return (Medication) med;
        }

        @Test
        void testNestedResolve() {
            var org_1 = createOrganization(ORG_ID_1, ORG_ID_2);
            var org_2 = createOrganization(ORG_ID_2, null).setName("name-83849");
            var med = createMedication(MED_ID_1, ORG_ID_1);
            when(dataStore.executeBundle(any())).thenAnswer(invocation -> {
                Bundle queryBundle = invocation.getArgument(0);
                return Mono.just(returnResourcesByQuery(queryBundle, org_1, org_2, med));
            });
            ResourceBundle resourceBundle = new ResourceBundle();
            resourceBundle.put(med);
            resourceBundle.addResourceGroupValidity(rgFromResource(med, MED_GROUP), true);
            Map<String, AnnotatedAttributeGroup> groupMap = new HashMap<>();
            AnnotatedAttribute med_manufacturer = new AnnotatedAttribute(MANUFACTURER_PATH, MANUFACTURER_PATH, false, List.of(LINKED_ORG_GROUP_1));
            AnnotatedAttribute org_partOf = new AnnotatedAttribute(PARTOF_PATH, PARTOF_PATH, false, List.of(LINKED_ORG_GROUP_2));
            AnnotatedAttribute org_name = new AnnotatedAttribute(ORGNAME_PATH, ORGNAME_PATH, false, List.of());
            AnnotatedAttributeGroup medAG = new AnnotatedAttributeGroup(AG_1, MEDICATION_TYPE,
                    MEDICATION_PROFILE, List.of(med_manufacturer), List.of());
            AnnotatedAttributeGroup linkedOrgAG_1 = new AnnotatedAttributeGroup(AG_2, ORGANIZATION_TYPE,
                    ORGANIZATION_PROFILE, List.of(org_partOf), List.of());
            AnnotatedAttributeGroup linkedOrgAG_2 = new AnnotatedAttributeGroup(AG_3, ORGANIZATION_TYPE,
                    ORGANIZATION_PROFILE, List.of(org_name), List.of());
            groupMap.put(MED_GROUP, medAG);
            groupMap.put(LINKED_ORG_GROUP_1, linkedOrgAG_1);
            groupMap.put(LINKED_ORG_GROUP_2, linkedOrgAG_2);


            var resultBundle = referenceResolver.resolveCoreBundle(resourceBundle, groupMap).block();


            assertThat(resultBundle).isNotNull();
            assertThat(resultBundle.resourceGroupValidity()).containsOnly(
                    Map.entry(rgFromResource(med, MED_GROUP), true),
                    Map.entry(rgFromResource(org_1, LINKED_ORG_GROUP_1), true),
                    Map.entry(rgFromResource(org_2, LINKED_ORG_GROUP_2), true));
            assertThat(resultBundle.cache()).containsOnly(
                    Map.entry(MED_ID_1, Optional.of(med)),
                    Map.entry(ORG_ID_1, Optional.of(org_1)),
                    Map.entry(ORG_ID_2, Optional.of(org_2))
            );
        }

        @Test
        void testSameLinkedGroupTwice() {
            var org_1 = createOrganization(ORG_ID_1, null);
            var med = createMedication(MED_ID_1, ORG_ID_1);
            when(dataStore.executeBundle(any())).thenAnswer(invocation -> {
                Bundle queryBundle = invocation.getArgument(0);
                return Mono.just(returnResourcesByQuery(queryBundle, org_1, med));
            });
            ResourceBundle resourceBundle = new ResourceBundle();
            resourceBundle.put(med);
            resourceBundle.addResourceGroupValidity(rgFromResource(med, MED_GROUP), true);
            Map<String, AnnotatedAttributeGroup> groupMap = new HashMap<>();
            AnnotatedAttribute med_manufacturer = new AnnotatedAttribute(MANUFACTURER_PATH, MANUFACTURER_PATH, false, List.of(LINKED_ORG_GROUP_1));
            AnnotatedAttribute org_partOf = new AnnotatedAttribute(PARTOF_PATH, PARTOF_PATH, false, List.of(LINKED_ORG_GROUP_2));
            // test what happens if two attributes have the same linked group (LINKED_ORG_GROUP_2)
            AnnotatedAttribute org_partOf_2 = new AnnotatedAttribute(PARTOF_PATH, PARTOF_PATH, false, List.of(LINKED_ORG_GROUP_2));
            AnnotatedAttributeGroup medAG = new AnnotatedAttributeGroup(AG_1, MEDICATION_TYPE,
                    MEDICATION_PROFILE, List.of(med_manufacturer), List.of());
            AnnotatedAttributeGroup linkedOrgAG_1 = new AnnotatedAttributeGroup(AG_2, ORGANIZATION_TYPE,
                    ORGANIZATION_PROFILE, List.of(org_partOf, org_partOf_2), List.of());

            groupMap.put(MED_GROUP, medAG);
            groupMap.put(LINKED_ORG_GROUP_1, linkedOrgAG_1);


            var resultBundle = referenceResolver.resolveCoreBundle(resourceBundle, groupMap).block();


            assertThat(resultBundle).isNotNull();
            assertThat(resultBundle.resourceGroupValidity()).containsOnly(
                    Map.entry(rgFromResource(med, MED_GROUP), true),
                    Map.entry(rgFromResource(org_1, LINKED_ORG_GROUP_1), true));
            assertThat(resultBundle.cache()).containsOnly(
                    Map.entry(MED_ID_1, Optional.of(med)),
                    Map.entry(ORG_ID_1, Optional.of(org_1)));
        }

        @Test
        void testNestedInvalid() {
            var org_1 = createOrganization(ORG_ID_1, ORG_ID_2);
            var org_2 = createOrganization(ORG_ID_2, null); // has no name but name is set to must-have=true -> invalid
            var med = createMedication(MED_ID_1, ORG_ID_1);
            when(dataStore.executeBundle(any())).thenAnswer(invocation -> {
                Bundle queryBundle = invocation.getArgument(0);
                return Mono.just(returnResourcesByQuery(queryBundle, org_1, org_2, med));
            });
            ResourceBundle resourceBundle = new ResourceBundle();
            resourceBundle.put(med);
            resourceBundle.addResourceGroupValidity(rgFromResource(med, MED_GROUP), true);
            Map<String, AnnotatedAttributeGroup> groupMap = new HashMap<>();
            AnnotatedAttribute med_manufacturer = new AnnotatedAttribute(MANUFACTURER_PATH, MANUFACTURER_PATH, false, List.of(LINKED_ORG_GROUP_1));
            AnnotatedAttribute org_partOf = new AnnotatedAttribute(PARTOF_PATH, PARTOF_PATH, false, List.of(LINKED_ORG_GROUP_2));
            AnnotatedAttribute org_name = new AnnotatedAttribute(ORGNAME_PATH, ORGNAME_PATH, true, List.of());
            AnnotatedAttributeGroup medAG = new AnnotatedAttributeGroup(AG_1, MEDICATION_TYPE,
                    MEDICATION_PROFILE, List.of(med_manufacturer), List.of());
            AnnotatedAttributeGroup linkedOrgAG_1 = new AnnotatedAttributeGroup(AG_2, ORGANIZATION_TYPE,
                    ORGANIZATION_PROFILE, List.of(org_partOf), List.of());
            AnnotatedAttributeGroup linkedOrgAG_2 = new AnnotatedAttributeGroup(AG_3, ORGANIZATION_TYPE,
                    ORGANIZATION_PROFILE, List.of(org_name), List.of());
            groupMap.put(MED_GROUP, medAG);
            groupMap.put(LINKED_ORG_GROUP_1, linkedOrgAG_1);
            groupMap.put(LINKED_ORG_GROUP_2, linkedOrgAG_2);


            var resultBundle = referenceResolver.resolveCoreBundle(resourceBundle, groupMap).block();


            assertThat(resultBundle).isNotNull();
            assertThat(resultBundle.resourceGroupValidity()).containsOnly(
                    Map.entry(rgFromResource(med, MED_GROUP), true),
                    Map.entry(rgFromResource(org_1, LINKED_ORG_GROUP_1), true),
                    Map.entry(rgFromResource(org_2, LINKED_ORG_GROUP_2), false));
            assertThat(resultBundle.cache()).containsOnly(
                    Map.entry(MED_ID_1, Optional.of(med)),
                    Map.entry(ORG_ID_1, Optional.of(org_1)),
                    Map.entry(ORG_ID_2, Optional.of(org_2)) // was fetched but set as invalid
            );
        }

    }

    @Nested
    class ResolvePatientBundle {
        public static final String CONDITION_PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose";
        public static final String ENCOUNTER_PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung";
        public static final String PATIENT_PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung";
        public static final String ENC_ID_1 = "Encounter/enc_1";
        public static final String ENC_ID_2 = "Encounter/enc_2";
        public static final String COND_ID_1 = "Condition/cond_1";
        public static final String COND_ID_2 = "Condition/cond_2";
        public static final String PAT_ID_1 = "Patient/pat-1";
        public static final String COND_GROUP = "cond-group";
        public static final String PAT_GROUP = "pat-group";
        public static final String ENC_GROUP = "enc-group";
        public static final String LINKED_GROUP_1 = "linked-group-1";
        public static final String LINKED_GROUP_2 = "linked-group-2";
        public static final String AG_1 = "ag-1";
        public static final String AG_2 = "ag-2";
        public static final String AG_3 = "ag-3";
        public static final String AG_4 = "ag-4";
        public static final String CONDITION_TYPE = "Condition";
        public static final String ENCOUNTER_TYPE = "Encounter";
        public static final String PATIENT_TYPE = "Patient";
        public static final String ENCOUNTER_PATH = "Condition.encounter";
        public static final String CODE_PATH = "Condition.code";
        public static final String PARTOF_PATH = "Encounter.partOf";
        public static final String STATUS_PATH = "Encounter.status";
        public static final String REASON_PATH = "Encounter.reasonReference";
        public static final CodeableConcept COND_CODE = new CodeableConcept().addCoding(new Coding("sys-725", "code-450", "some display"));


        private Encounter createEncounter(String encId, String subjectId, String partOfId) {
            var enc = (Encounter) new Encounter()
                    .setSubject(new Reference(subjectId))
                    .setId(encId)
                    .setMeta(new Meta().setProfile(List.of((CanonicalType)new CanonicalType().setValue(ENCOUNTER_PROFILE))));
            if (partOfId != null) {
                enc.setPartOf(new Reference(partOfId));
            }
            return enc;
        }

        private Condition createCondition(String condId, String subjectId, String encId) {
            var cond = new Condition()
                    .setSubject(new Reference(subjectId))
                    .setEncounter(new Reference(encId))
                    .setId(condId)
                    .setMeta(new Meta().setProfile(List.of((CanonicalType)new CanonicalType().setValue(CONDITION_PROFILE))));

            return (Condition) cond;
        }

        private Patient createPatient(String patId) {
            var pat = new Patient()
                    .setId(patId)
                    .setMeta(new Meta().setProfile(List.of((CanonicalType)new CanonicalType().setValue(PATIENT_PROFILE))));

            return (Patient) pat;
        }

        private String stripType(String resourceId) {
            return resourceId.contains("/") ? resourceId.split("/")[1] : resourceId;
        }

        @Test
        void testNested() {
            var pat = createPatient(PAT_ID_1);
            var enc_1 = createEncounter(ENC_ID_1, PAT_ID_1, ENC_ID_2);
            var enc_2 = createEncounter(ENC_ID_2, PAT_ID_1, null).setStatus(Encounter.EncounterStatus.ARRIVED);
            var cond = createCondition(COND_ID_1, PAT_ID_1, ENC_ID_1);
            when(dataStore.executeBundle(any())).thenAnswer(invocation -> {
                Bundle queryBundle = invocation.getArgument(0);
                var list = returnResourcesByQuery(queryBundle, enc_1, enc_2, cond, pat);
                return Mono.just(list);
            });

            var coreBundle = new ResourceBundle();
            var patBundles = Map.of(stripType(PAT_ID_1), new PatientResourceBundle(stripType(PAT_ID_1)));
            patBundles.get(stripType(PAT_ID_1)).bundle().put(pat);
            patBundles.get(stripType(PAT_ID_1)).bundle().put(cond);
            patBundles.get(stripType(PAT_ID_1)).bundle().addResourceGroupValidity(rgFromResource(pat, PAT_GROUP), true);
            patBundles.get(stripType(PAT_ID_1)).bundle().addResourceGroupValidity(rgFromResource(cond, COND_GROUP), true);
            var batch = new PatientBatchWithConsent(patBundles, false, coreBundle);

            Map<String, AnnotatedAttributeGroup> groupMap = new HashMap<>();
            AnnotatedAttribute cond_encounter = new AnnotatedAttribute(ENCOUNTER_PATH, ENCOUNTER_PATH, false, List.of(LINKED_GROUP_1));
            AnnotatedAttribute enc_partOf = new AnnotatedAttribute(PARTOF_PATH, PARTOF_PATH, false, List.of(LINKED_GROUP_2));
            AnnotatedAttribute enc_status = new AnnotatedAttribute(STATUS_PATH, STATUS_PATH, false, List.of());
            AnnotatedAttributeGroup condAG = new AnnotatedAttributeGroup(AG_1, CONDITION_TYPE,
                    CONDITION_PROFILE, List.of(cond_encounter), List.of());
            AnnotatedAttributeGroup linkedEncAG_1 = new AnnotatedAttributeGroup(AG_2, ENCOUNTER_TYPE,
                    ENCOUNTER_PROFILE, List.of(enc_partOf), List.of());
            AnnotatedAttributeGroup linkedEncAG_2 = new AnnotatedAttributeGroup(AG_3, ENCOUNTER_TYPE,
                    ENCOUNTER_PROFILE, List.of(enc_status), List.of());
            AnnotatedAttributeGroup patAG = new AnnotatedAttributeGroup(AG_4, PATIENT_TYPE,
                    PATIENT_PROFILE, List.of(), List.of());
            groupMap.put(COND_GROUP, condAG);
            groupMap.put(PAT_GROUP, patAG);
            groupMap.put(LINKED_GROUP_1, linkedEncAG_1);
            groupMap.put(LINKED_GROUP_2, linkedEncAG_2);


            var resultBatch = referenceResolver.resolvePatientBatch(batch, groupMap).block();


            assertThat(resultBatch).isNotNull();
            assertThat(resultBatch.bundles()).hasSize(1);
            var patBundle = resultBatch.bundles().get(stripType(PAT_ID_1)).bundle();
            assertThat(patBundle.resourceGroupValidity()).containsOnly(
                    Map.entry(rgFromResource(cond, COND_GROUP), true),
                    Map.entry(rgFromResource(enc_1, LINKED_GROUP_1), true),
                    Map.entry(rgFromResource(enc_2, LINKED_GROUP_2), true),
                    Map.entry(rgFromResource(pat, PAT_GROUP), true));
            assertThat(patBundle.cache()).containsOnly(
                    Map.entry(COND_ID_1, Optional.of(cond)),
                    Map.entry(ENC_ID_1, Optional.of(enc_1)),
                    Map.entry(ENC_ID_2, Optional.of(enc_2)),
                    Map.entry(PAT_ID_1, Optional.of(pat))
            );
        }

        @Test
        void testTwoRefsSameLevel() {
            var pat = createPatient(PAT_ID_1);
            var enc = createEncounter(ENC_ID_1, PAT_ID_1, null);
            var cond_1 = createCondition(COND_ID_1, PAT_ID_1, ENC_ID_1).setCode(COND_CODE);
            var cond_2 = createCondition(COND_ID_2, PAT_ID_1, ENC_ID_1).setCode(COND_CODE);
            enc.setReasonReference(List.of(new Reference(COND_ID_1), new Reference(COND_ID_2)));
            when(dataStore.executeBundle(any())).thenAnswer(invocation -> {
                Bundle queryBundle = invocation.getArgument(0);
                var list = returnResourcesByQuery(queryBundle, enc, cond_1, cond_2, pat);
                return Mono.just(list);
            });

            var coreBundle = new ResourceBundle();
            var patBundles = Map.of(stripType(PAT_ID_1), new PatientResourceBundle(stripType(PAT_ID_1)));
            patBundles.get(stripType(PAT_ID_1)).bundle().put(pat);
            patBundles.get(stripType(PAT_ID_1)).bundle().put(enc);
            patBundles.get(stripType(PAT_ID_1)).bundle().addResourceGroupValidity(rgFromResource(pat, PAT_GROUP), true);
            patBundles.get(stripType(PAT_ID_1)).bundle().addResourceGroupValidity(rgFromResource(enc, ENC_GROUP), true);
            var batch = new PatientBatchWithConsent(patBundles, false, coreBundle);

            Map<String, AnnotatedAttributeGroup> groupMap = new HashMap<>();
            AnnotatedAttribute enc_reason = new AnnotatedAttribute(REASON_PATH, REASON_PATH, false, List.of(LINKED_GROUP_1));
            AnnotatedAttribute cond_code = new AnnotatedAttribute(CODE_PATH, CODE_PATH, false, List.of());
            AnnotatedAttributeGroup linkedCondAG = new AnnotatedAttributeGroup(AG_1, CONDITION_TYPE,
                    CONDITION_PROFILE, List.of(cond_code), List.of());
            AnnotatedAttributeGroup encAG = new AnnotatedAttributeGroup(AG_2, ENCOUNTER_TYPE,
                    ENCOUNTER_PROFILE, List.of(enc_reason), List.of());
            AnnotatedAttributeGroup patAG = new AnnotatedAttributeGroup(AG_3, PATIENT_TYPE,
                    PATIENT_PROFILE, List.of(), List.of());
            groupMap.put(ENC_GROUP, encAG);
            groupMap.put(PAT_GROUP, patAG);
            groupMap.put(LINKED_GROUP_1, linkedCondAG);


            var resultBatch = referenceResolver.resolvePatientBatch(batch, groupMap).block();


            assertThat(resultBatch).isNotNull();
            assertThat(resultBatch.bundles()).hasSize(1);
            var patBundle = resultBatch.bundles().get(stripType(PAT_ID_1)).bundle();
            assertThat(patBundle.resourceGroupValidity()).containsOnly(
                    Map.entry(rgFromResource(cond_1, LINKED_GROUP_1), true),
                    Map.entry(rgFromResource(cond_2, LINKED_GROUP_1), true),
                    Map.entry(rgFromResource(enc, ENC_GROUP), true),
                    Map.entry(rgFromResource(pat, PAT_GROUP), true));
            assertThat(patBundle.cache()).containsOnly(
                    Map.entry(COND_ID_1, Optional.of(cond_1)),
                    Map.entry(COND_ID_2, Optional.of(cond_2)),
                    Map.entry(ENC_ID_1, Optional.of(enc)),
                    Map.entry(PAT_ID_1, Optional.of(pat))
            );
        }
    }
}
