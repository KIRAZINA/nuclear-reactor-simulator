package org.reactor_model.regulation;

import org.reactor_model.core.ReactorCore;
import org.reactor_model.logger.ReactorLogger;
import org.reactor_model.util.MathUtil;

/**
 * Automatic power regulator using a pluggable control strategy (PID by default).
 * Reacts to reactor core updates via the event bus.
 */
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
    private static final double STABILITY_TOLERANCE = 0.02; // ±2%

    private static final double DT = 0.1;
    private static final double MAX_ROD_STEP = 0.05; // max allowed rod movement per update

    public AutoRegulator(ReactorCore core, ReactorLogger logger, RegulationStrategy strategy) {
        this.core = core;
        this.logger = logger;
        this.strategy = strategy;

        core.eventBus.subscribe(this::regulate);
    }

    private void regulate() {
        if (!enabled || core.isShutdown()) {
            return;
        }

        double currentPower = core.getPower();
        double error = targetPower - currentPower;

        double adjustment = strategy.computeAdjustment(currentPower, targetPower, DT);
        adjustment = MathUtil.clamp(adjustment, -MAX_ROD_STEP, MAX_ROD_STEP);

        double oldPos = core.getControlRodPosition();
        double newPos = oldPos + adjustment;
        core.setControlRodPosition(newPos);

        long now = System.currentTimeMillis();

        // Log rod movement
        if (Math.abs(adjustment) > 0.0001 && now - lastRodAdjustmentLog > ROD_LOG_COOLDOWN) {
            logger.logDecision("AutoRegulator",
                    String.format("Rod adjustment: %.4f → new pos %.3f", adjustment, newPos));
            lastRodAdjustmentLog = now;
        }

        // Stability detection
        if (Math.abs(error) < targetPower * STABILITY_TOLERANCE &&
                now - lastStabilityCheck > STABILITY_LOG_COOLDOWN) {

            double errorPercent = Math.abs(error) / targetPower * 100.0;

            logger.logDecision("AutoRegulator",
                    String.format("Power stabilized at %.2f MW (error %.2f%%)",
                            currentPower, errorPercent));

            lastStabilityCheck = now;
        }
    }

    public void setTargetPower(double target) {
        this.targetPower = target;
    }

    public double getTargetPower() {
        return targetPower;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
