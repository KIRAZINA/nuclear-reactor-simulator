package org.reactor_model.ui;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.util.concurrent.BlockingQueue;

/**
 * Scrollable event log panel that drains {@link UiReactorLogger}'s queue.
 * Color-codes entries: INFO = light blue, WARNING = yellow, CRITICAL = red.
 * 
 * Responsive features:
 *   - Flexible height with min/max constraints
 *   - Horizontal scrolling for long log entries
 *   - Dynamic font sizing based on panel height
 */
public class EventLogPanel extends JPanel {

    private static final Color BG          = new Color(10, 12, 22);
    private static final Color INFO_COLOR  = new Color(160, 190, 255);
    private static final Color WARN_COLOR  = new Color(255, 210, 60);
    private static final Color CRIT_COLOR  = new Color(255, 80,  80);
    private static final int   MAX_LINES   = 400;
    
    // Responsive sizing
    private static final int MIN_HEIGHT = 100;
    private static final int MAX_HEIGHT = 300;
    private static final int PREFERRED_HEIGHT = 180;

    private final JTextPane   textPane;
    private final StyledDocument doc;
    private final SimpleAttributeSet infoStyle;
    private final SimpleAttributeSet warnStyle;
    private final SimpleAttributeSet critStyle;

    private final BlockingQueue<UiReactorLogger.LogEntry> queue;

    public EventLogPanel(BlockingQueue<UiReactorLogger.LogEntry> queue) {
        this.queue = queue;
        setBackground(BG);
        setLayout(new BorderLayout(0, 0));
        
        // Set size constraints for responsive behavior
        setMinimumSize(new Dimension(0, MIN_HEIGHT));
        setPreferredSize(new Dimension(0, PREFERRED_HEIGHT));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, MAX_HEIGHT));

        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setBackground(BG);
        textPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        doc = textPane.getStyledDocument();

        infoStyle = makeStyle(INFO_COLOR);
        warnStyle = makeStyle(WARN_COLOR);
        critStyle = makeStyle(CRIT_COLOR);

        // Scroll pane with both vertical and horizontal scrolling
        JScrollPane scroll = new JScrollPane(textPane);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(40, 50, 80)));
        scroll.setBackground(BG);
        scroll.getViewport().setBackground(BG);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        
        // Smoother scrolling
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getHorizontalScrollBar().setUnitIncrement(16);

        // Header bar with responsive layout
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(new Color(18, 22, 38));
        header.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        
        JLabel title = new JLabel("EVENT LOG");
        title.setForeground(new Color(140, 160, 210));
        title.setFont(new Font("Inter", Font.BOLD, 12));
        
        // Panel for buttons to allow responsive layout
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttonPanel.setOpaque(false);
        
        JButton clearBtn = new JButton("Clear");
        clearBtn.setFont(new Font("Inter", Font.PLAIN, 11));
        clearBtn.setBackground(new Color(40, 50, 80));
        clearBtn.setForeground(new Color(180, 190, 220));
        clearBtn.setBorderPainted(false);
        clearBtn.setFocusPainted(false);
        clearBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        clearBtn.addActionListener(e -> {
            try { doc.remove(0, doc.getLength()); }
            catch (BadLocationException ex) { /* ignore */ }
        });
        
        buttonPanel.add(clearBtn);
        
        header.add(title, BorderLayout.WEST);
        header.add(buttonPanel, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        add(scroll,  BorderLayout.CENTER);
    }

    /**
     * Drains all pending log entries from the queue → EDT-safe because
     * this method is called from within a Swing Timer.
     */
    public void drainQueue() {
        UiReactorLogger.LogEntry entry;
        while ((entry = queue.poll()) != null) {
            appendEntry(entry);
        }
        trimToMaxLines();
    }

    private void appendEntry(UiReactorLogger.LogEntry entry) {
        SimpleAttributeSet style = switch (entry.level) {
            case WARNING  -> warnStyle;
            case CRITICAL -> critStyle;
            default       -> infoStyle;
        };
        try {
            doc.insertString(doc.getLength(), entry.text + "\n", style);
            // Auto-scroll to bottom
            textPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            // ignore
        }
    }

    private void trimToMaxLines() {
        Element root = doc.getDefaultRootElement();
        int excess = root.getElementCount() - MAX_LINES;
        if (excess > 0) {
            Element first = root.getElement(0);
            Element last  = root.getElement(excess - 1);
            try {
                doc.remove(first.getStartOffset(),
                           last.getEndOffset() - first.getStartOffset());
            } catch (BadLocationException e) {
                // ignore
            }
        }
    }

    private static SimpleAttributeSet makeStyle(Color c) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, c);
        return s;
    }
}
