package de.medizininformatikinitiative.torch;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.model.Attribute;
import de.medizininformatikinitiative.torch.model.AttributeGroup;
import de.medizininformatikinitiative.torch.model.Crtdl;
import de.medizininformatikinitiative.torch.util.FhirSearchBuilder;
import org.junit.jupiter.api.Test;

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
        try (FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_diagnosis_date_code_filter.json")) {
            Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);
            assertNotNull(crtdl);
            AttributeGroup group1 = crtdl.getDataExtraction().getAttributeGroups().getFirst();
            Attribute attribute1 = group1.getAttributes().getFirst();
            assertEquals("Condition.code", attribute1.getAttributeRef());
            FhirSearchBuilder searchBuilder = new FhirSearchBuilder();
            List<String> batches = searchBuilder.getSearchBatches(group1,Stream.of("1", "2", "3", "4", "5", "7", "8", "9", "10").collect(toCollection(ArrayList::new)),2);
            System.out.println(batches.size());
            System.out.println(batches.getFirst());
            assertEquals(5, batches.size());
            batches.addAll(searchBuilder.getSearchBatches(group1,Stream.of("1", "2", "3", "4", "5", "7", "8", "9", "10").collect(toCollection(ArrayList::new)),2));
            assertEquals(10, batches.size());

        } catch (Exception e) {
           logger.error("",e);
        }


    }


}

