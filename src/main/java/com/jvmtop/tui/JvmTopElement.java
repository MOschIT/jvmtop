package com.jvmtop.tui;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.elements.Row;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;

import static dev.tamboui.toolkit.Toolkit.*;

public class JvmTopElement implements Element {

    public static final String VERSION = "1.0.0-SNAPSHOT";

    private final JvmTopStateManager stateManager;
    private JvmTopView currentView = JvmTopView.OVERVIEW;
    private Integer selectedPid = null;

    public JvmTopElement(JvmTopStateManager stateManager, Integer targetPid, boolean profileMode) {
        this.stateManager = stateManager;
        this.selectedPid = targetPid;
        if (targetPid != null) {
            this.currentView = profileMode ? JvmTopView.PROFILE : JvmTopView.DETAIL;
        }
    }

    @Override
    public void render(Frame frame, Rect area, dev.tamboui.toolkit.element.RenderContext context) {
        String sortLabel = "[" + stateManager.getSortMode().name() + "]";
        String viewLabel = currentView.name();
        String pidLabel = selectedPid != null ? " PID:" + selectedPid : "";

        Element topPanel = panel(() -> row(
                text(" jvmtop " + VERSION).bold().fg(Color.CYAN),
                spacer(),
                text(sortLabel).dim(),
                text("  ").dim(),
                text(viewLabel).dim(),
                text("  ").dim(),
                text(pidLabel).dim()
        )).rounded().borderColor(Color.DARK_GRAY);

        Element bottomPanel = panel(() -> row(
                text(" [Up/Dn] Navigate ").dim(),
                text(" [Enter] Select ").dim(),
                text(" [o] Overview ").dim(),
                text(" [d] Detail ").dim(),
                text(" [f] Profile ").dim(),
                spacer(),
                text(" [s] Sort ").dim(),
                text(" [q] Quit ").dim()
        )).rounded().borderColor(Color.DARK_GRAY);

        dock()
                .top(topPanel)
                .center(new CenterDelegateElement(this))
                .bottom(bottomPanel)
                .render(frame, area, context);
    }

    Element renderCurrentView() {
        return switch (currentView) {
            case OVERVIEW -> stateManager.renderOverview();
            case DETAIL -> stateManager.renderDetail(selectedPid);
            case PROFILE -> stateManager.renderProfile(selectedPid);
        };
    }

    @Override
    public EventResult handleKeyEvent(KeyEvent event, boolean focused) {
        if (event.isQuit()) {
            System.exit(0);
            return EventResult.HANDLED;
        }

        if (event.isCharIgnoreCase('o')) {
            currentView = JvmTopView.OVERVIEW;
            return EventResult.HANDLED;
        }
        if (event.isCharIgnoreCase('d')) {
            if (selectedPid != null) {
                currentView = JvmTopView.DETAIL;
            }
            return EventResult.HANDLED;
        }
        if (event.isCharIgnoreCase('f')) {
            if (selectedPid != null) {
                currentView = JvmTopView.PROFILE;
            }
            return EventResult.HANDLED;
        }
        if (event.isCharIgnoreCase('s')) {
            stateManager.toggleSortMode();
            return EventResult.HANDLED;
        }

        if (currentView == JvmTopView.OVERVIEW) {
            return handleOverviewKeys(event);
        }

        return EventResult.UNHANDLED;
    }

    private EventResult handleOverviewKeys(KeyEvent event) {
        var rows = stateManager.getOverviewRows();
        if (rows.isEmpty()) {
            return EventResult.UNHANDLED;
        }

        int current = stateManager.getSelectedRowIndex();

        if (event.isDown()) {
            stateManager.setSelectedRowIndex(Math.min(current + 1, rows.size() - 1));
            return EventResult.HANDLED;
        }
        if (event.isUp()) {
            stateManager.setSelectedRowIndex(Math.max(current - 1, 0));
            return EventResult.HANDLED;
        }
        if (event.isPageDown()) {
            stateManager.setSelectedRowIndex(Math.min(current + 10, rows.size() - 1));
            return EventResult.HANDLED;
        }
        if (event.isPageUp()) {
            stateManager.setSelectedRowIndex(Math.max(current - 10, 0));
            return EventResult.HANDLED;
        }
        if (event.isHome()) {
            stateManager.setSelectedRowIndex(0);
            return EventResult.HANDLED;
        }
        if (event.isEnd()) {
            stateManager.setSelectedRowIndex(rows.size() - 1);
            return EventResult.HANDLED;
        }
        if (event.isSelect()) {
            selectCurrentVm();
            return EventResult.HANDLED;
        }

        return EventResult.UNHANDLED;
    }

    private void selectCurrentVm() {
        var rows = stateManager.getOverviewRows();
        int idx = stateManager.getSelectedRowIndex();
        if (idx >= 0 && idx < rows.size()) {
            selectedPid = rows.get(idx).pid();
            currentView = JvmTopView.DETAIL;
        }
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight,
                              dev.tamboui.toolkit.element.RenderContext context) {
        return Size.UNKNOWN;
    }

    public enum JvmTopView {
        OVERVIEW, DETAIL, PROFILE
    }
}
