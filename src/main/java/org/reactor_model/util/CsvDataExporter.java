package org.reactor_model.util;

import org.reactor_model.ui.ReactorStateSnapshot;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records {@link ReactorStateSnapshot} samples on the simulation thread and
 * exports them as CSV for offline analysis. No external dependencies.
 *
 * <p>Thread-safe: samples arrive on the simulation event-bus thread while
 * exports are triggered from the Swing EDT or CLI thread.
 *
 * <p>The internal buffer is cleared after every successful export to prevent
 * unbounded memory growth. A hard cap ({@link #MAX_SAMPLES}) drops the oldest
 * samples if an export never happens during a very long run.
 */
public class CsvDataExporter {

    /** CSV header: {@value}. */
    public static final String HEADER =
            "Time (s), Power (MW), Temperature (K), Reactivity ($), Rod Position, Coolant Flow, Target Power";

    /** Maximum buffered samples; oldest entries are dropped beyond this. */
    public static final int MAX_SAMPLES = 100_000;

    /** Unchecked wrapper for export failures. The original cause is preserved. */
    public static class CsvExportException extends RuntimeException {
        public CsvExportException(String message) {
            super(message);
        }

        public CsvExportException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** One buffered sample: snapshot plus seconds since recording started. */
    private static final class Sample {
        final ReactorStateSnapshot snapshot;
        final double elapsedSeconds;

        Sample(ReactorStateSnapshot snapshot, double elapsedSeconds) {
            this.snapshot = snapshot;
            this.elapsedSeconds = elapsedSeconds;
        }
    }

    private final CopyOnWriteArrayList<Sample> samples = new CopyOnWriteArrayList<>();
    private volatile long startNanos = -1L;

    /**
     * Records a snapshot with a monotonic elapsed-time stamp.
     * Null snapshots are ignored.
     */
    public void recordSnapshot(ReactorStateSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        long now = System.nanoTime();
        if (startNanos < 0L) {
            startNanos = now;
        }
        double elapsed = (now - startNanos) / 1_000_000_000.0;
        samples.add(new Sample(snapshot, elapsed));
        // Hard cap: drop oldest so a forgotten export cannot exhaust memory.
        while (samples.size() > MAX_SAMPLES) {
            samples.remove(0);
        }
    }

    /** Returns the number of currently buffered samples. */
    public int getSampleCount() {
        return samples.size();
    }

    /** Discards all buffered samples without writing a file. */
    public void clear() {
        samples.clear();
        startNanos = -1L;
    }

    /**
     * Writes all buffered samples to {@code filePath} as CSV and clears the
     * buffer on success. An empty buffer still produces a headers-only file.
     *
     * @param filePath destination file (overwritten if it exists)
     * @throws CsvExportException if the path is blank or writing fails
     */
    public void export(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new CsvExportException("Cannot export CSV: file path is missing.");
        }
        try (BufferedWriter writer = Files.newBufferedWriter(
                Paths.get(filePath), StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            writer.newLine();
            for (Sample sample : samples) {
                writer.write(formatRow(sample));
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace(System.err);
            throw new CsvExportException(
                    "Failed to export CSV data to '" + filePath + "'.", e);
        }
        // Only reached on success: free the buffer for the next recording window.
        clear();
    }

    private static String formatRow(Sample sample) {
        ReactorStateSnapshot s = sample.snapshot;
        return String.format(Locale.US, "%.2f, %.4f, %.2f, %.6f, %.4f, %.4f, %.2f",
                sample.elapsedSeconds,
                s.power,
                s.temperature,
                s.reactivity,
                s.controlRodPosition,
                s.coolantFlowRate,
                s.targetPower);
    }
}
