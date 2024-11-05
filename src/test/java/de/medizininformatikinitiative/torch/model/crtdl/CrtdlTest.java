package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class CrtdlTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    public void testCondition() throws Exception {
        try (FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_diagnosis_basic_date.json")) {
            Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);

            assertThat(crtdl.dataExtraction().attributeGroups().getFirst().attributes())
                    .containsExactly(new Attribute("Condition.code", false));
        }
    }

    @Test
    public void testObservation() throws Exception {
        try (FileInputStream fis = new FileInputStream("src/test/resources/CRTDL/CRTDL_observation.json")) {
            Crtdl crtdl = objectMapper.readValue(fis, Crtdl.class);

            assertThat(crtdl.dataExtraction().attributeGroups().getFirst().attributes())
                    .containsExactly(new Attribute("Observation.code", false),
                            new Attribute("Observation.encounter", false),
                            new Attribute("Observation.value[x]", true));
        }
    }

    // TODO: test consentKey()
}
