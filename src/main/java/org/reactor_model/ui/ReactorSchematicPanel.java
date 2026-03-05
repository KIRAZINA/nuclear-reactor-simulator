package org.reactor_model.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * Custom-drawn schematic diagram of the nuclear reactor.
 * Reads from a {@link ReactorStateSnapshot} and re-paints every 200 ms via Swing Timer.
 * 
 * Responsive features:
 *   - Scales all elements based on available panel size
 *   - Minimum size enforcement (350x250)
 *   - Dynamic font sizing
 *   - Adaptive parameter label positioning
 */
public class ReactorSchematicPanel extends JPanel {

    // Temperature thresholds matching ReactorCore
    private static final double TEMP_COOL   = 300.0;
    private static final double TEMP_WARN   = 620.0;
    private static final double TEMP_CRIT   = 680.0;
    private static final double TEMP_SCRAM  = 750.0;

    private static final Color BG_DARK      = new Color(12, 15, 25);
    private static final Color VESSEL_COLOR = new Color(60, 70, 100);
    private static final Color VESSEL_GLOW  = new Color(80, 100, 160);
    private static final Color ROD_COLOR    = new Color(40, 120, 80);
    private static final Color ROD_HOT_COL  = new Color(200, 80, 30);
    private static final Color COOLANT_COL  = new Color(30, 120, 200);
    private static final Color FUEL_NORMAL  = new Color(60, 200, 80);
    private static final Color FUEL_HOT     = new Color(255, 160, 0);

    private volatile ReactorStateSnapshot snapshot = null;

    // Animation tick counter (incremented by Swing Timer)
    private int animTick = 0;
    
    // Responsive sizing
    private static final int MIN_WIDTH = 350;
    private static final int MIN_HEIGHT = 250;
    private static final int PREFERRED_WIDTH = 520;
    private static final int PREFERRED_HEIGHT = 420;

    public ReactorSchematicPanel() {
        setOpaque(true);
        setBackground(BG_DARK);
        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        setPreferredSize(new Dimension(PREFERRED_WIDTH, PREFERRED_HEIGHT));
    }

