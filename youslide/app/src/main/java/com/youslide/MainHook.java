package com.youslide;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
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
    private static File logFile;

    private static void logToFile(String msg) {
        try {
            if (logFile == null) {
                File dir = new File(Environment.getExternalStorageDirectory(), "YouSlide");
                dir.mkdirs();
                logFile = new File(dir, "youslide.log");
            }
            String ts = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date());
            FileWriter fw = new FileWriter(logFile, true);
            fw.write("[" + ts + "] " + msg + "\n");
            fw.close();
        } catch (Throwable ignored) {}
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!YOUTUBE_PKG.equals(lpparam.packageName)) return;

        Log.e(TAG, "========================================");
        Log.e(TAG, "YouSlide: Loaded into YouTube process!");
        Log.e(TAG, "========================================");
        logToFile("YouSlide v1.0.3 loaded into YouTube process");

        // Hook Application.onCreate - guaranteed to run
        try {
            XposedHelpers.findAndHookMethod(
                Application.class,
                "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Application app = (Application) param.thisObject;
                        if (!YOUTUBE_PKG.equals(app.getPackageName())) return;
                        Log.e(TAG, "YouSlide: Application.onCreate() called");
                        logToFile("Application.onCreate() hooked");

                        // Register ActivityLifecycleCallbacks to catch all activities
                        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                            private int activityCount = 0;
                            @Override
                            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                                String name = activity.getClass().getSimpleName();
                                activityCount++;
                                Log.i(TAG, "Activity #" + activityCount + " created: " + name);
                                logToFile("Activity: " + name);

                                // Show toast on first main activity (not splash)
                                if (!toastShown && !name.contains("Splash") && !name.contains("Launch")) {
                                    toastShown = true;
                                    try {
                                        Toast.makeText(activity, "YouSlide ✓ 已注入", Toast.LENGTH_LONG).show();
                                    } catch (Throwable t) {
                                        Log.w(TAG, "Toast failed: " + t.getMessage());
                                    }
                                }
                            }
                            @Override public void onActivityStarted(Activity a) {}
                            @Override public void onActivityResumed(Activity a) {}
                            @Override public void onActivityPaused(Activity a) {}
                            @Override public void onActivityStopped(Activity a) {}
                            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
                            @Override public void onActivityDestroyed(Activity a) {}
                        });
                    }
                }
            );
            Log.e(TAG, "YouSlide: Application.onCreate hook installed");
            logToFile("Application.onCreate hook installed");
        } catch (Throwable t) {
            Log.e(TAG, "YouSlide: Failed to hook Application.onCreate", t);
            logToFile("FAILED to hook Application.onCreate: " + t.getMessage());
        }

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

                            Preference existing = screen.findPreference("youslide_status");
                            if (existing != null) return;

                            PreferenceCategory cat = new PreferenceCategory(activity);
                            cat.setTitle("YouSlide");
                            cat.setKey("youslide_category");
                            screen.addPreference(cat);

                            Preference status = new Preference(activity);
                            status.setKey("youslide_status");
                            status.setTitle("模块状态");
                            status.setSummary("已激活 | v1.0.3");
                            status.setEnabled(false);
                            cat.addPreference(status);

                            Log.e(TAG, "YouSlide: Settings entry added!");
                            logToFile("Settings entry added");
                        } catch (Throwable t) {
                            Log.w(TAG, "Settings add failed: " + t.getMessage());
                        }
                    }
                }
            );
            Log.e(TAG, "YouSlide: Settings hook installed");
            logToFile("Settings hook installed");
        } catch (Throwable t) {
            Log.w(TAG, "Settings hook setup failed: " + t.getMessage());
            logToFile("Settings hook FAILED: " + t.getMessage());
        }

        // ---- Gesture: Hook ViewGroup.dispatchTouchEvent ----
        try {
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
                            Log.i(TAG, "Handler -> " + activity.getClass().getSimpleName());
                        }
                        boolean consumed = handler.onTouch(null, event);
                        if (consumed) {
                            param.setResult(true);
                        }
                    }
                }
            );
            Log.e(TAG, "YouSlide: Gesture hook installed");
            logToFile("Gesture hook installed");
        } catch (Throwable t) {
            Log.e(TAG, "YouSlide: Gesture hook FAILED", t);
            logToFile("Gesture hook FAILED: " + t.getMessage());
        }

        Log.e(TAG, "YouSlide: All hooks setup complete");
        logToFile("=== Setup complete ===");
    }
}