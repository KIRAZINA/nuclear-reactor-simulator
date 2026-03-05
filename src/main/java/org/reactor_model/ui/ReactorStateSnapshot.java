package org.reactor_model.ui;

/**
 * Immutable snapshot of reactor state.
 * Safe to pass between the simulation thread and the Swing EDT.
 */
public final class ReactorStateSnapshot {

    public final double power;
    public final double temperature;
    public final double coolantFlowRate;
    public final double controlRodPosition;
    public final double reactivity;
    public final double targetPower;
    public final int overheatTicks;
    public final boolean shutdown;
    public final boolean autoRegulatorEnabled;

    public ReactorStateSnapshot(
            double power,
            double temperature,
            double coolantFlowRate,
            double controlRodPosition,
            double reactivity,
            double targetPower,
            int overheatTicks,
            boolean shutdown,
            boolean autoRegulatorEnabled) {

        this.power               = power;
        this.temperature         = temperature;
        this.coolantFlowRate     = coolantFlowRate;
        this.controlRodPosition  = controlRodPosition;
        this.reactivity          = reactivity;
        this.targetPower         = targetPower;
        this.overheatTicks       = overheatTicks;
        this.shutdown            = shutdown;
        this.autoRegulatorEnabled = autoRegulatorEnabled;
    }
}
