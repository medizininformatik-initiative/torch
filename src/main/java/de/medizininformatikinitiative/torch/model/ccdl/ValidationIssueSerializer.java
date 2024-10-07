package de.medizininformatikinitiative.torch.model.ccdl;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

/**
 * Custom Serializer for {@link ValidationIssue} to add prefix to code and replace boolean with yes/no.
 */
public class ValidationIssueSerializer extends StdSerializer<ValidationIssue> {

    public ValidationIssueSerializer() {
        this(null);
    }

    public ValidationIssueSerializer(Class<ValidationIssue> t) {
        super(t);
    }

    @Override
    public void serialize(ValidationIssue validationIssue, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("code", "VAL-" + validationIssue.code());
        jsonGenerator.writeStringField("detail", validationIssue.detail());
        jsonGenerator.writeEndObject();
    }
}
