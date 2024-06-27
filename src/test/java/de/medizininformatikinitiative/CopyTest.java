package de.medizininformatikinitiative;

import de.medizininformatikinitiative.model.*;
import de.medizininformatikinitiative.util.ElementCopier;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static de.medizininformatikinitiative.util.ResourceReader.readResource;
import static org.junit.jupiter.api.Assertions.*;



public class CopyTest extends BaseTest{

    @Test
    public void testDefinitionIsContained() {
        StructureDefinition definition = cds.getDefinition("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose");
        assertNotNull(definition, "The element should be contained in the map");
        assertEquals(ResourceType.StructureDefinition, definition.getResourceType(), "Resource type should be StructureDefinition");
    }


    @Test
    public void testDiagnosis() {
        ElementCopier copier = new ElementCopier(cds);
        String[] resources = {"Diagnosis1.json"};
        Arrays.stream(resources).forEach(resource -> {
            try {
                DomainResource resourceSrc = (DomainResource) readResource("src/test/resources/InputResources/Condition/" + resource);
                DomainResource resourceExpected = (DomainResource) readResource("src/test/resources/CopyTest/expectedOutput/"+resource);
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
        ElementCopier copier = new ElementCopier(cds);
        try {
            DomainResource resourcesrc = (DomainResource) readResource("src/test/resources/InputResources/Observation/Example-MI-Initiative-Laborprofile-Laborwerte.json");
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


}

