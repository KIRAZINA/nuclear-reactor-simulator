package org.reactor_model.logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * File-based implementation of {@link ReactorLogger} for post-mortem analysis.
 * Appends operational logs to {@code logs/reactor_log_&lt;timestamp&gt;.txt},
 * creating the {@code logs/} directory when necessary.
 */
public class FileReactorLogger implements ReactorLogger, AutoCloseable {

    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final Path logFile;
    private final PrintWriter writer;
    private volatile boolean closed = false;

    /** Opens a new timestamped log file under {@code logs/}. */
    public FileReactorLogger() {
        this(Paths.get("logs"));
    }

    /**
     * Opens a new timestamped log file under {@code logDir}.
     *
     * @param logDir directory for log files; created if missing
     * @throws IllegalStateException if the file cannot be opened
     */
    public FileReactorLogger(Path logDir) {
        try {
            Files.createDirectories(logDir);
            String stamp = LocalDateTime.now().format(FILE_STAMP);
            this.logFile = logDir.resolve("reactor_log_" + stamp + ".txt");
            BufferedWriter buffered = new BufferedWriter(new OutputStreamWriter(
                    Files.newOutputStream(logFile,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND),
                    StandardCharsets.UTF_8));
            this.writer = new PrintWriter(buffered, true);
            writer.println(getTimestamp() + " [SYSTEM] File logging started: " + logFile.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to open reactor log file in '" + logDir + "'.", e);
        }
    }

    /** Returns the log file being written to. */
    public Path getLogFile() {
        return logFile;
    }

    @Override
    public synchronized void logState(double power, double temperature, double coolantFlowRate,
                                      double controlRodPosition, double reactivity) {
        if (closed) {
            return;
        }
        writer.printf(
                "%s [STATE] Power: %.2f MW | Temp: %.2f C | Coolant: %.2f | Rods: %.2f%% | Reactivity: %.4f%n",
                getTimestamp(), power, temperature, coolantFlowRate, controlRodPosition, reactivity);
    }

    @Override
    public synchronized void logWarning(String message) {
        if (closed) {
            return;
        }
        writer.printf("%s [WARNING] %s%n", getTimestamp(), message);
    }

    @Override
    public synchronized void logDecision(String subsystem, String decision) {
        if (closed) {
            return;
        }
        writer.printf("%s [%s] Decision: %s%n",
                getTimestamp(), subsystem.toUpperCase(), decision);
    }

    /**
     * Flushes and closes the underlying file handle. Idempotent and safe
     * to call from a JVM shutdown hook.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            writer.println(getTimestamp() + " [SYSTEM] File logging stopped.");
        } catch (Exception e) {
            // Best effort during shutdown; never throw from close().
            System.err.println("[FileReactorLogger] Failed to write shutdown marker: " + e.getMessage());
        } finally {
            writer.close();
        }
    }
}
