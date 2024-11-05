package de.medizininformatikinitiative.torch.cql;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Library;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hl7.fhir.r4.model.Bundle.BundleType.TRANSACTION;
import static org.hl7.fhir.r4.model.Bundle.HTTPVerb.POST;

@Component
public class FhirHelper {
    private static final Logger logger = LoggerFactory.getLogger(FhirHelper.class);

    private static final String CQL_REPRESENTATION = "text/cql";
    private final FhirContext context;

    public FhirHelper(FhirContext context) {
        this.context = context;
    }

    /**
     * Add the CQL query to a {@link Library}
     *
     * @param library the {@link Library} to add the CQL string to
     * @param cql     the CQL string to add
     * @return the {@link Library} with the added CQL
     */
    private static Library appendCql(Library library, String cql) {
        library.getContentFirstRep().setContentType(CQL_REPRESENTATION);
        library.getContentFirstRep().setData(cql.getBytes(UTF_8));
        return library;
    }

    /**
     * Create a {@link Bundle} of a {@link Library} and a {@link Measure}
     *
     * @param library the {@link Library} to add to the {@link Bundle}
     * @param measure the {@link Measure} to add to the {@link Bundle}
     * @return the {@link Bundle}, consisting of the given {@link Library} and {@link Measure}
     */
    private static Bundle bundleLibraryAndMeasure(Library library, Measure measure) {
        var bundle = new Bundle();
        bundle.setType(TRANSACTION);
        bundle.addEntry().setResource(library).getRequest().setMethod(POST).setUrl("Library");
        bundle.addEntry().setResource(measure).getRequest().setMethod(POST).setUrl("Measure");
        return bundle;
    }

    /**
     * Read file contents as String
     *
     * @param fileName name of the resource file
     * @return the String contents of the file
     */
    public static String getResourceFileAsString(String fileName) throws IOException {
        InputStream is = getResourceFileAsInputStream(fileName);
        if (is != null) {
            return new String(is.readAllBytes(), UTF_8);
        } else {
            throw new RuntimeException("File not found in classpath: " + fileName);
        }
    }

    /**
     * Read file contents as {@link InputStream}
     *
     * @param fileName name of the resource file
     * @return an {@link InputStream} of the file
     */
    private static InputStream getResourceFileAsInputStream(String fileName) {
        return CqlClient.class.getResourceAsStream(fileName);
    }

    /**
     * Create a {@link Bundle} with predefined library and measure URI, as well as CQL String
     *
     * @param cql        the plaintext cql definition
     * @param libraryUri a library uri {@link String} to be included in the {@link Bundle}
     * @param measureUri a measure uri {@link String} to be included in the {@link Bundle}
     * @return the {@link Bundle}, consisting of a {@link Library} and {@link Measure}, containing the submitted values
     */
    public Bundle createBundle(String cql, String libraryUri, String measureUri) throws IOException {
        var library = appendCql(parseResource(Library.class,
                getResourceFileAsString("Library.json")).setUrl(libraryUri), cql);
        var measure = parseResource(Measure.class,
                getResourceFileAsString("Measure.json"))
                .setUrl(measureUri)
                .addLibrary(libraryUri);
        logger.debug("Measure {}", measure);
        return bundleLibraryAndMeasure(library, measure);
    }

    public Parameters getListExecutionParams() throws IOException {

        return parseResource(org.hl7.fhir.r4.model.Parameters.class, getResourceFileAsString("PatientListParams.json"));

    }

    /**
     * Parse a String as an {@link IBaseResource} implementation
     *
     * @param type  the concrete {@link IBaseResource} implementation class to parse to
     * @param input the {@link String} to parse
     * @param <T>   any implementation of {@link IBaseResource}
     * @return the wanted {@link IBaseResource} implementation object
     */
    private <T extends IBaseResource> T parseResource(Class<T> type, String input) {
        var parser = context.newJsonParser();
        return type.cast(parser.parseResource(input));
    }
}
