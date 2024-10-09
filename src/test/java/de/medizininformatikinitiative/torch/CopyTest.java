package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.model.Attribute;
import de.medizininformatikinitiative.torch.setup.BaseTestSetup;
import de.medizininformatikinitiative.torch.util.ResourceReader;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CopyTest {
    private static final Logger logger = LoggerFactory.getLogger(CopyTest.class);

    // Create an instance of BaseTestSetup
    private final BaseTestSetup baseTestSetup = new BaseTestSetup();

    @Test
    public void testDefinitionIsContained() {
        StructureDefinition definition = baseTestSetup.getCds().getDefinition("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose");
        assertNotNull(definition, "The element should be contained in the map");
        assertEquals(ResourceType.StructureDefinition, definition.getResourceType(), "Resource type should be StructureDefinition");
    }

    @Test
    public void testDiagnosis() {
        String[] resources = {"Diagnosis1.json"};
        Arrays.stream(resources).forEach(resource -> {
            try {
                DomainResource resourceSrc = (DomainResource) ResourceReader.readResource("src/test/resources/InputResources/Condition/" + resource);
                DomainResource resourceExpected = (DomainResource) ResourceReader.readResource("src/test/resources/CopyTest/expectedOutput/"+resource);
                Class<? extends DomainResource> resourceClass = resourceSrc.getClass().asSubclass(DomainResource.class);
                DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();

                baseTestSetup.getCopier().copy(resourceSrc, tgt, new Attribute("Condition.onset[x]", false));
                baseTestSetup.getCopier().copy(resourceSrc, tgt, new Attribute("Condition.meta", true));
                baseTestSetup.getCopier().copy(resourceSrc, tgt, new Attribute("Condition.id", true));
                baseTestSetup.getCopier().copy(resourceSrc, tgt, new Attribute("Condition.code", false));

                assertNotNull(tgt);
                assertEquals(
                        baseTestSetup.getFhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(resourceExpected),
                        baseTestSetup.getFhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt),
                        resource + " Expected not equal to actual output"
                );

            } catch (Exception e) {
                logger.error("", e);
            }
        });
    }

    @Test
    public void testObservation() {
        try {
            DomainResource resourcesrc = (DomainResource) ResourceReader.readResource("src/test/resources/InputResources/Observation/Example-MI-Initiative-Laborprofile-Laborwerte.json");
            Class<? extends DomainResource> resourceClass = resourcesrc.getClass().asSubclass(DomainResource.class);
            DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();

            baseTestSetup.getCopier().copy(resourcesrc, tgt, new Attribute("Observation.referenceRange.low", false));
            baseTestSetup.getCopier().copy(resourcesrc, tgt, new Attribute("Observation.referenceRange.high", false));
            baseTestSetup.getCopier().copy(resourcesrc, tgt, new Attribute("Observation.interpretation", false));
            baseTestSetup.getCopier().copy(resourcesrc,tgt,new Attribute("Observation.value[x]:valueCodeableConcept.coding.display",false));
            baseTestSetup.getCopier().copy(resourcesrc,tgt,new Attribute("Observation.value[x]",false));

            assertNotNull(tgt);
        } catch (Exception e) {
            logger.error("", e);
        }
    }

    @Test
    public void testIdentityList() {
        try {
            DomainResource resourcesrc = (DomainResource) ResourceReader.readResource("src/test/resources/InputResources/Observation/Example-MI-Initiative-Laborprofile-Laborwerte-list.json");
            Class<? extends DomainResource> resourceClass = resourcesrc.getClass().asSubclass(DomainResource.class);
            DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();

            baseTestSetup.getCopier().copy(resourcesrc, tgt, new Attribute("Observation.identifier", false));
            baseTestSetup.getCopier().copy(resourcesrc, tgt, new Attribute("Observation.referenceRange.low", false));
            baseTestSetup.getCopier().copy(resourcesrc, tgt, new Attribute("Observation.referenceRange.high", false));
            baseTestSetup.getCopier().copy(resourcesrc, tgt, new Attribute("Observation.interpretation", false));
            baseTestSetup.getCopier().copy(resourcesrc,tgt,new Attribute("Observation.value[x]:valueCodeableConcept.coding.display",false));
            baseTestSetup.getCopier().copy(resourcesrc,tgt,new Attribute("Observation.value[x]",false));

            assertNotNull(tgt);
            logger.info(baseTestSetup.getFhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt));
        } catch (Exception e) {
            logger.error("", e);
        }
    }

    @Test
    public void testEncounter() {
        try {
            DomainResource resourcesrc = (DomainResource) ResourceReader.readResource("src/test/resources/InputResources/Encounter/Encounter-mii-exa-fall-kontakt-gesundheitseinrichtung-2.json");
            Class<? extends DomainResource> resourceClass = resourcesrc.getClass().asSubclass(DomainResource.class);
            DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();

            baseTestSetup.getCopier().copy(resourcesrc, tgt, new Attribute("Encounter.diagnosis.use", false));
            assertNotNull(tgt);
        } catch (Exception e) {
            logger.error("", e);
        }
    }
}
