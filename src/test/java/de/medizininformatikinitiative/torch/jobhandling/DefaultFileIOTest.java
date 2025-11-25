package de.medizininformatikinitiative.torch.jobhandling;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
            Files.writeString(source, "x");

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
                io.atomicMove(source, target); // ← your method under test

                // assert: fallback was used
                files.verify(() -> Files.move(
                        source,
                        target,
                        StandardCopyOption.REPLACE_EXISTING));
            }
        }
    }
}
