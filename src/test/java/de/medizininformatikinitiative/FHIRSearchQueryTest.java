package de.medizininformatikinitiative;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.CDSStructureDefinitionHandler;
import de.medizininformatikinitiative.util.FHIRSearchBuilder;
import de.medizininformatikinitiative.util.model.Attribute;
import de.medizininformatikinitiative.util.model.CRTDL;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toCollection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;



public class FHIRSearchQueryTest extends BaseTest{
    private final ObjectMapper objectMapper = new ObjectMapper();


    @Test
    public void testCondition() {
        try (FileInputStream fis = new FileInputStream(new File("src/test/resources/CRTDL/CRTDL_diagnosis_withoutCCDL.json"))) {
            CRTDL CRTDL = objectMapper.readValue(fis, CRTDL.class);
            assertNotNull(CRTDL);
            Attribute attribute1 = CRTDL.getCohortDefinition().getDataExtraction().getAttributeGroups().get(0).getAttributes().get(0);
            assertEquals("Condition.code", attribute1.getAttributeRef());
            FHIRSearchBuilder searchBuilder = new FHIRSearchBuilder(CRTDL, CDS);
            List<String> batches = searchBuilder.getSearchBatches(Stream.of("1", "2", "3", "4", "5", "7", "8", "9", "10").collect(toCollection(ArrayList::new)), "http://testserver.com/fhir/condition", 2);
            System.out.println(batches.size());
            System.out.println(batches.get(0));
            assertEquals(15, batches.size());
        } catch (Exception e) {
            e.printStackTrace();
        }


    }


}

