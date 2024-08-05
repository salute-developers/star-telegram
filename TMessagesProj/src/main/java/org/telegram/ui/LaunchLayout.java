package org.telegram.ui;

import android.content.Context;
import android.widget.RelativeLayout;

public class LaunchLayout extends RelativeLayout {

    public LaunchLayout(Context context) {
        super(context);
    }

    private boolean fullscreen = false;

    public void setFullscreenMode(boolean fullscreen) {
        this.fullscreen = fullscreen;
        requestLayout();
    }

    public boolean isFullscreenMode() {
        return fullscreen;
    }
}
