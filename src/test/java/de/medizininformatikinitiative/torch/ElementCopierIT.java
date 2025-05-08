package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.FhirPathBuilder;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


public class ElementCopierIT {

    private final IntegrationTestSetup itSetup = new IntegrationTestSetup();
    private final FhirContext fhirContext = FhirContext.forR4();
    private final ElementCopier copier = new ElementCopier(fhirContext);
    private final StructureDefinitionHandler structureDefinitionHandler = itSetup.structureDefinitionHandler();

    @Test
    public void testDefinitionIsContained() {
        StructureDefinition definition = itSetup.structureDefinitionHandler().getDefinition("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose");
        assertNotNull(definition, "The element should be contained in the map");
        assertEquals(ResourceType.StructureDefinition, definition.getResourceType(), "Resource type should be StructureDefinition");
    }


    @Test
    public void testOpenChoice() throws NoSuchMethodException, IOException, InvocationTargetException, InstantiationException, IllegalAccessException, MustHaveViolatedException {
        String resource = "Diagnosis1.json";

        DomainResource src = itSetup.readResource("src/test/resources/InputResources/Condition/" + resource);
        DomainResource expected = itSetup.readResource("src/test/resources/CopyTest/expectedOutput/" + resource);
        String profileDiagnosis = src.getMeta().getProfile().get(1).getValue();
        Class<? extends DomainResource> resourceClass = src.getClass().asSubclass(DomainResource.class);
        DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();

        copier.copy(src, tgt, new AnnotatedAttribute("Condition.onset[x]", "Condition.onset", "Condition.onset[x]", false));
        copier.copy(src, tgt, new AnnotatedAttribute("Condition.meta", "Condition.meta", "Condition.meta", true));
        copier.copy(src, tgt, new AnnotatedAttribute("Condition.id", "Condition.id", "Condition.id", true));
        copier.copy(src, tgt, new AnnotatedAttribute("Condition.code", "Condition.code", "Condition.code", false));


        assertThat(fhirContext.newJsonParser().encodeResourceToString(tgt))
                .isEqualTo(fhirContext.newJsonParser().encodeResourceToString(expected));
    }

    @Test
    public void testChoiceSlicingFail() throws IOException, MustHaveViolatedException {

        Observation observation = (Observation) itSetup.readResource("src/test/resources/InputResources/Observation/Example-MI-Initiative-Laborprofile-Laborwerte.json");
        Observation expected = (Observation) itSetup.readResource("src/test/resources/CopyTest/expectedOutput/Observation.json");
        Observation tgt = new Observation();
        String profile = observation.getMeta().getProfile().getFirst().getValue();

        String[] paths = FhirPathBuilder.handleSlicingForFhirPath("Observation.value[x]:valueCodeableConcept", structureDefinitionHandler.getSnapshot(profile));
        copier.copy(observation, tgt, new AnnotatedAttribute("Observation.value[x]:valueCodeableConcept", paths[0], paths[1], false));

        assertThat(fhirContext.newJsonParser().encodeResourceToString(tgt))
                .isNotEqualTo(fhirContext.newJsonParser().encodeResourceToString(expected));
    }

    @Test
    public void choiceSuccessSliceFound() throws IOException, MustHaveViolatedException {

        Observation source = (Observation) itSetup.readResource("src/test/resources/InputResources/Observation/ObservationValueCodableConcept.json");
        Observation expected = (Observation) itSetup.readResource("src/test/resources/CopyTest/expectedOutput/ObservationCodeableConceptSlice.json");
        Observation tgt = new Observation();
        String profile = source.getMeta().getProfile().getFirst().getValue();

        String[] paths = FhirPathBuilder.handleSlicingForFhirPath("Observation.value[x]:valueCodeableConcept.coding.display", structureDefinitionHandler.getSnapshot(profile));


        copier.copy(source, tgt, new AnnotatedAttribute("Observation.value[x]:valueCodeableConcept.coding.display", paths[0], paths[1], true));

        assertThat(fhirContext.newJsonParser().encodeResourceToString(tgt))
                .isEqualTo(fhirContext.newJsonParser().encodeResourceToString(expected));
    }


    @Test
    public void testChoiceSlicingSuccess() throws IOException, MustHaveViolatedException {

        Observation source = (Observation) itSetup.readResource("src/test/resources/InputResources/Observation/Example-MI-Initiative-Laborprofile-Laborwerte.json");
        Observation expected = (Observation) itSetup.readResource("src/test/resources/CopyTest/expectedOutput/Observation.json");
        Observation tgt = new Observation();

        String profile = source.getMeta().getProfile().getFirst().getValue();
        String[] paths = FhirPathBuilder.handleSlicingForFhirPath("Observation.value[x]:valueQuantity", structureDefinitionHandler.getSnapshot(profile));

        copier.copy(source, tgt, new AnnotatedAttribute("Observation.value[x]:valueQuantity", paths[0], paths[1], false));

        assertThat(fhirContext.newJsonParser().encodeResourceToString(tgt))
                .isEqualTo(fhirContext.newJsonParser().encodeResourceToString(expected));
    }


