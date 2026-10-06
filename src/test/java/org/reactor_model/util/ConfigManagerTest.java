package org.reactor_model.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers external configuration loading, defaults fallback, and recovery
 * from malformed values. Always restores global state afterwards: other
 * tests construct simulation components that read these same statics.
 */
@DisplayName("ConfigManager Tests")
class ConfigManagerTest {

    @TempDir
    private Path tempDir;

    @AfterEach
    void restoreGlobalState() {
        ConfigManager.load(); // re-read working dir (defaults in CI)
    }

    @Test
    @DisplayName("Defaults should apply when no config file exists")
    void defaultsWithoutFile() {
        ConfigManager.loadFrom(tempDir.resolve("absent.properties"));

        assertFalse(ConfigManager.isConfigFileLoaded());
        assertEquals(ConfigManager.DEFAULT_SIMULATION_DT, ConfigManager.getSimulationDt(), 1e-12);
        assertEquals(ConfigManager.DEFAULT_REFRESH_MS, ConfigManager.getRefreshMs());
        assertEquals(ConfigManager.DEFAULT_MAX_SAFE_POWER, ConfigManager.getMaxSafePower(), 1e-9);
        assertEquals(ConfigManager.DEFAULT_CRITICAL_TEMP, ConfigManager.getCriticalTemp(), 1e-9);
    }

    @Test
    @DisplayName("Valid config file should override every key")
    void fileOverridesAllKeys() throws Exception {
        Path file = tempDir.resolve("reactor.properties");
        Files.writeString(file,
                "simulation.dt=0.05\nsimulation.refresh_ms=100\n"
                        + "reactor.max_safe_power=3000.0\nreactor.critical_temp=1100.0\n",
                StandardCharsets.UTF_8);

        ConfigManager.loadFrom(file);

        assertTrue(ConfigManager.isConfigFileLoaded());
        assertEquals(0.05, ConfigManager.getSimulationDt(), 1e-12);
        assertEquals(100, ConfigManager.getRefreshMs());
        assertEquals(3000.0, ConfigManager.getMaxSafePower(), 1e-9);
        assertEquals(1100.0, ConfigManager.getCriticalTemp(), 1e-9);
    }

    @Test
    @DisplayName("Malformed values should fall back to defaults without throwing")
    void malformedValuesFallBack() throws Exception {
        Path file = tempDir.resolve("reactor.properties");
        Files.writeString(file,
                "simulation.dt=not-a-number\nsimulation.refresh_ms=-5\n",
                StandardCharsets.UTF_8);

        assertDoesNotThrow(() -> ConfigManager.loadFrom(file));

        assertEquals(ConfigManager.DEFAULT_SIMULATION_DT, ConfigManager.getSimulationDt(), 1e-12);
        assertEquals(ConfigManager.DEFAULT_REFRESH_MS, ConfigManager.getRefreshMs());
    }

    @Test
    @DisplayName("resetToDefaults should restore hardcoded values")
    void resetRestoresDefaults() throws Exception {
        Path file = tempDir.resolve("reactor.properties");
        Files.writeString(file, "simulation.dt=0.05\n", StandardCharsets.UTF_8);
        ConfigManager.loadFrom(file);
        assertEquals(0.05, ConfigManager.getSimulationDt(), 1e-12);

        ConfigManager.resetToDefaults();

        assertFalse(ConfigManager.isConfigFileLoaded());
        assertEquals(ConfigManager.DEFAULT_SIMULATION_DT, ConfigManager.getSimulationDt(), 1e-12);
    }
}
