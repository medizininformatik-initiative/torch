package de.medizininformatikinitiative;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.CDSStructureDefinitionHandler;
import de.medizininformatikinitiative.util.Redaction;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static de.medizininformatikinitiative.util.ResourceReader.readResource;
import static org.junit.jupiter.api.Assertions.*;



public class RedactTest extends BaseTest {

    @Test
    public void testDiagnosis() {

            Redaction redaction = new Redaction(CDS);


            String [] resources =  {"Diagnosis1.json","Diagnosis2.json"};

            Arrays.stream(resources).forEach(resource ->{
                try {

                DomainResource resourcesrc = (DomainResource) readResource("src/test/resources/InputResources/Condition/"+resource);
                DomainResource resourceexpected = (DomainResource) readResource("src/test/resources/RedactTest/expectedOutput/"+resource);
                resourcesrc =(DomainResource) redaction.redact(resourcesrc,"",1);
                assertTrue(resourcesrc.equalsDeep(resourceexpected),resource+" Expected not equal to actual output");
            } catch (Exception e) {
                e.printStackTrace();
            }

            });





    }






}

