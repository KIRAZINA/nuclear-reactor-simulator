package org.reactor_model.core;

/**
 * Simplified point-kinetics Xenon-135 / Iodine-135 fission-product model.
 *
 * <p>Xenon-135 is a voracious neutron absorber produced directly from fission
 * and from Iodine-135 decay. After shutdown, burnout stops while iodine keeps
 * decaying into xenon, so poisoning PEAKS hours after shutdown and can hold
 * the reactor subcritical ("xenon dead time") until it decays away.
 *
 * <p>Numerics: explicit Euler. Keep {@code (LAMBDA * dt) << 1} (dt of seconds
 * to tens of minutes); day-long steps go unstable.
 */
public class XenonModel {

    // Decay constants (1/s)
    public static final double LAMBDA_I = 2.87e-5;   // I-135, ~6.7 h half-life
    public static final double LAMBDA_XE = 2.09e-5;  // Xe-135, ~9.2 h half-life

    // Fission yields (per fission neutron scale)
    public static final double GAMMA_I = 0.063;
    public static final double GAMMA_XE = 0.003;

    // Microscopic Xe-135 absorption cross section (cm^2)
    public static final double SIGMA_XE = 2.65e-18;

    /** Neutron flux per MW of thermal power (n/cm^2/s per MW). */
    public static final double FLUX_PER_MW = 1.0e10;

    /**
     * Reactivity per unit xenon concentration ($). Tuned so full-power
     * (~3000 MW) equilibrium sits at about -$2.50; the post-shutdown peak
     * runs several times higher, exactly as in a real PWR.
     */
    public static final double XENON_WORTH = 1.2677e-16;

    private double iodineConc = 0.0;
    private double xenonConc = 0.0;

    /**
     * Advances I/Xe inventory over {@code dt} seconds at the given power.
     * Call every tick (including shutdown ticks, on decay-heat power).
     */
    public void update(double powerMW, double dt) {
        double flux = Math.max(powerMW, 0.0) * FLUX_PER_MW;

        double dIodine = GAMMA_I * flux - LAMBDA_I * iodineConc;
        double dXenon = GAMMA_XE * flux + LAMBDA_I * iodineConc
                - LAMBDA_XE * xenonConc - SIGMA_XE * flux * xenonConc;

        iodineConc = Math.max(iodineConc + dIodine * dt, 0.0);
        xenonConc = Math.max(xenonConc + dXenon * dt, 0.0);
    }

    /** Negative reactivity from the current xenon inventory, in dollars. */
    public double getXenonReactivity() {
        return -XENON_WORTH * xenonConc;
    }

    public double getIodineConc() { return iodineConc; }

    public void setIodineConc(double iodineConc) {
        this.iodineConc = Math.max(iodineConc, 0.0);
    }

    public double getXenonConc() { return xenonConc; }

    public void setXenonConc(double xenonConc) {
        this.xenonConc = Math.max(xenonConc, 0.0);
    }

    /** Clears all fission-product inventory (fresh-fuel condition). */
    public void reset() {
        iodineConc = 0.0;
        xenonConc = 0.0;
    }
}
