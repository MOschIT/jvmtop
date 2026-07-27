package com.jvmtop.tui;

import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.Size;

/**
 * Delegate element that renders the current view based on the app's state.
 * Since the state manager's data is updated atomically, this element
 * will always render the latest data.
 */
class CenterDelegateElement implements Element {

    private final JvmTopElement parent;

    CenterDelegateElement(JvmTopElement parent) {
        this.parent = parent;
    }

    @Override
    public void render(Frame frame, Rect area, dev.tamboui.toolkit.element.RenderContext context) {
        Element view = parent.renderCurrentView();
        if (view != null) {
            view.render(frame, area, context);
        }
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight,
                              dev.tamboui.toolkit.element.RenderContext context) {
        return Size.UNKNOWN;
    }
}
