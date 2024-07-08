package de.medizininformatikinitiative.torch;

import de.medizininformatikinitiative.torch.util.Redaction;
import de.medizininformatikinitiative.torch.util.ResourceReader;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;



public class RedactTest extends BaseTest {

    @Test
    public void testDiagnosis() {

            Redaction redaction = new Redaction(cds);


            String [] resources =  {"Diagnosis1.json","Diagnosis2.json"};

            Arrays.stream(resources).forEach(resource ->{
                try {

                DomainResource resourceSrc = (DomainResource) ResourceReader.readResource("src/test/resources/InputResources/Condition/"+resource);
                DomainResource resourceExpected = (DomainResource) ResourceReader.readResource("src/test/resources/RedactTest/expectedOutput/"+resource);
                resourceSrc =(DomainResource) redaction.redact(resourceSrc,"",1);
                assertTrue(resourceSrc.equalsDeep(resourceExpected),resource+" Expected not equal to actual output");
            } catch (Exception e) {
                e.printStackTrace();
            }

            });





    }






}

