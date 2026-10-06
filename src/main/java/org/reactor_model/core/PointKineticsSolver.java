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
    // Hard physical ceiling (MWt): bounds prompt-burst numerical overshoot so
    // downstream systems (decay-heat seed, ATWS power display) stay finite.
    private static final double MAX_POWER = 1e7;

    public PointKineticsSolver(double initialPower) {
        this.power = initialPower;
        for (int i = 0; i < 6; i++) {
            precursors[i] = (BETA[i] / (LAMBDA[i] * PROMPT_LIFETIME)) * power;
        }
    }

    public double advance(double reactivity, double dt) {
        double subDt = dt / SUBSTEPS;

        for (int step = 0; step < SUBSTEPS; step++) {
            // Delayed neutron source (explicit, using current precursors)
            double delayedSource = 0.0;
            for (int i = 0; i < 6; i++) {
                delayedSource += LAMBDA[i] * precursors[i];
            }

            // Implicit Euler for power: P_new = (P_old + dt * delayedSource) / (1 - dt * (rho - beta) / Lambda)
            double coeff = (reactivity - BETA_TOTAL) / PROMPT_LIFETIME;
            double denominator = 1.0 - coeff * subDt;
            // Safeguard denominator against zero or negative (extreme transients)
            if (denominator <= 0.0) {
                denominator = 1e-10;
            }
            double powerNew = (power + subDt * delayedSource) / denominator;

            // Rate limiter safeguard
            double maxChange = power * MAX_POWER_CHANGE;
            if (Math.abs(powerNew - power) > maxChange) {
                powerNew = power + Math.signum(powerNew - power) * maxChange;
            }
            powerNew = Math.max(powerNew, 1e-10);

            // Semi-implicit Euler for precursors (implicit in precursor decay)
            for (int i = 0; i < 6; i++) {
                double lambdaDt = LAMBDA[i] * subDt;
                double source = (BETA[i] / PROMPT_LIFETIME) * powerNew;
                precursors[i] = (precursors[i] + source * subDt) / (1.0 + lambdaDt);
            }

            power = powerNew;
        }

        power = Math.min(power, MAX_POWER);
        return power;
    }

    public double getPower() {
        return power;
    }

    public void setPower(double power) {
        double newPower = Math.max(power, 1e-10);
        // Scale precursors proportionally to maintain physical continuity
        double ratio = newPower / Math.max(this.power, 1e-10);
        for (int i = 0; i < 6; i++) {
            precursors[i] *= ratio;
        }
        this.power = newPower;
    }

    public void scram() {
        power *= 0.01;
        for (int i = 0; i < 6; i++) {
            precursors[i] *= 0.1;
        }
    }

    public double[] getPrecursors() {
        return precursors.clone();
    }

    /**
     * Restores the delayed-neutron precursor concentrations (e.g. from a saved state).
     * Values are clamped to be non-negative for physical validity.
     */
    public void setPrecursors(double[] values) {
        if (values == null || values.length != precursors.length) {
            throw new IllegalArgumentException(
                    "Precursor array must have length " + precursors.length);
        }
        for (int i = 0; i < precursors.length; i++) {
            precursors[i] = Math.max(values[i], 0.0);
        }
    }
}