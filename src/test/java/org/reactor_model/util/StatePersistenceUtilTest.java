package org.reactor_model.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.reactor_model.core.ReactorCore;
import org.reactor_model.logger.ConsoleReactorLogger;
import org.reactor_model.util.StatePersistenceUtil.StatePersistenceException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip and failure coverage for {@link StatePersistenceUtil}.
 */
@DisplayName("StatePersistenceUtil Tests")
class StatePersistenceUtilTest {

    @TempDir
    private Path tempDir;

    private ReactorCore core;

    @BeforeEach
    void setUp() {
        core = new ReactorCore(new ConsoleReactorLogger());
    }

    @Test
    @DisplayName("Save then load should restore all key fields and precursors")
    void saveLoadRoundTripRestoresState() throws Exception {
        // Arrange: drive the core away from its cold defaults.
        core.setTemperature(450.0);
        core.setCoolantFlowRate(0.75);
        core.setManualFlowControl(true);
        core.setControlRodPosition(0.8);
        core.setExternalReactivity(0.004);
        core.setReactivity(0.002);
        core.setOverheatTicks(7);
        core.setFuelTemperature(620.0);
        core.setPower(1234.5);
        double[] precursors = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        core.setPrecursorConcentrations(precursors);

        Path file = tempDir.resolve("state.properties");

        // Act: save, mutate everything, then restore.
        StatePersistenceUtil.saveState(core, file.toString());
        assertTrue(Files.isRegularFile(file), "Save should create the state file");

        core.setTemperature(300.0);
        core.setCoolantFlowRate(1.0);
        core.setManualFlowControl(false);
        core.setControlRodPosition(0.1);
        core.setExternalReactivity(0.0);
        core.setReactivity(0.0);
        core.setOverheatTicks(0);
        core.setFuelTemperature(450.0);
        core.setPower(0.01);
        core.setPrecursorConcentrations(new double[]{9.0, 9.0, 9.0, 9.0, 9.0, 9.0});

        StatePersistenceUtil.loadState(core, file.toString());

        // Assert: every persisted field matches the saved snapshot.
        assertEquals(450.0, core.getTemperature(), 1e-9);
        assertEquals(0.75, core.getCoolantFlowRate(), 1e-9);
        assertTrue(core.isManualFlowControl());
        assertEquals(0.8, core.getControlRodPosition(), 1e-9);
        assertEquals(0.004, core.getExternalReactivity(), 1e-9);
        assertEquals(0.002, core.getReactivity(), 1e-9);
        assertEquals(7, core.getOverheatTicks());
        assertEquals(620.0, core.getFuelTemperature(), 1e-9);
        assertEquals(1234.5, core.getPower(), 1e-9);
        assertArrayEquals(precursors, core.getPrecursorConcentrations(), 1e-9);
    }

    @Test
    @DisplayName("Loading a non-existent file should throw StatePersistenceException")
    void loadMissingFileThrows() {
        Path missing = tempDir.resolve("does-not-exist.properties");

        StatePersistenceException e = assertThrows(
                StatePersistenceException.class,
                () -> StatePersistenceUtil.loadState(core, missing.toString()));
        assertNotNull(e.getCause(), "Original I/O cause should be preserved");
    }

    @Test
    @DisplayName("Loading a corrupt file with missing keys should throw StatePersistenceException")
    void loadCorruptFileThrows() throws Exception {
        Path corrupt = tempDir.resolve("corrupt.properties");
        Files.writeString(corrupt, "temperature=300.0\n", StandardCharsets.UTF_8);

        assertThrows(StatePersistenceException.class,
                () -> StatePersistenceUtil.loadState(core, corrupt.toString()));
    }

    @Test
    @DisplayName("Null core and blank paths should be rejected with StatePersistenceException")
    void invalidArgumentsRejected() throws Exception {
        Path file = tempDir.resolve("state.properties");
        StatePersistenceUtil.saveState(core, file.toString());

        assertThrows(StatePersistenceException.class,
                () -> StatePersistenceUtil.saveState(null, file.toString()));
        assertThrows(StatePersistenceException.class,
                () -> StatePersistenceUtil.saveState(core, "  "));
        assertThrows(StatePersistenceException.class,
                () -> StatePersistenceUtil.loadState(null, file.toString()));
        assertThrows(StatePersistenceException.class,
                () -> StatePersistenceUtil.loadState(core, null));
    }
}
