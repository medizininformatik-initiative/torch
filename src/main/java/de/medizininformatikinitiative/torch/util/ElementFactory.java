package de.medizininformatikinitiative.torch.util;

import org.bouncycastle.oer.its.etsi102941.Url;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Factory;
import org.hl7.fhir.r4.model.StringType;

public class ElementFactory {

    Factory factory = new Factory();

    /**
     * Creates an empty Base of a given Type.
     * Currently it only calls the factory.create method.
     *
     * @param type
     * @return Base object of the given type
     */
    public Base createElement(String type) {
        if (type != "BackboneElement") {
            return (Base) factory.create(type);
        } else {
            //TODO Custom Backbone Element handler
            return null;
        }

    }

    public Base stringtoPrimitive(String string,String type){
        switch (type) {
            case "dateTime":
                return new DateTimeType(string);

            // Add any other FHIR primitives here as needed
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }


    }

    public Base caster(Base base, String type) {
        switch (type) {
            case "boolean":
                return base.castToBoolean(base);
            case "integer":
                return base.castToInteger(base);
            case "string":
                return base.castToString(base);
            case "decimal":
                return base.castToDecimal(base);
            case "uri":
                return base.castToUri(base);
            case "dateTime":
                return base.castToDateTime(base);
            case "date":
                return base.castToDate(base);
            case "time":
                return base.castToTime(base);
            case "code":
                return base.castToCode(base);
            case "oid":
                return base.castToOid(base);
            case "id":
                return base.castToId(base);
            case "markdown":
                return base.castToMarkdown(base);
            case "unsignedInt":
                return base.castToUnsignedInt(base);
            case "positiveInt":
                return base.castToPositiveInt(base);
            case "instant":
                return base.castToInstant(base);
            case "url":
                return base.castToUrl(base);
            case "canonical":
                return base.castToCanonical(base);
            case "base64Binary":
                return base.castToBase64Binary(base);
            // Add any other FHIR primitives here as needed
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

}