    @Test
    public void testPatternSlicing() throws IOException, MustHaveViolatedException {

        Condition source = (Condition) itSetup.readResource("src/test/resources/InputResources/Condition/Diagnosis2.json");
        Condition expected = (Condition) itSetup.readResource("src/test/resources/CopyTest/expectedOutput/Diagnosis2Slice.json");
        Condition tgt = new Condition();
        String profile = source.getMeta().getProfile().getFirst().getValue();
        String[] paths = FhirPathBuilder.handleSlicingForFhirPath("Condition.code.coding:icd10-gm", structureDefinitionHandler.getSnapshot(profile));

        copier.copy(source, tgt, new AnnotatedAttribute("Condition.code.coding:icd10-gm", paths[0], paths[1], false));

        assertThat(fhirContext.newJsonParser().encodeResourceToString(tgt))
                .isEqualTo(fhirContext.newJsonParser().encodeResourceToString(expected));
    }


    @Test
    public void testIdentityList() throws IOException, NoSuchMethodException, MustHaveViolatedException, InvocationTargetException, InstantiationException, IllegalAccessException {


        DomainResource source = itSetup.readResource("src/test/resources/InputResources/Observation/Example-MI-Initiative-Laborprofile-Laborwerte-list.json");
        Observation expected = (Observation) itSetup.readResource("src/test/resources/CopyTest/expectedOutput/ObservationIdentityList.json");
        Class<? extends DomainResource> resourceClass = source.getClass().asSubclass(DomainResource.class);
        DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();
        String profile = source.getMeta().getProfile().getFirst().getValue();

        copier.copy(source, tgt, new AnnotatedAttribute("Observation.identifier", "Observation.identifier", "Observation.identifier", false));
        copier.copy(source, tgt, new AnnotatedAttribute("Observation.referenceRange.low", "Observation.referenceRange.low", "Observation.referenceRange.low", false));
        copier.copy(source, tgt, new AnnotatedAttribute("Observation.referenceRange.high", "Observation.referenceRange.high", "Observation.referenceRange.high", false));
        copier.copy(source, tgt, new AnnotatedAttribute("Observation.interpretation", "Observation.interpretation", "Observation.interpretation", false));
        copier.copy(source, tgt, new AnnotatedAttribute("Observation.value[x]", "Observation.value", "Observation.value[x]", false));

        assertThat(fhirContext.newJsonParser().encodeResourceToString(tgt))
                .isEqualTo(fhirContext.newJsonParser().encodeResourceToString(expected));
    }

    @Test
    public void testEncounter() throws MustHaveViolatedException, IOException {

        Encounter source = (Encounter) itSetup.readResource("src/test/resources/InputResources/Encounter/Encounter-mii-exa-fall-kontakt-gesundheitseinrichtung-2.json");
        Encounter expected = (Encounter) itSetup.readResource("src/test/resources/CopyTest/expectedOutput/Encounter.json");
        Encounter tgt = new Encounter();
        String profile = source.getMeta().getProfile().getFirst().getValue();

        copier.copy(source, tgt, new AnnotatedAttribute("Encounter.diagnosis.use", "Encounter.diagnosis.use", "Encounter.diagnosis.use", false));

        assertThat(fhirContext.newJsonParser().encodeResourceToString(tgt))
                .isEqualTo(fhirContext.newJsonParser().encodeResourceToString(expected));
    }


    @Nested
    class Terser {

        @Test
        void choiceOperatorBaseLevel() throws MustHaveViolatedException {
            Observation source = new Observation();

            DateTimeType dateTime = new DateTimeType("2022-10-01");
            source.setEffective(dateTime);

            Meta meta = new Meta();
            meta.setProfile(List.of(
                    new CanonicalType(
                            "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab")
            ));
            source.setMeta(meta);
            Observation tgt = new Observation();
            String profile = source.getMeta().getProfile().getFirst().getValue();


            copier.copy(source, tgt, new AnnotatedAttribute("Observation.effective[x]", "Observation.effective", "Observation.effective[x]", false));
            copier.copy(source, tgt, new AnnotatedAttribute("Observation.meta", "Observation.meta", "Observation.meta", false));

            assertThat(fhirContext.newJsonParser().encodeResourceToString(tgt))
                    .isEqualTo(fhirContext.newJsonParser().encodeResourceToString(source));


        }


    }
}
