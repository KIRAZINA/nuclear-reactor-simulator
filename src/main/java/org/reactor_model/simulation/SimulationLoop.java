package org.reactor_model.simulation;

import org.reactor_model.core.ReactorCore;
import org.reactor_model.cooling.CoolingSystem;
import org.reactor_model.disturbance.PowerDemandSimulator;
import org.reactor_model.regulation.AutoRegulator;

/**
 * Main real-time simulation loop.
 * Runs in a dedicated thread and updates all subsystems at a fixed timestep.
 */
public class SimulationLoop {

    private final ReactorCore core;
    private final AutoRegulator regulator;
    private final PowerDemandSimulator demandSimulator;
    private final CoolingSystem coolingSystem;

    private volatile boolean running = false;
    private Thread loopThread;

    private static final double DT = 0.1;
    private static final int LOG_INTERVAL_TICKS = 15;

    private int tick = 0;
    private double previousPower = 0.01;

    public SimulationLoop(ReactorCore core,
                          AutoRegulator regulator,
                          PowerDemandSimulator demandSimulator,
                          CoolingSystem coolingSystem) {

        this.core = core;
        this.regulator = regulator;
        this.demandSimulator = demandSimulator;
        this.coolingSystem = coolingSystem;
    }

    /**
     * Starts the simulation loop in a separate thread.
     * If already running, the call is ignored.
     */
    public synchronized void start() {
        if (running) {
            return;
        }

        running = true;

        loopThread = new Thread(this::runLoop, "ReactorSimulationLoop");
        loopThread.start();
    }

    /**
     * Stops the simulation loop gracefully.
     */
    public synchronized void stop() {
        running = false;

        if (loopThread != null) {
            loopThread.interrupt();
        }
    }

    private void runLoop() {
        while (running) {
            updateSubsystems();
            handleOverheatProtection();
            logPeriodicState();
            sleepForTimestep();
        }
    }

    private void updateSubsystems() {
        demandSimulator.update();
        coolingSystem.update(regulator.getTargetPower());

        // Protect against NaN or Infinity in previousPower
        if (Double.isNaN(previousPower) || Double.isInfinite(previousPower)) {
            previousPower = 0.01;
        }

        core.update(DT, regulator.getTargetPower(), previousPower);
        previousPower = core.getPower();
    }

    private void handleOverheatProtection() {
        if (core.getOverheatTicks() > ReactorCore.OVERHEAT_MAX_TICKS) {
            double newTarget = regulator.getTargetPower() * 0.8;
            regulator.setTargetPower(newTarget);
            core.resetOverheatTicks();
        }
    }

    private void logPeriodicState() {
        tick++;
        if (tick % LOG_INTERVAL_TICKS == 0) {
            core.logCurrentState();
        }
    }

    private void sleepForTimestep() {
        try {
            Thread.sleep((long) (DT * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
