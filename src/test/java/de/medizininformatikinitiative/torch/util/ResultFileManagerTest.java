package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ResultFileManagerTest {
    static final String RESULTS_DIR = "ResultFileManagerDir";
    static final String ERROR_FILE = "error.json";
    static final HttpStatus HTTP_OK = HttpStatus.OK;

    ResultFileManager resultFileManager = new ResultFileManager(RESULTS_DIR, "PT20S", FhirContext.forR4(), "hostname", "fileServerName");

    @BeforeEach
    void setUp() throws IOException {
        var dirFile = new File(RESULTS_DIR);
        if (dirFile.exists()) {
            FileSystemUtils.deleteRecursively(dirFile);
        }
        Files.createDirectory(new File(RESULTS_DIR).toPath());
    }

    @AfterEach
    void tearDown() {
        FileSystemUtils.deleteRecursively(new File(RESULTS_DIR));
    }

    private String readJobErrorFile(String jobDir) throws IOException {
        try (Stream<String> lines = Files.lines(Path.of(RESULTS_DIR, jobDir, ERROR_FILE))) {
            var linesList = lines.toList();
            if (linesList.isEmpty()) {
                return "";
            } else {
                return linesList.getFirst();
            }
        }

    }

    @Test
    void testSaveErrorToJson() throws IOException {
        var jobId = "job-102903";
        var operationOutcome = new OperationOutcome();

        resultFileManager.saveErrorToJson(jobId, operationOutcome, HTTP_OK).block();

        assertThat(readJobErrorFile(jobId)).isEqualTo(fhirParser().encodeResourceToString(operationOutcome));
    }

    @Test
    void testLoadErrorDirect() throws IOException {
        var jobId = "job-110619";
        var error = "error-110656";
        Files.createDirectories(Path.of(RESULTS_DIR, jobId));
        Files.writeString(Path.of(RESULTS_DIR, jobId, ERROR_FILE), error);

        var loadedError = resultFileManager.loadErrorFromFileSystem(jobId);

        assertThat(loadedError).isEqualTo(error);
    }

    @Test
    void testLoadErrorFileNotExists() {
        var jobId = "job-110619";

        var loadedError = resultFileManager.loadErrorFromFileSystem(jobId);

        assertThat(fhirParser().parseResource(loadedError)).isInstanceOf(OperationOutcome.class);
    }

    @Test
    void testSaveAndLoad() {
        var jobId = "job-102903";
        var operationOutcome = new OperationOutcome();

        resultFileManager.saveErrorToJson(jobId, operationOutcome, HTTP_OK).block();
        var loadedError = resultFileManager.loadErrorFromFileSystem(jobId).replace(System.lineSeparator(), "");

        assertThat(loadedError).isEqualTo(fhirParser().encodeResourceToString(operationOutcome));
    }

    @Test
    void testLoadExistingResult_FatalAndInvalid() throws IOException {
        var jobId = "job-115645";
        var operationOutcome = new OperationOutcome()
                .setIssue(List.of(new OperationOutcome.OperationOutcomeIssueComponent()
                        .setSeverity(OperationOutcome.IssueSeverity.FATAL)
                        .setCode(OperationOutcome.IssueType.INVALID)));
        Files.createDirectories(Path.of(RESULTS_DIR, jobId));
        Files.writeString(Path.of(RESULTS_DIR, jobId, ERROR_FILE), fhirParser().encodeResourceToString(operationOutcome));

        resultFileManager = new ResultFileManager(RESULTS_DIR, "PT20S", FhirContext.forR4(), "hostname", "fileServerName");

        assertThat(resultFileManager.getStatus(jobId)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void testLoadExistingResult_NoNdjsonExists() throws IOException {
        var jobId = "job-115645";
        var operationOutcome = new OperationOutcome()
                .setIssue(List.of(new OperationOutcome.OperationOutcomeIssueComponent()
                        .setSeverity(OperationOutcome.IssueSeverity.WARNING)
                        .setCode(OperationOutcome.IssueType.INVALID)));
        Files.createDirectories(Path.of(RESULTS_DIR, jobId));
        Files.writeString(Path.of(RESULTS_DIR, jobId, ERROR_FILE), fhirParser().encodeResourceToString(operationOutcome));

        resultFileManager = new ResultFileManager(RESULTS_DIR, "PT20S", FhirContext.forR4(), "hostname", "fileServerName");

        assertThat(resultFileManager.getStatus(jobId)).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testLoadExistingResult_NdjsonExists() throws IOException {
        var jobId = "job-115645";
        Files.createDirectories(Path.of(RESULTS_DIR, jobId));
        Files.writeString(Path.of(RESULTS_DIR, jobId, "bundle.ndjson"), fhirParser().encodeResourceToString(new Bundle()));

        resultFileManager = new ResultFileManager(RESULTS_DIR, "PT20S", FhirContext.forR4(), "hostname", "fileServerName");

        assertThat(resultFileManager.getStatus(jobId)).isEqualTo(HttpStatus.OK);
    }

    IParser fhirParser() {
        return FhirContext.forR4().newJsonParser().setPrettyPrint(false);
    }
}
