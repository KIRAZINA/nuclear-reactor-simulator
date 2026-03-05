package org.reactor_model.regulation;

import org.reactor_model.util.MathUtil;

/**
 * Basic PID controller used for regulating reactor power.
 * 
 * Tuned for stable power tracking with minimal overshoot:
 * - Kp: Proportional response to power error
 * - Ki: Integral term eliminates steady-state error (with anti-windup)
 * - Kd: Derivative term dampens oscillations
 */
public class SimplePIDStrategy implements RegulationStrategy {

    // Tuned PID coefficients for reactor power control
    private static final double KP = 0.0001;   // Proportional gain
    private static final double KI = 0.00001;  // Integral gain (low to prevent windup)
    private static final double KD = 0.00005;  // Derivative gain (smoothing)

    private static final double INTEGRAL_MAX = 10000.0;  // Anti-windup limit
    private static final double DERIVATIVE_MAX = 1000.0; // Prevent derivative spikes

    private static final double DERIVATIVE_SMOOTHING = 0.3; // Higher = more smoothing

    private double integral = 0.0;
    private double prevError = 0.0;
    private double prevDerivative = 0.0;

    @Override
    public double computeAdjustment(double currentPower, double targetPower, double dt) {
        double error = targetPower - currentPower;

        // Integral term with anti-windup
        integral += error * dt;
        integral = MathUtil.clamp(integral, -INTEGRAL_MAX, INTEGRAL_MAX);

        // Derivative term with smoothing
        double rawDerivative = (error - prevError) / dt;
        rawDerivative = MathUtil.clamp(rawDerivative, -DERIVATIVE_MAX, DERIVATIVE_MAX);

        double derivative = prevDerivative * (1 - DERIVATIVE_SMOOTHING)
                + rawDerivative * DERIVATIVE_SMOOTHING;

        prevDerivative = derivative;
        prevError = error;

        return KP * error + KI * integral + KD * derivative;
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
