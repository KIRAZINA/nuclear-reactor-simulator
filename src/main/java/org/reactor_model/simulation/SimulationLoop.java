// src/main/java/org/reactor_model/simulation/SimulationLoop.java
package org.reactor_model.simulation;

import org.reactor_model.core.ReactorCore;
import org.reactor_model.cooling.CoolingSystem;
import org.reactor_model.disturbance.PowerDemandSimulator;
import org.reactor_model.regulation.AutoRegulator;

public class SimulationLoop {
    private final ReactorCore core;
    private final AutoRegulator regulator;
    private final PowerDemandSimulator demandSimulator;
    private final CoolingSystem coolingSystem;
    private volatile boolean running = false;
    private static final double DT = 0.1;
    private int tick = 0;
    private double previousPower = 0.01;

    public SimulationLoop(ReactorCore core, AutoRegulator regulator, PowerDemandSimulator demandSimulator, CoolingSystem coolingSystem) {
        this.core = core;
        this.regulator = regulator;
        this.demandSimulator = demandSimulator;
        this.coolingSystem = coolingSystem;
    }

    public void start() {
        running = true;
        new Thread(() -> {
            while (running) {
                demandSimulator.update();
                coolingSystem.update(regulator.getTargetPower());
                core.update(DT, regulator.getTargetPower(), previousPower);
                previousPower = core.getPower();

                if (core.getOverheatTicks() > ReactorCore.OVERHEAT_MAX_TICKS) {
                    double newTarget = regulator.getTargetPower() * 0.8;
                    regulator.setTargetPower(newTarget);
                    core.resetOverheatTicks();
                }

                tick++;
                if (tick % 15 == 0) {
                    core.logCurrentState();
                }

                try {
                    Thread.sleep((long) (DT * 1000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }

    public void stop() {
        running = false;
    }
}