    public void updateSnapshot(ReactorStateSnapshot snap) {
        this.snapshot = snap;
        animTick++;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        int W = getWidth();
        int H = getHeight();
        
        // Ensure minimum dimensions for drawing
        if (W < MIN_WIDTH || H < MIN_HEIGHT) {
            drawSizeWarning(g, W, H);
            return;
        }
        
        ReactorStateSnapshot snap = snapshot;
        if (snap == null) {
            drawWaiting(g, W, H);
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawReactorVessel(g2, W, H, snap);
        drawCoolantFlow(g2, W, H, snap);
        drawFuelRods(g2, W, H, snap);
        drawControlRods(g2, W, H, snap);
        drawStatusOverlay(g2, W, H, snap);
        drawParameterLabels(g2, W, H, snap);

        g2.dispose();
    }

    // ---- Drawing helpers ———————————————————————————————————
    
    private void drawSizeWarning(Graphics g, int W, int H) {
        g.setColor(new Color(100, 110, 140));
        g.setFont(new Font("Inter", Font.BOLD, 14));
        String msg = "Panel too small";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(msg, (W - fm.stringWidth(msg)) / 2, H / 2);
    }

    private void drawWaiting(Graphics g, int W, int H) {
        g.setColor(new Color(100, 110, 140));
        g.setFont(new Font("Inter", Font.BOLD, Math.max(14, Math.min(18, W / 25))));
        String msg = "Waiting for simulation...";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(msg, (W - fm.stringWidth(msg)) / 2, H / 2);
    }

    private void drawReactorVessel(Graphics2D g2, int W, int H, ReactorStateSnapshot snap) {
        // Calculate vessel dimensions based on panel size
        int vx = W / 6;
        int vy = H / 10;
        int vw = Math.min(W * 4 / 6, W - 100);
        int vh = Math.min(H * 7 / 10, H - 80);

        // Core glow (temperature-based radial gradient)
        Color coreColor = coreColor(snap.temperature);
        RadialGradientPaint glow = new RadialGradientPaint(
                new Point2D.Double(vx + vw / 2.0, vy + vh / 2.0),
                Math.max(vw, vh) * 0.6f,
                new float[]{0f, 0.5f, 1f},
                new Color[]{
                        withAlpha(coreColor, 200),
                        withAlpha(coreColor, 80),
                        withAlpha(coreColor, 0)
                }
        );
        g2.setPaint(glow);
        g2.fillRoundRect(vx - 20, vy - 20, vw + 40, vh + 40, 30, 30);

        // Vessel wall
        int strokeOuter = Math.max(2, vw / 80);
        int strokeInner = Math.max(1, vw / 120);
        
        g2.setStroke(new BasicStroke(strokeOuter));
        g2.setColor(VESSEL_GLOW);
        g2.drawRoundRect(vx, vy, vw, vh, 20, 20);
        g2.setColor(VESSEL_COLOR);
        g2.setStroke(new BasicStroke(strokeInner));
        g2.drawRoundRect(vx + 4, vy + 4, vw - 8, vh - 8, 16, 16);

        // Top cap
        int capHeight = Math.max(16, vh / 15);
        g2.setColor(VESSEL_COLOR);
        g2.fillRoundRect(vx + vw / 4, vy - capHeight + 4, vw / 2, capHeight + 4, 8, 8);
        g2.setColor(VESSEL_GLOW);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(vx + vw / 4, vy - capHeight + 4, vw / 2, capHeight + 4, 8, 8);

        // Bottom cap
        g2.setColor(VESSEL_COLOR);
        g2.fillRoundRect(vx + vw / 4, vy + vh - 4, vw / 2, capHeight + 4, 8, 8);
        g2.setColor(VESSEL_GLOW);
        g2.drawRoundRect(vx + vw / 4, vy + vh - 4, vw / 2, capHeight + 4, 8, 8);
    }

    private void drawCoolantFlow(Graphics2D g2, int W, int H, ReactorStateSnapshot snap) {
        int vx = W / 6;
        int vy = H / 10;
        int vw = Math.min(W * 4 / 6, W - 100);
        int vh = Math.min(H * 7 / 10, H - 80);

        float flow = (float) snap.coolantFlowRate;
        if (flow < 0.01f) return; // no flow – nothing to draw

        // Animate arrow offset
        float speed = 12f * flow;
        int offset  = (int) (animTick * speed) % 48;

        int pipeX1 = vx - 30;
        int pipeX2 = vx + vw + 30;
        int centerY = vy + vh / 2;

        Color ac = withAlpha(COOLANT_COL, (int) (160 * flow));
        g2.setColor(ac);
        int pipeStroke = Math.max(2, vw / 80);
        g2.setStroke(new BasicStroke(pipeStroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                0f, new float[]{10, 6}, offset));

        // Left pipe (coolant in)
        g2.drawLine(pipeX1, centerY, vx, centerY);

        // Right pipe (coolant out)
        g2.drawLine(vx + vw, centerY, pipeX2, centerY);

        // Arrow heads on right pipe
        int ax = vx + vw + 14;
        g2.setStroke(new BasicStroke(pipeStroke));
        drawArrow(g2, ax, centerY, true, ac);
    }

    private void drawFuelRods(Graphics2D g2, int W, int H, ReactorStateSnapshot snap) {
        int vx = W / 6;
        int vy = H / 10;
        int vw = Math.min(W * 4 / 6, W - 100);
        int vh = Math.min(H * 7 / 10, H - 80);

        int rodCount = 7;
        int spacing  = vw / (rodCount + 1);
        int rodW     = Math.max(6, vw / 35);
        int rodH     = (int) (vh * 0.55);
        int rodTop   = vy + (vh - rodH) / 2;

        double powerRatio = snap.power / 8000.0;
        float pulse = (float) (0.7 + 0.3 * Math.sin(animTick * 0.25 * Math.PI * powerRatio));

        Color fuelColor = interpolateColor(FUEL_NORMAL, FUEL_HOT, (float) powerRatio);
        Color fuelFinal = withAlpha(fuelColor, (int) (220 * pulse));

        for (int i = 1; i <= rodCount; i++) {
            int rx = vx + i * spacing - rodW / 2;

            // Fuel rod body
            GradientPaint gp = new GradientPaint(
                    rx, rodTop, withAlpha(new Color(60, 60, 80), 200),
                    rx + rodW, rodTop, fuelFinal);
            g2.setPaint(gp);
            g2.fillRoundRect(rx, rodTop, rodW, rodH, 4, 4);

            // Rod outline
            g2.setColor(new Color(80, 90, 120));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(rx, rodTop, rodW, rodH, 4, 4);
        }
    }

    private void drawControlRods(Graphics2D g2, int W, int H, ReactorStateSnapshot snap) {
        int vx = W / 6;
        int vy = H / 10;
        int vw = Math.min(W * 4 / 6, W - 100);
        int vh = Math.min(H * 7 / 10, H - 80);

        // Control rods (3 rods above fuel, descend into core based on rodPosition)
        // rodPosition 0.0 = fully inserted, 1.0 = fully withdrawn
        int cRodCount = 3;
        int[] offsets = {vw / 4, vw / 2, vw * 3 / 4};
        int rodW      = Math.max(8, vw / 30);
        int maxDepth  = (int) (vh * 0.65);
        int topSlot   = vy - 14;

        for (int xOff : offsets) {
            int rx = vx + xOff - rodW / 2;

            // Insertion depth: 0 = fully inserted (top of vessel), 1 = withdrawn
            double insertion = 1.0 - snap.controlRodPosition; // 0=withdrawn,1=inserted
            int rodLen = (int) (maxDepth * (0.05 + insertion * 0.95));

            // Slot at top
            g2.setColor(new Color(40, 50, 80));
            g2.fillRect(rx, topSlot, rodW, 18);
            g2.setColor(new Color(60, 70, 110));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRect(rx, topSlot, rodW, 18);

            // Rod body (darker = inserted more)
            Color rodColor = snap.temperature > TEMP_WARN ? ROD_HOT_COL : ROD_COLOR;
            GradientPaint gp = new GradientPaint(rx, vy, rodColor,
                    rx + rodW, vy, new Color(20, 80, 40));
            g2.setPaint(gp);
            g2.fillRoundRect(rx, vy, rodW, rodLen, 3, 3);
            g2.setColor(new Color(80, 140, 100));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(rx, vy, rodW, rodLen, 3, 3);
        }
    }

    private void drawStatusOverlay(Graphics2D g2, int W, int H, ReactorStateSnapshot snap) {
        String statusText;
        Color  statusColor;
        Color  bgColor;

        if (snap.shutdown) {
            statusText  = "⚠  SCRAM — EMERGENCY SHUTDOWN";
            statusColor = Color.WHITE;
            bgColor     = withAlpha(new Color(180, 20, 20), 210);
        } else if (snap.temperature > TEMP_CRIT) {
            statusText  = "CRITICAL TEMPERATURE";
            statusColor = Color.WHITE;
            bgColor     = withAlpha(new Color(200, 50, 0), 180);
        } else if (snap.temperature > TEMP_WARN || snap.reactivity > 0.012) {
            statusText  = "WARNING — Monitor Parameters";
            statusColor = new Color(255, 240, 0);
            bgColor     = withAlpha(new Color(100, 80, 0), 170);
        } else {
            statusText  = "NORMAL OPERATION";
            statusColor = new Color(0, 220, 130);
            bgColor     = withAlpha(new Color(0, 60, 40), 160);
        }

        // Responsive font size
        int fontSize = Math.max(11, Math.min(15, W / 35));
        Font f = new Font("Inter", Font.BOLD, fontSize);
        g2.setFont(f);
        FontMetrics fm = g2.getFontMetrics();
        int sw = fm.stringWidth(statusText);
        int bx = (W - sw - 24) / 2;
        int by = H - Math.max(36, H / 12);
        int bh = Math.max(24, fm.getHeight() + 8);

        g2.setColor(bgColor);
        g2.fillRoundRect(bx, by, sw + 24, bh, 12, 12);
        g2.setColor(statusColor);
        g2.drawString(statusText, bx + 12, by + bh / 2 + fm.getAscent() / 2 - 2);
    }

    private void drawParameterLabels(Graphics2D g2, int W, int H, ReactorStateSnapshot snap) {
        // Responsive font size and panel width
        int fontSize = Math.max(9, Math.min(11, W / 45));
        g2.setFont(new Font("Inter", Font.PLAIN, fontSize));
        g2.setColor(new Color(140, 160, 200));

        // Top-right mini readout with responsive width
        String[] lines = {
                String.format("Power:    %.0f / %.0f MW", snap.power, snap.targetPower),
                String.format("Temp:     %.1f °C",         snap.temperature),
                String.format("Coolant:  %.2f",             snap.coolantFlowRate),
                String.format("Rod pos:  %.2f",             snap.controlRodPosition),
                String.format("React:   %+.5f",             snap.reactivity),
        };
        
        FontMetrics fm = g2.getFontMetrics();
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, fm.stringWidth(line));
        }
        
