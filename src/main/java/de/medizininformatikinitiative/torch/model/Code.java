package de.medizininformatikinitiative.torch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.config.SpringContext;
import de.numcodex.sq2cql.model.common.TermCode;
import de.numcodex.sq2cql.model.structured_query.ContextualTermCode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Code {

    // No-argument constructor
    public Code() {
    }

    // Constructor for direct instantiation (for testing)
    public Code(String system, String code) {
        this.system = system;
        this.code = code;
    }

    @JsonProperty("code")
    private String code;

    @JsonProperty("system")
    private String system;

    @JsonProperty("display")
    private String display;


    public static Code of(ContextualTermCode t) {
        Code codeObj = new Code();
        codeObj.system = t.termCode().system();
        codeObj.code = t.termCode().code();
        codeObj.display = t.termCode().display();
        return codeObj;
    }

    public String getCodeURL(){
        String encodedString = "";
        try {
            encodedString = URLEncoder.encode(system + "|" + code, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return encodedString;

    }

    public ContextualTermCode getContextualTermCode() {
        var context = SpringContext.getCodeSystemToContextMapping().getContext(system, code);
        var termCode = TermCode.of(system, code, "");
        return ContextualTermCode.of(context, termCode);
    }

    // Getters and Setters
}
