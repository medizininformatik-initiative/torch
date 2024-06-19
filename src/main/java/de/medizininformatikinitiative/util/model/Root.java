import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.util.CRTDL.CohortDefinition;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Root {
    @JsonProperty("version")
    private String version;

    @JsonProperty("display")
    private String display;

    @JsonProperty("cohortDefinition")
    private CohortDefinition cohortDefinition;

    // Getters and Setters
}