        int panelWidth = maxWidth + 20;
        int panelHeight = lines.length * (fm.getHeight() + 2) + 10;
        int lx = W - panelWidth - 10;
        int ly = 16;
        
        // Background for labels
        g2.setColor(withAlpha(new Color(12, 15, 25), 200));
        g2.fillRoundRect(lx - 6, ly - 12, panelWidth, panelHeight, 8, 8);
        
        g2.setColor(new Color(140, 160, 200));
        int lineY = ly;
        for (String l : lines) {
            g2.drawString(l, lx, lineY);
            lineY += fm.getHeight() + 2;
        }
    }

    // ---- Color helpers —————————————————————————————————————

    private Color coreColor(double temp) {
        if (temp < TEMP_COOL) return new Color(0, 80, 200);
        if (temp < TEMP_WARN) return interpolateColor(
                new Color(0, 80, 200), new Color(255, 140, 0),
                (float) ((temp - TEMP_COOL) / (TEMP_WARN - TEMP_COOL)));
        if (temp < TEMP_CRIT) return interpolateColor(
                new Color(255, 140, 0), new Color(220, 40, 20),
                (float) ((temp - TEMP_WARN) / (TEMP_CRIT - TEMP_WARN)));
        return new Color(220, 20, 20);
    }

    private Color interpolateColor(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
                (int) (a.getRed()   + (b.getRed()   - a.getRed())   * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue()  + (b.getBlue()  - a.getBlue())  * t)
        );
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(),
                Math.max(0, Math.min(255, alpha)));
    }

    private void drawArrow(Graphics2D g2, int x, int y, boolean right, Color c) {
        int d = right ? 8 : -8;
        int[] xs = {x, x - d, x - d};
        int[] ys = {y, y - 5, y + 5};
        g2.setColor(c);
        g2.fillPolygon(xs, ys, 3);
    }
}
