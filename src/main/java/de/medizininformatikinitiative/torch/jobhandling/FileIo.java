package de.medizininformatikinitiative.torch.jobhandling;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.util.stream.Stream;


/**
 * Abstraction over filesystem access used by job persistence.
 *
 * <p>Exists mainly to allow testing and controlled file operations
 * (e.g. atomic moves, recursive deletes) without binding directly to {@link java.nio.file.Files}.</p>
 */
public interface FileIo {

    BufferedWriter newBufferedWriter(Path path) throws IOException;

    Reader newBufferedReader(Path path) throws IOException;

    Stream<String> lines(Path path) throws IOException;

    void createDirectories(Path dir) throws IOException;

    boolean isDirectory(Path path);

    boolean exists(Path path);

    /**
     * List directory entries as a Stream<Path>.
     */
    Stream<Path> list(Path dir) throws IOException;

    /**
     * Move with explicit options (used by JobPersistenceService).
     */
    void atomicMove(Path source, Path target) throws IOException;

    void setPosixPermissionsIfSupported(Path path, String perms) throws IOException;
}
