package org.reactor_model.ui;

import org.reactor_model.ui.gauges.BarGauge;
import org.reactor_model.ui.gauges.CircularGauge;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Main application window for the Reactor Dashboard.
 * Auto-starts the simulation on launch with the auto-regulator enabled.
 *
 * Layout:
 *   ┌──────────────────────────────────────────────┐
 *   │  TITLE BAR                                   │
 *   ├────────────────────┬─────────────────────────┤
 *   │  ReactorSchematic  │  GaugePanel + Controls  │
 *   │  Panel (CENTER)    │  (EAST)                 │
 *   ├────────────────────┴─────────────────────────┤
 *   │  EventLogPanel  (SOUTH)                      │
 *   └──────────────────────────────────────────────┘
 * 
 * Responsive Design Features:
 *   - Horizontal scrolling when content exceeds viewport
 *   - Adaptive right panel width (min 280px, max 380px)
 *   - Minimum window size enforcement (900x600)
 *   - Component scaling based on available space
 */
public class ReactorDashboard extends JFrame {

    private static final Color BG_FRAME  = new Color(10, 12, 22);
    private static final int   REFRESH_MS = 200;
    
    // Responsive sizing constants
    private static final int MIN_WINDOW_WIDTH = 900;
    private static final int MIN_WINDOW_HEIGHT = 700;
    private static final int RIGHT_PANEL_MIN_WIDTH = 260;
    private static final int RIGHT_PANEL_MAX_WIDTH = 380;
    private static final int RIGHT_PANEL_PREFERRED_WIDTH = 320;
    private static final int SCHEMATIC_MIN_WIDTH = 400;
    private static final int EVENT_LOG_MIN_HEIGHT = 120;
    private static final int EVENT_LOG_MAX_HEIGHT = 250;

    private final ReactorUIAdapter      adapter;
    private final ReactorSchematicPanel schematic;
    private final ControlPanel          controls;
    private final EventLogPanel         eventLog;
    
    // Split pane + scroll pane for responsive behavior
    private JSplitPane contentSplitPane;
    private JScrollPane mainScrollPane;
    private JPanel rightPanel;

    // Circular gauges
    private final CircularGauge powerGauge;
    private final CircularGauge tempGauge;
    private final CircularGauge reactivityGauge;
    private final CircularGauge coolantGauge;

    // Bar gauges
    private final BarGauge targetPowerBar;
    private final BarGauge rodPositionBar;

    public ReactorDashboard(ReactorUIAdapter adapter, EventLogPanel eventLog) {
        super("Nuclear Reactor Simulator — Dashboard");
        this.adapter  = adapter;
        this.eventLog = eventLog;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBackground(BG_FRAME);
        getContentPane().setBackground(BG_FRAME);

        // Circular gauges
        powerGauge      = new CircularGauge("POWER",       "MW",    0,    8000);
        tempGauge       = new CircularGauge("TEMPERATURE", "°C",    0,     750);
        reactivityGauge = new CircularGauge("REACTIVITY",  "",    -0.02,  0.02);
        coolantGauge    = new CircularGauge("COOLANT",     "flow",  0,     1.0);

        powerGauge     .setWarningThresholds(6000, 7500);
        tempGauge      .setWarningThresholds(620,  680);
        reactivityGauge.setWarningThresholds(0.010, 0.014);

        // Bar gauges
        targetPowerBar = new BarGauge("Target", "MW",  0, 8000);
        rodPositionBar = new BarGauge("Rod",    "0-1", 0, 1.0);

        schematic = new ReactorSchematicPanel();
        controls  = new ControlPanel(adapter);

        buildLayout();
        setupResponsiveBehavior();
        startRefreshTimer();

        pack();
        setMinimumSize(new Dimension(MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT));
        setPreferredSize(new Dimension(980, 760));
        setLocationRelativeTo(null);
    }

    // ---- Layout ————————————————————————————————————————

