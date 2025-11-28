package de.medizininformatikinitiative.torch.jobhandling;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

public class DefaultFileIO implements FileIO {

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
    public void move(Path source, Path target,
                     StandardCopyOption opt1,
                     StandardCopyOption opt2) throws IOException {
        Files.move(source, target, opt1, opt2);
    }

    @Override
    public void move(Path source, Path target,
                     StandardCopyOption... options) throws IOException {
        Files.move(source, target, options);
    }

    @Override
    public boolean isDirectory(Path path) {
        return Files.isDirectory(path);
    }
}
