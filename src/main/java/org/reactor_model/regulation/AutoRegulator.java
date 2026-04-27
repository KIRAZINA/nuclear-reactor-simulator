package org.reactor_model.regulation;

import org.reactor_model.core.ReactorCore;
import org.reactor_model.logger.ReactorLogger;
import org.reactor_model.util.MathUtil;

/**
 * Automatic power regulator using a pluggable control strategy (PID by default).
 * Reacts to reactor core updates via the event bus.
 * 
 * Precision mode: Maintains power within ±10 MW of target when using PrecisionPIDStrategy.
 */
public class AutoRegulator {

    private final ReactorCore core;
    private final ReactorLogger logger;
    private final AntiWindupPID pid;
    private final RegulationStrategy strategy;

    private double targetPower = 100.0;
    private boolean enabled = true;

    // Control parameters
    private static final double ROD_SPEED_LIMIT = 0.01; // Max rod movement per second
    private static final double PRECISION_TOLERANCE = 10.0; // ±10 MW

    private long lastRodAdjustmentLog = 0;
    private long lastPrecisionCheck = 0;
    private long lastStabilityCheck = 0;

    private static final long ROD_LOG_COOLDOWN = 5000L;
    private static final long PRECISION_LOG_COOLDOWN = 3000L;
    private static final long STABILITY_LOG_COOLDOWN = 10000L;
    private static final double STABILITY_TOLERANCE = 0.02; // ±2%
    private static final double DT = 0.1;

    public AutoRegulator(ReactorCore core, ReactorLogger logger) {
        this(core, logger, null);
    }

    public AutoRegulator(ReactorCore core, ReactorLogger logger, RegulationStrategy strategy) {
        this.core = core;
        this.logger = logger;
        this.strategy = strategy;

        // Initialize PID with anti-windup (used when no external strategy is supplied)
        this.pid = new AntiWindupPID(0.001, 0.0001, 0.0005, 1000.0, ROD_SPEED_LIMIT);

        core.eventBus.subscribe(this::regulate);
    }

    private void regulate() {
        if (!enabled || core.isShutdown()) {
            return;
        }

        double currentPower = core.getPower();
        double error = targetPower - currentPower;
        boolean inPrecisionRange = Math.abs(error) <= PRECISION_TOLERANCE;

        // Compute adjustment via optional strategy or internal PID
        double adjustment;
        if (strategy != null) {
            adjustment = strategy.computeAdjustment(currentPower, targetPower, DT);
        } else {
            adjustment = pid.compute(error, DT);
        }

        // Apply rate limiting for realistic rod speed
        adjustment = MathUtil.clamp(adjustment, -ROD_SPEED_LIMIT, ROD_SPEED_LIMIT);

        double oldPos = core.getControlRodPosition();
        double newPos = MathUtil.clamp(oldPos + adjustment, 0.0, 1.0);
        core.setControlRodPosition(newPos);

        long now = System.currentTimeMillis();

        // Log significant rod movement
        if (Math.abs(oldPos - newPos) > 0.001 && now - lastRodAdjustmentLog > ROD_LOG_COOLDOWN) {
            logger.logDecision("AutoRegulator",
                    String.format("Rod adjustment: %+.4f → pos %.3f", (newPos - oldPos), newPos));
            lastRodAdjustmentLog = now;
        }

        // Precision mode logging
        if (inPrecisionRange && now - lastPrecisionCheck > PRECISION_LOG_COOLDOWN) {
            logger.logDecision("AutoRegulator",
                    String.format("PRECISION: Power %.1f MW (target %.1f MW, error %+.1f MW)",
                            currentPower, targetPower, error));
            lastPrecisionCheck = now;
        }

        // Stability detection
        if (Math.abs(error) < targetPower * STABILITY_TOLERANCE &&
                now - lastStabilityCheck > STABILITY_LOG_COOLDOWN) {

            double errorPercent = Math.abs(error) / targetPower * 100.0;
            String precisionStatus = Math.abs(error) <= PRECISION_TOLERANCE ? "✓ PRECISION" : "⚠ STABLE";

            logger.logDecision("AutoRegulator",
                    String.format("%s: Power %.2f MW (error: %+.2f MW / %.2f%%)",
                            precisionStatus, currentPower, error, errorPercent));

            lastStabilityCheck = now;
        }
    }

    public void setTargetPower(double target) {
        double oldTarget = this.targetPower;
        this.targetPower = target;
        
        // Reset controller state on significant target change
        if (Math.abs(target - oldTarget) > 100.0) {
            pid.reset();
            if (strategy instanceof SimplePIDStrategy) {
                ((SimplePIDStrategy) strategy).reset();
            } else if (strategy instanceof PrecisionPIDStrategy) {
                ((PrecisionPIDStrategy) strategy).reset();
            }
        }
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

    /**
     * Public method for testing purposes.
     * Allows direct invocation of regulate() in unit tests.
     */
    public void regulateForTest() {
        regulate();
    }
}
