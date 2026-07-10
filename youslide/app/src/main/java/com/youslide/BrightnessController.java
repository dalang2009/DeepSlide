package com.youslide;

import android.app.Activity;
import android.provider.Settings;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

public class BrightnessController {

    private static final String TAG = "YouSlide";
    private static final float BRIGHTNESS_SENSITIVITY = 2.0f; // Full screen swipe = 200% change

    private final Activity activity;
    private float baseBrightness = -1f;

    public BrightnessController(Activity activity) {
        this.activity = activity;
    }

    /**
     * Adjust brightness based on vertical swipe delta.
     * Swiping up (positive deltaY) increases brightness.
     *
     * @param deltaY      Vertical movement since last event (positive = up/swipe up)
     * @param screenHeight Screen height for scaling
     */
    public void adjustBrightness(float deltaY, int screenHeight) {
        try {
            Window window = activity.getWindow();
            WindowManager.LayoutParams attrs = window.getAttributes();

            // Lazy-init: read current brightness on first adjustment
            if (baseBrightness < 0) {
                baseBrightness = attrs.screenBrightness;
                if (baseBrightness < 0) {
                    // Window brightness not set, read system brightness
                    try {
                        int sysBrightness = Settings.System.getInt(
                            activity.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS);
                        baseBrightness = sysBrightness / 255f;
                    } catch (Exception e) {
                        baseBrightness = 0.5f; // Default
                    }
                }
            }

            // Calculate new brightness
            float change = (deltaY / screenHeight) * BRIGHTNESS_SENSITIVITY;
            float newBrightness = baseBrightness + change;

            // Clamp to valid range [0.01, 1.0] — 0 would turn screen off
            newBrightness = Math.max(0.01f, Math.min(1.0f, newBrightness));

            attrs.screenBrightness = newBrightness;
            window.setAttributes(attrs);
        } catch (Exception e) {
            Log.w(TAG, "YouSlide: Failed to adjust brightness: " + e.getMessage());
        }
    }
}
