package org.reactor_model.core;

/**
 * Point kinetics model with delayed neutrons for realistic reactor dynamics.
 * Implements the standard 6-group delayed neutron model for PWR-like behavior.
 */
public class PointKineticsSolver {

    private static final double[] BETA = {0.000215, 0.001424, 0.001274, 0.002568, 0.000748, 0.000273};
    private static final double BETA_TOTAL = 0.006502;
    private static final double[] LAMBDA = {0.0124, 0.0305, 0.1110, 0.3011, 1.1360, 3.0140};
    private static final double PROMPT_LIFETIME = 2e-5;

    private double power;
    private final double[] precursors = new double[6];
    private static final int SUBSTEPS = 10;
    private static final double MAX_POWER_CHANGE = 10.0;

    public PointKineticsSolver(double initialPower) {
        this.power = initialPower;
        for (int i = 0; i < 6; i++) {
            precursors[i] = (BETA[i] / (LAMBDA[i] * PROMPT_LIFETIME)) * power;
        }
    }

    public double advance(double reactivity, double dt) {
        double subDt = dt / SUBSTEPS;

        for (int step = 0; step < SUBSTEPS; step++) {
            double rho = reactivity;
            double delayedSource = 0.0;
            for (int i = 0; i < 6; i++) {
                delayedSource += LAMBDA[i] * precursors[i];
            }

            double powerDerivative = ((rho - BETA_TOTAL) / PROMPT_LIFETIME) * power + delayedSource;

            double[] precursorDerivatives = new double[6];
            for (int i = 0; i < 6; i++) {
                precursorDerivatives[i] = (BETA[i] / PROMPT_LIFETIME) * power - LAMBDA[i] * precursors[i];
            }

            double powerNew = power + powerDerivative * subDt;
            double maxChange = power * MAX_POWER_CHANGE;
            if (Math.abs(powerNew - power) > maxChange) {
                powerNew = power + Math.signum(powerNew - power) * maxChange;
            }

            for (int i = 0; i < 6; i++) {
                precursors[i] += precursorDerivatives[i] * subDt;
            }

            power = Math.max(powerNew, 1e-10);
        }

        return power;
    }

    public double getPower() {
        return power;
    }

    public void setPower(double power) {
        this.power = Math.max(power, 1e-10);
        for (int i = 0; i < 6; i++) {
            precursors[i] = (BETA[i] / (LAMBDA[i] * PROMPT_LIFETIME)) * this.power;
        }
    }

    public void scram() {
        power *= 0.01;
        for (int i = 0; i < 6; i++) {
            precursors[i] *= 0.1;
        }
    }
}