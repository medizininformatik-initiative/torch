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
    public void setPosixPermissionsIfSupported(Path path, String perms) throws IOException {
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(perms));
    }

}
