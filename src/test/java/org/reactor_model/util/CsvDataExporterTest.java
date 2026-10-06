package org.reactor_model.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.reactor_model.ui.ReactorStateSnapshot;
import org.reactor_model.util.CsvDataExporter.CsvExportException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers CSV export: headers, row content, buffer clearing and failures.
 */
@DisplayName("CsvDataExporter Tests")
class CsvDataExporterTest {

    @TempDir
    private Path tempDir;

    private CsvDataExporter exporter;

    @BeforeEach
    void setUp() {
        exporter = new CsvDataExporter();
    }

    private static ReactorStateSnapshot snapshot(double power, double temperature) {
        return new ReactorStateSnapshot(
                power, temperature, 0.9, 0.6, 0.001, 1000.0, 0, false, true);
    }

    @Test
    @DisplayName("Export should write headers plus one row per recorded snapshot")
    void exportWritesHeadersAndRows() throws Exception {
        exporter.recordSnapshot(snapshot(100.0, 300.0));
        exporter.recordSnapshot(snapshot(200.0, 310.0));
        assertEquals(2, exporter.getSampleCount());

        Path file = tempDir.resolve("data.csv");
        exporter.export(file.toString());

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(3, lines.size(), "Expected header + 2 data rows");
        assertEquals(CsvDataExporter.HEADER, lines.get(0));
        assertTrue(lines.get(1).contains("100.0000"),
                "First row should carry the first power value: " + lines.get(1));
        assertTrue(lines.get(2).contains("200.0000"),
                "Second row should carry the second power value: " + lines.get(2));
        // Locale check: decimals must use '.' regardless of default locale.
        assertFalse(lines.get(1).contains(";"),
                "CSV must use comma separators: " + lines.get(1));
    }

    @Test
    @DisplayName("Successful export should clear the buffer")
    void exportClearsBuffer() throws Exception {
        exporter.recordSnapshot(snapshot(100.0, 300.0));
        Path file = tempDir.resolve("data.csv");
        exporter.export(file.toString());

        assertEquals(0, exporter.getSampleCount(),
                "Buffer must be cleared after a successful export");

        // A second export writes headers only.
        Path second = tempDir.resolve("empty.csv");
        exporter.export(second.toString());
        List<String> lines = Files.readAllLines(second, StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        assertEquals(CsvDataExporter.HEADER, lines.get(0));
    }

    @Test
    @DisplayName("Blank paths and null snapshots should be handled gracefully")
    void invalidInputHandling() {
        exporter.recordSnapshot(null);
        assertEquals(0, exporter.getSampleCount(),
                "Null snapshots must be ignored");

        assertThrows(CsvExportException.class, () -> exporter.export("  "));
        assertThrows(CsvExportException.class, () -> exporter.export(null));
    }
}
