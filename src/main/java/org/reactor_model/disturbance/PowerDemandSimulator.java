package org.reactor_model.disturbance;

import org.reactor_model.core.ReactorCore;
import org.reactor_model.logger.ReactorLogger;
import org.reactor_model.regulation.AutoRegulator;
import org.reactor_model.util.MathUtil;

import java.util.Random;

public class PowerDemandSimulator {

    private final ReactorCore core;
    private final AutoRegulator regulator;
    private final ReactorLogger logger;
    private final Random random = new Random();

    private int tickCounter = 0;

    private static final int DISTURB_INTERVAL = 50;
    private static final double MAX_REACTIVITY_SPIKE = 0.005;
    private static final double MAX_TARGET_INCREASE = 50.0;

    public PowerDemandSimulator(ReactorCore core, AutoRegulator regulator, ReactorLogger logger) {
        this.core = core;
        this.regulator = regulator;
        this.logger = logger;
    }

    public void update() {
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
}
