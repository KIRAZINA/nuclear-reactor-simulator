package org.reactor_model.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Targeted layout audit for Phase 6: verifies that components added in
 * Phases 2–5 (Export button, Save/Load buttons, speed selector, status bar)
 * cannot collapse to zero size, and that scrollbars activate instead of
 * clipping content at small window sizes.
 *
 * <p>Uses plain Swing (no AssertJ-Swing) so it runs independently of the
 * known AssertJ module-access issue. Panel-level tests are headless-safe;
 * the 800x600 frame test is skipped when headless.
 */
@DisplayName("Layout Audit Tests")
class LayoutAuditTest {

    private static final String[] ACTION_BUTTONS = {
            "btnStart", "btnStop", "btnSpike", "btnFailure",
            "btnScram", "btnRestart", "btnSave", "btnLoad", "btnExport"
    };

    // ---- EDT helper ----------------------------------------------------

    private static void onEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(task);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting for the EDT");
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AssertionError ae) {
                throw ae;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            fail("EDT task failed: " + cause);
        }
    }

    // ---- Component search ----------------------------------------------

    private static Component findByName(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) {
                return child;
            }
            if (child instanceof Container container) {
                Component found = findByName(container, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // ---- Panel-level tests (headless-safe) ------------------------------

    @Test
    @DisplayName("Action button rows enforce a panel minimum covering every button")
    void actionsPanelMinimumCoversAllButtons() {
        onEdt(() -> {
            ControlPanel panel = new ControlPanel(mock(ReactorUIAdapter.class));

            Component export = findByName(panel, "btnExport");
            assertNotNull(export, "Export button must exist in the actions panel");
            Container actions = export.getParent();

            int rows = actions.getComponentCount();
            assertTrue(rows >= 7, "Actions panel should hold at least 7 buttons, was: " + rows);
            assertTrue(actions.getMinimumSize().height >= rows * 28,
                    "Actions minimum height (" + actions.getMinimumSize().height
                            + ") must fit all " + rows + " rows at 28px each");

            for (String name : ACTION_BUTTONS) {
                Component button = findByName(panel, name);
                assertNotNull(button, "Button '" + name + "' must exist");
                assertTrue(button.getMinimumSize().height >= 28,
                        "Button '" + name + "' must keep a 28px minimum height");
            }
        });
    }

    @Test
    @DisplayName("Speed selector row keeps a minimum height when space is tight")
    void speedRowKeepsMinimumHeight() {
        onEdt(() -> {
            ControlPanel panel = new ControlPanel(mock(ReactorUIAdapter.class));

            JComboBox<?> speed = (JComboBox<?>) findByName(panel, "comboSpeed");
            assertNotNull(speed, "Speed combo must exist in the Simulation section");
            assertTrue(speed.getMinimumSize().height >= 28,
                    "Speed combo must keep a 28px minimum height");
            assertTrue(speed.getParent().getMinimumSize().height >= 28,
                    "Speed row must keep a 28px minimum height");
        });
    }

    @Test
    @DisplayName("Buttons keep full height when the panel has enough space")
    void buttonsKeepHeightWithAdequateSpace() {
        onEdt(() -> {
            ControlPanel panel = new ControlPanel(mock(ReactorUIAdapter.class));
            panel.setSize(new Dimension(300, 1200));
            // validate() is a no-op without displayable peers (headless), so lay
            // out recursively instead — doLayout() only handles a single level.
            layoutRecursively(panel);

            for (String name : ACTION_BUTTONS) {
                Component button = findByName(panel, name);
                assertNotNull(button, "Button '" + name + "' must exist");
                assertTrue(button.getHeight() >= 28,
                        "Button '" + name + "' collapsed to " + button.getHeight() + "px");
                assertTrue(button.getWidth() > 0,
                        "Button '" + name + "' has zero width");
            }
            Component speed = findByName(panel, "comboSpeed");
            assertNotNull(speed);
            assertTrue(speed.getHeight() > 0, "Speed combo collapsed to zero height");

            // Manual rod controls must survive too (the audit caught a -45px rod slider).
            Component rod = findByName(panel, "sliderRod");
            assertNotNull(rod, "Rod slider must exist");
            assertTrue(rod.getHeight() > 0,
                    "Rod slider collapsed to " + rod.getHeight() + "px");
        });
    }

    /** Recursively lays out a container tree without requiring peers/display. */
    private static void layoutRecursively(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container container) {
                layoutRecursively(container);
            }
        }
    }

    @Test
    @DisplayName("Scroll pane activates instead of clipping a short viewport")
    void scrollPaneActivatesOnShortViewport() {
        onEdt(() -> {
            ControlPanel panel = new ControlPanel(mock(ReactorUIAdapter.class));
            JScrollPane scroll = new JScrollPane(panel);
            scroll.setSize(new Dimension(280, 200));
            scroll.validate();

            assertTrue(scroll.getVerticalScrollBar().isVisible(),
                    "Vertical scrollbar must appear when the viewport is shorter than the panel");
        });
    }

    // ---- Frame-level 800x600 stress test (needs a display) --------------

    @Test
    @DisplayName("Dashboard remains fully usable at 800x600")
    void dashboardUsableAt800x600() {
        assumeFalse(GraphicsEnvironment.isHeadless(),
                "Frame-level stress test requires a display");

        AtomicReference<ReactorDashboard> frameRef = new AtomicReference<>();
        onEdt(() -> {
            ReactorUIAdapter adapter = mock(ReactorUIAdapter.class);
            when(adapter.getSnapshot()).thenReturn(new ReactorStateSnapshot(
                    100.0, 300.0, 1.0, 0.5, 0.0, 100.0, 0, false, true));
            EventLogPanel eventLog = new EventLogPanel(new LinkedBlockingQueue<>());
            ReactorDashboard frame = new ReactorDashboard(adapter, eventLog);
            frame.setSize(new Dimension(800, 600));
            frame.setVisible(true);
            frame.validate();
            frameRef.set(frame);
        });

        try {
            onEdt(() -> {
                ReactorDashboard frame = frameRef.get();
                assertNotNull(frame);

                // Status bar: fixed height, full width, showing initial text.
                JLabel status = (JLabel) findByName(frame, "statusBar");
                assertNotNull(status, "Status bar must exist");
                assertTrue(status.isShowing(), "Status bar must be showing");
                assertTrue(status.getHeight() >= 22 && status.getHeight() <= 30,
                        "Status bar height must stay pinned, was: " + status.getHeight());
                assertTrue(status.getWidth() > 100,
                        "Status bar must stretch, width was: " + status.getWidth());
                assertEquals("Ready", status.getText());

                // Split pane: neither side squished to zero. Below 980px the
                // split flips to VERTICAL, so minimums are asserted per axis.
                JSplitPane split = findFirst(frame, JSplitPane.class);
                assertNotNull(split, "Content split pane must exist");
                Component first = split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT
                        ? split.getLeftComponent() : split.getTopComponent();
                Component second = split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT
                        ? split.getRightComponent() : split.getBottomComponent();
                assertTrue(first.isShowing() && second.isShowing(),
                        "Both split panes must be showing");
                int loc = split.getDividerLocation();
                if (split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT) {
                    int firstMin = first.getMinimumSize().width;
                    int secondMin = second.getMinimumSize().width;
                    assertTrue(first.getWidth() > 0, "First pane squished to zero width");
                    assertTrue(second.getWidth() > 0, "Second pane squished to zero width");
                    assertTrue(loc + 2 >= firstMin,
                            "Divider (" + loc + ") violates first minimum (" + firstMin + ")");
                    assertTrue(loc + split.getDividerSize() + secondMin - 2 <= split.getWidth(),
                            "Divider (" + loc + ") violates second minimum (" + secondMin + ")");
                } else {
                    int firstMin = first.getMinimumSize().height;
                    int secondMin = second.getMinimumSize().height;
                    assertTrue(first.getHeight() > 0, "Top pane squished to zero height");
                    assertTrue(second.getHeight() > 0, "Bottom pane squished to zero height");
                    assertTrue(loc + 2 >= firstMin,
                            "Divider (" + loc + ") violates top minimum (" + firstMin + ")");
                    assertTrue(loc + split.getDividerSize() + secondMin - 2 <= split.getHeight(),
                            "Divider (" + loc + ") violates bottom minimum (" + secondMin + ")");
                }

                // Critical operator controls keep usable heights.
                for (String name : new String[]{
                        "btnStart", "btnStop", "btnSave", "btnLoad", "btnExport"}) {
                    Component button = findByName(frame, name);
                    assertNotNull(button, "Button '" + name + "' must exist");
                    assertTrue(button.isShowing(), "Button '" + name + "' must be showing");
                    assertTrue(button.getHeight() >= 28,
                            "Button '" + name + "' collapsed to " + button.getHeight() + "px");
                }
                Component speed = findByName(frame, "comboSpeed");
                assertNotNull(speed, "Speed combo must exist");
                assertTrue(speed.isShowing(), "Speed combo must be showing");
                assertTrue(speed.getHeight() > 0, "Speed combo collapsed to zero height");
            });
        } finally {
            onEdt(() -> {
                ReactorDashboard frame = frameRef.get();
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static <T extends Component> T findFirst(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container container) {
                T found = findFirst(container, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
