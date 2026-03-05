package org.reactor_model.cooling;

import org.reactor_model.core.ReactorCore;
import org.reactor_model.logger.ReactorLogger;
import org.reactor_model.util.MathUtil;

public class CoolingSystem {

    private final ReactorCore core;
    private final ReactorLogger logger;

    private static final double HIGH_TEMP_THRESHOLD = 620.0;
    private static final double COOLANT_BASE_TEMP = 300.0;

    private static final double MIN_FLOW = 0.2;
    private static final double FLOW_RANGE = 0.7;
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
        // Skip automatic cooling update if manual control is active
        if (core.isManualFlowControl()) {
            return;
        }
        
        double temp = core.getTemperature();
        double power = core.getPower();
        long now = System.currentTimeMillis();

        double flow = computeFlow(temp);

        // Overpower protection
        if (power > ReactorCore.MAX_SAFE_POWER) {
            flow = MAX_FLOW;
            core.addReactivity(-0.003);

            if (now - lastAggressiveLog > DECISION_COOLDOWN_MS) {
                logger.logDecision("CoolingSystem",
                        "Aggressive cooling activated due to overpower.");
                lastAggressiveLog = now;
            }
        }

        core.setCoolantFlowRate(flow);

        // Log max flow activation
        if (flow >= MAX_FLOW && now - lastMaxFlowDecision > DECISION_COOLDOWN_MS) {
            logger.logDecision("CoolingSystem",
                    "Maximum coolant flow rate applied due to high temperature.");
            lastMaxFlowDecision = now;
        }
    }

    private double computeFlow(double temp) {
        if (temp <= COOLANT_BASE_TEMP) {
            return MIN_FLOW;
        }

        double normalized = (temp - COOLANT_BASE_TEMP) / (HIGH_TEMP_THRESHOLD - COOLANT_BASE_TEMP);
        double flow = MIN_FLOW + (normalized * normalized) * (MAX_FLOW - MIN_FLOW);

        return MathUtil.clamp(flow, 0.0, MAX_FLOW);
    }
}
