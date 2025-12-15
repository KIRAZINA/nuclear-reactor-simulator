package org.reactor_model.disturbance;

import org.reactor_model.core.ReactorCore;
import org.reactor_model.logger.ReactorLogger;
import org.reactor_model.regulation.AutoRegulator;

import java.util.Random;

public class PowerDemandSimulator {
    private final ReactorCore core;
    private final AutoRegulator regulator;
    private final ReactorLogger logger;
    private final Random random = new Random();
    private int tickCounter = 0;
    private static final int DISTURB_INTERVAL = 50;

    public PowerDemandSimulator(ReactorCore core, AutoRegulator regulator, ReactorLogger logger) {
        this.core = core;
        this.regulator = regulator;
        this.logger = logger;
    }

    public void update() {
        tickCounter++;
        if (tickCounter % DISTURB_INTERVAL == 0) {
            if (random.nextBoolean()) {
                double deltaReactivity = random.nextDouble() * 0.005;
                core.addReactivity(deltaReactivity);
                logger.logDecision("PowerDemand",
                        String.format("Introduced positive reactivity spike: %.4f", deltaReactivity));
            } else {
                double currentTarget = regulator.getTargetPower();
                double increase = random.nextDouble() * 50;
                double newTarget = currentTarget + increase;

                // Clamp на MAX_SAFE_POWER
                newTarget = Math.min(newTarget, ReactorCore.MAX_SAFE_POWER);

                regulator.setTargetPower(newTarget);
                logger.logDecision("PowerDemand",
                        String.format("Increased target power to: %.2f MW (clamped if exceeded max)", newTarget));
            }
        }
    }
}