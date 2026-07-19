package de.medizininformatikinitiative.torch;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public record StatusResponse(List<FileEntry> output, List<ExtensionEntry> extension) {

    Optional<URI> coreBundleUrl() {
        return output.stream()
                .filter(entry -> entry.url().getPath().endsWith("core.ndjson"))
                .map(FileEntry::url)
                .findFirst();
    }

    List<URI> patientBundleUrls() {
        return output.stream()
                .filter(entry -> !entry.url().getPath().endsWith("core.ndjson"))
                .map(FileEntry::url)
                .toList();
    }

    Optional<URI> jobSummaryUrl() {
        return extension.stream()
                .filter(entry -> entry.url().equals("torch-job-diagnostics-summary"))
                .map(ExtensionEntry::valueUrl)
                .findFirst();
    }

    Optional<URI> resourceExclusionsUrl() {
        return extension.stream()
                .filter(entry -> entry.url().equals("torch-resource-exclusions"))
                .map(ExtensionEntry::valueUrl)
                .findFirst();
    }

    Optional<URI> patientExclusionsUrl() {
        return extension.stream()
                .filter(entry -> entry.url().equals("torch-patient-exclusions"))
                .map(ExtensionEntry::valueUrl)
                .findFirst();
    }

    record FileEntry(String type, URI url) {
    }

    record ExtensionEntry(String url, URI valueUrl){}
}
