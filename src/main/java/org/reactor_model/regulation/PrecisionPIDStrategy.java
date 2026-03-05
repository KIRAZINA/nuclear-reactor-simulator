package org.reactor_model.regulation;

import org.reactor_model.util.MathUtil;

/**
 * High-precision PID controller for maintaining reactor power within ±10 MW tolerance.
 * 
 * Features:
 * - Adaptive gain scheduling based on power level
 * - Deadband to prevent hunting around setpoint
 * - Enhanced integral action for zero steady-state error
 * - Derivative filtering for noise rejection
 */
public class PrecisionPIDStrategy implements RegulationStrategy {

    // Base PID coefficients (will be scaled adaptively)
    private static final double KP_BASE = 0.0003;    // Proportional gain
    private static final double KI_BASE = 0.00008;   // Integral gain
    private static final double KD_BASE = 0.0001;    // Derivative gain

    // Precision control parameters
    private static final double POWER_TOLERANCE = 10.0;  // ±10 MW deadband
    private static final double DEADBAND_FACTOR = 0.5;   // Reduce gain inside deadband

    // Anti-windup limits
    private static final double INTEGRAL_MAX = 50000.0;
    private static final double DERIVATIVE_MAX = 2000.0;

    // Derivative filter
    private static final double DERIVATIVE_SMOOTHING = 0.25;

    // State variables
    private double integral = 0.0;
    private double prevError = 0.0;
    private double prevDerivative = 0.0;

    @Override
    public double computeAdjustment(double currentPower, double targetPower, double dt) {
        double error = targetPower - currentPower;

        // Apply deadband: if error is within tolerance, reduce integral accumulation
        boolean inDeadband = Math.abs(error) <= POWER_TOLERANCE;
        
        // Adaptive gain scheduling based on power level
        double gainFactor = computeGainFactor(currentPower, targetPower);

        // Adaptive coefficients
        double kp = KP_BASE * gainFactor;
        double ki = KI_BASE * gainFactor;
        double kd = KD_BASE * gainFactor;

        // Reduce gains inside deadband to prevent hunting
        if (inDeadband) {
            kp *= DEADBAND_FACTOR;
            ki *= DEADBAND_FACTOR;
            kd *= DEADBAND_FACTOR;
        }

        // Integral term with conditional anti-windup
        // Only accumulate integral when outside deadband or when error is growing
        if (!inDeadband || Math.abs(error) > Math.abs(prevError)) {
            integral += error * dt;
        }
        integral = MathUtil.clamp(integral, -INTEGRAL_MAX, INTEGRAL_MAX);

        // Derivative term with filtering
        double rawDerivative = (error - prevError) / dt;
        rawDerivative = MathUtil.clamp(rawDerivative, -DERIVATIVE_MAX, DERIVATIVE_MAX);

        // Low-pass filter on derivative
        double derivative = prevDerivative * (1 - DERIVATIVE_SMOOTHING)
                + rawDerivative * DERIVATIVE_SMOOTHING;

        prevDerivative = derivative;
        prevError = error;

        // Compute PID output
        double output = kp * error + ki * integral + kd * derivative;

        // Add extra boost when far from setpoint for faster response
        if (Math.abs(error) > POWER_TOLERANCE * 5) {
            output *= 1.5;
        }

        return output;
    }

    /**
     * Computes adaptive gain factor based on operating conditions.
     * Higher power requires more careful control (lower gains).
     * Lower power allows more aggressive control (higher gains).
     */
    private double computeGainFactor(double currentPower, double targetPower) {
        // Base factor
        double factor = 1.0;

        // Reduce gains at high power (>5000 MW) for stability
        if (currentPower > 5000) {
            factor = 0.7;
        }
        // Increase gains at low power (<1000 MW) for faster response
        else if (currentPower < 1000) {
            factor = 1.5;
        }

        // Increase gains when far from target for faster convergence
        double errorRatio = Math.abs(targetPower - currentPower) / Math.max(targetPower, 100);
        if (errorRatio > 0.5) {
            factor *= 1.3;
        }

        return factor;
    }

    /**
     * Resets the internal state of the PID controller.
     * Call when target power changes significantly or after SCRAM.
     */
    public void reset() {
        integral = 0.0;
        prevError = 0.0;
        prevDerivative = 0.0;
    }

    /**
     * Returns the current integral value (for debugging/monitoring).
     */
    public double getIntegral() {
        return integral;
    }

    /**
     * Returns the current error (for debugging/monitoring).
     */
    public double getLastError() {
        return prevError;
    }
}
