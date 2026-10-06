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

    // Ramped setpoint actually regulated (MW). The operator target can jump
    // arbitrarily far, but chasing it directly saturates rod drive for the
    // whole approach: rods march at max speed through critical and the plant
    // prompt-jumps past the target before any feedback can brake. Real plants
    // ramp load demand instead (%/min); the effective setpoint glides toward
    // the operator target so error stays small and D + deadband stay in charge.
    private double effectiveTarget = 100.0;
    private static final double TARGET_RAMP_RATE = 100.0; // MW per sim-second

    // Control parameters
    private static final double ROD_SPEED_LIMIT = 0.01; // Max rod movement per second
    // Withdrawal is deliberately far slower than insertion (motor drive out,
    // spring-assisted in — as in real rod drives). Creeping through critical
    // keeps the power period long so feedback and D-braking stay ahead of the
    // excursion; fast insertion is always available to kill overshoot.
    private static final double MAX_WITHDRAW_PER_CALL = 0.0005;
    private static final double PRECISION_TOLERANCE = 10.0; // ±10 MW
    // Anti-hunting: hold rods inside this deadband (fraction of target) while steady.
    private static final double DEADBAND_FRACTION = 0.015; // 1.5% of target power
    // Power-trend stability gate for the deadband (fraction of target per tick).
    private static final double TREND_STABLE_FRACTION = 0.005;
    // Movements smaller than this rod fraction are microscopic jitter: skip them.
    // Must stay well below MAX_WITHDRAW_PER_CALL or withdrawal deadlocks.
    private static final double MIN_ROD_MOVE = MAX_WITHDRAW_PER_CALL / 4.0;

    private long lastRodAdjustmentLog = 0;
    private long lastPrecisionCheck = 0;
    private long lastStabilityCheck = 0;

    // Previous-tick power for the deadband trend gate (NaN until first call).
    private double prevPower = Double.NaN;

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

        // Initialize PID with anti-windup (used when no external strategy is supplied).
        // Gains deliberately sluggish: the S-curve plant is ~3x steeper mid-core
        // than the old linear model, so P is gentler and I slightly stronger.
        // integralMax is scaled to output units (ki * integralMax ~= outputMax/2)
        // so the integrator trims steady-state droop but can never pin the
        // output into saturation by itself — the classic windup/overshoot trap.
        this.pid = new AntiWindupPID(0.0004, 0.00015, 0.001, 30.0, ROD_SPEED_LIMIT);
        this.pid.setSlewRateLimit(ROD_SPEED_LIMIT);

        core.eventBus.subscribe(this::regulate);
    }

    private void regulate() {
        if (!enabled || core.isShutdown()) {
            return;
        }

        double currentPower = core.getPower();

        // Glide the effective setpoint toward the operator target. All error
        // math below uses the effective target; getTargetPower() still reports
        // the operator's demand (UI + tests rely on that).
        double rampStep = TARGET_RAMP_RATE * DT;
        effectiveTarget += MathUtil.clamp(targetPower - effectiveTarget, -rampStep, rampStep);
        double error = effectiveTarget - currentPower;
        boolean inPrecisionRange = Math.abs(error) <= PRECISION_TOLERANCE;

        // Strict deadband against rod hunting: inside 1.5% of target with a
        // steady power trend, real reactors hold rods absolutely still
        // (no wear-inducing micro-movements).
        double deadband = Math.max(1.0, Math.abs(effectiveTarget) * DEADBAND_FRACTION);
        double trendGate = Math.max(0.5, Math.abs(effectiveTarget) * TREND_STABLE_FRACTION);
        boolean trendStable = !Double.isNaN(prevPower)
                && Math.abs(currentPower - prevPower) <= trendGate;
        prevPower = currentPower;
        if (Math.abs(error) <= deadband && trendStable) {
            return;
        }

        // Compute adjustment via optional strategy or internal PID
        double adjustment;
        if (strategy != null) {
            adjustment = strategy.computeAdjustment(currentPower, targetPower, DT);
        } else {
            adjustment = pid.compute(error, DT);
        }

        // Apply rate limiting for realistic rod speed (symmetric anti-jerk cap
        // from the PID slew limiter, then the stricter withdrawal-only cap).
        adjustment = MathUtil.clamp(adjustment, -ROD_SPEED_LIMIT, ROD_SPEED_LIMIT);
        if (adjustment > 0.0) {
            adjustment = Math.min(adjustment, MAX_WITHDRAW_PER_CALL);
        }

        double oldPos = core.getControlRodPosition();
        double newPos = MathUtil.clamp(oldPos + adjustment, 0.0, 1.0);

        // Minimum movement threshold: skip microscopic jitter (including
        // saturation dither when rods already sit at a travel limit).
        if (Math.abs(newPos - oldPos) < MIN_ROD_MOVE) {
            return;
        }
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
                            currentPower, effectiveTarget, error));
            lastPrecisionCheck = now;
        }

        // Stability detection
        if (effectiveTarget > 0.0 && Math.abs(error) < effectiveTarget * STABILITY_TOLERANCE &&
                now - lastStabilityCheck > STABILITY_LOG_COOLDOWN) {

            double errorPercent = Math.abs(error) / effectiveTarget * 100.0;
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

        // Scale integral term proportionally to prevent discontinuous mechanical "kick"
        if (Math.abs(target - oldTarget) > 100.0 && oldTarget > 0) {
            double ratio = target / oldTarget;
            pid.scaleIntegral(ratio);
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
