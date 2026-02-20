package de.medizininformatikinitiative.torch.jobhandling;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.stream.Stream;

/**
 * Implementation of FileIo Interface using Java.nio.files.
 * Interface was created for  mocking of file behavior.
 */
public class DefaultFileIO implements FileIo {

    @Override
    public BufferedWriter newBufferedWriter(Path path) throws IOException {
        return Files.newBufferedWriter(path);
    }

    @Override
    public Reader newBufferedReader(Path path) throws IOException {
        return Files.newBufferedReader(path);
    }

    @Override
    public Stream<String> lines(Path path) throws IOException {
        return Files.lines(path);
    }

    @Override
    public void createDirectories(Path dir) throws IOException {
        Files.createDirectories(dir);
    }

    @Override
    public boolean exists(Path path) {
        return Files.exists(path);
    }

    @Override
    public Stream<Path> list(Path dir) throws IOException {
        return Files.list(dir);
    }

    @Override
    public void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }


    @Override
    public boolean isDirectory(Path path) {
        return Files.isDirectory(path);
    }
    @Override
    public void deleteDir(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted((p1, p2) -> p2.compareTo(p1)) // delete children before parents
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete " + path, e);
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }
}
