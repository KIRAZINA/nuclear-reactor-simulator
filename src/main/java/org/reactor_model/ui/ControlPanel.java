package org.reactor_model.ui;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * Operator control panel with responsive layout.
 * All controls that change reactor state dispatch commands through {@link ReactorUIAdapter}.
 * 
 * Layout improvements:
 *   - Fixed element overlapping with proper GridBagConstraints
 *   - Minimum/preferred sizes for all components
 *   - Flexible spacing that adapts to panel size
 *   - Scrollable when content exceeds available space
 */
public class ControlPanel extends JPanel {

    /**
     * Non-intrusive feedback channel for file operations (save/load/export).
     * Wired by {@code ReactorDashboard} to its status bar; when absent,
     * dialogs are used as a fallback so the panel also works standalone.
     */
    public interface StatusReporter {
        void report(String message, boolean isError);
    }

    private final ReactorUIAdapter adapter;
    private volatile StatusReporter statusReporter;

    // Power control (initialized in constructor)
    private JSlider  powerSlider;
    private JSpinner powerSpinner;

    // Control rod (manual) (initialized in constructor)
    private JSlider rodSlider;
    private JLabel  rodValueLabel;

    // Toggle buttons (initialized in constructor)
    private JToggleButton autoRegBtn;
    private JToggleButton disturbanceBtn;

    // Action buttons (initialized in constructor)
    private JButton startBtn;
    private JButton stopBtn;
    private JButton scramBtn;
    private JButton restartBtn;

    private boolean updatingPower = false;

    // Rising-edge latch so the xenon-poisoning warning fires once per episode,
    // not on every 200 ms refresh tick.
    private boolean xenonWarningLatched = false;

    // Rising-edge latch for the meltdown announcement.
    private boolean meltdownReported = false;
    
    // Target power hard limits (shared by spinner, slider and adapter clamp).
    private static final int TARGET_POWER_MIN = 0;
    private static final int TARGET_POWER_MAX = 5000;

    // Layout constraints constants
    private static final int MIN_COMPONENT_HEIGHT = 28;
    private static final int PREFERRED_COMPONENT_HEIGHT = 34;
    private static final int SECTION_SPACING = 8;
    private static final int INTERNAL_PADDING = 6;

    public ControlPanel(ReactorUIAdapter adapter) {
        this.adapter = adapter;

        setBackground(new Color(14, 17, 28));
        setLayout(new GridBagLayout());
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(SECTION_SPACING / 2, INTERNAL_PADDING, SECTION_SPACING / 2, INTERNAL_PADDING);
        gbc.fill   = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.weightx = 1.0;
        gbc.gridx = 0;

        // —— Simulation controls ————————————————————————
        JPanel simPanel = createSimulationPanel();
        gbc.gridy = 0;
        gbc.weighty = 0;
        add(simPanel, gbc);

        // —— Target power ———————————————————————————————
        JPanel pwrPanel = createPowerPanel();
        gbc.gridy = 1;
        add(pwrPanel, gbc);

        // —— Auto-regulator + manual rod ————————————————
        JPanel regPanel = createRegulatorPanel();
        gbc.gridy = 2;
        add(regPanel, gbc);

        // —— Operator actions ———————————————————————————
        JPanel actPanel = createActionsPanel();
        gbc.gridy = 3;
        gbc.weighty = 1.0; // Push all panels to top
        gbc.fill = GridBagConstraints.BOTH;
        add(actPanel, gbc);
        
        // NOTE: no explicit preferred/minimum size is set here — GridBagLayout
        // computes honest sizes from the section panels (currently ~280x750).
        // A hardcoded smaller size would make scroll panes and GridBag squeeze
        // the button rows instead of scrolling (the Phase 6 audit caught
        // 12px-tall buttons caused by exactly such a hardcoded 280x460).
    }

    /**
     * Sets the status reporter used for save/load/export feedback.
     * Typically wired to the dashboard status bar.
     */
    public void setStatusReporter(StatusReporter reporter) {
        this.statusReporter = reporter;
    }

