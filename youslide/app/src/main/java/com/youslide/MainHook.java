package com.youslide;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
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
    private boolean initDone = false;

    private static void writeLog(String msg) {
        try {
            Log.e("YouSlide", msg);
            // Try multiple locations
            String[] paths = {
                "/data/local/tmp/youslide.log",
                "/sdcard/YouSlide/youslide.log",
                "/storage/emulated/0/YouSlide/youslide.log"
            };
            String ts = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            String line = "[" + ts + "] " + msg + "\n";
            for (String p : paths) {
                try {
                    File f = new File(p);
                    f.getParentFile().mkdirs();
                    FileOutputStream fos = new FileOutputStream(f, true);
                    fos.write(line.getBytes("UTF-8"));
                    fos.close();
                    break;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // Log ALL package loads so we can see what'`s happening
        writeLog("handleLoadPackage: " + lpparam.packageName + " | process=" + lpparam.processName);

        if (!YOUTUBE_PKG.equals(lpparam.packageName)) return;

        writeLog("=== YOUTUBE MATCHED! Loading hooks... ===");

        if (initDone) {
            writeLog("Already initialized, skipping");
            return;
        }
        initDone = true;

        // ---- Step 1: Hook Application.onCreate ----
        try {
            XposedHelpers.findAndHookMethod(
                Application.class,
                "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Application app = (Application) param.thisObject;
                        writeLog("Application.onCreate: " + app.getPackageName());

                        if (!YOUTUBE_PKG.equals(app.getPackageName())) return;

                        writeLog("=== YOUTUBE APP ONCREATE ===");

                        // Show toast
                        try {
                            Toast.makeText(app, "YouSlide 已注入", Toast.LENGTH_LONG).show();
                            writeLog("Toast shown");
                        } catch (Throwable t) {
                            writeLog("Toast FAILED: " + t.getMessage());
                        }

                        // Register activity callbacks
                        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                            @Override
                            public void onActivityCreated(Activity a, Bundle b) {
                                writeLog("Activity: " + a.getClass().getSimpleName());
                                a.getWindow().getDecorView().postDelayed(() -> {
                                    try {
                                        Toast.makeText(a, "YouSlide", Toast.LENGTH_SHORT).show();
                                    } catch (Throwable ignored) {}
                                }, 500);
                            }
                            @Override public void onActivityStarted(Activity a) {}
                            @Override public void onActivityResumed(Activity a) {
                                // Show toast on first resumed non-splash activity
                                if (a.getClass().getSimpleName().contains("Main") ||
                                    a.getClass().getSimpleName().contains("Home") ||
                                    a.getClass().getSimpleName().contains("Watch")) {
                                    writeLog("Resumed: " + a.getClass().getSimpleName());
                                }
                            }
                            @Override public void onActivityPaused(Activity a) {}
                            @Override public void onActivityStopped(Activity a) {}
                            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
                            @Override public void onActivityDestroyed(Activity a) {}
                        });
                        writeLog("Lifecycle callbacks registered");
                    }
                }
            );
            writeLog("Application.onCreate hook OK");
        } catch (Throwable t) {
            writeLog("Application hook FAILED: " + t.getMessage());
        }

        // ---- Step 2: Gesture hooks ----
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
                            writeLog("Handler attached: " + activity.getClass().getSimpleName());
                        }
                        boolean consumed = handler.onTouch(null, event);
                        if (consumed) {
                            param.setResult(true);
                        }
                    }
                }
            );
            writeLog("Gesture hook OK");
        } catch (Throwable t) {
            writeLog("Gesture hook FAILED: " + t.getMessage());
        }

        writeLog("=== Setup complete ===");
    }
}