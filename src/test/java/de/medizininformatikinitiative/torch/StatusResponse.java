package de.medizininformatikinitiative.torch;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public record StatusResponse(List<FileEntry> output) {

    Optional<URI> coreBundleUrl() {
        return output.stream()
                .filter(entry -> entry.url.getPath().endsWith("core.ndjson"))
                .map(FileEntry::url)
                .findFirst();
    }

    List<URI> patientBundleUrls() {
        return output.stream().filter(entry -> !entry.url.getPath().endsWith("core.ndjson"))
                .map(FileEntry::url)
                .toList();
    }

    record FileEntry (String type, URI url) {
    }
}
