package com.youslide;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

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

        // Hook ViewGroup.dispatchTouchEvent to intercept ALL touches including SurfaceView
        XposedHelpers.findAndHookMethod(
            ViewGroup.class,
            "dispatchTouchEvent",
            MotionEvent.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    ViewGroup view = (ViewGroup) param.thisObject;
                    Context context = view.getContext();
                    if (!(context instanceof Activity)) return;

                    Activity activity = (Activity) context;
                    if (!activity.getClass().getName().startsWith("com.google.android.youtube")) return;

                    MotionEvent event = (MotionEvent) param.args[0];

                    GestureHandler handler = handlers.get(activity);
                    if (handler == null) {
                        handler = new GestureHandler(activity);
                        handlers.put(activity, handler);
                        Log.i(TAG, "YouSlide: Handler attached to " + activity.getClass().getSimpleName());
                    }

                    boolean consumed = handler.onTouch(null, event);
                    if (consumed) {
                        param.setResult(true);
                    }
                }
            }
        );

        Log.i(TAG, "YouSlide: Hook installed at ViewGroup level");
    }
}