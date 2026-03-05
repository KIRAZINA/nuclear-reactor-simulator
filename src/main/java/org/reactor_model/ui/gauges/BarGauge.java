package org.reactor_model.ui.gauges;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Vertical bar gauge for showing a value within a fixed range.
 * 
 * Responsive features:
 *   - Adapts to available width and height
 *   - Minimum size enforcement for readability
 *   - Dynamic font sizing
 */
public class BarGauge extends JPanel {

    private final String label;
    private final String unit;
    private final double minValue;
    private final double maxValue;
    private double warnThreshold  = Double.MAX_VALUE;
    private double dangerThreshold = Double.MAX_VALUE;

    private volatile double value;

    private static final Color COLOR_BG      = new Color(18, 20, 30);
    private static final Color COLOR_TRACK   = new Color(45, 50, 70);
    private static final Color COLOR_NORM    = new Color(0, 200, 130);
    private static final Color COLOR_WARN    = new Color(255, 190, 0);
    private static final Color COLOR_DANGER  = new Color(220, 50, 50);
    private static final Color COLOR_TEXT    = new Color(200, 210, 230);
    private static final Color COLOR_VALUE   = new Color(255, 255, 255);
    
    // Responsive sizing
    private static final int MIN_WIDTH = 50;
    private static final int MIN_HEIGHT = 100;
    private static final int PREFERRED_WIDTH = 80;
    private static final int PREFERRED_HEIGHT = 140;

    public BarGauge(String label, String unit, double minValue, double maxValue) {
        this.label    = label;
        this.unit     = unit;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.value    = minValue;
        setOpaque(false);
        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        setPreferredSize(new Dimension(PREFERRED_WIDTH, PREFERRED_HEIGHT));
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
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        
        // Calculate responsive font sizes
        int fontSizeLabel = Math.max(9, Math.min(12, w / 7));
        int fontSizeValue = Math.max(8, Math.min(11, w / 8));
        
        Font labelFont = new Font("Inter", Font.PLAIN, fontSizeLabel);
        Font valueFont = new Font("Inter", Font.BOLD, fontSizeValue);
        g2.setFont(labelFont);
        FontMetrics fm = g2.getFontMetrics();

        // Calculate padding based on size
        int topPad = Math.max(fm.getHeight() + 2, h / 8);
        int botPad = Math.max(fm.getHeight() * 2 + 4, h / 5);
        
        // Bar dimensions
        int barW = Math.max(w / 3, 12);
        int barX = (w - barW) / 2;
        int barTop = topPad;
        int barH = Math.max(h - topPad - botPad, h / 2);

        // Track
        g2.setColor(COLOR_TRACK);
        int cornerRadius = Math.max(4, barW / 4);
        g2.fill(new RoundRectangle2D.Double(barX, barTop, barW, barH, cornerRadius, cornerRadius));

        // Fill
        double ratio = (value - minValue) / (maxValue - minValue);
        int fillH = (int) (barH * ratio);
        int fillY = barTop + barH - fillH;
        g2.setColor(getFillColor());
        g2.fill(new RoundRectangle2D.Double(barX, fillY, barW, fillH, cornerRadius, cornerRadius));

        // Label (top) - only if there's enough space
        if (h > MIN_HEIGHT) {
            g2.setFont(labelFont);
            g2.setColor(COLOR_TEXT);
            int lx = (w - fm.stringWidth(label)) / 2;
            g2.drawString(label, lx, fm.getAscent());
        }

        // Value (bottom)
        String valStr = formatValue(value);
        g2.setFont(valueFont);
        g2.setColor(COLOR_VALUE);
        FontMetrics fmV = g2.getFontMetrics();
        int valueY = h - botPad + fmV.getAscent() + 2;
        g2.drawString(valStr, (w - fmV.stringWidth(valStr)) / 2, valueY);
        
        // Unit (below value) - only if there's enough space
        if (h > MIN_HEIGHT + 20) {
            g2.setColor(COLOR_TEXT);
            g2.drawString(unit, (w - fmV.stringWidth(unit)) / 2, valueY + fmV.getAscent() + 2);
        }

        g2.dispose();
    }

    private Color getFillColor() {
        if (value >= dangerThreshold) return COLOR_DANGER;
        if (value >= warnThreshold)   return COLOR_WARN;
        return COLOR_NORM;
    }

    private String formatValue(double v) {
        if (Math.abs(v) >= 1000) return String.format("%.0f", v);
        if (Math.abs(v) >= 10)   return String.format("%.1f", v);
        return String.format("%.3f", v);
    }
}
