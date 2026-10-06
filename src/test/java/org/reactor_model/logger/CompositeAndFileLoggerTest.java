package org.reactor_model.logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Covers the composite fan-out (including fault isolation) and the file
 * logger (content, naming, idempotent close).
 */
@DisplayName("Composite & File Logger Tests")
class CompositeAndFileLoggerTest {

    @TempDir
    private Path tempDir;

    @Test
    @DisplayName("Composite should require at least one delegate and expose them")
    void compositeRequiresDelegates() {
        ReactorLogger first = mock(ReactorLogger.class);
        ReactorLogger second = mock(ReactorLogger.class);

        CompositeReactorLogger composite = new CompositeReactorLogger(first, second);

        assertEquals(List.of(first, second), composite.getDelegates());
        assertThrows(IllegalArgumentException.class, () -> new CompositeReactorLogger());
        assertThrows(IllegalArgumentException.class,
                () -> new CompositeReactorLogger((ReactorLogger) null));
    }

    @Test
    @DisplayName("Composite should fan out to every delegate")
    void compositeFansOut() {
        ReactorLogger first = mock(ReactorLogger.class);
        ReactorLogger second = mock(ReactorLogger.class);
        CompositeReactorLogger composite = new CompositeReactorLogger(first, second);

        composite.logState(1.0, 2.0, 3.0, 4.0, 5.0);
        composite.logWarning("boom");
        composite.logDecision("sub", "did a thing");

        verify(first).logState(1.0, 2.0, 3.0, 4.0, 5.0);
        verify(second).logState(1.0, 2.0, 3.0, 4.0, 5.0);
        verify(first).logWarning("boom");
        verify(second).logDecision("sub", "did a thing");
    }

    @Test
    @DisplayName("Failing delegate must not silence the remaining loggers")
    void compositeIsolatesFailures() {
        ReactorLogger failing = mock(ReactorLogger.class);
        doThrow(new RuntimeException("disk on fire")).when(failing)
                .logWarning(anyString());
        ReactorLogger healthy = mock(ReactorLogger.class);
        CompositeReactorLogger composite = new CompositeReactorLogger(failing, healthy);

        assertDoesNotThrow(() -> composite.logWarning("boom"));
        verify(healthy).logWarning("boom");
    }

    @Test
    @DisplayName("File logger should write timestamped entries and close cleanly")
    void fileLoggerWritesAndCloses() throws Exception {
        FileReactorLogger logger = new FileReactorLogger(tempDir);

        assertTrue(logger.getLogFile().getFileName().toString().startsWith("reactor_log_"));
        assertTrue(logger.getLogFile().getFileName().toString().endsWith(".txt"));

        logger.logState(100.0, 300.0, 1.0, 0.5, 0.0);
        logger.logWarning("something hot");
        logger.logDecision("Test", "did a thing");
        logger.close();
        assertDoesNotThrow(logger::close, "close() must be idempotent");

        String content = Files.readString(logger.getLogFile(), StandardCharsets.UTF_8);
        assertTrue(content.contains("[STATE]"), "State entry missing:\n" + content);
        assertTrue(content.contains("[WARNING]"), "Warning entry missing:\n" + content);
        assertTrue(content.contains("[TEST]"), "Decision entry missing:\n" + content);
        assertTrue(content.contains("something hot"), "Warning text missing:\n" + content);
    }

    @Test
    @DisplayName("File logger should create a missing log directory")
    void fileLoggerCreatesDirectory() {
        Path nested = tempDir.resolve("a").resolve("b");

        assertDoesNotThrow(() -> new FileReactorLogger(nested).close());
        assertTrue(Files.isDirectory(nested));
    }

    @Test
    @DisplayName("Logging after close must be a silent no-op, not a failure")
    void loggingAfterCloseIsIgnored() throws Exception {
        FileReactorLogger logger = new FileReactorLogger(tempDir);
        logger.close();

        assertDoesNotThrow(() -> {
            logger.logState(1.0, 2.0, 3.0, 4.0, 5.0);
            logger.logWarning("late");
            logger.logDecision("s", "d");
        }, "Post-close logging must be silently ignored");
    }
}
