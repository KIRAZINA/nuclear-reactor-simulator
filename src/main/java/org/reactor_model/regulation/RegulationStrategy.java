package org.reactor_model.regulation;

/**
 * Strategy interface for control algorithms (PID, fuzzy logic, etc.).
 */
public interface RegulationStrategy {
    double computeAdjustment(double currentPower, double targetPower, double dt);
}
