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
        void atomicMove_fallsBackWhenAtomicNotSupported(@TempDir Path dir) throws Exception {
            Path source = dir.resolve("source.txt");
            Path target = dir.resolve("target.txt");

            try (MockedStatic<Files> files = mockStatic(Files.class)) {

                // atomic move fails
                files.when(() -> Files.move(
                                eq(source),
                                eq(target),
                                eq(StandardCopyOption.REPLACE_EXISTING),
                                eq(StandardCopyOption.ATOMIC_MOVE)))
                        .thenThrow(new AtomicMoveNotSupportedException(
                                source.toString(), target.toString(), "no"));

                // fallback succeeds
                files.when(() -> Files.move(
                                eq(source),
                                eq(target),
                                eq(StandardCopyOption.REPLACE_EXISTING)))
                        .thenReturn(target);

                // act
                io.atomicMove(source, target);

                // assert: tried atomic first ...
                files.verify(() -> Files.move(
                        source,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE));

                // ... then used fallback
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
        void deleteDir_null_returnsWithoutTouchingFiles() throws Exception {
            try (MockedStatic<Files> files = mockStatic(Files.class)) {
                io.deleteDir(null);

                // nothing should be called at all
                files.verifyNoInteractions();
            }
        }

        @Test
        void deleteDir_notExisting_returnsWithoutWalking(@TempDir Path dir) throws Exception {
            Path notExisting = dir.resolve("missing");

            try (MockedStatic<Files> files = mockStatic(Files.class)) {
                files.when(() -> Files.exists(notExisting)).thenReturn(false);

                io.deleteDir(notExisting);

                files.verify(() -> Files.exists(notExisting));
                files.verifyNoMoreInteractions();
            }
        }

        @Test
        void deleteDir_happyPath_deletesAllPaths(@TempDir Path dir) throws Exception {
            Path root = dir.resolve("root");
            Path child = root.resolve("child");
            Path file = child.resolve("file.txt");

            // NOTE: order in the stream doesn't matter; your code sorts reverse by compareTo.
            Stream<Path> walk = Stream.of(root, child, file);

            try (MockedStatic<Files> files = mockStatic(Files.class)) {
                files.when(() -> Files.exists(root)).thenReturn(true);
                files.when(() -> Files.walk(root)).thenReturn(walk);

                // act
                io.deleteDir(root);

                // assert: all deleted (at least attempted)
                files.verify(() -> Files.deleteIfExists(file));
                files.verify(() -> Files.deleteIfExists(child));
                files.verify(() -> Files.deleteIfExists(root));
            }
        }

        @Test
        void deleteDir_whenDeleteThrowsIOException_unwrapsAndThrowsIOException(@TempDir Path dir) throws Exception {
            Path root = dir.resolve("root");
            Path file = root.resolve("file.txt");

            Stream<Path> walk = Stream.of(root, file);

            IOException ioex = new IOException("nope");

            try (MockedStatic<Files> files = mockStatic(Files.class)) {
                files.when(() -> Files.exists(root)).thenReturn(true);
                files.when(() -> Files.walk(root)).thenReturn(walk);

                // Make deleteIfExists throw IOException -> your lambda wraps into RuntimeException(cause=IOException)
                files.when(() -> Files.deleteIfExists(file)).thenThrow(ioex);

                assertThatThrownBy(() -> io.deleteDir(root))
                        .isSameAs(ioex); // important: your code rethrows the original IOException

                // (optional) verify we attempted at least the failing delete
                files.verify(() -> Files.deleteIfExists(file));
            }
        }

        @Test
        void deleteDir_whenDeleteThrowsRuntimeExceptionWithoutIOExceptionCause_rethrowsRuntime(@TempDir Path dir) throws Exception {
            Path root = dir.resolve("root");
            Path file = root.resolve("file.txt");

            Stream<Path> walk = Stream.of(root, file);

            RuntimeException boom = new RuntimeException("boom");

            try (MockedStatic<Files> files = mockStatic(Files.class)) {
                files.when(() -> Files.exists(root)).thenReturn(true);
                files.when(() -> Files.walk(root)).thenReturn(walk);

                // RuntimeException is not caught inside the lambda (only IOException is caught),
                // so it bubbles to the outer catch and hits "throw e;"
                files.when(() -> Files.deleteIfExists(any(Path.class))).thenThrow(boom);

                assertThatThrownBy(() -> io.deleteDir(root))
                        .isSameAs(boom);
            }
        }
    }


}
