package de.medizininformatikinitiative;

import de.medizininformatikinitiative.util.Redaction;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static de.medizininformatikinitiative.util.ResourceReader.readResource;
import static org.junit.jupiter.api.Assertions.*;



public class RedactTest extends BaseTest {

    @Test
    public void testDiagnosis() {

            Redaction redaction = new Redaction(cds);


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

