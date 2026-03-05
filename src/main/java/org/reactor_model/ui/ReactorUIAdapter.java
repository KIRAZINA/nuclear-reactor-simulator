package org.reactor_model.ui;

import org.reactor_model.core.ReactorCore;
import org.reactor_model.cooling.CoolingSystem;
import org.reactor_model.disturbance.PowerDemandSimulator;
import org.reactor_model.regulation.AutoRegulator;
import org.reactor_model.simulation.SimulationLoop;

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

    public void setTargetPower(double mw) {
        regulator.setTargetPower(mw);
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
}
