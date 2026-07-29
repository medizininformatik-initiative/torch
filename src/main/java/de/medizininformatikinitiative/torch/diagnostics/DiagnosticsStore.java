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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

@Component
public class DiagnosticsStore {
    // TODO make configurable?
    private static final String REPORT_DIR_NAME = "reports";
    private static final String REPORT_JOB_FILE_NAME = "job-summary.json";
    private static final String RESOURCE_EXCLUSIONS_FILE = "resource-exclusions.csv";
    private static final String PATIENT_EXCLUSIONS_FILE = "patient-exclusions.csv";
    private static final String DETAILS_FILE = "details.json"; // TODO rename?

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
        return jobDir.resolve(REPORT_DIR_NAME);
    }

    private Path jobSummaryFile(Path jobDir) {
        return reportDir(jobDir).resolve(REPORT_JOB_FILE_NAME);
    }

    private Path resourceExclusionsFile(Path jobDir, String batchId) { return reportDir(jobDir).resolve(batchId).resolve(RESOURCE_EXCLUSIONS_FILE); }

    private Path patientExclusionsFile(Path jobDir, String batchId) { return reportDir(jobDir).resolve(batchId).resolve(PATIENT_EXCLUSIONS_FILE); }
    private Path detailsFile(Path jobDir, String batchId) { return reportDir(jobDir).resolve(batchId).resolve(DETAILS_FILE); }

    public void writeDiagnostics(BatchDiagnostics diagnostics,
                                 Path jobDir,
                                        String batchId) throws IOException {
        io.createDirectories(reportDir(jobDir).resolve(batchId));

        writeToCsv(resourceExclusionsFile(jobDir, batchId), diagnostics.batchExclusions().getResourceExclusions(), ResourceExclusionEvent.getHeaderNames(), batchId);
        writeToCsv(patientExclusionsFile(jobDir, batchId), diagnostics.batchExclusions().getPatientExclusions(), PatientExclusionEvent.getHeaderNames(), batchId);
        writeBatchDetails(detailsFile(jobDir, batchId), diagnostics);
    }

    public void writeBatchDetails(Path file, BatchDiagnostics diagnostics) throws IOException {
        try (Writer writer = io.newBufferedWriter(file)) {
            mapper.writeValue(writer, diagnostics.batchDetails());
        }
    }

    public BatchDetails readBatchDetails(File file) throws IOException {
        return mapper.readValue(file, BatchDetails.class);
    }

    public void writeMergedExclusions(Map<String, BatchDiagnostics> diagnostics, Path jobDir) {
        diagnostics.forEach((batchId, batchDiagnostics) -> {
            try {
                writeToCsv(reportDir(jobDir).resolve(RESOURCE_EXCLUSIONS_FILE), batchDiagnostics.batchExclusions().getResourceExclusions(), ResourceExclusionEvent.getHeaderNames(), batchId);
                writeToCsv(reportDir(jobDir).resolve(PATIENT_EXCLUSIONS_FILE), batchDiagnostics.batchExclusions().getPatientExclusions(), PatientExclusionEvent.getHeaderNames(), batchId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void writeSummary(JobDiagnosticSummary summary, Path jobDir) throws IOException {
        try (Writer writer = io.newBufferedWriter(jobSummaryFile(jobDir))) {
            mapper.writeValue(writer, summary);
        }
    }

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

    public Map<String, BatchDiagnostics> loadAllDiagnostics(Path jobDir) throws IOException {
        return io.listDirectories(reportDir(jobDir)).map(batchDir -> {
            try {
                String batchId = batchDir.getFileName().toString();
                BatchDiagnostics diagnostics = loadDiagnostics(
                        resourceExclusionsFile(jobDir, batchId).toFile(),
                        patientExclusionsFile(jobDir, batchId).toFile(),
                        detailsFile(jobDir, batchId).toFile());

                return Map.entry(batchId, diagnostics);
            } catch (IOException | CsvValidationException e) {
                throw new RuntimeException(e); // TODO better?
            }
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Removes the per-batch report directories loaded by {@link #loadAllDiagnostics(Path)}.
     *
     * <p>Call only after the merged exclusions and job summary derived from those batches have been
     * written successfully, so a failure in between leaves the per-batch source data recoverable.
     */
    public void deleteBatchDiagnostics(Set<String> batchIds, Path jobDir) throws IOException {
        for (String batchId : batchIds) {
            io.deleteDir(reportDir(jobDir).resolve(batchId));
        }
    }

    private BatchDiagnostics loadDiagnostics(File resouceExclusionsFile,
                                                   File patientExclusionsFile,
                                                   File detailsFile) throws IOException, CsvValidationException{
        BatchDiagnostics diagnostics = BatchDiagnostics.empty();

        readCsv(resouceExclusionsFile, ResourceExclusionEvent::fromCsv, diagnostics.batchExclusions()::addResourceExclusion);
        readCsv(patientExclusionsFile, PatientExclusionEvent::fromCsv, diagnostics.batchExclusions()::addPatientExclusion);
        BatchDetails batchDetails = readBatchDetails(detailsFile);

        return diagnostics.setBatchDetails(batchDetails);
    }

    private <T extends ExclusionEvent> void writeToCsv(Path file, List<T> rows, String[] header, String batchId) throws IOException {
        boolean writeHeader  = !io.exists(file);
        try(CSVWriter writer = new CSVWriter(new FileWriter(file.toFile(), true))) {
            if (writeHeader)
                writer.writeNext(addBatchColumn(header));
            rows.stream()
                    .map(ExclusionEvent::toCsvElements)
                    .map(event -> addBatchId(event, batchId))
                    .forEach(writer::writeNext);
        }
    }

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
