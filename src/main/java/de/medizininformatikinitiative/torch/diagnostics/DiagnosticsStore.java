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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
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
    public static final String REGISTERED_PATIENTS_FILE = "registered-patients.csv";
    public static final String CONSENT_TRAIL_DIRECTORY = "consent-trail";
    public static final String DETAILS_FILE = "details.json";

    private static final String[] INITIAL_PROVISION_PERIODS_TRAIL_HEADER = {"Code", "Permit", "Period-Start", "Period-End", "Consent-ID"};
    private static final String[] FINAL_NON_CONTINUOUS_PERIODS_TRAIL_HEADER = {"From", "To"};
    private static final String[] CONSENT_CONSIDERED_RESOURCES_TRAIL_HEADER = {"ID", "Survived", "Date"};

    /** FHIR {@code id} datatype format (HL7 R4 §Element.id) — enforced before a patient ID is used as a path segment. */
    private static final Pattern FHIR_ID_PATTERN = Pattern.compile("^[A-Za-z0-9\\-.]{1,64}$");

    private static final Logger logger = LoggerFactory.getLogger(DiagnosticsStore.class);

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
    private Path intermediateRegisteredPatientsFile(Path jobDir, String batchId) { return reportDir(jobDir).resolve(batchId).resolve(REGISTERED_PATIENTS_FILE); }
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
        writeRegisteredPatients(diagnostics.consentDiagnostics().getPatientIds(), intermediateRegisteredPatientsFile(jobDir, batchId));
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
     * when opt-in consent diagnostics are present, a per-patient {@value #CONSENT_TRAIL_DIRECTORY} folder for
     * side-by-side debugging of one patient at a time.
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

        writeConsentTrailPerPatient(
                mergeByBatch(diagnostics, d -> d.consentDiagnostics().getRawProvisions()),
                mergeByBatch(diagnostics, d -> d.consentDiagnostics().getFinalPeriods()),
                mergeByBatch(diagnostics, d -> d.consentDiagnostics().getConsideredResources()),
                mergeByBatch(diagnostics, d -> d.consentDiagnostics().getPatientIds()),
                jobDir);
    }

    private <T> Map<String, List<T>> mergeByBatch(Map<String, BatchDiagnostics> diagnostics, Function<BatchDiagnostics, List<T>> rowsOf) {
        return diagnostics.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> rowsOf.apply(e.getValue())));
    }

    /**
     * Splits raw provisions, final periods and per-resource consent decisions into one folder per patient — the
     * "Initial Provision Periods", "Final Non-Continuous Periods" and "Consent-Considered Resources" files from
     * issue #1254 — small enough to open side by side without scrolling past other patients' rows. A pure
     * filter/regroup over already-written rows, no consent logic is re-derived.
     * <p>
     * A patient with zero raw provisions and zero final periods (their consent calculation genuinely produced
     * nothing) still gets a folder with header-only files, as long as {@code ConsentHandler} registered them —
     * that emptiness is itself diagnostic information, not the absence of a patient to report on.
     *
     * @throws IOException if writing to the file system goes wrong
     */
    private void writeConsentTrailPerPatient(Map<String, List<RawProvisionEvent>> rawProvisionsByBatch,
                                             Map<String, List<FinalPeriodEvent>> finalPeriodsByBatch,
                                             Map<String, List<ConsentConsideredResourceEvent>> consideredResourcesByBatch,
                                             Map<String, List<String>> registeredPatientIdsByBatch,
                                             Path jobDir) throws IOException {
        Map<String, List<RawProvisionEvent>> rawProvisionsByPatient = groupByPatient(rawProvisionsByBatch, RawProvisionEvent::patientId);
        Map<String, List<FinalPeriodEvent>> finalPeriodsByPatient = groupByPatient(finalPeriodsByBatch, FinalPeriodEvent::patientId);
        Map<String, List<ConsentConsideredResourceEvent>> consideredResourcesByPatient = groupByPatient(consideredResourcesByBatch, ConsentConsideredResourceEvent::patientId);

        Set<String> patientIds = new HashSet<>();
        patientIds.addAll(rawProvisionsByPatient.keySet());
        patientIds.addAll(finalPeriodsByPatient.keySet());
        patientIds.addAll(consideredResourcesByPatient.keySet());
        registeredPatientIdsByBatch.values().forEach(patientIds::addAll);

        for (String patientId : patientIds) {
            // the FHIR id charset alone still permits "." and ".." — the two reserved single-segment
            // names a filesystem treats specially, so reject them explicitly on top of the charset check
            if (!FHIR_ID_PATTERN.matcher(patientId).matches() || patientId.equals(".") || patientId.equals("..")) {
                logger.warn("Skipping consent trail folder for patient ID with unexpected format: {}", patientId);
                continue;
            }
            Path patientDir = reportDir(jobDir).resolve(CONSENT_TRAIL_DIRECTORY).resolve(patientId);
            io.createDirectories(patientDir);

            writeCsvNoBatchId(rawProvisionsByPatient.getOrDefault(patientId, List.of()),
                    patientDir.resolve(RAW_PROVISIONS_FILE), INITIAL_PROVISION_PERIODS_TRAIL_HEADER, DiagnosticsStore::trailRow);
            writeCsvNoBatchId(finalPeriodsByPatient.getOrDefault(patientId, List.of()),
                    patientDir.resolve(FINAL_PERIODS_FILE), FINAL_NON_CONTINUOUS_PERIODS_TRAIL_HEADER, DiagnosticsStore::trailRow);
            writeCsvNoBatchId(consideredResourcesByPatient.getOrDefault(patientId, List.of()),
                    patientDir.resolve(CONSENT_CONSIDERED_RESOURCES_FILE), CONSENT_CONSIDERED_RESOURCES_TRAIL_HEADER, DiagnosticsStore::trailRow);
        }
    }

    private <T> Map<String, List<T>> groupByPatient(Map<String, List<T>> rowsByBatch, Function<T, String> patientIdOf) {
        return rowsByBatch.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(patientIdOf));
    }

    private static String[] trailRow(RawProvisionEvent e) {
        return new String[]{e.code(), Boolean.toString(e.permit()), e.periodStart().toString(), e.periodEnd().toString(), e.consentId()};
    }

    private static String[] trailRow(FinalPeriodEvent e) {
        return new String[]{e.periodStart().toString(), e.periodEnd().toString()};
    }

    private static String[] trailRow(ConsentConsideredResourceEvent e) {
        return new String[]{e.resourceId(), Boolean.toString(e.included()), e.date()};
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

    public boolean consentTrailExists(Path jobDir) {
        return io.exists(reportDir(jobDir).resolve(CONSENT_TRAIL_DIRECTORY));
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
            if (batchId.equals(CONSENT_TRAIL_DIRECTORY)) {
                continue; // permanent per-patient output from a previous merge, not a per-batch intermediate directory
            }
            BatchDiagnostics diagnostics = loadDiagnostics(
                    intermediateResourceExclusionsFile(jobDir, batchId).toFile(),
                    intermediatePatientExclusionsFile(jobDir, batchId).toFile(),
                    intermediateRawProvisionsFile(jobDir, batchId).toFile(),
                    intermediateFinalPeriodsFile(jobDir, batchId).toFile(),
                    intermediateConsentConsideredResourcesFile(jobDir, batchId).toFile(),
                    intermediateRegisteredPatientsFile(jobDir, batchId).toFile(),
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
            if (batchDir.getFileName().toString().equals(CONSENT_TRAIL_DIRECTORY)) {
                continue; // permanent per-patient output, not a per-batch intermediate directory
            }
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
     * @param registeredPatientsFile        the file containing the batch's registered-for-consent-evaluation
     *                                       patient IDs, absent for a batch directory written before this file existed
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
                                                   File registeredPatientsFile,
                                                   File detailsFile) throws IOException, CsvValidationException{
        BatchDiagnostics diagnostics = BatchDiagnostics.empty().withConsentDiagnosticsEnabled(true);

        readCsv(resourceExclusionsFile, ResourceExclusionEvent::fromCsv, diagnostics.batchExclusions()::addResourceExclusion);
        readCsv(patientExclusionsFile, PatientExclusionEvent::fromCsv, diagnostics.batchExclusions()::addPatientExclusion);
        readCsvIfExists(rawProvisionsFile, RawProvisionEvent::fromCsv, diagnostics.consentDiagnostics()::addRawProvision);
        readCsvIfExists(finalPeriodsFile, FinalPeriodEvent::fromCsv, diagnostics.consentDiagnostics()::addFinalPeriod);
        readCsvIfExists(consentConsideredResourcesFile, ConsentConsideredResourceEvent::fromCsv, diagnostics.consentDiagnostics()::addConsideredResource);
        readRegisteredPatientsIfExists(registeredPatientsFile, diagnostics.consentDiagnostics()::registerPatient);
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
     * Writes diagnostic rows to a CSV file with no Batch-ID/Patient-ID column, for output already scoped to a
     * single patient (or otherwise not needing that grouping key restated on every row).
     *
     * @param <T>             the type of row to write
     * @param rows            the rows to write to the file
     * @param file            the file to write to (is newly created if it does not exist yet)
     * @param header          the human-readable header fields to write at the top of the file
     * @param toCsvElements   converts a row to its ordered CSV column elements
     * @throws IOException  if writing to the file system goes wrong
     */
    private <T> void writeCsvNoBatchId(List<T> rows, Path file, String[] header, Function<T, String[]> toCsvElements) throws IOException {
        Path tmp = io.createTempFile(file.toFile()).toPath();

        try(CSVWriter writer = new CSVWriter(io.newBufferedWriter(tmp))) {
            writer.writeNext(header);
            rows.stream().map(toCsvElements).forEach(writer::writeNext);
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

    /**
     * Writes the patient IDs registered for consent evaluation in this batch, one per line under a header —
     * a plain list rather than a full {@link de.medizininformatikinitiative.torch.diagnostics.exclusions.CsvDefinition}-backed
     * row type, since a well-formed FHIR ID needs no column structure or quoting (the ID charset excludes
     * commas). A patient ID that doesn't match that charset is skipped rather than written verbatim — unlike
     * the {@code id} datatype, the {@code patient} request parameter is a plain FHIR {@code string}, which
     * permits embedded newlines that would otherwise split into extra bogus "patients" on the line-based
     * read-back in {@link #readRegisteredPatientsIfExists}.
     *
     * @throws IOException  if writing to the file system goes wrong
     */
    private void writeRegisteredPatients(List<String> patientIds, Path file) throws IOException {
        Path tmp = io.createTempFile(file.toFile()).toPath();

        try (Writer writer = io.newBufferedWriter(tmp)) {
            writer.write("Patient-ID");
            writer.write(System.lineSeparator());
            for (String patientId : patientIds) {
                if (!FHIR_ID_PATTERN.matcher(patientId).matches()) {
                    logger.warn("Skipping registration of patient ID with unexpected format: {}", patientId);
                    continue;
                }
                writer.write(patientId);
                writer.write(System.lineSeparator());
            }
        }

        io.atomicMove(tmp, file);
    }

    /**
     * Reads registered patient IDs written by {@link #writeRegisteredPatients}, tolerating a missing file
     * (a batch directory written before this file existed) as containing zero registrations.
     *
     * @throws IOException  if reading from the file system goes wrong
     */
    private void readRegisteredPatientsIfExists(File file, Consumer<String> consumer) throws IOException {
        if (!file.exists()) {
            return;
        }
        try (var lines = io.lines(file.toPath())) {
            lines.skip(1).forEach(consumer); // skip header
        }
    }

    private String[] addBatchColumn(String[] header) {
        return ArrayUtils.insert(0, header, "Batch-ID");
    }

    private String[] addBatchId(String[] csvElements, String batchId) {
        return ArrayUtils.insert(0, csvElements, batchId);
    }
}
