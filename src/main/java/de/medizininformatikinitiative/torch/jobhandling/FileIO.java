package de.medizininformatikinitiative.torch.jobhandling;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

public interface FileIO {

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
    void move(Path source, Path target,
              StandardCopyOption option1,
              StandardCopyOption option2) throws IOException;

    /**
     * General-purpose move with varargs (convenience).
     */
    void move(Path source, Path target,
              StandardCopyOption... options) throws IOException;
}
