import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.CDSStructureDefinitionHandler;
import de.medizininformatikinitiative.CopyRedactExecutor;
import de.medizininformatikinitiative.util.CRTDL.Attribute;
import de.medizininformatikinitiative.util.CRTDL.AttributeGroup;
import de.medizininformatikinitiative.util.FhirExtensionsUtil;
import de.medizininformatikinitiative.util.Redaction;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class CopyRedactManualTest {







    private CDSStructureDefinitionHandler CDS;

    private IParser parser;

    private FhirContext ctx;
    public CopyRedactManualTest() {
        ctx=FhirContext.forR4();
        parser = ctx.newJsonParser();
        CDS= new CDSStructureDefinitionHandler();
        try {

            CDS.readStructureDefinition("src/test/resources/StructureDefinitions/StructureDefinition-mii-pr-person-patient.json");
            CDS.readStructureDefinition("src/test/resources/StructureDefinitions/Profile-DiagnosticReportLab.json");
            CDS.readStructureDefinition("src/test/resources/StructureDefinitions/Profile-ObservationLab.json");
            CDS.readStructureDefinition("src/test/resources/StructureDefinitions/Profile-ServiceRequestLab.json");
            CDS.readStructureDefinition("src/test/resources/StructureDefinitions/Profile-ServiceRequestLab.json");
            CDS.readStructureDefinition("src/test/resources/StructureDefinitions/StructureDefinition-mii-pr-diagnose-condition.json");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }



    @Test
    public void testManualCopyOnDiagnosis() {

        //parse CDS from file

        StructureDefinition definition = CDS.getDefinition("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose");
        assertNotNull(definition, "The element should be contained in the map");



        assertEquals(ResourceType.StructureDefinition, definition.getResourceType(), "Resource type should be StructureDefinition");
        Class<? extends DomainResource> resourceClass = (Class<? extends DomainResource>) ctx.getResourceDefinition(definition.getType()).getImplementingClass();
        DomainResource resource;
        try {
            resource = resourceClass.getDeclaredConstructor().newInstance();
            // Check that the resource is a Diagnostic Report
            assertEquals("Condition", definition.getType(), "Resource type should be Condition");
            resource.setProperty("code",new CodeableConcept().setId("Test"));
            resource.setId("TestID");
            resource.setMeta(new Meta().addProfile("https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose"));
            //resource.setProperty("identifier",new Identifier().setValue("Testing ID321"));
            resource.setProperty("subject",new Reference().setReference("Patient/123"));
            resource.setProperty("onset[x]",new DateTimeType("2024-10"));

            DomainResource copyDestination = resourceClass.getDeclaredConstructor().newInstance();




            assertEquals("Identifier",resource.getChildByName("identifier").getTypeCode());

            System.out.println(parser.setPrettyPrint(true).encodeResourceToString(resource));

            String TestString="{\n" +
                    "  \"resourceType\": \"Condition\",\n" +
                    "  \"id\": \"mii-exa-diagnose-condition-minimal\",\n" +
                    "  \"meta\": {\n" +
                    "    \"profile\": [\n" +
                    "      \"https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose\"\n" +
                    "    ]\n" +
                    "  },\n" +
                    "  \"clinicalStatus\": {\n" +
                    "    \"coding\": [\n" +
                    "      {\n" +
                    "        \"code\": \"active\",\n" +
                    "        \"system\": \"http://terminology.hl7.org/CodeSystem/condition-clinical\"\n" +
                    "      }\n" +
                    "    ]\n" +
                    "  },\n" +
                    "  \"code\": {\n" +
                    "    \"coding\": [\n" +
                    "      {\n" +
                    "        \"system\": \"http://fhir.de/CodeSystem/bfarm/icd-10-gm\",\n" +
                    "        \"display\": \"Prellung des Ellenbogens\"\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"code\": \"91613004\",\n" +
                    "        \"system\": \"http://snomed.info/sct\",\n" +
                    "        \"version\": \"http://snomed.info/sct/900000000000207008/version/20230731\",\n" +
                    "        \"display\": \"Contusion of elbow (disorder)\"\n" +
                    "      }\n" +
                    "    ],\n" +
                    "    \"text\": \"Prellung des linken Ellenbogens\"\n" +
                    "  },\n" +
                    "  \"subject\": {\n" +
                    "    \"reference\": \"Patient/12345\"\n" +
                    "  },\n" +
                    "  \"encounter\": {\n" +
                    "    \"reference\": \"Encounter/12345\"\n" +
                    "  },\n" +
                    "  \"onsetPeriod\": {\n" +
                    "    \"end\": \"2020-03-05T13:00:00+01:00\"\n" +
                    "  }"+
                    "}\n";

            Redaction redaction = new Redaction(CDS);
            resource= (DomainResource) redaction.redact(resource,"",1);
            // Add more assertions as needed for other elements
            resource = (DomainResource) parser.parseResource(TestString);
            resource= (DomainResource) redaction.redact(resource,"",1);

            System.out.println(parser.setPrettyPrint(true).encodeResourceToString(resource));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }





}

