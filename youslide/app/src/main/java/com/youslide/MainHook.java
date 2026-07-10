package com.youslide;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "YouSlide";
    private static final String YOUTUBE_PKG = "com.google.android.youtube";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!YOUTUBE_PKG.equals(lpparam.packageName)) return;

        Log.i(TAG, "YouSlide: YouTube detected, hooking...");

        // Hook Activity.onCreate to intercept player activities
        XposedHelpers.findAndHookMethod(
            Activity.class,
            "onCreate",
            Bundle.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Activity activity = (Activity) param.thisObject;
                    String className = activity.getClass().getName();

                    // Only hook YouTube'`s own activities
                    if (!className.startsWith("com.google.android.youtube")) return;

                    // Attach a global layout listener to find the player view
                    View rootView = activity.getWindow().getDecorView().getRootView();
                    rootView.getViewTreeObserver().addOnGlobalLayoutListener(
                        new ViewTreeObserver.OnGlobalLayoutListener() {
                            private boolean attached = false;

                            @Override
                            public void onGlobalLayout() {
                                if (attached) return;

                                View playerView = findPlayerView(rootView);
                                if (playerView != null) {
                                    attached = true;
                                    Log.i(TAG, "YouSlide: Player view found in " + className);
                                    attachGestureHandler(activity, playerView);
                                }
                            }
                        }
                    );
                }
            }
        );
    }

    /**
     * Traverse the view tree to find the YouTube player container.
     * YouTube player is typically a large sub-view at/near the top of the layout.
     */
    private View findPlayerView(View root) {
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        int screenWidth = root.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = root.getResources().getDisplayMetrics().heightPixels;

        // Look for a view that is at least 70% of screen width and is a ViewGroup
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child == null) continue;

            if (child instanceof ViewGroup
                && child.getWidth() >= screenWidth * 0.7
                && child.getHeight() >= screenHeight * 0.25) {

                String name = child.getClass().getName();
                // YouTube player views often contain these class names
                if (name.contains("Player") || name.contains("Watch")
                    || name.contains("Surface") || name.contains("Video")
                    || name.contains("Main") || name.contains("player")) {
                    return child;
                }

                // Recurse one level deeper
                View deeper = findPlayerView(child);
                if (deeper != null) return deeper;
            }
        }

        // Fallback: if we couldn't find by name, take the largest child
        View largest = null;
        int maxArea = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child == null) continue;
            int area = child.getWidth() * child.getHeight();
            if (area > maxArea && area >= screenWidth * screenHeight * 0.25) {
                maxArea = area;
                largest = child;
            }
        }

        if (largest instanceof ViewGroup && maxArea > 0) {
            View deeper = findPlayerView(largest);
            if (deeper != null) return deeper;
        }

        return largest instanceof ViewGroup ? largest : null;
    }

    /**
     * Attach our gesture handler to intercept touches on the player view.
     */
    private void attachGestureHandler(Activity activity, View playerView) {
        GestureHandler handler = new GestureHandler(activity);

        playerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                boolean handled = handler.onTouch(v, event);
                // Return false to let YouTube handle the event normally if we did not consume it
                return handled;
            }
        });

        Log.i(TAG, "YouSlide: Gesture handler attached");
    }
}
