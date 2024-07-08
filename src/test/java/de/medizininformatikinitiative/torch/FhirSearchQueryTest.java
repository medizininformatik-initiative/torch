package de.medizininformatikinitiative.torch;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.model.Attribute;
import de.medizininformatikinitiative.torch.model.AttributeGroup;
import de.medizininformatikinitiative.torch.model.Crtdl;
import de.medizininformatikinitiative.torch.util.FhirSearchBuilder;
import de.medizininformatikinitiative.torch.model.*;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toCollection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


/**
 * Test the creation of fhr search strings
 *
 */
public class FhirSearchQueryTest extends BaseTest{
    private final ObjectMapper objectMapper = new ObjectMapper();


    @Test
    public void testCondition() {
        try (FileInputStream fis = new FileInputStream(new File("src/test/resources/CRTDL/CRTDL_diagnosis_withoutCCDL.json"))) {
            Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);
            assertNotNull(crtdl);
            AttributeGroup group1 =crtdl.getDataExtraction().getAttributeGroups().get(0);
            AttributeGroup group2 =crtdl.getDataExtraction().getAttributeGroups().get(0);
            Attribute attribute1 = group1.getAttributes().get(0);
            assertEquals("Condition.code", attribute1.getAttributeRef());
            FhirSearchBuilder searchBuilder = new FhirSearchBuilder();
            List<String> batches = searchBuilder.getSearchBatches(group1,Stream.of("1", "2", "3", "4", "5", "7", "8", "9", "10").collect(toCollection(ArrayList::new)),2);
            System.out.println(batches.size());
            System.out.println(batches.get(0));
            assertEquals(5, batches.size());
            batches.addAll(searchBuilder.getSearchBatches(group1,Stream.of("1", "2", "3", "4", "5", "7", "8", "9", "10").collect(toCollection(ArrayList::new)),2));
            assertEquals(10, batches.size());

        } catch (Exception e) {
            e.printStackTrace();
        }


    }


}

