package de.medizininformatikinitiative.torch.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import de.medizininformatikinitiative.torch.diagnostics.consent.ConsentConsideredResourceEvent;
import de.medizininformatikinitiative.torch.diagnostics.consent.ConsentDiagnostics;
import de.medizininformatikinitiative.torch.diagnostics.consent.FinalPeriodEvent;
import de.medizininformatikinitiative.torch.diagnostics.consent.RawProvisionEvent;
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
import java.util.Comparator;
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
    public static final String RAW_PROVISIONS_FILE = "raw-provisions.csv";
    public static final String FINAL_PERIODS_FILE = "final-periods.csv";
    public static final String CONSENT_CONSIDERED_RESOURCES_FILE = "consent-considered-resources.csv";
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
    private Path intermediateRawProvisionsFile(Path jobDir, String batchId) { return reportDir(jobDir).resolve(batchId).resolve(RAW_PROVISIONS_FILE); }
    private Path intermediateFinalPeriodsFile(Path jobDir, String batchId) { return reportDir(jobDir).resolve(batchId).resolve(FINAL_PERIODS_FILE); }
    private Path intermediateConsentConsideredResourcesFile(Path jobDir, String batchId) { return reportDir(jobDir).resolve(batchId).resolve(CONSENT_CONSIDERED_RESOURCES_FILE); }
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
                ResourceExclusionEvent.getHeaderNames(), ResourceExclusionEvent::toCsvElements);
        writeToCsv(Map.of(batchId, diagnostics.batchExclusions().getPatientExclusions()), intermediatePatientExclusionsFile(jobDir, batchId),
                PatientExclusionEvent.getHeaderNames(), PatientExclusionEvent::toCsvElements);
        writeToCsv(Map.of(batchId, diagnostics.consentDiagnostics().getRawProvisions()), intermediateRawProvisionsFile(jobDir, batchId),
                RawProvisionEvent.getHeaderNames(), RawProvisionEvent::toCsvElements);
        writeToCsv(Map.of(batchId, diagnostics.consentDiagnostics().getFinalPeriods()), intermediateFinalPeriodsFile(jobDir, batchId),
                FinalPeriodEvent.getHeaderNames(), FinalPeriodEvent::toCsvElements);
        writeToCsv(Map.of(batchId, diagnostics.consentDiagnostics().getConsideredResources()), intermediateConsentConsideredResourcesFile(jobDir, batchId),
                ConsentConsideredResourceEvent.getHeaderNames(), ConsentConsideredResourceEvent::toCsvElements);
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
     * Writes resource exclusions and patient exclusions of all batches each into their own job-wide file, plus,
     * when opt-in consent diagnostics are present, one job-wide file each for raw provisions, final periods and
     * consent-considered resources.
     *
     * @param diagnostics   a map from Batch-ID to the diagnostics of the batch
     * @param jobDir        the directory of the job
     * @throws IOException  if writing to the file system goes wrong
     */
    public void writeMergedExclusions(Map<String, BatchDiagnostics> diagnostics, Path jobDir) throws IOException {
        writeToCsv(mergeByBatch(diagnostics, d -> d.batchExclusions().getResourceExclusions()),
                reportDir(jobDir).resolve(RESOURCE_EXCLUSIONS_FILE), ResourceExclusionEvent.getHeaderNames(), ResourceExclusionEvent::toCsvElements);
        writeToCsv(mergeByBatch(diagnostics, d -> d.batchExclusions().getPatientExclusions()),
                reportDir(jobDir).resolve(PATIENT_EXCLUSIONS_FILE), PatientExclusionEvent.getHeaderNames(), PatientExclusionEvent::toCsvElements);

        writeSortedByPatientIfNotEmpty(mergeByBatch(diagnostics, d -> d.consentDiagnostics().getRawProvisions()),
                reportDir(jobDir).resolve(RAW_PROVISIONS_FILE), RawProvisionEvent.getHeaderNames(),
                RawProvisionEvent::toCsvElements, RawProvisionEvent::patientId);
        writeSortedByPatientIfNotEmpty(mergeByBatch(diagnostics, d -> d.consentDiagnostics().getFinalPeriods()),
                reportDir(jobDir).resolve(FINAL_PERIODS_FILE), FinalPeriodEvent.getHeaderNames(),
                FinalPeriodEvent::toCsvElements, FinalPeriodEvent::patientId);
        writeSortedByPatientIfNotEmpty(mergeByBatch(diagnostics, d -> d.consentDiagnostics().getConsideredResources()),
                reportDir(jobDir).resolve(CONSENT_CONSIDERED_RESOURCES_FILE), ConsentConsideredResourceEvent.getHeaderNames(),
                ConsentConsideredResourceEvent::toCsvElements, ConsentConsideredResourceEvent::patientId);
    }

    private <T> Map<String, List<T>> mergeByBatch(Map<String, BatchDiagnostics> diagnostics, Function<BatchDiagnostics, List<T>> rowsOf) {
        return diagnostics.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> rowsOf.apply(e.getValue())));
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

    public boolean rawProvisionsExists(Path jobDir) {
        return io.exists(reportDir(jobDir).resolve(RAW_PROVISIONS_FILE));
    }

    public boolean finalPeriodsExists(Path jobDir) {
        return io.exists(reportDir(jobDir).resolve(FINAL_PERIODS_FILE));
    }

    public boolean consentConsideredResourcesExists(Path jobDir) {
        return io.exists(reportDir(jobDir).resolve(CONSENT_CONSIDERED_RESOURCES_FILE));
    }

    /**
     * Reads the separately stored diagnostics of all batches.
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
                    intermediateRawProvisionsFile(jobDir, batchId).toFile(),
                    intermediateFinalPeriodsFile(jobDir, batchId).toFile(),
                    intermediateConsentConsideredResourcesFile(jobDir, batchId).toFile(),
                    detailsFile(jobDir, batchId).toFile());

            diagnosticsPerBatch.put(batchId, diagnostics);
        }

        return diagnosticsPerBatch;
    }

    /**
     * Deletes the intermediate batch report directories.
     *
     * @param jobDir        the directory of the job
     * @throws IOException  if deleting the batch report directories goes wrong
     */
    public void deleteIntermediateDiagnostics(Path jobDir) throws IOException {
        for(Path batchDir : io.listDirectories(reportDir(jobDir)).toList()) {
            io.deleteDir(reportDir(jobDir).resolve(batchDir));
        }
    }

    /**
     * Reads diagnostics of a single batch from the file system.
     * <p>
     * Expects diagnostics on the file system to be inside a directory for the batch with one CSV file for resource exclusions,
     * one for patient exclusions and  one JSON file for other measurements.
     *
     * @param resourceExclusionsFile        the file containing the resource exclusions of the batch
     * @param patientExclusionsFile         the file containing the patient exclusions of the batch
     * @param rawProvisionsFile             the file containing the batch's raw consent provisions, absent for a
     *                                       batch directory written before this file existed
     * @param finalPeriodsFile              the file containing the batch's final consent periods, absent for a
     *                                       batch directory written before this file existed
     * @param consentConsideredResourcesFile the file containing the batch's per-resource consent decisions,
     *                                       absent for a batch directory written before this file existed
     * @param detailsFile                   the file containing other measurements of the batch diagnostics
     * @return                          the new batch diagnostics object containing all information read from the files
     * @throws IOException              if reading from the file system goes wrong
     * @throws CsvValidationException   if reading the csv files goes wrong
     */
    private BatchDiagnostics loadDiagnostics(File resourceExclusionsFile,
                                                   File patientExclusionsFile,
                                                   File rawProvisionsFile,
                                                   File finalPeriodsFile,
                                                   File consentConsideredResourcesFile,
                                                   File detailsFile) throws IOException, CsvValidationException{
        BatchDiagnostics diagnostics = BatchDiagnostics.empty().withConsentDiagnosticsEnabled(true);

        readCsv(resourceExclusionsFile, ResourceExclusionEvent::fromCsv, diagnostics.batchExclusions()::addResourceExclusion);
        readCsv(patientExclusionsFile, PatientExclusionEvent::fromCsv, diagnostics.batchExclusions()::addPatientExclusion);
        readCsvIfExists(rawProvisionsFile, RawProvisionEvent::fromCsv, diagnostics.consentDiagnostics()::addRawProvision);
        readCsvIfExists(finalPeriodsFile, FinalPeriodEvent::fromCsv, diagnostics.consentDiagnostics()::addFinalPeriod);
        readCsvIfExists(consentConsideredResourcesFile, ConsentConsideredResourceEvent::fromCsv, diagnostics.consentDiagnostics()::addConsideredResource);
        BatchDetails batchDetails = readBatchDetails(detailsFile);

        return diagnostics.setBatchDetails(batchDetails);
    }

    /**
     * Reads a CSV file like {@link #readCsv}, but treats a missing file as containing zero rows instead of
     * failing — a batch directory written before this file existed (a job in flight across a TORCH upgrade)
     * has none of the opt-in consent-diagnostics files at all, which is equivalent to empty diagnostics.
     *
     * @throws IOException              if reading from the file system goes wrong
     * @throws CsvValidationException   if reading the csv files goes wrong
     */
    private <T> void readCsvIfExists(File file, Function<String[], T> decoder, Consumer<T> consumer) throws IOException, CsvValidationException {
        if (!file.exists()) {
            return;
        }
        readCsv(file, decoder, consumer);
    }

    /**
     * Writes diagnostic rows to a CSV file.
     * <p>
     * Creates a new file or overwrites it if it already exists. The Batch-ID is prepended to each row in the CSV file.
     *
     * @param <T>             the type of row to write
     * @param rowsPerBatch    a map from Batch-ID to rows to write to the file
     * @param file            the file to write to (is newly created if it does not exist yet)
     * @param header          the human-readable header fields to write at the top of the file
     * @param toCsvElements   converts a row to its ordered CSV column elements
     * @throws IOException  if writing to the file system goes wrong
     */
    private <T> void writeToCsv(Map<String, List<T>> rowsPerBatch, Path file, String[] header, Function<T, String[]> toCsvElements) throws IOException {
        Path tmp = io.createTempFile(file.toFile()).toPath();

        try(CSVWriter writer = new CSVWriter(io.newBufferedWriter(tmp))) {
            writer.writeNext(addBatchColumn(header));
            rowsPerBatch.forEach((batchId, rows) -> rows.stream()
                    .map(toCsvElements)
                    .map(event -> addBatchId(event, batchId))
                    .forEach(writer::writeNext)
            );
        }

        io.atomicMove(tmp, file);
    }

    /**
     * Writes diagnostic rows like {@link #writeToCsv}, but ordered by Patient-ID across all batches so that each
     * patient's rows are contiguous, and skips the file entirely when there are no rows (consent diagnostics
     * were not requested).
     *
     * @param <T>             the type of row to write
     * @param rowsPerBatch    a map from Batch-ID to rows to write to the file
     * @param file            the file to write to (is newly created if it does not exist yet)
     * @param header          the human-readable header fields to write at the top of the file
     * @param toCsvElements   converts a row to its ordered CSV column elements
     * @param patientIdOf     extracts the Patient-ID to sort by; rows of the same patient keep their recorded order
     * @throws IOException  if writing to the file system goes wrong
     */
    private <T> void writeSortedByPatientIfNotEmpty(Map<String, List<T>> rowsPerBatch, Path file, String[] header,
                                                    Function<T, String[]> toCsvElements, Function<T, String> patientIdOf) throws IOException {
        List<Map.Entry<String, T>> rows = rowsPerBatch.entrySet().stream()
                .flatMap(e -> e.getValue().stream().map(row -> Map.entry(e.getKey(), row)))
                .sorted(Comparator.comparing(e -> patientIdOf.apply(e.getValue())))
                .toList();
        if (rows.isEmpty()) {
            return;
        }

        Path tmp = io.createTempFile(file.toFile()).toPath();

        try(CSVWriter writer = new CSVWriter(io.newBufferedWriter(tmp))) {
            writer.writeNext(addBatchColumn(header));
            rows.forEach(e -> writer.writeNext(addBatchId(toCsvElements.apply(e.getValue()), e.getKey())));
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