    /** Reports success/failure: status bar when wired, dialog fallback otherwise. */
    private void reportStatus(String message, boolean isError) {
        StatusReporter reporter = statusReporter;
        if (reporter != null) {
            reporter.report(message, isError);
            return;
        }
        JOptionPane.showMessageDialog(this, message,
                isError ? "Error" : "Success",
                isError ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
    }
    
    // ---- Panel creation methods ———————————————————————

    private JPanel createSimulationPanel() {
        JPanel simPanel = titledPanel("Simulation");
        simPanel.setLayout(new BorderLayout(4, 4));

        JPanel btnRow = new JPanel(new GridLayout(1, 2, 8, 0));
        btnRow.setOpaque(false);

        startBtn = actionButton("▶  Start", new Color(0, 160, 80));
        stopBtn  = actionButton("⏹  Stop",  new Color(100, 100, 120));
        
        startBtn.setName("btnStart");
        stopBtn.setName("btnStop");
        // Minimum widths so the 2-column row cannot squeeze buttons to slivers;
        // heights already enforced per-button by actionButton().
        startBtn.setMinimumSize(new Dimension(100, MIN_COMPONENT_HEIGHT));
        stopBtn.setMinimumSize(new Dimension(100, MIN_COMPONENT_HEIGHT));
        btnRow.setMinimumSize(new Dimension(208, MIN_COMPONENT_HEIGHT));
        startBtn.addActionListener(e -> {
            // Rapid-fire protection: ignore if already running or already pressed.
            if (!startBtn.isEnabled() || adapter.isRunning()) {
                return;
            }
            adapter.startLoop();
            startBtn.setEnabled(false);
            stopBtn.setEnabled(true);
        });
        stopBtn .addActionListener(e -> {
            adapter.stopLoop();
            stopBtn.setEnabled(false);
            startBtn.setEnabled(true);
        });

        btnRow.add(startBtn);
        btnRow.add(stopBtn);

        simPanel.add(btnRow, BorderLayout.CENTER);
        simPanel.add(buildSpeedRow(), BorderLayout.SOUTH);

        // Explicit section size: the generic titled-panel minimum (80px) does not
        // cover button row (34) + speed row (34) + titled-border tax (~25), which
        // squeezed Start/Stop to 12px. 100px guarantees both rows stay full height.
        simPanel.setMinimumSize(new Dimension(208, 100));
        simPanel.setPreferredSize(new Dimension(240, 104));

        return simPanel;
    }

    /** Speed selector row: 1x / 2x / 5x / 10x, delegated to the simulation loop. */
    private JPanel buildSpeedRow() {
        JPanel speedRow = new JPanel(new BorderLayout(6, 0));
        speedRow.setOpaque(false);

        JLabel speedLabel = new JLabel("Speed:");
        speedLabel.setForeground(new Color(160, 170, 200));
        speedLabel.setFont(new Font("Inter", Font.PLAIN, 11));

        JComboBox<String> speedCombo = new JComboBox<>(new String[]{"1x", "2x", "5x", "10x"});
        speedCombo.setName("comboSpeed");
        speedCombo.setFont(new Font("Inter", Font.PLAIN, 12));
        speedCombo.setMinimumSize(new Dimension(0, MIN_COMPONENT_HEIGHT));
        speedCombo.setPreferredSize(new Dimension(0, PREFERRED_COMPONENT_HEIGHT));
        speedCombo.addActionListener(e -> {
            String selected = (String) speedCombo.getSelectedItem();
            if (selected == null) {
                return;
            }
            try {
                double multiplier = Double.parseDouble(selected.replace("x", "").trim());
                adapter.setSimulationSpeed(multiplier);
            } catch (NumberFormatException ex) {
                // Should never happen with fixed combo items; ignore.
                System.err.println("[ControlPanel] Invalid speed selection: " + selected);
            }
        });

        speedRow.add(speedLabel, BorderLayout.WEST);
        speedRow.add(speedCombo, BorderLayout.CENTER);
        // Row-level minimum so the speed selector cannot collapse to 0 height
        // when horizontal space is constrained.
        speedRow.setMinimumSize(new Dimension(0, MIN_COMPONENT_HEIGHT));
        return speedRow;
    }

    private JPanel createPowerPanel() {
        JPanel pwrPanel = titledPanel("Target Power");
        pwrPanel.setLayout(new GridBagLayout());
        
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(2, 4, 2, 4);
        g.fill = GridBagConstraints.HORIZONTAL;
        
        // Label
        JLabel mwLabel = new JLabel("MW:");
        mwLabel.setForeground(new Color(160, 170, 200));
        mwLabel.setFont(new Font("Inter", Font.PLAIN, 11));
        g.gridx = 0; g.gridy = 0;
        g.weightx = 0;
        pwrPanel.add(mwLabel, g);
        
        // Spinner (hard limits at the component level via SpinnerNumberModel)
        SpinnerNumberModel spinModel = new SpinnerNumberModel(100, TARGET_POWER_MIN, TARGET_POWER_MAX, 50);
        powerSpinner = new JSpinner(spinModel);
        powerSpinner.setName("spinnerTargetPower");
        styleSpinner(powerSpinner);
        powerSpinner.setMinimumSize(new Dimension(80, MIN_COMPONENT_HEIGHT));
        powerSpinner.setPreferredSize(new Dimension(100, PREFERRED_COMPONENT_HEIGHT));
        g.gridx = 1;
        g.weightx = 1;
        pwrPanel.add(powerSpinner, g);
        
        // Slider (hard limits match the spinner model)
        powerSlider = new JSlider(TARGET_POWER_MIN, TARGET_POWER_MAX, 100);
        powerSlider.setName("sliderTargetPower");
        styleSlider(powerSlider);
        powerSlider.setMinimumSize(new Dimension(150, MIN_COMPONENT_HEIGHT));
        g.gridx = 0; g.gridy = 1;
        g.gridwidth = 2;
        g.weightx = 1;
        pwrPanel.add(powerSlider, g);
        
        // Setup listeners
        setupPowerListeners();
        
        return pwrPanel;
    }

    private JPanel createRegulatorPanel() {
        JPanel regPanel = titledPanel("Regulator");
        regPanel.setLayout(new BorderLayout(4, 4));

        // Auto-regulator toggle
        autoRegBtn = new JToggleButton("Auto-Regulator: ON", true);
        autoRegBtn.setName("tglAutoReg");
        styleToggle(autoRegBtn, true);
        autoRegBtn.setMinimumSize(new Dimension(0, MIN_COMPONENT_HEIGHT));
        autoRegBtn.setPreferredSize(new Dimension(0, PREFERRED_COMPONENT_HEIGHT));

        // Disturbance simulation toggle (OFF by default)
        disturbanceBtn = new JToggleButton("Disturbances: OFF", false);
        disturbanceBtn.setName("tglDisturbances");
        styleToggle(disturbanceBtn, false);
        disturbanceBtn.setMinimumSize(new Dimension(0, MIN_COMPONENT_HEIGHT));
        disturbanceBtn.setPreferredSize(new Dimension(0, PREFERRED_COMPONENT_HEIGHT));
        disturbanceBtn.addActionListener(e -> {
            boolean on = disturbanceBtn.isSelected();
            adapter.toggleDisturbances();
            styleToggle(disturbanceBtn, on);
            disturbanceBtn.setText("Disturbances: " + (on ? "ON ⚠" : "OFF"));
        });

        // Rod slider (vertical)
        rodSlider = new JSlider(JSlider.VERTICAL, 0, 100, 50);
        rodSlider.setName("sliderRod");
        styleSlider(rodSlider);
        rodSlider.setEnabled(false);
        rodSlider.setMinimumSize(new Dimension(40, 80));
        rodSlider.setPreferredSize(new Dimension(50, 120));

        // Rod value label
        rodValueLabel = new JLabel("Rod: 0.50");
        rodValueLabel.setForeground(new Color(160, 170, 200));
        rodValueLabel.setFont(new Font("Inter", Font.PLAIN, 11));
        rodValueLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Rod wrapper
        JPanel rodWrapper = new JPanel(new BorderLayout(4, 4));
        rodWrapper.setOpaque(false);
        rodWrapper.add(rodValueLabel, BorderLayout.NORTH);
        rodWrapper.add(rodSlider, BorderLayout.CENTER);
        rodWrapper.setMinimumSize(new Dimension(60, 100));
        rodWrapper.setPreferredSize(new Dimension(70, 140));

        // Setup listeners
        setupRegulatorListeners();

        // Wrapper for toggle buttons
        JPanel toggleWrapper = new JPanel(new GridLayout(2, 1, 4, 4));
        toggleWrapper.setOpaque(false);
        toggleWrapper.add(autoRegBtn);
        toggleWrapper.add(disturbanceBtn);

        regPanel.add(toggleWrapper, BorderLayout.NORTH);
        regPanel.add(rodWrapper, BorderLayout.CENTER);

        // Explicit section size: toggles (72) + rod label/slider (~105 minimum)
        // + border tax exceed the generic 80px minimum, which drove the rod
        // slider to negative height. 200px keeps every control fully visible.
        regPanel.setMinimumSize(new Dimension(208, 200));
        regPanel.setPreferredSize(new Dimension(240, 250));

        return regPanel;
    }

    private JPanel createActionsPanel() {
        JPanel actPanel = titledPanel("Operator Actions");
        actPanel.setLayout(new GridLayout(0, 1, 4, 6)); // Variable rows, single column
        
        JButton spikeBtn = actionButton("⚡  Inject Reactivity Spike", new Color(180, 140, 0));
        JButton failBtn  = actionButton("💧  Simulate Coolant Failure",  new Color(160, 60,  20));
        scramBtn = actionButton("🛑  EMERGENCY SCRAM",           new Color(200, 20,  20));
        restartBtn       = actionButton("↺  Restart Reactor",            new Color(0, 80, 160));
        restartBtn.setName("btnRestart");
        restartBtn.setEnabled(false);

        spikeBtn.setName("btnSpike");
        spikeBtn.addActionListener(e -> adapter.injectSpike());

        failBtn.setName("btnFailure");
        failBtn.addActionListener(e -> {
            int r = JOptionPane.showConfirmDialog(this,
                    "Simulate catastrophic coolant pump failure?\nThis will likely cause SCRAM.",
                    "Confirm Coolant Failure",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r == JOptionPane.YES_OPTION) adapter.simulateCoolantFailure();
        });

        scramBtn.setName("btnScram");
        scramBtn.addActionListener(e -> {
            // Guard: never SCRAM twice — the button is disabled while shut down,
            // but double-check the snapshot for rapid-fire protection.
            if (adapter.getSnapshot().shutdown) {
                return;
            }
            int r = JOptionPane.showConfirmDialog(this,
                    "Initiate Emergency SCRAM?\nReactor will shut down immediately.",
                    "Confirm SCRAM",
                    JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
            if (r == JOptionPane.YES_OPTION) adapter.scram();
        });

        restartBtn.addActionListener(e -> {
            adapter.restart();
            if (!adapter.isCoreMeltedDown()) {
                restartBtn.setEnabled(false);
            } else {
                reportStatus("Cannot restart: Core is damaged.", true);
            }
        });

        // LOFA drill: realistic pump coastdown (manual lock, en-route to SCRAM).
        JButton pumpBtn = actionButton("🛢  Fail Coolant Pump", new Color(160, 60, 20));
        pumpBtn.setName("btnFailPump");
        pumpBtn.addActionListener(e -> {
            int r = JOptionPane.showConfirmDialog(this,
                    "Trip the main coolant pump?\nFlow will coast to zero over 5 s (LOFA).",
                    "Confirm Pump Trip",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r == JOptionPane.YES_OPTION) {
                adapter.failCoolantPump();
                reportStatus("Coolant pump tripped: flow coasting down.", false);
            }
        });

        // ATWS drill: hold the protection breakers closed. Red while armed.
        JToggleButton bypassBtn = new JToggleButton("Bypass SCRAM: OFF", false);
        bypassBtn.setName("tglBypassScram");
        styleToggle(bypassBtn, false);
        bypassBtn.setText("Bypass SCRAM: OFF");
        bypassBtn.addActionListener(e -> {
            boolean armed = bypassBtn.isSelected();
            adapter.setBypassScram(armed);
            if (armed) {
                bypassBtn.setBackground(new Color(200, 20, 20));
                bypassBtn.setText("BYPASS ARMED ⚠");
                reportStatus("Auto-SCRAM bypass ARMED (ATWS mode).", true);
            } else {
                styleToggle(bypassBtn, false);
                bypassBtn.setText("Bypass SCRAM: OFF");
                reportStatus("Auto-SCRAM bypass disarmed.", false);
            }
        });

        JButton saveBtn = actionButton("💾  Save State", new Color(60, 90, 160));
        JButton loadBtn = actionButton("📂  Load State", new Color(60, 90, 160));
        JButton exportBtn = actionButton("📊  Export Data (CSV)", new Color(40, 110, 90));
        saveBtn.setName("btnSave");
        loadBtn.setName("btnLoad");
        exportBtn.setName("btnExport");
        saveBtn.addActionListener(e -> saveStateViaChooser());
        loadBtn.addActionListener(e -> loadStateViaChooser());
        exportBtn.addActionListener(e -> exportDataViaChooser());

        actPanel.add(spikeBtn);
        actPanel.add(failBtn);
        actPanel.add(pumpBtn);
        actPanel.add(bypassBtn);
        actPanel.add(scramBtn);
        actPanel.add(restartBtn);
        actPanel.add(saveBtn);
        actPanel.add(loadBtn);
        actPanel.add(exportBtn);

        // Harden against GridLayout squish: GridLayout divides space equally and
        // ignores child minimums when shrinking, so enforce a panel-level minimum
        // (one full button height per row + gaps). Below this height the parent
        // JScrollPane scrolls instead of compressing buttons into unusable slivers.
        int rows = actPanel.getComponentCount();
        int actMinH = Math.max(180, rows * (MIN_COMPONENT_HEIGHT + 6) + 16); // gaps + border slack
        actPanel.setMinimumSize(new Dimension(240, actMinH));
        
        return actPanel;
    }
    
    // ---- Listener setup methods ———————————————————————
    
    private void setupPowerListeners() {
        powerSlider.addChangeListener(e -> {
            if (updatingPower) return;
            updatingPower = true;
            try {
                int v = powerSlider.getValue();
                powerSpinner.setValue(v);
                adapter.setTargetPower(v);
            } finally {
                updatingPower = false;
            }
        });
        
        powerSpinner.addChangeListener(e -> {
            if (updatingPower) return;
            updatingPower = true;
            try {
                // Clamp typed input to the hard [min, max] range: users can type
                // arbitrary text into the spinner editor, so never trust it raw.
                Object raw = powerSpinner.getValue();
                int v = (raw instanceof Number n)
                        ? n.intValue()
                        : powerSlider.getValue();
                int clamped = Math.max(TARGET_POWER_MIN, Math.min(TARGET_POWER_MAX, v));
                if (!(raw instanceof Number) || clamped != ((Number) raw).intValue()) {
                    powerSpinner.setValue(clamped);
                }
                powerSlider.setValue(clamped);
                adapter.setTargetPower(clamped);
            } finally {
                updatingPower = false;
            }
        });
    }
    
    private void setupRegulatorListeners() {
        rodSlider.addChangeListener(e -> {
            if (!rodSlider.isEnabled()) return;
            double pos = rodSlider.getValue() / 100.0;
            // Live label feedback while dragging, but only send the position to
            // the core on release: per-tick updates during a drag are spam that
            // fights the regulator and wears the (simulated) drive mechanisms.
            rodValueLabel.setText(String.format("Rod: %.2f", pos));
            if (rodSlider.getValueIsAdjusting()) return;
            adapter.setControlRodPosition(pos);
        });

        autoRegBtn.addActionListener(e -> {
            boolean on = autoRegBtn.isSelected();
            adapter.setAutoRegulator(on);
            styleToggle(autoRegBtn, on);
            autoRegBtn.setText("Auto-Regulator: " + (on ? "ON" : "OFF"));
            rodSlider.setEnabled(!on);
        });
    }

    /** Called by the Swing Timer with the latest snapshot to sync UI state. */
    public void syncFromSnapshot(ReactorStateSnapshot snap) {
        boolean melted = adapter.isCoreMeltedDown();
        // A melted core can never restart: pin the button off forever.
        restartBtn.setEnabled(snap.shutdown && !melted);
        scramBtn.setEnabled(!snap.shutdown);
        if (melted && !meltdownReported) {
            meltdownReported = true;
            reportStatus("CORE MELTDOWN — restart impossible.", true);
        }

        // Xenon-poisoning lockout: rods fully withdrawn yet power pinned near
        // zero means Xe-135 is holding the core subcritical — manual rod motion
        // cannot help, so lock the slider and say so (once per episode).
        boolean xenonPoisoned = !snap.shutdown
                && snap.controlRodPosition >= 0.99
                && snap.power < 5.0;
        rodSlider.setEnabled(!autoRegBtn.isSelected() && !xenonPoisoned);
        if (xenonPoisoned && !xenonWarningLatched) {
            reportStatus("Cannot overcome Xenon poisoning; wait for decay.", true);
        }
        xenonWarningLatched = xenonPoisoned;

        if (!updatingPower) {
            // Clamp the synced target into the slider/spinner range so a state
            // restored from elsewhere (e.g. a loaded file) cannot break the controls.
            int target = (int) Math.round(
                    Math.max(TARGET_POWER_MIN, Math.min(TARGET_POWER_MAX, snap.targetPower)));
            if (powerSlider.getValue() != target) {
                updatingPower = true;
                try {
                    powerSlider.setValue(target);
                    powerSpinner.setValue(target);
                } finally {
                    updatingPower = false;
                }
            }
        }
    }

    // ---- State persistence (save/load via file chooser) ------------------

    private void saveStateViaChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Reactor State");
        int choice = chooser.showSaveDialog(this);
        if (choice != JFileChooser.APPROVE_OPTION) {
            return;
        }
        String path = chooser.getSelectedFile().getAbsolutePath();
        try {
            adapter.saveState(path);
            reportStatus("Reactor state saved to " + path + ".", false);
        } catch (Exception ex) {
            reportStatus("Failed to save reactor state: " + ex.getMessage(), true);
            showSevereErrorDialog("Failed to save reactor state:\n" + ex.getMessage(), "Save Error");
        }
    }

    private void loadStateViaChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load Reactor State");
        int choice = chooser.showOpenDialog(this);
        if (choice != JFileChooser.APPROVE_OPTION) {
            return;
        }
        String path = chooser.getSelectedFile().getAbsolutePath();
        try {
            adapter.loadState(path);
            reportStatus("Reactor state loaded from " + path + ".", false);
        } catch (Exception ex) {
            reportStatus("Failed to load reactor state: " + ex.getMessage(), true);
            showSevereErrorDialog("Failed to load reactor state:\n" + ex.getMessage(), "Load Error");
        }
    }

