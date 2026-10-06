package org.reactor_model.simulation;

import org.reactor_model.core.ReactorCore;
import org.reactor_model.cooling.CoolingSystem;
import org.reactor_model.disturbance.PowerDemandSimulator;
import org.reactor_model.regulation.AutoRegulator;
import org.reactor_model.util.ConfigManager;
import org.reactor_model.util.MathUtil;

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

    private static final int LOG_INTERVAL_TICKS = 15;
    private static final long MIN_SLEEP_MS = 10L;

    /** Physics timestep in seconds (configurable via {@code simulation.dt}). */
    private volatile double dt = ConfigManager.DEFAULT_SIMULATION_DT;

    /** Simulation speed multiplier: 1.0 = real time, higher = faster. Range [1.0, 10.0]. */
    private volatile double speedMultiplier = 1.0;

    private int tick = 0;

    public SimulationLoop(ReactorCore core,
                          AutoRegulator regulator,
                          PowerDemandSimulator demandSimulator,
                          CoolingSystem coolingSystem) {

        this.core = core;
        this.regulator = regulator;
        this.demandSimulator = demandSimulator;
        this.coolingSystem = coolingSystem;
        this.dt = ConfigManager.getSimulationDt();
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
        loopThread.setDaemon(true);
        loopThread.start();
    }

    /**
     * Stops the simulation loop gracefully.
     * Joins the loop thread (up to 1s) so physics processing has fully
     * halted before returning — required for race-free save/load.
     */
    public void stop() {
        final Thread threadToJoin;
        synchronized (this) {
            running = false;
            threadToJoin = loopThread;
            if (threadToJoin != null) {
                threadToJoin.interrupt();
            }
        }
        // Join outside the monitor so isRunning()/start() callers are not
        // blocked while we wait, and never join ourselves (deadlock).
        if (threadToJoin != null && threadToJoin != Thread.currentThread()) {
            try {
                threadToJoin.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Sets the simulation speed multiplier, clamped to [1.0, 10.0].
     * Higher values shorten the per-timestep sleep (fast-forward).
     */
    public void setSpeedMultiplier(double multiplier) {
        this.speedMultiplier = MathUtil.clamp(multiplier, 1.0, 10.0);
    }

    /** Returns the current simulation speed multiplier. */
    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    /** Returns the physics timestep in seconds (from {@code simulation.dt}). */
    public double getDt() {
        return dt;
    }

    /**
     * Computes the per-timestep sleep in milliseconds for the current speed.
     * Extracted for testability: base sleep shortens with the speed
     * multiplier and never drops below {@value #MIN_SLEEP_MS} ms.
     */
    long computeSleepMs() {
        long sleepMs = (long) ((dt * 1000) / Math.max(speedMultiplier, 1.0));
        return Math.max(sleepMs, MIN_SLEEP_MS);
    }

    /**
     * Returns whether the simulation loop thread is currently running.
     * Used by the UI layer for button state management and rapid-fire protection.
     */
    public synchronized boolean isRunning() {
        return running;
    }

    private void runLoop() {
        while (running) {
            try {
                updateSubsystems();
                handleOverheatProtection();
                logPeriodicState();
            } catch (Exception e) {
                // Safety net: never let the simulation thread die silently.
                // Log the failure, force a SCRAM, and terminate the loop so no
                // erroneous physics calculations continue.
                System.err.println("[SimulationLoop] Unhandled exception, triggering SCRAM: " + e);
                e.printStackTrace(System.err);
                try {
                    core.emergencyShutdown("System Exception triggered SCRAM: " + e);
                } catch (Exception scramFailure) {
                    System.err.println("[SimulationLoop] SCRAM trigger failed: " + scramFailure);
                    scramFailure.printStackTrace(System.err);
                }
                running = false;
                break;
            }
            sleepForTimestep();
        }
    }

    private void updateSubsystems() {
        // Improved coupling order for numerical stability:
        // 1. External disturbances (PowerDemandSimulator)
        demandSimulator.update();

        // 2. Calculate reactivity feedbacks (core internal)
        // (Reactivity calculation now happens inside core.update())

        // 3. Advance neutronics and thermal-hydraulics (core)
        core.update(dt);

        // 4. Update cooling system based on current state
        coolingSystem.update(regulator.getTargetPower());

        // 5. Run control system (regulator) - now sees updated state
        // Regulator runs via event bus subscription

        // 6. Apply automatic protections (already done in core.update())
    }

    private void handleOverheatProtection() {
        if (core.getOverheatTicks() > ReactorCore.OVERHEAT_MAX_TICKS) {
            if (!regulator.isEnabled()) {
                // Direct intervention: forcibly insert rods when regulator is disabled
                core.handleOverheatProtectionManualOverride();
            } else {
                // Normal path: reduce target power and let regulator respond
                double newTarget = regulator.getTargetPower() * 0.8;
                regulator.setTargetPower(newTarget);
            }
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
            Thread.sleep(computeSleepMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
