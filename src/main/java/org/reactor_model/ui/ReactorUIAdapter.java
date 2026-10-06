package org.reactor_model.ui;

import org.reactor_model.core.ReactorCore;
import org.reactor_model.disturbance.PowerDemandSimulator;
import org.reactor_model.regulation.AutoRegulator;
import org.reactor_model.simulation.SimulationLoop;
import org.reactor_model.util.CsvDataExporter;
import org.reactor_model.util.MathUtil;
import org.reactor_model.util.StatePersistenceUtil;

/**
 * Thread-safe bridge between the simulation and the Swing EDT.
 * The simulation thread writes a new snapshot on every EventBus tick.
 * The EDT reads the latest snapshot via {@link #getSnapshot()} without blocking.
 */
public class ReactorUIAdapter {

    private final ReactorCore core;
    private final AutoRegulator regulator;
    private final SimulationLoop loop;
    private final PowerDemandSimulator demandSimulator;

    private volatile ReactorStateSnapshot snapshot;

    /** Buffers snapshots for CSV export (recorded on every event-bus tick). */
    private final CsvDataExporter exporter = new CsvDataExporter();

    public ReactorUIAdapter(ReactorCore core,
                            AutoRegulator regulator,
                            SimulationLoop loop,
                            PowerDemandSimulator demandSimulator) {
        this.core      = core;
        this.regulator = regulator;
        this.loop      = loop;
        this.demandSimulator = demandSimulator;

        // Build an initial snapshot so the UI never sees null
        snapshot = buildSnapshot();

        // Subscribe to every core update event
        core.eventBus.subscribe(this::refreshSnapshot);
    }

    // ---- snapshot access -----------------------------------------------

    public ReactorStateSnapshot getSnapshot() {
        return snapshot;
    }

    private void refreshSnapshot() {
        snapshot = buildSnapshot();
        exporter.recordSnapshot(snapshot);
    }

    private ReactorStateSnapshot buildSnapshot() {
        return new ReactorStateSnapshot(
                core.getPower(),
                core.getTemperature(),
                core.getCoolantFlowRate(),
                core.getControlRodPosition(),
                core.getReactivity(),
                regulator.getTargetPower(),
                core.getOverheatTicks(),
                core.isShutdown(),
                regulator.isEnabled()
        );
    }

    // ---- operator commands (safe to call from EDT) ---------------------

    public void startLoop()  { loop.start(); }
    public void stopLoop()   { loop.stop();  }

    /** Returns whether the simulation loop is currently running. */
    public boolean isRunning() { return loop.isRunning(); }

    public void setTargetPower(double mw) {
        // Defense in depth: clamp to the UI control range so out-of-range
        // values can never reach the regulator, even from programmatic callers.
        regulator.setTargetPower(MathUtil.clamp(mw, 0.0, 5000.0));
    }

    public void setAutoRegulator(boolean enabled) {
        regulator.setEnabled(enabled);
    }

    public void setControlRodPosition(double pos) {
        core.setControlRodPosition(pos);
    }

    public void injectSpike() {
        core.addReactivity(0.006);
    }

    public void simulateCoolantFailure() {
        core.setCoolantFlowRate(0.0);
    }

    public void scram() {
        // Emergency SCRAM: fully insert control rods and add large negative reactivity
        // This immediately stops the fission chain reaction
        core.setControlRodPosition(0.0);
        core.addReactivity(-0.5);
    }

    public void restart() {
        if (core.isShutdown()) {
            core.restart();
        }
    }

    public double getTargetPower() {
        return regulator.getTargetPower();
    }

    public double getControlRodPosition() {
        return core.getControlRodPosition();
    }

    // ---- Disturbance simulation control ----

    /**
     * Enable random disturbance simulation.
     * This will inject random reactivity spikes and change target power.
     * Only use for testing regulator response.
     */
    public void enableDisturbances() {
        demandSimulator.enable();
    }

    /**
     * Disable random disturbance simulation (default).
     * Reactor will operate in stable, predictable mode.
     */
    public void disableDisturbances() {
        demandSimulator.disable();
    }

    /**
     * Toggle disturbance simulation on/off.
     */
    public void toggleDisturbances() {
        demandSimulator.toggle();
    }

    /**
     * Returns whether disturbance simulation is active.
     */
    public boolean isDisturbanceEnabled() {
        return demandSimulator.isEnabled();
    }

    // ---- State persistence (save/load) -------------------------------

    /**
     * Saves the current reactor core state to {@code filePath}.
     * Safe to call from the EDT; file I/O is brief (small properties file).
     */
    public void saveState(String filePath) {
        StatePersistenceUtil.saveState(core, filePath);
    }

    /**
     * Loads a previously saved reactor core state from {@code filePath}.
     * Stops the loop first (stop() joins the physics thread) to avoid
     * concurrent mutation, then restarts it if it was running.
     * On load failure the loop is left stopped (safer than resuming on
     * a half-restored core) and the exception is rethrown for the caller
     * to report.
     */
    public void loadState(String filePath) {
        boolean wasRunning = loop.isRunning();
        if (wasRunning) {
            loop.stop();
        }
        try {
            StatePersistenceUtil.loadState(core, filePath);
        } catch (RuntimeException e) {
            System.err.println("[ReactorUIAdapter] Failed to load state from '"
                    + filePath + "': " + e.getMessage());
            throw e;
        }
        if (wasRunning) {
            loop.start();
        }
    }

    /**
     * Sets the simulation speed multiplier (delegates to the loop).
     * Values are clamped to [1.0, 10.0] by the loop.
     */
    public void setSimulationSpeed(double multiplier) {
        loop.setSpeedMultiplier(multiplier);
    }

    /** Returns the current simulation speed multiplier. */
    public double getSimulationSpeed() {
        return loop.getSpeedMultiplier();
    }

    // ---- CSV data export (offline analysis) ----------------------------

    /**
     * Exports all snapshots recorded since the last export to a CSV file
     * and clears the internal buffer.
     * Safe to call from the EDT; delegates to {@link CsvDataExporter}.
     *
     * @param filePath destination {@code .csv} file
     */
    public void exportData(String filePath) {
        exporter.export(filePath);
    }

    /** Returns how many snapshots are currently buffered for export. */
    public int getBufferedSampleCount() {
        return exporter.getSampleCount();
    }

    // ---- Accident testing (LOFA / ATWS) ----------------------------------

    /**
     * Trips the main coolant pump (flywheel coastdown to zero flow).
     * Safe to call from the EDT.
     */
    public void failCoolantPump() {
        core.failCoolantPump();
    }

    /**
     * Arms or disarms the automatic-SCRAM bypass for ATWS testing.
     * A bypassed core logs severe warnings instead of tripping.
     */
    public void setBypassScram(boolean bypass) {
        core.setBypassAutoScram(bypass);
    }

    /** Returns whether the auto-SCRAM bypass is currently armed. */
    public boolean isBypassScram() {
        return core.isBypassAutoScram();
    }

    /** Returns whether the core has melted down (restart is then impossible). */
    public boolean isCoreMeltedDown() {
        return core.isMeltedDown();
    }
}