    private void exportDataViaChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Recorded Data (CSV)");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "CSV files (*.csv)", "csv"));
        int choice = chooser.showSaveDialog(this);
        if (choice != JFileChooser.APPROVE_OPTION) {
            return;
        }
        String path = chooser.getSelectedFile().getAbsolutePath();
        if (!path.toLowerCase().endsWith(".csv")) {
            path += ".csv";
        }
        try {
            int samples = adapter.getBufferedSampleCount();
            adapter.exportData(path);
            reportStatus("Simulation data exported to " + path
                    + " (" + samples + " samples).", false);
        } catch (Exception ex) {
            reportStatus("Failed to export simulation data: " + ex.getMessage(), true);
            showSevereErrorDialog("Failed to export simulation data:\n" + ex.getMessage(), "Export Error");
        }
    }

    /**
     * Shows a modal error dialog only when a status reporter is wired
     * (the status bar is then primary and the dialog flags the severe case).
     * Without a reporter, {@link #reportStatus} already showed a dialog.
     */
    private void showSevereErrorDialog(String message, String title) {
        if (statusReporter != null) {
            JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- UI helpers ———————————————————————————————————

    private JPanel titledPanel(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(new Color(20, 24, 38));
        
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(60, 70, 110), 1), title);
        border.setTitleColor(new Color(140, 160, 210));
        border.setTitleFont(new Font("Inter", Font.BOLD, 11));
        
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(4, 4, 4, 4), border));
        
        // Set minimum panel size
        p.setMinimumSize(new Dimension(240, 80));
        return p;
    }

    private JButton actionButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Inter", Font.BOLD, 12));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        // Set explicit sizes to prevent overlapping.
        // NOTE: preferred width must stay small and sane — layout managers sum
        // child preferred widths (GridLayout, scroll viewports, pack()), and
        // Integer.MAX_VALUE overflows those sums to negative values.
        btn.setMinimumSize(new Dimension(0, MIN_COMPONENT_HEIGHT));
        btn.setPreferredSize(new Dimension(200, PREFERRED_COMPONENT_HEIGHT));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        Color hover = bg.brighter();
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { btn.setBackground(hover); }
            @Override public void mouseExited (java.awt.event.MouseEvent e) { btn.setBackground(bg);    }
        });
        return btn;
    }

    private void styleToggle(JToggleButton btn, boolean on) {
        btn.setFont(new Font("Inter", Font.BOLD, 12));
        btn.setBackground(on ? new Color(0, 140, 70) : new Color(140, 30, 30));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMinimumSize(new Dimension(0, MIN_COMPONENT_HEIGHT));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
    }

    private void styleSlider(JSlider s) {
        s.setOpaque(false);
        s.setForeground(new Color(0, 200, 130));
        s.setBackground(new Color(20, 24, 38));
        
        // Ensure slider has proper size
        if (s.getOrientation() == JSlider.HORIZONTAL) {
            s.setMinimumSize(new Dimension(100, MIN_COMPONENT_HEIGHT));
        } else {
            s.setMinimumSize(new Dimension(40, 80));
        }
    }

    private void styleSpinner(JSpinner s) {
        s.setFont(new Font("Inter", Font.PLAIN, 12));
        if (s.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().setBackground(new Color(30, 35, 55));
            editor.getTextField().setForeground(new Color(200, 220, 255));
        }
    }
}
