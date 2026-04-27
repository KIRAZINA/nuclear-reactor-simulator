package org.reactor_model.regulation;

/**
 * Anti-windup PID controller with back-calculation for nuclear reactor control.
 * Prevents integral windup when control rods hit limits.
 */
public class AntiWindupPID {

    private final double kp, ki, kd;
    private final double integralMax;
    private final double outputMax;

    // State
    private double integral = 0.0;
    private double prevError = 0.0;
    private double prevOutput = 0.0;

    // Anti-windup parameters
    private final double backCalcCoeff = 2.0; // Back-calculation coefficient

    public AntiWindupPID(double kp, double ki, double kd, double integralMax, double outputMax) {
        this.kp = kp;
        this.ki = ki;
        this.kd = kd;
        this.integralMax = integralMax;
        this.outputMax = outputMax;
    }

    public double compute(double error, double dt) {
        // Proportional term
        double pTerm = kp * error;

        // Integral term with anti-windup
        double iTerm = ki * integral;

        // Derivative term (with filtering)
        double dTerm = kd * (error - prevError) / dt;

        // Compute output
        double output = pTerm + iTerm + dTerm;

        // Clamp output
        double clampedOutput = Math.max(-outputMax, Math.min(outputMax, output));

        // Anti-windup: back-calculate if output was clamped
        if (clampedOutput != output) {
            double backCalc = (clampedOutput - output) * backCalcCoeff;
            integral += backCalc / ki; // Adjust integral to prevent windup
        }

        // Normal integral accumulation
        integral += error * dt;
        integral = Math.max(-integralMax, Math.min(integralMax, integral));

        prevError = error;
        prevOutput = clampedOutput;

        return clampedOutput;
    }

    public void reset() {
        integral = 0.0;
        prevError = 0.0;
        prevOutput = 0.0;
    }

    public double getIntegral() {
        return integral;
    }
}