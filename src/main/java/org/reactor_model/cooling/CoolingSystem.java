// src/main/java/org/reactor_model/cooling/CoolingSystem.java
package org.reactor_model.cooling;

import org.reactor_model.core.ReactorCore;
import org.reactor_model.logger.ReactorLogger;

public class CoolingSystem {
    private final ReactorCore core;
    private final ReactorLogger logger;

    private static final double HIGH_TEMP_THRESHOLD = 520.0;
    private static final double MAX_FLOW = 1.0;

    // Rate limiting
    private long lastMaxFlowDecision = 0;
    private long lastAggressiveLog = 0;
    private static final long DECISION_COOLDOWN_MS = 8000L;

    public CoolingSystem(ReactorCore core, ReactorLogger logger) {
        this.core = core;
        this.logger = logger;
    }

    public void update(double targetPower) {
        double temp = core.getTemperature();
        double power = core.getPower();
        long now = System.currentTimeMillis();

        double flow = (temp > HIGH_TEMP_THRESHOLD)
                ? MAX_FLOW
                : 0.5 + (temp - 300.0) / (HIGH_TEMP_THRESHOLD - 300.0) * 0.5;

        if (power > ReactorCore.MAX_SAFE_POWER) {
            flow = MAX_FLOW;
            core.addReactivity(-0.003);

            if (now - lastAggressiveLog > DECISION_COOLDOWN_MS) {
                logger.logDecision("CoolingSystem",
                        "Aggressive cooling activated! Reduction to ~6000 MW.");
                lastAggressiveLog = now;
            }
        }

        core.setCoolantFlowRate(flow);

        if (flow >= MAX_FLOW && now - lastMaxFlowDecision > DECISION_COOLDOWN_MS) {
            logger.logDecision("CoolingSystem",
                    "Max. coolant flow rate due to high temperature.");
            lastMaxFlowDecision = now;
        }
    }
}