package org.reactor_model.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MathUtil Unit Tests")
class MathUtilTest {

    @Test
    @DisplayName("Clamp should return value when within range")
    void testClampWithinRange() {
        assertEquals(5.0, MathUtil.clamp(5.0, 0.0, 10.0));
        assertEquals(0.0, MathUtil.clamp(0.0, 0.0, 10.0));
        assertEquals(10.0, MathUtil.clamp(10.0, 0.0, 10.0));
    }

    @Test
    @DisplayName("Clamp should return min when below range")
    void testClampBelowMin() {
        assertEquals(0.0, MathUtil.clamp(-5.0, 0.0, 10.0));
        assertEquals(100.0, MathUtil.clamp(50.0, 100.0, 200.0));
    }

    @Test
    @DisplayName("Clamp should return max when above range")
    void testClampAboveMax() {
        assertEquals(10.0, MathUtil.clamp(15.0, 0.0, 10.0));
        assertEquals(200.0, MathUtil.clamp(250.0, 100.0, 200.0));
    }

    @Test
    @DisplayName("Clamp should handle negative ranges")
    void testClampNegativeRange() {
        assertEquals(-5.0, MathUtil.clamp(-5.0, -10.0, 0.0));
        assertEquals(-10.0, MathUtil.clamp(-15.0, -10.0, 0.0));
        assertEquals(0.0, MathUtil.clamp(5.0, -10.0, 0.0));
    }

    @Test
    @DisplayName("Clamp should handle zero range")
    void testClampZeroRange() {
        assertEquals(5.0, MathUtil.clamp(5.0, 5.0, 5.0));
        assertEquals(0.0, MathUtil.clamp(100.0, 0.0, 0.0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1000.0, -1.0, 0.0, 1.0, 1000.0})
    @DisplayName("Clamp should handle various double values")
    void testClampVarious(double value) {
        double result = MathUtil.clamp(value, -100.0, 100.0);
        
        assertTrue(result >= -100.0);
        assertTrue(result <= 100.0);
    }

    @Test
    @DisplayName("Clamp should work with floating point precision")
    void testClampFloatingPoint() {
        double result = MathUtil.clamp(0.123456789, 0.0, 1.0);
        assertEquals(0.123456789, result, 0.000000001);
    }
}
