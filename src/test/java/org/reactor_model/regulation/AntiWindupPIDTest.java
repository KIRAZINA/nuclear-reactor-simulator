package org.reactor_model.regulation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit coverage for the anti-windup PID's own limiters: output clamp,
 * slew-rate cap, integral clamping, and reset semantics.
 */
@DisplayName("AntiWindupPID Unit Tests")
class AntiWindupPIDTest {

    @Test
    @DisplayName("Output should clamp to the configured maximum")
    void outputClampsToMaximum() {
        AntiWindupPID pid = new AntiWindupPID(0.01, 0.001, 0.0, 100.0, 0.5);

        assertEquals(0.5, pid.compute(1000.0, 0.1), 1e-12);
        assertEquals(-0.5, pid.compute(-1000.0, 0.1), 1e-12);
    }

    @Test
    @DisplayName("Slew limit should cap per-call movement and be reported")
    void slewLimitCapsPerCallMovement() {
        AntiWindupPID pid = new AntiWindupPID(1.0, 0.0, 0.0, 100.0, 10.0);
        pid.setSlewRateLimit(0.1); // 0.1 units/s -> 0.01 per 0.1 s call

        assertEquals(0.1, pid.getSlewRateLimit(), 1e-12);
        assertEquals(0.01, pid.compute(1000.0, 0.1), 1e-12,
                "Huge demand must still move at most slew*dt per call");

        AntiWindupPID unlimited = new AntiWindupPID(1.0, 0.0, 0.0, 100.0, 10.0);
        assertEquals(10.0, unlimited.compute(1000.0, 0.1), 1e-12,
                "Non-positive slew limit must leave the output clamp in charge");
    }

    @Test
    @DisplayName("Integral term must stay clamped under sustained error")
    void integralStaysClamped() {
        AntiWindupPID pid = new AntiWindupPID(0.0, 0.001, 0.0, 5.0, 100.0);

        for (int i = 0; i < 1000; i++) {
            pid.compute(100.0, 0.1);
        }

        assertTrue(Math.abs(pid.getIntegral()) <= 5.0 + 1e-9,
                "Integral must respect integralMax, was: " + pid.getIntegral());
    }

    @Test
    @DisplayName("reset() should clear integral, error, and output memory")
    void resetClearsState() {
        AntiWindupPID pid = new AntiWindupPID(0.001, 0.001, 0.001, 100.0, 100.0);
        for (int i = 0; i < 50; i++) {
            pid.compute(500.0, 0.1);
        }
        assertNotEquals(0.0, pid.getIntegral(), "Precondition: integral must build up");

        pid.reset();

        assertEquals(0.0, pid.getIntegral(), 1e-12);
    }

    @Test
    @DisplayName("Filtered derivative must not amplify single-tick chatter")
    void derivativeIgnoresChatter() {
        // Pure-D controller: output comes only from the filtered derivative.
        AntiWindupPID pid = new AntiWindupPID(0.0, 0.0, 1.0, 100.0, 100.0);

        double first = pid.compute(10.0, 0.1);
        // Raw derivative would be 1.0 * 10/0.1 = 100; the 3 s lag admits ~3%.
        assertTrue(Math.abs(first) < 10.0,
                "Filtered derivative must blunt single-tick steps, was: " + first);
    }
}
