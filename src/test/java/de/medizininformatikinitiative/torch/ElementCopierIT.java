package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static org.assertj.core.api.Fail.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ElementCopierIT {
    private static final Logger logger = LoggerFactory.getLogger(ElementCopierIT.class);

    // Create an instance of BaseTestSetup
    private final IntegrationTestSetup itSetup = new IntegrationTestSetup();

    private final ElementCopier copier = new ElementCopier(itSetup.getCds(), itSetup.fhirContext(), itSetup.fhirPathBuilder());

    //TODO To be put in another test class
    @Test
    public void testDefinitionIsContained() {
        StructureDefinition definition = itSetup.getCds().getDefinition("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose");
        assertNotNull(definition, "The element should be contained in the map");
        assertEquals(ResourceType.StructureDefinition, definition.getResourceType(), "Resource type should be StructureDefinition");
    }

    //TODO Parameterized Test or no loop
    //TODO Single Copy with special Data types: mustHave, BackBone, Choice Elements, smaller Tests, mit HAPI aufbauen (deepEquals?)
    //TODO For future AssertJ
    @Test
    public void testDiagnosis() {
        String[] resources = {"Diagnosis1.json"};
        Arrays.stream(resources).forEach(resource -> {
            try {
                DomainResource resourceSrc = itSetup.readResource("src/test/resources/InputResources/Condition/" + resource);
                DomainResource resourceExpected = itSetup.readResource("src/test/resources/CopyTest/expectedOutput/" + resource);
                Class<? extends DomainResource> resourceClass = resourceSrc.getClass().asSubclass(DomainResource.class);
                DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();

                copier.copy(resourceSrc, tgt, new Attribute("Condition.onset[x]", false));
                copier.copy(resourceSrc, tgt, new Attribute("Condition.meta", true));
                copier.copy(resourceSrc, tgt, new Attribute("Condition.id", true));
                copier.copy(resourceSrc, tgt, new Attribute("Condition.code", false));

                assertNotNull(tgt);
                assertEquals(
                        itSetup.fhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(resourceExpected),
                        itSetup.fhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt),
                        resource + " Expected not equal to actual output"
                );

            } catch (Exception e) {
                logger.error("", e);
                fail("Deserialization failed: " + e.getMessage(), e);
            }
        });
    }

    @Test
    public void testObservation() {
        try {
            DomainResource resourcesrc = itSetup.readResource("src/test/resources/InputResources/Observation/Example-MI-Initiative-Laborprofile-Laborwerte.json");
            Class<? extends DomainResource> resourceClass = resourcesrc.getClass().asSubclass(DomainResource.class);
            DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();

            copier.copy(resourcesrc, tgt, new Attribute("Observation.referenceRange.low", false));
            copier.copy(resourcesrc, tgt, new Attribute("Observation.referenceRange.high", false));
            copier.copy(resourcesrc, tgt, new Attribute("Observation.interpretation", false));
            copier.copy(resourcesrc, tgt, new Attribute("Observation.value[x]:valueCodeableConcept.coding.display", false));
            copier.copy(resourcesrc, tgt, new Attribute("Observation.value[x]", false));

            assertNotNull(tgt);
        } catch (Exception e) {
            logger.error("", e);
            fail("Deserialization failed: " + e.getMessage(), e);
        }
    }

    @Test
    public void testIdentityList() {
        try {
            DomainResource resourcesrc = itSetup.readResource("src/test/resources/InputResources/Observation/Example-MI-Initiative-Laborprofile-Laborwerte-list.json");
            Class<? extends DomainResource> resourceClass = resourcesrc.getClass().asSubclass(DomainResource.class);
            DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();

            copier.copy(resourcesrc, tgt, new Attribute("Observation.identifier", false));
            copier.copy(resourcesrc, tgt, new Attribute("Observation.referenceRange.low", false));
            copier.copy(resourcesrc, tgt, new Attribute("Observation.referenceRange.high", false));
            copier.copy(resourcesrc, tgt, new Attribute("Observation.interpretation", false));
            copier.copy(resourcesrc, tgt, new Attribute("Observation.value[x]:valueCodeableConcept.coding.display", false));
            copier.copy(resourcesrc, tgt, new Attribute("Observation.value[x]", false));

            assertNotNull(tgt);
            logger.info(itSetup.fhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(tgt));
        } catch (Exception e) {
            logger.error("", e);
            fail("Deserialization failed: " + e.getMessage(), e);
        }
    }

    @Test
    public void testEncounter() {
        try {
            DomainResource resourcesrc = itSetup.readResource("src/test/resources/InputResources/Encounter/Encounter-mii-exa-fall-kontakt-gesundheitseinrichtung-2.json");
            Class<? extends DomainResource> resourceClass = resourcesrc.getClass().asSubclass(DomainResource.class);
            DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();

            copier.copy(resourcesrc, tgt, new Attribute("Encounter.diagnosis.use", false));
            assertNotNull(tgt);
        } catch (Exception e) {
            logger.error("", e);
            fail("Deserialization failed: " + e.getMessage(), e);
        }
    }
}
