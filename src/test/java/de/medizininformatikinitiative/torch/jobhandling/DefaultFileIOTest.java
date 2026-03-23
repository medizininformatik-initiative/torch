package de.medizininformatikinitiative.torch.jobhandling;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mockStatic;

class DefaultFileIOTest {

    private final DefaultFileIO io = new DefaultFileIO();

    @Nested
    class AtomicMoveTests {

        @Test
        void atomicMove_fallsBackToRegularMove_whenAtomicMoveIsNotSupported(@TempDir Path dir) throws Exception {
            // arrange
            Path source = dir.resolve("source.txt");
            Path target = dir.resolve("target.txt");

            try (MockedStatic<Files> files = mockStatic(Files.class)) {
                files.when(() -> Files.move(
                                eq(source),
                                eq(target),
                                eq(StandardCopyOption.REPLACE_EXISTING),
                                eq(StandardCopyOption.ATOMIC_MOVE)))
                        .thenThrow(new AtomicMoveNotSupportedException(
                                source.toString(),
                                target.toString(),
                                "atomic move not supported"
                        ));

                files.when(() -> Files.move(
                                eq(source),
                                eq(target),
                                eq(StandardCopyOption.REPLACE_EXISTING)))
                        .thenReturn(target);

                // act
                io.atomicMove(source, target);

                // assert
                files.verify(() -> Files.move(
                        source,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE));

                files.verify(() -> Files.move(
                        source,
                        target,
                        StandardCopyOption.REPLACE_EXISTING));
            }
        }
    }

    @Nested
    class DeleteDirTests {

        @Test
        void deleteDir_doesNothing_whenPathIsNull() throws Exception {
            try (MockedStatic<Files> files = mockStatic(Files.class)) {
                io.deleteDir(null);
                files.verifyNoInteractions();
            }
        }

        @Test
        void deleteDir_doesNothing_whenPathDoesNotExist(@TempDir Path dir) throws Exception {
            Path missing = dir.resolve("missing");

            try (MockedStatic<Files> files = mockStatic(Files.class)) {
                files.when(() -> Files.exists(missing)).thenReturn(false);

                io.deleteDir(missing);

                files.verify(() -> Files.exists(missing));
                files.verifyNoMoreInteractions();
            }
        }

        @Test
        void deleteDir_deletesAllDiscoveredPaths(@TempDir Path dir) throws Exception {
            Path root = dir.resolve("root");
            Path child = root.resolve("child");
            Path file = child.resolve("file.txt");

            Stream<Path> walk = Stream.of(root, child, file);

            try (MockedStatic<Files> files = mockStatic(Files.class)) {
                files.when(() -> Files.exists(root)).thenReturn(true);
                files.when(() -> Files.walk(root)).thenReturn(walk);

                io.deleteDir(root);

                files.verify(() -> Files.deleteIfExists(file));
                files.verify(() -> Files.deleteIfExists(child));
                files.verify(() -> Files.deleteIfExists(root));
            }
        }

        @Test
        void deleteDir_rethrowsOriginalIOException_whenDeletionFails(@TempDir Path dir) throws Exception {
            Path root = dir.resolve("root");
            Path file = root.resolve("file.txt");
            Stream<Path> walk = Stream.of(root, file);
            IOException failure = new IOException("nope");

            try (MockedStatic<Files> files = mockStatic(Files.class)) {
                files.when(() -> Files.exists(root)).thenReturn(true);
                files.when(() -> Files.walk(root)).thenReturn(walk);
                files.when(() -> Files.deleteIfExists(file)).thenThrow(failure);

                assertThatThrownBy(() -> io.deleteDir(root))
                        .isSameAs(failure);

                files.verify(() -> Files.deleteIfExists(file));
            }
        }

        @Test
        void deleteDir_rethrowsRuntimeException_whenDeletionFailsWithRuntimeException(@TempDir Path dir) throws Exception {
            Path root = dir.resolve("root");
            Path file = root.resolve("file.txt");
            Stream<Path> walk = Stream.of(root, file);
            RuntimeException failure = new RuntimeException("boom");

            try (MockedStatic<Files> files = mockStatic(Files.class)) {
                files.when(() -> Files.exists(root)).thenReturn(true);
                files.when(() -> Files.walk(root)).thenReturn(walk);
                files.when(() -> Files.deleteIfExists(any(Path.class))).thenThrow(failure);

                assertThatThrownBy(() -> io.deleteDir(root))
                        .isSameAs(failure);
            }
        }
    }
}
