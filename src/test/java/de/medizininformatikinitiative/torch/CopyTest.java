package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.model.*;
import de.medizininformatikinitiative.torch.util.ResourceReader;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;



public class CopyTest extends BaseTest{
    private static final Logger logger = LoggerFactory.getLogger(CopyTest.class);

    @Test
    public void testDefinitionIsContained() {
        StructureDefinition definition = cds.getDefinition("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose");
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
                copier.copy(resourceSrc, tgt, new Attribute("Condition.onset[x]:onsetDateTime", false));
                copier.copy(resourceSrc, tgt, new Attribute("Condition.meta", true));
                copier.copy(resourceSrc, tgt, new Attribute("Condition.id", true));
                copier.copy(resourceSrc, tgt, new Attribute("Condition.code", false));

                assertNotNull(tgt);

                assertEquals(parser.setPrettyPrint(true).encodeResourceToString(tgt), parser.setPrettyPrint(true).encodeResourceToString(resourceExpected), resource + " Expected not equal to actual output");
            } catch (Exception e) {
                e.printStackTrace();
            }

        });
    }


    @Test
    public void testObservation() {
        try {
            DomainResource resourcesrc = (DomainResource) ResourceReader.readResource("src/test/resources/InputResources/Observation/Example-MI-Initiative-Laborprofile-Laborwerte.json");
            Class<? extends DomainResource> resourceClass = resourcesrc.getClass().asSubclass(DomainResource.class);
            DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();

            copier.copy(resourcesrc, tgt, new Attribute("Observation.referenceRange.low", false));
            copier.copy(resourcesrc, tgt, new Attribute("Observation.referenceRange.high", false));
            copier.copy(resourcesrc, tgt, new Attribute("Observation.interpretation", false));
            assertNotNull(tgt);
           // System.out.println(parser.setPrettyPrint(true).encodeResourceToString(tgt));
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    @Test
    public void testEncounter() {
        try {
            DomainResource resourcesrc = (DomainResource) ResourceReader.readResource("src/test/resources/InputResources/Encounter/Encounter-mii-exa-fall-kontakt-gesundheitseinrichtung-2.json");
            Class<? extends DomainResource> resourceClass = resourcesrc.getClass().asSubclass(DomainResource.class);

            DomainResource tgt = resourceClass.getDeclaredConstructor().newInstance();

            copier.copy(resourcesrc, tgt, new Attribute("Encounter.diagnosis.use", false));
            assertNotNull(tgt);
            //logger.debug(parser.setPrettyPrint(true).encodeResourceToString(tgt));
        } catch (Exception e) {
            e.printStackTrace();
        }


    }


}

