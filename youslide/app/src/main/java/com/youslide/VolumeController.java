package com.youslide;

import android.media.AudioManager;
import android.util.Log;

public class VolumeController {

    private static final String TAG = "YouSlide";
    private static final float VOLUME_SENSITIVITY = 1.5f; // Determines how much one screen-height swipe changes volume

    private final AudioManager audioManager;
    private final int maxVolume;
    private int baseVolume = -1;

    public VolumeController(AudioManager audioManager) {
        this.audioManager = audioManager;
        this.maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    /**
     * Adjust media volume based on vertical swipe delta.
     * Swiping up (positive deltaY) increases volume.
     *
     * @param deltaY       Vertical movement since last event (positive = swipe up)
     * @param screenHeight Screen height for scaling
     */
    public void adjustVolume(float deltaY, int screenHeight) {
        try {
            // Lazy-init base volume on first call
            if (baseVolume < 0) {
                baseVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            }

            // Calculate volume steps
            float changeFraction = (deltaY / screenHeight) * VOLUME_SENSITIVITY;
            int steps = Math.round(changeFraction * maxVolume);

            if (steps == 0) return;

            // Calculate target volume
            int targetVolume = baseVolume + steps;
            targetVolume = Math.max(0, Math.min(maxVolume, targetVolume));

            // Apply directly using setStreamVolume
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                targetVolume,
                AudioManager.FLAG_SHOW_UI // Show system volume UI
            );
        } catch (Exception e) {
            Log.w(TAG, "YouSlide: Failed to adjust volume: " + e.getMessage());
        }
    }
}
