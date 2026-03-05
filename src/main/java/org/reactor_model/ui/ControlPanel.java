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

    private final ReactorUIAdapter adapter;

    // Power control (initialized in constructor)
    private JSlider  powerSlider;
    private JSpinner powerSpinner;

    // Control rod (manual) (initialized in constructor)
    private JSlider rodSlider;
    private JLabel  rodValueLabel;

    // Toggle buttons (initialized in constructor)
    private JToggleButton autoRegBtn;

    // Action buttons (initialized in constructor)
    private JButton restartBtn;

    private boolean updatingPower = false;
    
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
        
        // Set minimum size for the entire panel
        setMinimumSize(new Dimension(260, 400));
        setPreferredSize(new Dimension(300, 500));
    }
    
    // ---- Panel creation methods ———————————————————————

    private JPanel createSimulationPanel() {
        JPanel simPanel = titledPanel("Simulation");
        simPanel.setLayout(new GridLayout(1, 2, 8, 0));
        
        JButton startBtn = actionButton("▶  Start", new Color(0, 160, 80));
        JButton stopBtn  = actionButton("⏹  Stop",  new Color(100, 100, 120));
        
        startBtn.addActionListener(e -> adapter.startLoop());
        stopBtn .addActionListener(e -> adapter.stopLoop());
        
        simPanel.add(startBtn);
        simPanel.add(stopBtn);
        
        return simPanel;
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
        
        // Spinner
        SpinnerNumberModel spinModel = new SpinnerNumberModel(100, 100, 8000, 50);
        powerSpinner = new JSpinner(spinModel);
        styleSpinner(powerSpinner);
        powerSpinner.setMinimumSize(new Dimension(80, MIN_COMPONENT_HEIGHT));
        powerSpinner.setPreferredSize(new Dimension(100, PREFERRED_COMPONENT_HEIGHT));
        g.gridx = 1;
        g.weightx = 1;
        pwrPanel.add(powerSpinner, g);
        
        // Slider
        powerSlider = new JSlider(100, 8000, 100);
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
        styleToggle(autoRegBtn, true);
        autoRegBtn.setMinimumSize(new Dimension(0, MIN_COMPONENT_HEIGHT));
        autoRegBtn.setPreferredSize(new Dimension(0, PREFERRED_COMPONENT_HEIGHT));
        
        // Rod slider (vertical)
        rodSlider = new JSlider(JSlider.VERTICAL, 0, 100, 50);
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
        
        regPanel.add(autoRegBtn, BorderLayout.NORTH);
        regPanel.add(rodWrapper, BorderLayout.CENTER);
        
        return regPanel;
    }

    private JPanel createActionsPanel() {
        JPanel actPanel = titledPanel("Operator Actions");
        actPanel.setLayout(new GridLayout(0, 1, 4, 6)); // Variable rows, single column
        
        JButton spikeBtn = actionButton("⚡  Inject Reactivity Spike", new Color(180, 140, 0));
        JButton failBtn  = actionButton("💧  Simulate Coolant Failure",  new Color(160, 60,  20));
        JButton scramBtn = actionButton("🛑  EMERGENCY SCRAM",           new Color(200, 20,  20));
        restartBtn       = actionButton("↺  Restart Reactor",            new Color(0, 80, 160));
        restartBtn.setEnabled(false);

        spikeBtn.addActionListener(e -> adapter.injectSpike());

        failBtn.addActionListener(e -> {
            int r = JOptionPane.showConfirmDialog(this,
                    "Simulate catastrophic coolant pump failure?\nThis will likely cause SCRAM.",
                    "Confirm Coolant Failure",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r == JOptionPane.YES_OPTION) adapter.simulateCoolantFailure();
        });

        scramBtn.addActionListener(e -> {
            int r = JOptionPane.showConfirmDialog(this,
                    "Initiate Emergency SCRAM?\nReactor will shut down immediately.",
                    "Confirm SCRAM",
                    JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
            if (r == JOptionPane.YES_OPTION) adapter.scram();
        });

        restartBtn.addActionListener(e -> {
            adapter.restart();
            restartBtn.setEnabled(false);
        });

        actPanel.add(spikeBtn);
        actPanel.add(failBtn);
        actPanel.add(scramBtn);
        actPanel.add(restartBtn);
        
        // Ensure minimum height for the panel
        actPanel.setMinimumSize(new Dimension(0, 180));
        
        return actPanel;
    }
    
    // ---- Listener setup methods ———————————————————————
    
    private void setupPowerListeners() {
        powerSlider.addChangeListener(e -> {
            if (updatingPower) return;
            updatingPower = true;
            int v = powerSlider.getValue();
            powerSpinner.setValue(v);
            adapter.setTargetPower(v);
            updatingPower = false;
        });
        
        powerSpinner.addChangeListener(e -> {
            if (updatingPower) return;
            updatingPower = true;
            int v = ((Number) powerSpinner.getValue()).intValue();
            powerSlider.setValue(v);
            adapter.setTargetPower(v);
            updatingPower = false;
        });
    }
    
    private void setupRegulatorListeners() {
        rodSlider.addChangeListener(e -> {
            if (!rodSlider.isEnabled()) return;
            double pos = rodSlider.getValue() / 100.0;
            adapter.setControlRodPosition(pos);
            rodValueLabel.setText(String.format("Rod: %.2f", pos));
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
        restartBtn.setEnabled(snap.shutdown);

        if (!updatingPower) {
            int target = (int) Math.round(snap.targetPower);
            if (powerSlider.getValue() != target) {
                updatingPower = true;
                powerSlider.setValue(target);
                powerSpinner.setValue(target);
                updatingPower = false;
            }
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
        
        // Set explicit sizes to prevent overlapping
        btn.setMinimumSize(new Dimension(0, MIN_COMPONENT_HEIGHT));
        btn.setPreferredSize(new Dimension(Integer.MAX_VALUE, PREFERRED_COMPONENT_HEIGHT));
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
