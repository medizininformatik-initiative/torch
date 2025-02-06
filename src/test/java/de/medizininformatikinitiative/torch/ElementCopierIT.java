package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import de.medizininformatikinitiative.torch.util.ElementCopier;
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
    private final ElementCopier copier = new ElementCopier(itSetup.structureDefinitionHandler(), fhirContext, itSetup.fhirPathBuilder());

    
    @Test
    public void testDefinitionIsContained() {
        StructureDefinition definition = itSetup.structureDefinitionHandler().getDefinition("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose");
        assertNotNull(definition, "The element should be contained in the map");
        assertEquals(ResourceType.StructureDefinition, definition.getResourceType(), "Resource type should be StructureDefinition");
    }


    @Test
    public void testOpenChoice() throws NoSuchMethodException, IOException, InvocationTargetException, InstantiationException, IllegalAccessException, MustHaveViolatedException {
        String resource = "Diagnosis1.json";

        DomainResource resourceSrc = itSetup.readResource("src/test/resources/InputResources/Condition/" + resource);
        DomainResource resourceExpected = itSetup.readResource("src/test/resources/CopyTest/expectedOutput/" + resource);
        Class<? extends DomainResource> resourceClass = resourceSrc.getClass().asSubclass(DomainResource.class);
        DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();

        copier.copy(resourceSrc, tgt, new Attribute("Condition.onset[x]", false));
        copier.copy(resourceSrc, tgt, new Attribute("Condition.meta", true));
        copier.copy(resourceSrc, tgt, new Attribute("Condition.id", true));
        copier.copy(resourceSrc, tgt, new Attribute("Condition.code", false));


        assertThat(fhirContext.newJsonParser().encodeResourceToString(resourceExpected))
                .isEqualTo(fhirContext.newJsonParser().encodeResourceToString(tgt));
    }

    @Test
    public void testChoiceSlicingFail() throws IOException, MustHaveViolatedException {

        Observation observation = (Observation) itSetup.readResource("src/test/resources/InputResources/Observation/Example-MI-Initiative-Laborprofile-Laborwerte.json");
        Observation expected = (Observation) itSetup.readResource("src/test/resources/CopyTest/expectedOutput/Observation.json");
        Observation tgt = new Observation();

        copier.copy(observation, tgt, new Attribute("Observation.value[x]:valueCodeableConcept", false));

        assertThat(fhirContext.newJsonParser().encodeResourceToString(expected))
                .isNotEqualTo(fhirContext.newJsonParser().encodeResourceToString(tgt));
    }

    @Test
    public void choiceSuccessSliceFound() throws IOException, MustHaveViolatedException {

        Observation observation = (Observation) itSetup.readResource("src/test/resources/InputResources/Observation/ObservationValueCodableConcept.json");
        Observation expected = (Observation) itSetup.readResource("src/test/resources/CopyTest/expectedOutput/ObservationCodeableConceptSlice.json");
        Observation tgt = new Observation();

        copier.copy(observation, tgt, new Attribute("Observation.value[x]:valueCodeableConcept.coding.display", true));

        assertThat(fhirContext.newJsonParser().encodeResourceToString(expected))
                .isEqualTo(fhirContext.newJsonParser().encodeResourceToString(tgt));
    }


    @Test
    public void testChoiceSlicingSuccess() throws IOException, MustHaveViolatedException {

        Observation observation = (Observation) itSetup.readResource("src/test/resources/InputResources/Observation/Example-MI-Initiative-Laborprofile-Laborwerte.json");
        Observation expected = (Observation) itSetup.readResource("src/test/resources/CopyTest/expectedOutput/Observation.json");
        Observation tgt = new Observation();

        copier.copy(observation, tgt, new Attribute("Observation.value[x]:valueQuantity", false));

        assertThat(fhirContext.newJsonParser().encodeResourceToString(expected))
                .isEqualTo(fhirContext.newJsonParser().encodeResourceToString(tgt));
    }


    @Test
    public void testPatternSlicing() throws IOException, MustHaveViolatedException {

        Condition condition = (Condition) itSetup.readResource("src/test/resources/InputResources/Condition/Diagnosis2.json");
        Condition expected = (Condition) itSetup.readResource("src/test/resources/CopyTest/expectedOutput/Diagnosis2Slice.json");
        Condition tgt = new Condition();

        copier.copy(condition, tgt, new Attribute("Condition.code.coding:icd10-gm", false));

        assertThat(fhirContext.newJsonParser().encodeResourceToString(expected))
                .isEqualTo(fhirContext.newJsonParser().encodeResourceToString(tgt));
    }


    @Test
    public void testIdentityList() throws IOException, NoSuchMethodException, MustHaveViolatedException, InvocationTargetException, InstantiationException, IllegalAccessException {


        DomainResource src = itSetup.readResource("src/test/resources/InputResources/Observation/Example-MI-Initiative-Laborprofile-Laborwerte-list.json");
        Observation expected = (Observation) itSetup.readResource("src/test/resources/CopyTest/expectedOutput/ObservationIdentityList.json");
        Class<? extends DomainResource> resourceClass = src.getClass().asSubclass(DomainResource.class);
        DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();

        copier.copy(src, tgt, new Attribute("Observation.identifier", false));
        copier.copy(src, tgt, new Attribute("Observation.referenceRange.low", false));
        copier.copy(src, tgt, new Attribute("Observation.referenceRange.high", false));
        copier.copy(src, tgt, new Attribute("Observation.interpretation", false));
        copier.copy(src, tgt, new Attribute("Observation.value[x]", false));

        assertThat(fhirContext.newJsonParser().encodeResourceToString(expected))
                .isEqualTo(fhirContext.newJsonParser().encodeResourceToString(tgt));
    }

    @Test
    public void testEncounter() throws MustHaveViolatedException, IOException {

        Encounter src = (Encounter) itSetup.readResource("src/test/resources/InputResources/Encounter/Encounter-mii-exa-fall-kontakt-gesundheitseinrichtung-2.json");
        Encounter expected = (Encounter) itSetup.readResource("src/test/resources/CopyTest/expectedOutput/Encounter.json");
        Encounter tgt = new Encounter();

        copier.copy(src, tgt, new Attribute("Encounter.diagnosis.use", false));

        assertThat(fhirContext.newJsonParser().encodeResourceToString(expected))
                .isEqualTo(fhirContext.newJsonParser().encodeResourceToString(tgt));
    }


    @Nested
    class Terser {

        @Test
        void choiceOperatorBaseLevel() throws MustHaveViolatedException {
            Observation src = new Observation();

            DateTimeType dateTime = new DateTimeType("2022-10-01");
            src.setEffective(dateTime);

            Meta meta = new Meta();
            meta.setProfile(List.of(
                    new CanonicalType(
                            "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab")
            ));
            src.setMeta(meta);
            Observation tgt = new Observation();


            copier.copy(src, tgt, new Attribute("Observation.effective[x]", false));
            copier.copy(src, tgt, new Attribute("Observation.meta", false));

            assertThat(fhirContext.newJsonParser().encodeResourceToString(tgt))
                    .isEqualTo(fhirContext.newJsonParser().encodeResourceToString(src));


        }


    }
}
