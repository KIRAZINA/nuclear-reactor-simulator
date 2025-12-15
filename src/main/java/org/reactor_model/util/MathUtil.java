// src/main/java/org/reactor_model/util/MathUtil.java
package org.reactor_model.util;

public final class MathUtil {
    private MathUtil() {}

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
