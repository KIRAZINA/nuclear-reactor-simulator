package org.reactor_model.regulation;

import org.reactor_model.util.MathUtil;

/**
 * Basic PID controller used for regulating reactor power.
 */
public class SimplePIDStrategy implements RegulationStrategy {

    private static final double KP = 0.0005;  // Increased from 0.00005
    private static final double KI = 0.00002;  // Increased from 0.000005
    private static final double KD = 0.0005;   // Increased from 0.0001

    private static final double INTEGRAL_MAX = 2000.0;
    private static final double DERIVATIVE_MAX = 5000.0;

    private static final double DERIVATIVE_SMOOTHING = 0.2;

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
}