    private void buildLayout() {
        // Main container with BorderLayout
        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBackground(BG_FRAME);
        root.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Title bar
        root.add(buildTitleBar(), BorderLayout.NORTH);

        // Content panel with schematic and right panel
        JPanel contentPanel = new JPanel(new BorderLayout(8, 0));
        contentPanel.setBackground(BG_FRAME);
        
        // Schematic panel with minimum size constraints
        schematic.setBorder(BorderFactory.createLineBorder(new Color(40, 50, 80)));
        schematic.setMinimumSize(new Dimension(SCHEMATIC_MIN_WIDTH, 300));

        // Right panel with gauges and controls
        rightPanel = buildRightPanel();

        // Wrap the right panel in its own scroll pane so its controls are reachable on short windows
        JScrollPane rightPanelScroll = new JScrollPane(rightPanel);
        rightPanelScroll.setBorder(null);
        rightPanelScroll.setBackground(BG_FRAME);
        rightPanelScroll.getViewport().setBackground(BG_FRAME);
        rightPanelScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        rightPanelScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        rightPanelScroll.getVerticalScrollBar().setUnitIncrement(20);
        rightPanelScroll.setMinimumSize(new Dimension(RIGHT_PANEL_MIN_WIDTH, 0));
        rightPanelScroll.setPreferredSize(new Dimension(RIGHT_PANEL_PREFERRED_WIDTH, 0));

        // Use a split pane so the right panel can move below the schematic on narrow windows
        contentSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, schematic, rightPanelScroll);
        contentSplitPane.setResizeWeight(0.7);
        contentSplitPane.setContinuousLayout(true);
        contentSplitPane.setOneTouchExpandable(true);
        contentSplitPane.setBorder(null);
        contentSplitPane.setMinimumSize(new Dimension(0, 0));

        // Wrap split pane in a scroll pane for very small windows
        mainScrollPane = new JScrollPane(contentSplitPane);
        mainScrollPane.setBorder(null);
        mainScrollPane.setBackground(BG_FRAME);
        mainScrollPane.getViewport().setBackground(BG_FRAME);
        mainScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        mainScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        mainScrollPane.getHorizontalScrollBar().setUnitIncrement(20);
        mainScrollPane.getHorizontalScrollBar().setBlockIncrement(100);
        mainScrollPane.getVerticalScrollBar().setUnitIncrement(20);
        mainScrollPane.getVerticalScrollBar().setBlockIncrement(100);

        root.add(mainScrollPane, BorderLayout.CENTER);

        // Event log with flexible sizing
        eventLog.setMinimumSize(new Dimension(0, EVENT_LOG_MIN_HEIGHT));
        eventLog.setPreferredSize(new Dimension(0, 180));
        eventLog.setMaximumSize(new Dimension(Integer.MAX_VALUE, EVENT_LOG_MAX_HEIGHT));
        root.add(eventLog, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel buildTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(16, 20, 35));
        bar.setBorder(new EmptyBorder(6, 12, 6, 12));
        bar.setMinimumSize(new Dimension(0, 40));
        bar.setPreferredSize(new Dimension(0, 40));

        JLabel title = new JLabel("☢  NUCLEAR REACTOR CONTROL DASHBOARD");
        title.setFont(new Font("Inter", Font.BOLD, 16));
        title.setForeground(new Color(0, 220, 140));

        JLabel subtitle = new JLabel("Automated regulation active  |  Real-time monitoring");
        subtitle.setFont(new Font("Inter", Font.PLAIN, 11));
        subtitle.setForeground(new Color(100, 120, 170));

