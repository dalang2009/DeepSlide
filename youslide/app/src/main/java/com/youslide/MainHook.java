package com.youslide;

import android.app.Activity;
import android.util.Log;
import android.view.MotionEvent;

import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "YouSlide";
    private static final String YOUTUBE_PKG = "com.google.android.youtube";

    private final WeakHashMap<Activity, GestureHandler> handlers = new WeakHashMap<>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!YOUTUBE_PKG.equals(lpparam.packageName)) return;

        Log.i(TAG, "YouSlide: YouTube detected, hooking...");

        XposedHelpers.findAndHookMethod(
            Activity.class,
            "dispatchTouchEvent",
            MotionEvent.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Activity activity = (Activity) param.thisObject;
                    String className = activity.getClass().getName();

                    if (!className.startsWith("com.google.android.youtube")) return;

                    MotionEvent event = (MotionEvent) param.args[0];

                    GestureHandler handler = handlers.get(activity);
                    if (handler == null) {
                        handler = new GestureHandler(activity);
                        handlers.put(activity, handler);
                        Log.i(TAG, "YouSlide: Gesture handler created for " + className);
                    }

                    boolean justStarted = !handler.isGestureActive();
                    boolean consumed = handler.onTouch(null, event);

                    if (consumed) {
                        if (justStarted) {
                            // Cancel YouTube's own gesture by injecting ACTION_CANCEL
                            MotionEvent cancel = MotionEvent.obtain(event);
                            cancel.setAction(MotionEvent.ACTION_CANCEL);
                            param.args[0] = cancel;
                            // Let original method dispatch the cancel, then consume
                            param.setResult(null);
                            // Schedule to re-intercept: let cancel through, then block next
                        } else {
                            // Block event from reaching YouTube
                            param.setResult(true);
                        }
                    }
                }
            }
        );

        Log.i(TAG, "YouSlide: Hook installed");
    }
}