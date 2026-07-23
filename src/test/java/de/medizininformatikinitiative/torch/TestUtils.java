package de.medizininformatikinitiative.torch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.BatchExclusions;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionEvent;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.ResourceExclusionEvent;
import de.medizininformatikinitiative.torch.jobhandling.JobParameters;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedDataExtraction;
import org.hl7.fhir.r4.model.Base64BinaryType;
import org.hl7.fhir.r4.model.Parameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestUtils {

    public static JsonNode nodeFromTreeString(String s) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(s);
    }

    public static JsonNode nodeFromValueString(String s) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.valueToTree(s);
    }

    private static String slurp(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    public static Parameters loadCrtdl(String path) throws IOException {
        var crtdl = Base64.getEncoder().encodeToString(slurp(path).getBytes(StandardCharsets.UTF_8));

        var parameters = new Parameters();
        parameters.setParameter("crtdl", new Base64BinaryType(crtdl));
        return parameters;
    }

    public static JobParameters emptyJobParams() {
        return new JobParameters(
                new AnnotatedCrtdl(
                        JsonNodeFactory.instance.objectNode(),
                        new AnnotatedDataExtraction(List.of()),
                        Optional.empty()
                ), List.of(), null
        );
    }

    public static <T> void readCsv(File file, Function<String[], T> decoder, Consumer<T> consumer) throws IOException, CsvValidationException {
        try(CSVReader reader = new CSVReader(new FileReader(file))) {
            reader.readNext(); // read over header
            reader.forEach(line -> {
                consumer.accept(decoder.apply(line));
            });
        }
    }

    public static <T> void readCsv(String exclusions, Function<String[], T> decoder, Consumer<T> consumer) throws IOException, CsvValidationException {
        try(CSVReader reader = new CSVReader(new StringReader(exclusions))) {
            reader.readNext(); // read over header
            reader.forEach(line -> {
                consumer.accept(decoder.apply(line));
            });
        }
    }

    /**
     * Helper method that reads the merged patient exclusions and resource exclusions.
     * <p>
     * There is no method for this in the main code because these files are only written and not read by torch (reading
     * happens solely by nginx).
     *
     * @param resourceExclusionsFile the resource exclusions file containing merged exclusions from multiple batches
     * @param patientExclusionsFile the patient exclusions file containing merged exclusions from multiple batches
     * @return                      a new BatchExclusions object containing both resource and patient exclusions
     * @throws CsvValidationException    If bad things happen during the read in the CSV reader
     * @throws IOException               If a user-defined validator fails in the CSV reader
     */
     public static BatchExclusions readMergedDiagnostics(File resourceExclusionsFile, File patientExclusionsFile) throws CsvValidationException, IOException {
        var exclusions = BatchExclusions.empty();
        readCsv(resourceExclusionsFile, ResourceExclusionEvent::fromCsv, exclusions::addResourceExclusion);
        readCsv(patientExclusionsFile, PatientExclusionEvent::fromCsv, exclusions::addPatientExclusion);

        return exclusions;
    }

    /**
     * Helper method that reads the merged patient exclusions and resource exclusions from strings.
     *
     * @param resourceExclusions     the resource exclusions containing merged exclusions from multiple batches
     * @param patientExclusions     the patient exclusions containing merged exclusions from multiple batches
     * @return                      a new BatchExclusions object containing both resource and patient exclusions
     * @throws CsvValidationException    If bad things happen during the read in the CSV reader
     * @throws IOException               If a user-defined validator fails in the CSV reader
     */
    public static BatchExclusions readMergedDiagnostics(String resourceExclusions, String patientExclusions) throws CsvValidationException, IOException {
        var exclusions = BatchExclusions.empty();
        readCsv(resourceExclusions, ResourceExclusionEvent::fromCsv, exclusions::addResourceExclusion);
        readCsv(patientExclusions, PatientExclusionEvent::fromCsv, exclusions::addPatientExclusion);

        return exclusions;
    }

    public static <T> List<T> concat(List<T> a, List<T> b) {
        return Stream.concat(a.stream(), b.stream()).collect(Collectors.toList());
    }

    public static long toMillis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }
}
