// src/main/java/org/reactor_model/regulation/AutoRegulator.java
package org.reactor_model.regulation;

import org.reactor_model.core.ReactorCore;
import org.reactor_model.logger.ReactorLogger;

public class AutoRegulator {
    private final ReactorCore core;
    private final ReactorLogger logger;
    private final RegulationStrategy strategy;

    private double targetPower = 100.0;
    private boolean enabled = true;

    private long lastRodAdjustmentLog = 0;
    private long lastStabilityCheck = 0;
    private static final long ROD_LOG_COOLDOWN = 5000L;
    private static final long STABILITY_LOG_COOLDOWN = 10000L;
    private static final double STABILITY_TOLERANCE = 0.02;  // ±2%

    public AutoRegulator(ReactorCore core, ReactorLogger logger, RegulationStrategy strategy) {
        this.core = core;
        this.logger = logger;
        this.strategy = strategy;
        core.eventBus.subscribe(this::regulate);
    }

    private void regulate() {
        if (!enabled) return;

        double dt = 0.1;
        double currentPower = core.getPower();
        double error = targetPower - currentPower;
        double adjustment = strategy.computeAdjustment(currentPower, targetPower, dt);
        double newPos = core.getControlRodPosition() + adjustment;
        core.setControlRodPosition(newPos);

        long now = System.currentTimeMillis();
        if (now - lastRodAdjustmentLog > ROD_LOG_COOLDOWN) {
            logger.logDecision("AutoRegulator",
                    String.format("Rod correction: %.4f → %.3f", adjustment, newPos));
            lastRodAdjustmentLog = now;
        }

        if (Math.abs(error) < targetPower * STABILITY_TOLERANCE &&
                now - lastStabilityCheck > STABILITY_LOG_COOLDOWN) {
            logger.logDecision("AutoRegulator",
                    String.format("Power stabilized at %.2f MW (error %.2f%%)",
                            currentPower, (Math.abs(error) / targetPower * 100)));
            lastStabilityCheck = now;
        }
    }

    public void setTargetPower(double target) { this.targetPower = target; }
    public double getTargetPower() { return targetPower; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
}
