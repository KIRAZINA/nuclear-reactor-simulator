// src/main/java/org/reactor_model/regulation/SimplePIDStrategy.java
package org.reactor_model.regulation;

import org.reactor_model.util.MathUtil;

public class SimplePIDStrategy implements RegulationStrategy {
    private double kp = 0.02;
    private double ki = 0.002;
    private double kd = 0.005;
    private double integral = 0.0;
    private double prevError = 0.0;
    private static final double INTEGRAL_MAX = 10.0;

    @Override
    public double computeAdjustment(double currentPower, double targetPower, double dt) {
        double error = targetPower - currentPower;
        integral += error * dt;
        integral = MathUtil.clamp(integral, -INTEGRAL_MAX, INTEGRAL_MAX);

        double derivative = (error - prevError) / dt;
        prevError = error;

        return kp * error + ki * integral + kd * derivative;
    }
}
