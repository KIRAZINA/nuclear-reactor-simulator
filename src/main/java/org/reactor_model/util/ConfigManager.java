package org.reactor_model.util;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * External configuration support for the reactor simulator.
 * No external dependencies — plain {@link Properties} file.
 *
 * <p>Looks for {@code reactor.properties} in the working directory
 * (the directory where the application is executed). If the file is
 * missing or a key is absent/unparseable, the hardcoded default is used.
 *
 * <p>Supported keys:
 * <ul>
 *   <li>{@code simulation.dt} (default 0.1)</li>
 *   <li>{@code simulation.refresh_ms} (default 200)</li>
 *   <li>{@code reactor.max_safe_power} (default 3411.0)</li>
 *   <li>{@code reactor.critical_temp} (default 1200.0)</li>
 * </ul>
 */
public final class ConfigManager {

    public static final String CONFIG_FILE = "reactor.properties";

    public static final double DEFAULT_SIMULATION_DT = 0.1;
    public static final int DEFAULT_REFRESH_MS = 200;
    public static final double DEFAULT_MAX_SAFE_POWER = 3411.0;
    public static final double DEFAULT_CRITICAL_TEMP = 1200.0;

    private static volatile boolean loaded = false;
    private static volatile boolean configFileLoaded = false;
    private static volatile Path configPath = Paths.get(CONFIG_FILE);

    private static volatile double simulationDt = DEFAULT_SIMULATION_DT;
    private static volatile int refreshMs = DEFAULT_REFRESH_MS;
    private static volatile double maxSafePower = DEFAULT_MAX_SAFE_POWER;
    private static volatile double criticalTemp = DEFAULT_CRITICAL_TEMP;

    static {
        load();
    }

    private ConfigManager() {}

    /**
     * (Re)loads configuration from {@code reactor.properties} in the working
     * directory. Missing file or bad values fall back to defaults.
     */
    public static synchronized void load() {
        loadFrom(configPath);
    }

    /**
     * Reloads configuration from an explicit path. Primarily for tests.
     *
     * @param path path to a properties file; may not exist (defaults are used)
     */
    public static synchronized void loadFrom(Path path) {
        configPath = path;
        loaded = true;
        configFileLoaded = false;

        Properties props = new Properties();
        if (path != null && Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                props.load(reader);
                configFileLoaded = true;
            } catch (IOException e) {
                System.err.println("[ConfigManager] Could not read '" + path
                        + "', using defaults: " + e.getMessage());
            }
        }

        simulationDt = getDouble(props, "simulation.dt", DEFAULT_SIMULATION_DT);
        refreshMs = (int) Math.round(getDouble(props, "simulation.refresh_ms", DEFAULT_REFRESH_MS));
        if (refreshMs <= 0) {
            System.err.println("[ConfigManager] Invalid 'simulation.refresh_ms' ("
                    + props.getProperty("simulation.refresh_ms") + "), using default "
                    + DEFAULT_REFRESH_MS + ".");
            refreshMs = DEFAULT_REFRESH_MS;
        }
        maxSafePower = getDouble(props, "reactor.max_safe_power", DEFAULT_MAX_SAFE_POWER);
        criticalTemp = getDouble(props, "reactor.critical_temp", DEFAULT_CRITICAL_TEMP);
    }

    /** Restores hardcoded defaults (convenience for tests). */
    public static synchronized void resetToDefaults() {
        simulationDt = DEFAULT_SIMULATION_DT;
        refreshMs = DEFAULT_REFRESH_MS;
        maxSafePower = DEFAULT_MAX_SAFE_POWER;
        criticalTemp = DEFAULT_CRITICAL_TEMP;
        configFileLoaded = false;
        loaded = true;
    }

    public static double getSimulationDt() { return simulationDt; }
    public static int getRefreshMs() { return refreshMs; }
    public static double getMaxSafePower() { return maxSafePower; }
    public static double getCriticalTemp() { return criticalTemp; }

    /** Whether {@link #load()} has run at least once. */
    public static boolean isLoaded() { return loaded; }

    /** Whether a {@code reactor.properties} file was successfully read. */
    public static boolean isConfigFileLoaded() { return configFileLoaded; }

    /** Path that was (or will be) used for loading. */
    public static Path getConfigPath() { return configPath; }

    private static double getDouble(Properties props, String key, double fallback) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            System.err.println("[ConfigManager] Invalid value for '" + key + "' ('"
                    + raw + "'), using default " + fallback + ".");
            return fallback;
        }
    }
}
