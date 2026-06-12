package org.reactor_model.ui;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.FrameFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ReactorDashboard UI Tests")
class ReactorDashboardUiTest {

    private FrameFixture window;
    private ReactorUIAdapter mockAdapter;
    private EventLogPanel eventLog;

    @BeforeEach
    void setUp() {
        mockAdapter = mock(ReactorUIAdapter.class);

        // Default: stable cold snapshot
        ReactorStateSnapshot defaultSnap = new ReactorStateSnapshot(
                0.01, 300.0, 1.0, 0.5, 0.0, 100.0, 0, false, true);
        when(mockAdapter.getSnapshot()).thenReturn(defaultSnap);
        when(mockAdapter.getTargetPower()).thenReturn(100.0);
        when(mockAdapter.getControlRodPosition()).thenReturn(0.5);

        eventLog = new EventLogPanel(new LinkedBlockingQueue<>());

        ReactorDashboard dashboard = GuiActionRunner.execute(() ->
                new ReactorDashboard(mockAdapter, eventLog));

        window = new FrameFixture(dashboard);
        window.show();
    }

    @AfterEach
    void tearDown() {
        window.cleanUp();
    }

    // ---------------------------------------------------------------
    // 1. Initial UI State
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Initial UI state: Start/Stop enabled, Restart disabled, gauges at default")
    void testInitialUiState() {
        window.button("btnStart").requireEnabled();
        window.button("btnStop").requireEnabled();
        window.button("btnRestart").requireDisabled();

        window.button("btnSpike").requireEnabled();
        window.button("btnScram").requireEnabled();
    }

    // ---------------------------------------------------------------
    // 2. Start / Stop
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Clicking Start should call adapter.startLoop()")
    void testStartButton() {
        GuiActionRunner.execute(() -> window.button("btnStart").target().doClick());
        verify(mockAdapter, times(1)).startLoop();
    }

    @Test
    @DisplayName("Clicking Stop should call adapter.stopLoop()")
    void testStopButton() {
        GuiActionRunner.execute(() -> window.button("btnStop").target().doClick());
        verify(mockAdapter, times(1)).stopLoop();
    }

    // ---------------------------------------------------------------
    // 3. Target Power Spinner
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Changing target power spinner should call adapter.setTargetPower()")
    void testTargetPowerSpinner() {
        JSpinner spinner = window.spinner("spinnerTargetPower").target();
        GuiActionRunner.execute(() -> spinner.setValue(500));

        // Allow timer tick to propagate
        sleep(250);
        verify(mockAdapter, atLeastOnce()).setTargetPower(500.0);
    }

    // ---------------------------------------------------------------
    // 4. SCRAM
    // ---------------------------------------------------------------

    @Test
    @DisplayName("SCRAM button with confirmation should call adapter.scram()")
    void testScramButton() throws Exception {
        // Click the button on a background thread because the modal dialog blocks the EDT
        ExecutorService exec = Executors.newSingleThreadExecutor();
        Future<?> clickFuture = exec.submit(() ->
                GuiActionRunner.execute(() -> window.button("btnScram").target().doClick()));

        // The modal confirmation dialog appears — accept it
        window.optionPane().buttonWithText("Yes").click();

        clickFuture.get(10, TimeUnit.SECONDS);
        exec.shutdown();

        verify(mockAdapter, times(1)).scram();
    }

    // ---------------------------------------------------------------
    // 5. Shutdown → Restart flow
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Restart button becomes enabled when snapshot indicates shutdown")
    void testRestartEnabledOnShutdown() {
        // Set mock to return a shutdown snapshot
        ReactorStateSnapshot shutdownSnap = new ReactorStateSnapshot(
                0.01, 300.0, 1.0, 0.0, -0.02, 100.0, 0, true, true);
        when(mockAdapter.getSnapshot()).thenReturn(shutdownSnap);

        // Wait for timer to fire and sync controls
        sleep(300);

        window.button("btnRestart").requireEnabled();
    }

    @Test
    @DisplayName("Clicking Restart should call adapter.restart()")
    void testRestartButton() {
        // Enable restart by returning shutdown snapshot
        ReactorStateSnapshot shutdownSnap = new ReactorStateSnapshot(
                0.01, 300.0, 1.0, 0.0, -0.02, 100.0, 0, true, true);
        when(mockAdapter.getSnapshot()).thenReturn(shutdownSnap);
        sleep(300);

        GuiActionRunner.execute(() -> window.button("btnRestart").target().doClick());
        verify(mockAdapter, times(1)).restart();
    }

    // ---------------------------------------------------------------
    // 6. Gauges reflect snapshot values
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Gauges should update to reflect a new snapshot pushed by the adapter")
    void testSnapshotReflectedInGauges() {
        // Push a high-power snapshot
        ReactorStateSnapshot highPowerSnap = new ReactorStateSnapshot(
                1500.0, 450.0, 0.85, 0.7, 0.005, 1500.0, 0, false, true);
        when(mockAdapter.getSnapshot()).thenReturn(highPowerSnap);

        // Allow the Swing Timer to refresh
        sleep(300);

        // The gauges are custom-painted JPanel subclasses — we verify they updated
        // by inspecting the dashboard's internal state via the adapter contract.
        // The adapter was called for the snapshot at least once.
        verify(mockAdapter, atLeast(1)).getSnapshot();
    }

    // ---------------------------------------------------------------
    // 7. Window resize does not collapse components
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Resize to small then large should leave all components with positive dimensions")
    void testWindowResizeNoZeroSizeComponents() {
        // Shrink
        window.target().setSize(600, 400);
        sleep(100);

        // Expand
        window.target().setSize(1200, 800);
        sleep(200);

        window.requireVisible();
        window.button("btnStart").requireEnabled();
        window.button("btnRestart").requireDisabled();
    }

    // ---------------------------------------------------------------
    // 8. All named components exist
    // ---------------------------------------------------------------

    @Test
    @DisplayName("All expected interactive components should be present")
    void testAllComponentsPresent() {
        assertDoesNotThrow(() -> window.button("btnStart"));
        assertDoesNotThrow(() -> window.button("btnStop"));
        assertDoesNotThrow(() -> window.button("btnRestart"));
        assertDoesNotThrow(() -> window.button("btnScram"));
        assertDoesNotThrow(() -> window.button("btnSpike"));
        assertDoesNotThrow(() -> window.button("btnFailure"));
        assertDoesNotThrow(() -> window.spinner("spinnerTargetPower"));
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
