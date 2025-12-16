package org.reactor_model.regulation;

import org.reactor_model.util.MathUtil;

/**
 * Basic PID controller used for regulating reactor power.
 */
public class SimplePIDStrategy implements RegulationStrategy {

    private static final double KP = 0.02;
    private static final double KI = 0.002;
    private static final double KD = 0.005;

    private static final double INTEGRAL_MAX = 10.0;
    private static final double DERIVATIVE_MAX = 500.0;

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
