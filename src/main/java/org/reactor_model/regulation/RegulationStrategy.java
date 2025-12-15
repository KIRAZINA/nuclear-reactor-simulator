package org.reactor_model.regulation;

public interface RegulationStrategy {
    double computeAdjustment(double currentPower, double targetPower, double dt);
}
