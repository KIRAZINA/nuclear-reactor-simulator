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

    private volatile ReactorStateSnapshot snapshot;

    public ReactorUIAdapter(ReactorCore core,
                            AutoRegulator regulator,
                            SimulationLoop loop) {
        this.core      = core;
        this.regulator = regulator;
        this.loop      = loop;

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
        // Force an emergency shutdown by raising temperature past critical threshold
        // We do it by calling the public restart-path in reverse: set shutdown via
        // injecting a massive reactivity spike that triggers SCRAM organically.
        // Actually simpler: just set coolant to 0 and add large positive reactivity
        core.setCoolantFlowRate(0.0);
        core.addReactivity(0.05);
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
}
