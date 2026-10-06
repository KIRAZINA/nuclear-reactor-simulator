package org.reactor_model.util;

import org.reactor_model.core.ReactorCore;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Saves and restores {@link ReactorCore} simulation state to a plain
 * {@link Properties} text file. No external dependencies required.
 *
 * <p>File format is {@code key=value} per line, UTF-8 encoded.
 */
public final class StatePersistenceUtil {

    private static final String VERSION_KEY = "state.version";
    private static final String VERSION = "1";

    private StatePersistenceUtil() {}

    /** Unchecked wrapper for save/load failures. The original cause is preserved. */
    public static class StatePersistenceException extends RuntimeException {
        public StatePersistenceException(String message) {
            super(message);
        }

        public StatePersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Serializes the essential {@link ReactorCore} fields (including neutronics
     * power and delayed-neutron precursors) to {@code filePath}.
     */
    public static void saveState(ReactorCore core, String filePath) {
        if (core == null) {
            throw new StatePersistenceException("Cannot save state: ReactorCore is null.");
        }
        if (filePath == null || filePath.isBlank()) {
            throw new StatePersistenceException("Cannot save state: file path is missing.");
        }

        Properties props = new Properties();
        props.setProperty(VERSION_KEY, VERSION);
        props.setProperty("temperature", Double.toString(core.getTemperature()));
        props.setProperty("coolantFlowRate", Double.toString(core.getCoolantFlowRate()));
        props.setProperty("manualFlowControl", Boolean.toString(core.isManualFlowControl()));
        props.setProperty("controlRodPosition", Double.toString(core.getControlRodPosition()));
        props.setProperty("externalReactivity", Double.toString(core.getExternalReactivity()));
        props.setProperty("reactivity", Double.toString(core.getReactivity()));
        props.setProperty("shutdown", Boolean.toString(core.isShutdown()));
        props.setProperty("overheatTicks", Integer.toString(core.getOverheatTicks()));
        props.setProperty("fuelTemperature", Double.toString(core.getFuelTemperature()));
        props.setProperty("power", Double.toString(core.getPower()));
        // Phase 7 physics state (decay-heat curve + fission-product inventory).
        props.setProperty("powerBeforeShutdown", Double.toString(core.getPowerBeforeShutdown()));
        props.setProperty("secondsSinceShutdown", Double.toString(core.getSecondsSinceShutdown()));
        props.setProperty("iodineConc", Double.toString(core.getIodineConcentration()));
        props.setProperty("xenonConc", Double.toString(core.getXenonConcentration()));
        // Phase 8 safety state (damage, pump trip, SCRAM bypass).
        props.setProperty("meltedDown", Boolean.toString(core.isMeltedDown()));
        props.setProperty("pumpTripped", Boolean.toString(core.isPumpTripped()));
        props.setProperty("bypassAutoScram", Boolean.toString(core.isBypassAutoScram()));

        double[] precursors = core.getPrecursorConcentrations();
        for (int i = 0; i < precursors.length; i++) {
            props.setProperty("precursor." + i, Double.toString(precursors[i]));
        }

        try (Writer writer = Files.newBufferedWriter(Paths.get(filePath), StandardCharsets.UTF_8)) {
            props.store(writer, "Reactor simulator saved state (version " + VERSION + ")");
        } catch (IOException e) {
            e.printStackTrace(System.err);
            throw new StatePersistenceException("Failed to save reactor state to '" + filePath + "'.", e);
        }
    }

    /**
     * Reads a file written by {@link #saveState} and restores its values into
     * the provided {@link ReactorCore} instance.
     */
    public static void loadState(ReactorCore core, String filePath) {
        if (core == null) {
            throw new StatePersistenceException("Cannot load state: ReactorCore is null.");
        }
        if (filePath == null || filePath.isBlank()) {
            throw new StatePersistenceException("Cannot load state: file path is missing.");
        }

        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            e.printStackTrace(System.err);
            throw new StatePersistenceException("Failed to load reactor state from '" + filePath + "'.", e);
        }

        try {
            requireKeys(props, "temperature", "coolantFlowRate", "manualFlowControl",
                    "controlRodPosition", "externalReactivity", "reactivity",
                    "shutdown", "overheatTicks", "fuelTemperature", "power");

            double[] precursors = new double[core.getPrecursorConcentrations().length];
            for (int i = 0; i < precursors.length; i++) {
                precursors[i] = getDouble(props, "precursor." + i);
            }

            // Thermal-hydraulics first (temperature setter also re-derives fuel temp,
            // which is then overwritten with the exact saved value below).
            core.setTemperature(getDouble(props, "temperature"));
            core.setCoolantFlowRate(getDouble(props, "coolantFlowRate"));
            core.setManualFlowControl(getBoolean(props, "manualFlowControl"));
            core.setControlRodPosition(getDouble(props, "controlRodPosition"));
            core.setExternalReactivity(getDouble(props, "externalReactivity"));
            core.setReactivity(getDouble(props, "reactivity"));
            core.setShutdown(getBoolean(props, "shutdown"));
            core.setOverheatTicks(getInt(props, "overheatTicks"));
            core.setFuelTemperature(getDouble(props, "fuelTemperature"));

            // Neutronics last: setPower rescales precursors, then restore exact values.
            core.setPower(getDouble(props, "power"));
            core.setPrecursorConcentrations(precursors);

            // Phase 7 fields are optional so pre-Phase-7 save files still load.
            core.setPowerBeforeShutdown(getDoubleOrDefault(props, "powerBeforeShutdown", 0.0));
            core.setSecondsSinceShutdown(getDoubleOrDefault(props, "secondsSinceShutdown", 0.0));
            core.setIodineConcentration(getDoubleOrDefault(props, "iodineConc", 0.0));
            core.setXenonConcentration(getDoubleOrDefault(props, "xenonConc", 0.0));

            // Phase 8 keys are optional so older save files still load.
            core.setMeltedDown(getBooleanOrDefault(props, "meltedDown", false));
            core.setPumpTripped(getBooleanOrDefault(props, "pumpTripped", false));
            core.setBypassAutoScram(getBooleanOrDefault(props, "bypassAutoScram", false));
        } catch (RuntimeException e) {
            e.printStackTrace(System.err);
            if (e instanceof StatePersistenceException spe) {
                throw spe;
            }
            throw new StatePersistenceException(
                    "Failed to load reactor state from '" + filePath + "': " + e.getMessage(), e);
        }
    }

    private static void requireKeys(Properties props, String... keys) {
        for (String key : keys) {
            if (!props.containsKey(key)) {
                throw new StatePersistenceException("Saved state is missing required key: '" + key + "'.");
            }
        }
    }

    private static double getDouble(Properties props, String key) {
        requireKeys(props, key);
        return Double.parseDouble(props.getProperty(key).trim());
    }

    /** Lenient variant for newer optional keys (missing -> fallback). */
    private static boolean getBooleanOrDefault(Properties props, String key, boolean fallback) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        return Boolean.parseBoolean(raw.trim());
    }
    /** Lenient double variant for newer optional keys (missing/invalid -> fallback). */
    private static double getDoubleOrDefault(Properties props, String key, double fallback) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            System.err.println("[StatePersistenceUtil] Invalid value for optional key '"
                    + key + "', using default " + fallback + ".");
            return fallback;
        }
    }

    private static int getInt(Properties props, String key) {
        requireKeys(props, key);
        return Integer.parseInt(props.getProperty(key).trim());
    }

    private static boolean getBoolean(Properties props, String key) {
        requireKeys(props, key);
        return Boolean.parseBoolean(props.getProperty(key).trim());
    }
}
