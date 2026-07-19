package de.medizininformatikinitiative.torch.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.ExclusionEvent;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionEvent;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.ResourceExclusionEvent;
import de.medizininformatikinitiative.torch.jobhandling.FileIo;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;




/**
 * Handles reading and writing of diagnostics to the file system.
 * <p>
 * During processing, diagnostics are written into a separate directory for each batch. This way, if something goes wrong
 * and a batch has to be re-processed, only its dedicated files need to be touched.
 * At the end of processing, the exclusions are merged across all batches and an additional summary is created.
 */
@Component
public class DiagnosticsStore {

    public static final String REPORTS_DIRECTORY = "reports";
    public static final String SUMMARY_FILE = "job-summary.json";
    public static final String RESOURCE_EXCLUSIONS_FILE = "resource-exclusions.csv";
    public static final String PATIENT_EXCLUSIONS_FILE = "patient-exclusions.csv";
    public static final String DETAILS_FILE = "details.json";

    private final FileIo io;
    private final ObjectMapper mapper;


    public DiagnosticsStore(FileIo io, ObjectMapper mapper) {
        this.io = requireNonNull(io);
        this.mapper = requireNonNull(mapper);
    }

    public void ensureDirectoryStructure(Path jobDir) throws IOException {
        io.createDirectories(reportDir(jobDir));
    }

    private Path reportDir(Path jobDir) {
        return jobDir.resolve(REPORTS_DIRECTORY);
    }

    private Path jobSummaryFile(Path jobDir) {
        return reportDir(jobDir).resolve(SUMMARY_FILE);
    }

    private Path intermediateResourceExclusionsFile(Path jobDir, String batchId) { return reportDir(jobDir).resolve(batchId).resolve(RESOURCE_EXCLUSIONS_FILE); }

    private Path intermediatePatientExclusionsFile(Path jobDir, String batchId) { return reportDir(jobDir).resolve(batchId).resolve(PATIENT_EXCLUSIONS_FILE); }
    private Path detailsFile(Path jobDir, String batchId) { return reportDir(jobDir).resolve(batchId).resolve(DETAILS_FILE); }

    /**
     * Writes diagnostics of a single batch to the file system.
     * <p>
     * Creates a new directory for the batch with one CSV file for resource exclusions, one for patient exclusions and
     * one JSON file for other measurements.
     *
     * @param diagnostics   the diagnostics of the batch to be written to the file system
     * @param jobDir        the directory of the job
     * @param batchId       the ID of the batch used to create the batch report directory
     * @throws IOException  if writing to the file system goes wrong
     */
    public void writeDiagnostics(BatchDiagnostics diagnostics, Path jobDir, String batchId) throws IOException {
        io.createDirectories(reportDir(jobDir).resolve(batchId));

        writeToCsv(Map.of(batchId, diagnostics.batchExclusions().getResourceExclusions()), intermediateResourceExclusionsFile(jobDir, batchId),
                ResourceExclusionEvent.getHeaderNames());
        writeToCsv(Map.of(batchId, diagnostics.batchExclusions().getPatientExclusions()), intermediatePatientExclusionsFile(jobDir, batchId),
                PatientExclusionEvent.getHeaderNames());
        writeBatchDetails(detailsFile(jobDir, batchId), diagnostics);
    }

    private void writeBatchDetails(Path file, BatchDiagnostics diagnostics) throws IOException {
        Path tmp = io.createTempFile(file.toFile()).toPath();
        try (Writer writer = io.newBufferedWriter(tmp)) {
            mapper.writeValue(writer, diagnostics.batchDetails());
        }

        io.atomicMove(tmp, file);
    }

    private BatchDetails readBatchDetails(File file) throws IOException {
        return mapper.readValue(file, BatchDetails.class);
    }

    /**
     * Writes resource exclusions of all batches into one file and patient exclusions into another file.
     *
     * @param diagnostics   a map from Batch-ID to the diagnostics of the batch
     * @param jobDir        the directory of the job
     * @throws IOException  if writing to the file system goes wrong
     */
    public void writeMergedExclusions(Map<String, BatchDiagnostics> diagnostics, Path jobDir) throws IOException {
        Map<String, List<ResourceExclusionEvent>> resourceExclusions = diagnostics.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().batchExclusions().getResourceExclusions()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<String, List<PatientExclusionEvent>> patientExclusions = diagnostics.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().batchExclusions().getPatientExclusions()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        writeToCsv(resourceExclusions, reportDir(jobDir).resolve(RESOURCE_EXCLUSIONS_FILE), ResourceExclusionEvent.getHeaderNames());
        writeToCsv(patientExclusions, reportDir(jobDir).resolve(PATIENT_EXCLUSIONS_FILE), PatientExclusionEvent.getHeaderNames());
    }

    /**
     * Writes a job summary to the file system.
     *
     * @param summary       the job summary to write to the file system.
     * @param jobDir        the directory of the job
     * @throws IOException  if writing to the file system goes wrong
     */
    public void writeSummary(JobDiagnosticSummary summary, Path jobDir) throws IOException {
        Path tmp = io.createTempFile(jobSummaryFile(jobDir).toFile()).toPath();

        try (Writer writer = io.newBufferedWriter(tmp)) {
            mapper.writeValue(writer, summary);
        }

        io.atomicMove(tmp, jobSummaryFile(jobDir));
    }

    /**
     * Reads a job summary from the file system.
     *
     * @param jobDir        the directory of the job
     * @return              the new job summary object read from the file system
     * @throws IOException  if writing to the file system goes wrong
     */
    public JobDiagnosticSummary readSummary(Path jobDir) throws IOException {
        return mapper.readValue(jobSummaryFile(jobDir).toFile(), JobDiagnosticSummary.class);
    }

