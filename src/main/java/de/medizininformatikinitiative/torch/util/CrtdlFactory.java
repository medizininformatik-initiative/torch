package de.medizininformatikinitiative.torch.util;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.DataExtraction;

import java.util.List;

public class CrtdlFactory {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Returns a Crtdl instance with empty/default fields.
     */
    public static Crtdl empty() {
        JsonNode emptyCohort = objectMapper.createObjectNode(); // empty JSON
        DataExtraction emptyExtraction = new DataExtraction(List.of());  // minimal instance
        return new Crtdl(emptyCohort, emptyExtraction);
    }
}
