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
    private double derivativeFiltered = 0.0;

    // First-order lag on the derivative (sim-seconds). Raw per-tick power
    // chatter would otherwise be amplified 1/dt into output-sized noise that
    // fights the P-term and stalls settling; genuine trends still pass within
    // a few seconds, preserving transient braking.
    private static final double DERIVATIVE_TIME_CONSTANT = 3.0;

    // Slew-rate limit (rod fraction per second, aligned with the regulator's
    // ROD_SPEED_LIMIT). Guards against rod jerks on large target steps.
    // Non-positive disables slew limiting (legacy behavior).
    private double slewRateLimit = -1.0;

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

        // Derivative term with first-order filtering (see field comment).
        double dTerm;
        if (dt > 0.0) {
            double dRaw = (error - prevError) / dt;
            double alpha = dt / (DERIVATIVE_TIME_CONSTANT + dt);
            derivativeFiltered += alpha * (dRaw - derivativeFiltered);
            dTerm = kd * derivativeFiltered;
        } else {
            dTerm = 0.0;
        }

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

        // Slew-rate limit: cap the absolute per-call output so rods glide instead
        // of jerking when a large error demands a big move. This is a cap on
        // movement per call (slewRateLimit * dt), NOT a ramp of the output.
        double slewLimited = clampedOutput;
        if (slewRateLimit > 0.0 && dt > 0.0) {
            double maxStep = slewRateLimit * dt;
            slewLimited = Math.max(-maxStep, Math.min(maxStep, clampedOutput));
        }

        prevError = error;
        prevOutput = slewLimited;

        return slewLimited;
    }

    /**
     * Sets the output slew-rate limit in output units per second.
     * Non-positive disables slew limiting.
     */
    public void setSlewRateLimit(double slewRateLimit) {
        this.slewRateLimit = slewRateLimit;
    }

    public double getSlewRateLimit() {
        return slewRateLimit;
    }

    public void reset() {
        integral = 0.0;
        prevError = 0.0;
        prevOutput = 0.0;
        derivativeFiltered = 0.0;
    }

    /**
     * Scales the integral term proportionally to avoid a discontinuous
     * "mechanical kick" when the target power changes significantly.
     */
    public void scaleIntegral(double ratio) {
        this.integral *= ratio;
    }

    public double getIntegral() {
        return integral;
    }
}