package com.youslide;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.util.List;

public class GestureHandler {

    private static final String TAG = "YouSlide";
    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_SEEK = 1;
    private static final int GESTURE_BRIGHTNESS = 2;
    private static final int GESTURE_VOLUME = 3;

    private static final long DOUBLE_TAP_TIMEOUT = 350; // ms
    private static final float MIN_SWIPE_DISTANCE = 30f; // dp-ish px threshold

    private final Activity activity;
    private final AudioManager audioManager;
    private final int screenWidth;
    private final int screenHeight;
    private final BrightnessController brightnessController;
    private final VolumeController volumeController;

    private int gestureType = GESTURE_NONE;
    private float downX, downY;
    private float lastX, lastY;
    private boolean isTracking = false;

    // Double tap detection
    private long lastTapTime = 0;
    private float lastTapX, lastTapY;

    // Media controller for seeking
    private MediaController mediaController;

    public GestureHandler(Activity activity) {
        this.activity = activity;
        this.audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        this.screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        this.screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        this.brightnessController = new BrightnessController(activity);
        this.volumeController = new VolumeController(audioManager);

        initMediaController();
    }

    private void initMediaController() {
        try {
            MediaSessionManager sessionManager =
                (MediaSessionManager) activity.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (sessionManager == null) return;

            List<MediaController> controllers = sessionManager.getActiveSessions(null);
            for (MediaController c : controllers) {
                if (c.getPackageName().equals("com.google.android.youtube")) {
                    mediaController = c;
                    break;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "YouSlide: Cannot get MediaController: " + e.getMessage());
        }
    }

    public boolean onTouch(View v, MotionEvent event) {
        float x = event.getRawX();
        float y = event.getRawY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                lastX = x;
                lastY = y;
                gestureType = GESTURE_NONE;
                isTracking = true;
                return false; // Let YouTube also see the down event

            case MotionEvent.ACTION_MOVE:
                if (!isTracking) return false;

                float dx = x - downX;
                float dy = y - downY;
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);

                // Determine gesture type on first significant movement
                if (gestureType == GESTURE_NONE && (absDx > MIN_SWIPE_DISTANCE || absDy > MIN_SWIPE_DISTANCE)) {
                    if (absDx >= absDy) {
                        gestureType = GESTURE_SEEK;
                    } else {
                        // Vertical swipe: left half = brightness, right half = volume
                        if (downX < screenWidth / 2) {
                            gestureType = GESTURE_BRIGHTNESS;
                        } else {
                            gestureType = GESTURE_VOLUME;
                        }
                    }
                }

                // Handle gesture
                float delta;
                switch (gestureType) {
                    case GESTURE_SEEK:
                        delta = x - lastX;
                        handleSeek(delta);
                        break;
                    case GESTURE_BRIGHTNESS:
                        delta = lastY - y; // swipe up = brighter
                        handleBrightness(delta);
                        break;
                    case GESTURE_VOLUME:
                        delta = lastY - y; // swipe up = louder
                        handleVolume(delta);
                        break;
                }

                lastX = x;
                lastY = y;

                // If we are in an active gesture, consume the event
                return gestureType != GESTURE_NONE;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isTracking = false;

                // Double tap detection: short tap without significant movement
                float totalDx = Math.abs(x - downX);
                float totalDy = Math.abs(y - downY);
                if (gestureType == GESTURE_NONE && totalDx < MIN_SWIPE_DISTANCE && totalDy < MIN_SWIPE_DISTANCE) {
                    long now = System.currentTimeMillis();
                    double tapDist = Math.hypot(x - lastTapX, y - lastTapY);
                    if (now - lastTapTime < DOUBLE_TAP_TIMEOUT && tapDist < 100) {
                        // Double tap detected → toggle play/pause
                        handleDoubleTap();
                        lastTapTime = 0;
                        gestureType = GESTURE_NONE;
                        return true;
                    }
                    lastTapTime = now;
                    lastTapX = x;
                    lastTapY = y;
                }

                gestureType = GESTURE_NONE;
                return false;
        }

        return false;
    }

    private void handleSeek(float deltaX) {
        // Convert horizontal swipe to seek amount
        // 1 screen width ≈ seek 10% of the video (adjustable sensitivity)
        float seekPercent = (deltaX / screenWidth) * 15f; // 15% seek per full screen width

        if (mediaController != null && mediaController.getPlaybackState() != null) {
            PlaybackState state = mediaController.getPlaybackState();
            long pos = state.getPosition();
            // Estimate total duration from state (YouTube may not expose this)
            // Use a reasonable default if we can't get it
            long duration = getDuration();
            if (duration > 0) {
                long newPos = pos + (long) (duration * seekPercent / 100f);
                newPos = Math.max(0, Math.min(duration, newPos));
                mediaController.getTransportControls().seekTo(newPos);
                return;
            }
        }

        // Fallback: use key events for coarse seeking
        if (Math.abs(deltaX) > MIN_SWIPE_DISTANCE * 5) {
            int keyCode = deltaX > 0 ? KeyEvent.KEYCODE_MEDIA_FAST_FORWARD : KeyEvent.KEYCODE_MEDIA_REWIND;
            dispatchMediaKey(keyCode);
        }
    }

    private long getDuration() {
        if (mediaController != null && mediaController.getMetadata() != null) {
            return mediaController.getMetadata().getLong(
                android.media.MediaMetadata.METADATA_KEY_DURATION);
        }
        return -1;
    }

    private void handleBrightness(float deltaY) {
        brightnessController.adjustBrightness(deltaY, screenHeight);
    }

    private void handleVolume(float deltaY) {
        volumeController.adjustVolume(deltaY, screenHeight);
    }

    private void handleDoubleTap() {
        Log.i(TAG, "YouSlide: Double tap → toggle play/pause");
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    private void dispatchMediaKey(int keyCode) {
        KeyEvent downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        KeyEvent upEvent = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
        activity.dispatchKeyEvent(downEvent);
        activity.dispatchKeyEvent(upEvent);
    }
}
