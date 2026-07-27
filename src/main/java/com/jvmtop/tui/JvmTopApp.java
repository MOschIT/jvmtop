package com.jvmtop.tui;

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.tui.TuiConfig;

import com.jvmtop.JvmTop;

public class JvmTopApp {

    private static final Logger logger = JvmTop.getLogger();

    public static final Duration DEFAULT_UPDATE_INTERVAL = Duration.ofSeconds(1);

    private final JvmTopElement element;
    private final JvmTopStateManager stateManager;
    private final Duration updateInterval;

    private JvmTopApp(Integer targetPid, boolean profileMode, Duration updateInterval) {
        this.updateInterval = updateInterval;
        this.stateManager = new JvmTopStateManager();
        this.element = new JvmTopElement(stateManager, targetPid, profileMode);
    }

    public static void run(Integer targetPid, boolean profileMode, Duration updateInterval) throws Exception {
        var config = TuiConfig.builder()
                .mouseCapture(false)
                .tickRate(updateInterval)
                .build();

        var app = new JvmTopApp(targetPid, profileMode, updateInterval);
        var runner = ToolkitRunner.create(config);

        runner.scheduleWithFixedDelay(() -> {
            try {
                app.stateManager.update();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error during update cycle", e);
            }
        }, updateInterval);

        runner.run(() -> app.element);
    }
}
