package org.reactor_model.ui.gauges;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * Analog circular gauge rendered entirely with Java2D.
 * Supports optional warning and danger zones drawn as arc sectors.
 * 
 * Responsive features:
 *   - Scales dynamically based on available space
 *   - Minimum size enforcement (80x80)
 *   - Proper aspect ratio maintenance
 */
public class CircularGauge extends JPanel {

    private final String label;
    private final String unit;
    private final double minValue;
    private final double maxValue;
    private double warnThreshold = Double.MAX_VALUE;
    private double dangerThreshold = Double.MAX_VALUE;

    private volatile double value;

    // Arc spans from 225° (bottom-left) to 315° (bottom-right), i.e. 270° sweep CCW
    private static final float START_ANGLE = 225f;   // degrees (AWT: CCW from 3 o'clock)
    private static final float SWEEP_ANGLE = -270f;  // negative = clockwise

    private static final Color COLOR_BG        = new Color(18, 20, 30);
    private static final Color COLOR_TRACK     = new Color(45, 50, 70);
    private static final Color COLOR_FILL_NORM = new Color(0, 200, 130);
    private static final Color COLOR_FILL_WARN = new Color(255, 190, 0);
    private static final Color COLOR_FILL_CRIT = new Color(220, 50, 50);
    private static final Color COLOR_NEEDLE    = new Color(240, 240, 255);
    private static final Color COLOR_TEXT      = new Color(200, 210, 230);
    private static final Color COLOR_VALUE     = new Color(255, 255, 255);
    
    // Responsive sizing
    private static final int MIN_SIZE = 80;
    private static final int PREFERRED_SIZE = 140;
    private static final int MAX_SIZE = 200;

    public CircularGauge(String label, String unit, double minValue, double maxValue) {
        this.label    = label;
        this.unit     = unit;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.value    = minValue;
        setOpaque(false);
        setMinimumSize(new Dimension(MIN_SIZE, MIN_SIZE));
        setPreferredSize(new Dimension(PREFERRED_SIZE, PREFERRED_SIZE));
        setMaximumSize(new Dimension(MAX_SIZE, MAX_SIZE));
    }

    public void setWarningThresholds(double warn, double danger) {
        this.warnThreshold   = warn;
        this.dangerThreshold = danger;
    }

    public void setValue(double v) {
        this.value = Math.min(maxValue, Math.max(minValue, v));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        // Calculate responsive size based on available space
        int availableWidth = getWidth();
        int availableHeight = getHeight();
        int size = Math.min(availableWidth, availableHeight) - 8;
        
        // Enforce minimum size for readability
        size = Math.max(size, MIN_SIZE - 8);
        
        // Center the gauge
        int x = (availableWidth - size) / 2;
        int y = (availableHeight - size) / 2;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Background circle
        g2.setColor(COLOR_BG);
        g2.fillOval(x, y, size, size);

        // Track arc
        int arcPad = Math.max(size / 8, 8);
        int arcSize = size - 2 * arcPad;
        int ax = x + arcPad, ay = y + arcPad;

        g2.setColor(COLOR_TRACK);
        g2.setStroke(new BasicStroke(Math.max(size / 12f, 4f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Arc2D.Double(ax, ay, arcSize, arcSize, START_ANGLE, SWEEP_ANGLE, Arc2D.OPEN));

        // Filled arc (value)
        double ratio = (value - minValue) / (maxValue - minValue);
        float sweep = (float) (SWEEP_ANGLE * ratio);
        Color fillColor = getFillColor();
        g2.setColor(fillColor);
        g2.draw(new Arc2D.Double(ax, ay, arcSize, arcSize, START_ANGLE, sweep, Arc2D.OPEN));

        // Needle
        double needleAngleRad = Math.toRadians(START_ANGLE + sweep);
        double cx = ax + arcSize / 2.0;
        double cy = ay + arcSize / 2.0;
        double r  = arcSize / 2.0;
        double nx = cx + r * Math.cos(needleAngleRad);
        double ny = cy - r * Math.sin(needleAngleRad);
        g2.setStroke(new BasicStroke(Math.max(2f, size / 40f)));
        g2.setColor(COLOR_NEEDLE);
        g2.draw(new Line2D.Double(cx, cy, nx, ny));
        int centerDotSize = Math.max(4, size / 20);
        g2.fillOval((int) cx - centerDotSize / 2, (int) cy - centerDotSize / 2, centerDotSize, centerDotSize);

        // Labels - scale font based on size
        int fontSizeLabel = Math.max(9, size / 14);
        int fontSizeValue = Math.max(11, size / 11);
        Font labelFont = new Font("Inter", Font.PLAIN, fontSizeLabel);
        Font valueFont = new Font("Inter", Font.BOLD,  fontSizeValue);

        int cy2 = y + size;

        // Value
        String valStr = formatValue(value) + " " + unit;
        g2.setFont(valueFont);
        g2.setColor(COLOR_VALUE);
        FontMetrics fmV = g2.getFontMetrics();
        g2.drawString(valStr, x + (size - fmV.stringWidth(valStr)) / 2,
                y + size / 2 + fmV.getAscent() / 2);

        // Label
        g2.setFont(labelFont);
        g2.setColor(COLOR_TEXT);
        FontMetrics fmL = g2.getFontMetrics();
        g2.drawString(label, x + (size - fmL.stringWidth(label)) / 2, cy2 - 4);

        g2.dispose();
    }

    private Color getFillColor() {
        if (value >= dangerThreshold) return COLOR_FILL_CRIT;
        if (value >= warnThreshold)   return COLOR_FILL_WARN;
        return COLOR_FILL_NORM;
    }

    private String formatValue(double v) {
        if (Math.abs(v) >= 1000) return String.format("%.0f", v);
        if (Math.abs(v) >= 10)   return String.format("%.1f", v);
        return String.format("%.3f", v);
    }
}
