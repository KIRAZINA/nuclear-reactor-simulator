package org.reactor_model.regulation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.reactor_model.core.ReactorCore;
import org.reactor_model.logger.ReactorLogger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AutoRegulator Transition Tests")
class AutoRegulatorTransitionTest {

    private AutoRegulator regulator;
    private ReactorCore core;

    @Mock
    private ReactorLogger mockLogger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        core = new ReactorCore(mockLogger);
        regulator = new AutoRegulator(core, mockLogger);
    }

    @Test
    @DisplayName("Large target change should scale integral instead of hard reset")
    void testLargeTargetChangeScalesIntegralSmoothly() {
        // Accumulate some integral term by running with an error
        regulator.setEnabled(true);
        core.setControlRodPosition(0.5);

        // Run several ticks with target higher than power to build integral
        regulator.setTargetPower(500.0);
        for (int i = 0; i < 20; i++) {
            core.update(0.1);
            // regulator reacts via event bus inside core.update
        }

        // Now make a large target change (>100 MW delta)
        regulator.setTargetPower(1200.0);

        // The regulator should not produce a discontinuous rod "kick" -
        // rod position change from this single regulation call should be bounded
        double oldPos = core.getControlRodPosition();
        core.eventBus.publish();
        double newPos = core.getControlRodPosition();
        double rodDelta = Math.abs(newPos - oldPos);

        // Rod change should be within reasonable bound (not a full-range jump)
        assertTrue(rodDelta < 0.5, "Rod position change should be smooth, not a full-range jump: " + rodDelta);
    }

    @Test
    @DisplayName("Disabled regulator should return early without PID computation")
    void testDisabledRegulatorReturnsEarly() {
        regulator.setEnabled(false);

        // This should not throw even if core state is unusual
        assertDoesNotThrow(() -> core.eventBus.publish(),
                "Disabled regulator should not cause issues on event bus publish");
    }

    @Test
    @DisplayName("Multiple large target changes should remain stable")
    void testMultipleLargeTargetChanges() {
        double[] targets = {200.0, 800.0, 1500.0, 500.0, 2000.0, 300.0};

        for (double target : targets) {
            regulator.setTargetPower(target);
            for (int i = 0; i < 5; i++) {
                core.update(0.1);
            }
            double pos = core.getControlRodPosition();
            assertTrue(pos >= 0.0 && pos <= 1.0,
                    "Rod position should stay in [0,1] after target change to " + target);
        }
    }
}
