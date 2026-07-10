package com.youslide;

import android.app.Activity;
import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "YouSlide";
    private static final String YOUTUBE_PKG = "com.google.android.youtube";

    private final WeakHashMap<Activity, GestureHandler> handlers = new WeakHashMap<>();
    private boolean toastShown = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!YOUTUBE_PKG.equals(lpparam.packageName)) return;

        Log.i(TAG, "YouSlide: YouTube detected, hooking...");

        // ---- Indicator: Show toast on first YouTube activity ----
        XposedHelpers.findAndHookMethod(
            Activity.class,
            "onResume",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (toastShown) return;
                    Activity activity = (Activity) param.thisObject;
                    if (!activity.getClass().getName().startsWith(YOUTUBE_PKG)) return;
                    toastShown = true;
                    Toast.makeText(activity, "YouSlide v1.0.3 | 已注入 ✓", Toast.LENGTH_LONG).show();
                    Log.i(TAG, "YouSlide: Injection confirmed - toast shown");
                }
            }
        );

        // ---- Settings: Add YouSlide entry to YouTube settings ----
        try {
            XposedHelpers.findAndHookMethod(
                "android.preference.PreferenceFragment",
                lpparam.classLoader,
                "onCreate",
                android.os.Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Object fragment = param.thisObject;
                            PreferenceScreen screen = (PreferenceScreen) XposedHelpers.callMethod(fragment, "getPreferenceScreen");
                            if (screen == null) return;

                            Activity activity = (Activity) XposedHelpers.callMethod(fragment, "getActivity");
                            if (activity == null) return;
                            if (!activity.getClass().getName().startsWith(YOUTUBE_PKG)) return;

                            // Check if already added
                            Preference existing = screen.findPreference("youslide_status");
                            if (existing != null) return;

                            PreferenceCategory cat = new PreferenceCategory(activity);
                            cat.setTitle("YouSlide");
                            cat.setKey("youslide_category");
                            screen.addPreference(cat);

                            Preference status = new Preference(activity);
                            status.setKey("youslide_status");
                            status.setTitle("模块状态");
                            status.setSummary("✓ 已激活 | v1.0.3");
                            status.setEnabled(false);
                            cat.addPreference(status);

                            Log.i(TAG, "YouSlide: Settings entry added");
                        } catch (Throwable t) {
                            Log.w(TAG, "YouSlide: Settings hook failed: " + t.getMessage());
                        }
                    }
                }
            );
        } catch (Throwable t) {
            Log.w(TAG, "YouSlide: Settings hook setup failed: " + t.getMessage());
        }

        // ---- Gesture: Hook ViewGroup.dispatchTouchEvent ----
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
                    if (!activity.getClass().getName().startsWith(YOUTUBE_PKG)) return;

                    MotionEvent event = (MotionEvent) param.args[0];

                    GestureHandler handler = handlers.get(activity);
                    if (handler == null) {
                        handler = new GestureHandler(activity);
                        handlers.put(activity, handler);
                        Log.i(TAG, "YouSlide: Handler -> " + activity.getClass().getSimpleName());
                    }

                    boolean consumed = handler.onTouch(null, event);
                    if (consumed) {
                        param.setResult(true);
                    }
                }
            }
        );

        Log.i(TAG, "YouSlide: All hooks installed");
    }
}