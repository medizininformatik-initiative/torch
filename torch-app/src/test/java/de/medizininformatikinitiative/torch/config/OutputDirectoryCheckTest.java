package de.medizininformatikinitiative.torch.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class OutputDirectoryCheckTest {

    @TempDir
    Path tempDir;

    @Test
    void run_doesNotThrowOnWritableDirectory() {
        var check = new OutputDirectoryCheck(tempDir);
        assertThatNoException().isThrownBy(() -> check.run(null));
    }

    @Test
    void run_createsDirectoryIfAbsent() throws Exception {
        Path absent = tempDir.resolve("new-output");
        var check = new OutputDirectoryCheck(absent);
        check.run(null);
        assertThatNoException().isThrownBy(() -> check.run(null));
    }

    @Test
    void run_doesNotThrowOnNonWritableDirectory() throws IOException {
        Path readOnly = tempDir.resolve("readonly");
        Files.createDirectories(readOnly);
        assertThat(readOnly.toFile().setWritable(false))
                .as("Failed to mark test directory as non-writable").isTrue();
        try {
            var check = new OutputDirectoryCheck(readOnly);
            assertThatNoException().isThrownBy(() -> check.run(null));
        } finally {
            assertThat(readOnly.toFile().setWritable(true))
                    .as("Failed to restore write permission on test directory: %s", readOnly).isTrue();
        }
    }
}