    public boolean jobSummaryExists(Path jobDir) {
        return io.exists(jobSummaryFile(jobDir));
    }

    public boolean patientExclusionsExists(Path jobDir) {
        return io.exists(reportDir(jobDir).resolve(PATIENT_EXCLUSIONS_FILE));
    }

    public boolean resourceExclusionsExists(Path jobDir) {
        return io.exists(reportDir(jobDir).resolve(RESOURCE_EXCLUSIONS_FILE));
    }

    /**
     * Reads the separately stored diagnostics of all batches and deletes the intermediate batch report directories at
     * the end.
     *
     * @param jobDir                    the directory of the job
     * @return                          a new map from Batch-ID to its read batch diagnostics
     * @throws IOException              if reading from the file system or deleting the batch report directories goes wrong
     * @throws CsvValidationException   if reading the csv files goes wrong
     */
    public Map<String, BatchDiagnostics> loadAllDiagnostics(Path jobDir) throws IOException, CsvValidationException {
        Map<String, BatchDiagnostics> diagnosticsPerBatch = new HashMap<>();
        List<Path> batchReportDirs = io.listDirectories(reportDir(jobDir)).toList();
        for(Path batchDir : batchReportDirs) {
            String batchId = batchDir.getFileName().toString();
            BatchDiagnostics diagnostics = loadDiagnostics(
                    intermediateResourceExclusionsFile(jobDir, batchId).toFile(),
                    intermediatePatientExclusionsFile(jobDir, batchId).toFile(),
                    detailsFile(jobDir, batchId).toFile());

            diagnosticsPerBatch.put(batchId, diagnostics);
        }

        for(Path batchDir : batchReportDirs) {
            io.deleteDir(reportDir(jobDir).resolve(batchDir));
        }

        return diagnosticsPerBatch;
    }

    /**
     * Reads diagnostics of a single batch from the file system.
     * <p>
     * Expects diagnostics on the file system to be inside a directory for the batch with one CSV file for resource exclusions,
     * one for patient exclusions and  one JSON file for other measurements.
     *
     * @param resourceExclusionsFile     the file containing the resource exclusions of the batch
     * @param patientExclusionsFile     the file containing the patient exclusions of the batch
     * @param detailsFile               the file containing other measurements of the batch diagnostics
     * @return                          the new batch diagnostics object containing all information read from the files
     * @throws IOException              if reading from the file system goes wrong
     * @throws CsvValidationException   if reading the csv files goes wrong
     */
    private BatchDiagnostics loadDiagnostics(File resourceExclusionsFile,
                                                   File patientExclusionsFile,
                                                   File detailsFile) throws IOException, CsvValidationException{
        BatchDiagnostics diagnostics = BatchDiagnostics.empty();

        readCsv(resourceExclusionsFile, ResourceExclusionEvent::fromCsv, diagnostics.batchExclusions()::addResourceExclusion);
        readCsv(patientExclusionsFile, PatientExclusionEvent::fromCsv, diagnostics.batchExclusions()::addPatientExclusion);
        BatchDetails batchDetails = readBatchDetails(detailsFile);

        return diagnostics.setBatchDetails(batchDetails);
    }

    /**
     * Writes exclusions events as rows to a CSV file.
     * <p>
     * Creates a new file or overwrites it if it already exists. The Batch-ID is prepended to each row in the CSV file.
     *
     * @param <T>           the type of exclusion event to write
     * @param rowsPerBatch  a map from Batch-ID to exclusion events to write to the file
     * @param file          the file to write to (is newly created if it does not exist yet)
     * @param header        the human-readable header fields to write at the top of the file
     * @throws IOException  if writing to the file system goes wrong
     */
    private <T extends ExclusionEvent> void writeToCsv(Map<String, List<T>> rowsPerBatch, Path file, String[] header) throws IOException {
        Path tmp = io.createTempFile(file.toFile()).toPath();

        try(CSVWriter writer = new CSVWriter(io.newBufferedWriter(tmp))) {
            writer.writeNext(addBatchColumn(header));
            rowsPerBatch.forEach((batchId, rows) -> rows.stream()
                    .map(ExclusionEvent::toCsvElements)
                    .map(event -> addBatchId(event, batchId))
                    .forEach(writer::writeNext)
            );
        }

        io.atomicMove(tmp, file);
    }

    /**
     * Reads a CSV file and applies a decoding and a consuming function to each read row.
     *
     * @param file                      the CSV file to read from
     * @param decoder                   a function decoding the CSV fields of a row (an array of strings) to {@link  T}
     * @param consumer                  a function doing something with each newly created {@link T}
     * @param <T>                       the type to decode a row to (e.g. an exclusion event)
     * @throws IOException              if reading from the file system goes wrong
     * @throws CsvValidationException   if reading the csv files goes wrong
     */
    private <T> void readCsv(File file, Function<String[], T> decoder, Consumer<T> consumer) throws IOException, CsvValidationException {
        try(CSVReader reader = new CSVReader(new FileReader(file))) {
            reader.readNext(); // read over header
            reader.forEach(line -> {
                consumer.accept(decoder.apply(line));
            });
        }
    }

    private String[] addBatchColumn(String[] header) {
        return ArrayUtils.insert(0, header, "Batch-ID");
    }

    private String[] addBatchId(String[] csvElements, String batchId) {
        return ArrayUtils.insert(0, csvElements, batchId);
    }
}
