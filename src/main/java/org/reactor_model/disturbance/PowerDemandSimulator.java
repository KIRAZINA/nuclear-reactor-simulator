package org.reactor_model.disturbance;

import org.reactor_model.core.ReactorCore;
import org.reactor_model.logger.ReactorLogger;
import org.reactor_model.regulation.AutoRegulator;
import org.reactor_model.util.MathUtil;

import java.util.Random;

/**
 * Simulates random power demand fluctuations and reactivity disturbances.
 * 
 * DISABLED by default for stable reactor operation.
 * Enable only for testing regulator response to disturbances.
 */
public class PowerDemandSimulator {

    private final ReactorCore core;
    private final AutoRegulator regulator;
    private final ReactorLogger logger;
    private final Random random = new Random();

    private int tickCounter = 0;
    private boolean enabled = false; // DISABLED by default for stable operation

    private static final int DISTURB_INTERVAL = 50;
    private static final double MAX_REACTIVITY_SPIKE = 0.005;
    private static final double MAX_TARGET_INCREASE = 50.0;

    public PowerDemandSimulator(ReactorCore core, AutoRegulator regulator, ReactorLogger logger) {
        this.core = core;
        this.regulator = regulator;
        this.logger = logger;
    }

    public void update() {
        // Skip if disabled (default state)
        if (!enabled) {
            return;
        }

        tickCounter++;

        if (tickCounter % DISTURB_INTERVAL != 0) {
            return;
        }

        // Balance: either increase or decrease target power
        if (random.nextBoolean()) {
            applyReactivitySpike();
        } else {
            // Randomly decide to increase or decrease target power
            if (random.nextBoolean()) {
                adjustTargetPower(true);  // increase
            } else {
                adjustTargetPower(false); // decrease
            }
        }
    }

    private void applyReactivitySpike() {
        double delta = random.nextDouble() * MAX_REACTIVITY_SPIKE;
        core.addReactivity(delta);

        logger.logDecision("PowerDemand",
                String.format("Injected positive reactivity spike: %.4f", delta));
    }

    private void adjustTargetPower(boolean increase) {
        double currentTarget = regulator.getTargetPower();
        double delta;

        if (increase) {
            if (currentTarget >= ReactorCore.MAX_SAFE_POWER) {
                return;
            }
            delta = random.nextDouble() * MAX_TARGET_INCREASE;
            double newTarget = currentTarget + delta;
            newTarget = MathUtil.clamp(newTarget, 0.0, ReactorCore.MAX_SAFE_POWER);

            regulator.setTargetPower(newTarget);
            logger.logDecision("PowerDemand",
                    String.format("Increased target power to %.2f MW", newTarget));
        } else {
            if (currentTarget <= 100.0) {
                return;
            }
            delta = random.nextDouble() * (MAX_TARGET_INCREASE * 0.5);  // Decrease more carefully
            double newTarget = currentTarget - delta;
            newTarget = MathUtil.clamp(newTarget, 100.0, ReactorCore.MAX_SAFE_POWER);

            regulator.setTargetPower(newTarget);
            logger.logDecision("PowerDemand",
                    String.format("Decreased target power to %.2f MW", newTarget));
        }
    }

    /**
     * Enables disturbance simulation.
     * Use for testing regulator response to unexpected events.
     */
    public void enable() {
        if (!enabled) {
            enabled = true;
            logger.logDecision("PowerDemand", "Disturbance simulation ENABLED");
        }
    }

    /**
     * Disables disturbance simulation (default state).
     * Reactor will operate in stable, predictable mode.
     */
    public void disable() {
        if (enabled) {
            enabled = false;
            logger.logDecision("PowerDemand", "Disturbance simulation DISABLED");
        }
    }

    /**
     * Returns whether disturbance simulation is active.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Toggle disturbance simulation on/off.
     */
    public void toggle() {
        if (enabled) {
            disable();
        } else {
            enable();
        }
    }
}