        bar.add(title,    BorderLayout.WEST);
        bar.add(subtitle, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildRightPanel() {
        JPanel right = new JPanel(new BorderLayout(6, 6));
        right.setBackground(BG_FRAME);
        right.setMinimumSize(new Dimension(RIGHT_PANEL_MIN_WIDTH, 0));
        right.setPreferredSize(new Dimension(RIGHT_PANEL_PREFERRED_WIDTH, 0));
        right.setMaximumSize(new Dimension(RIGHT_PANEL_MAX_WIDTH, Integer.MAX_VALUE));

        // —— Gauge grid (2×2 circular) with responsive behavior
        JPanel gaugeGrid = new JPanel(new GridBagLayout());
        gaugeGrid.setBackground(new Color(14, 17, 28));
        gaugeGrid.setBorder(BorderFactory.createLineBorder(new Color(40, 50, 80)));

        GridBagConstraints g = new GridBagConstraints();
        g.insets   = new Insets(4, 4, 4, 4);
        g.fill     = GridBagConstraints.BOTH;
        g.weightx  = 1;
        g.weighty  = 1;

        g.gridx = 0; g.gridy = 0; gaugeGrid.add(powerGauge,      g);
        g.gridx = 1;               gaugeGrid.add(tempGauge,       g);
        g.gridx = 0; g.gridy = 1; gaugeGrid.add(reactivityGauge, g);
        g.gridx = 1;               gaugeGrid.add(coolantGauge,    g);

        // —— Bar gauges with minimum height
        JPanel barRow = new JPanel(new GridLayout(1, 2, 6, 0));
        barRow.setBackground(new Color(14, 17, 28));
        barRow.add(targetPowerBar);
        barRow.add(rodPositionBar);
        barRow.setMinimumSize(new Dimension(0, 90));
        barRow.setPreferredSize(new Dimension(0, 100));
        barRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        JPanel gaugesWrapper = new JPanel(new BorderLayout(6, 6));
        gaugesWrapper.setBackground(BG_FRAME);
        gaugesWrapper.add(gaugeGrid, BorderLayout.CENTER);
        gaugesWrapper.add(barRow,    BorderLayout.SOUTH);
        gaugesWrapper.setMinimumSize(new Dimension(RIGHT_PANEL_MIN_WIDTH - 16, 220));

        // Controls panel with scroll capability for small heights
        JScrollPane controlsScroll = new JScrollPane(controls);
        controlsScroll.setMinimumSize(new Dimension(RIGHT_PANEL_MIN_WIDTH - 16, 160));
        controlsScroll.setBorder(BorderFactory.createLineBorder(new Color(40, 50, 80)));
        controlsScroll.setBackground(BG_FRAME);
        controlsScroll.getViewport().setBackground(BG_FRAME);
        controlsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        controlsScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        controlsScroll.setMinimumSize(new Dimension(RIGHT_PANEL_MIN_WIDTH - 16, 200));
        controlsScroll.getVerticalScrollBar().setUnitIncrement(16);

        right.add(gaugesWrapper, BorderLayout.NORTH);
        right.add(controlsScroll, BorderLayout.CENTER);

        return right;
    }
    
    // ---- Responsive Behavior ———————————————————————————
    
    private void setupResponsiveBehavior() {
        // Listen for window resize events to adjust layout
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                adjustLayoutForSize();
            }
        });
        
        // Initial adjustment
        SwingUtilities.invokeLater(this::adjustLayoutForSize);
    }
    
    private void adjustLayoutForSize() {
        Dimension size = getContentPane().getSize();
        int availableWidth = size.width - 16; // Account for borders
        int availableHeight = size.height - 16;
        
        // Calculate optimal right panel width based on available space
        int optimalRightWidth = Math.max(RIGHT_PANEL_MIN_WIDTH,
            Math.min(RIGHT_PANEL_MAX_WIDTH, availableWidth / 3));

        // Adjust schematic width and right panel width based on available space
        int minSchematicWidth = Math.max(SCHEMATIC_MIN_WIDTH, Math.min(availableWidth - optimalRightWidth - 50, SCHEMATIC_MIN_WIDTH));
        schematic.setMinimumSize(new Dimension(minSchematicWidth, 300));
        rightPanel.setPreferredSize(new Dimension(optimalRightWidth, 0));

        // Switch to vertical split on narrow windows so the right panel remains visible
        if (availableWidth < 980) {
            contentSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
            contentSplitPane.setDividerLocation(Math.max(280, availableHeight / 2));
            contentSplitPane.setResizeWeight(0.5);
        } else {
            contentSplitPane.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
            contentSplitPane.setDividerLocation(Math.max(minSchematicWidth, availableWidth * 2 / 3));
            contentSplitPane.setResizeWeight(0.7);
        }

        // Keep both scroll bars available for small windows
        mainScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        mainScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        // Adjust event log height for small windows
        if (availableHeight < 500) {
            eventLog.setPreferredSize(new Dimension(0, EVENT_LOG_MIN_HEIGHT));
        } else {
            eventLog.setPreferredSize(new Dimension(0, 180));
        }
        
        revalidate();
        repaint();
    }

    // ---- Refresh timer —————————————————————————————————

    private void startRefreshTimer() {
        Timer timer = new Timer(REFRESH_MS, e -> refresh());
        timer.setCoalesce(true);
        timer.start();
    }

    private void refresh() {
        ReactorStateSnapshot snap = adapter.getSnapshot();

        powerGauge     .setValue(snap.power);
        tempGauge      .setValue(snap.temperature);
        reactivityGauge.setValue(snap.reactivity);
        coolantGauge   .setValue(snap.coolantFlowRate);
        targetPowerBar .setValue(snap.targetPower);
        rodPositionBar .setValue(snap.controlRodPosition);

        schematic.updateSnapshot(snap);
        controls .syncFromSnapshot(snap);
        eventLog .drainQueue();
    }
}
