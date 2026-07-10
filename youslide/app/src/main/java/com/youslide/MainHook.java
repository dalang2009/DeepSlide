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
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "YouSlide";
    // AndroidManifest package name (app ID)
    private static final String YOUTUBE_PKG = "com.google.android.youtube";
    // YouTube Java class prefix (actual class names use "apps" sub-package!)
    private static final String YOUTUBE_CLS = "com.google.android.apps.youtube";

    private final WeakHashMap<Activity, GestureHandler> handlers = new WeakHashMap<>();
    private boolean initDone = false;

    private static void log(String msg) {
        Log.e(TAG, msg);
        try {
            File f = new File("/data/local/tmp/youslide.log");
            f.getParentFile().mkdirs();
            String ts = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            FileOutputStream fos = new FileOutputStream(f, true);
            fos.write(("[" + ts + "] " + msg + "\n").getBytes("UTF-8"));
            fos.close();
        } catch (Throwable ignored) {}
    }

    private static boolean isYouTubeActivity(Activity a) {
        String name = a.getClass().getName();
        return name.startsWith(YOUTUBE_CLS) || name.startsWith(YOUTUBE_PKG);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        log("handleLoadPackage: " + lpparam.packageName);

        if (!YOUTUBE_PKG.equals(lpparam.packageName)) return;

        log("=== YOUTUBE MATCHED ===");
        if (initDone) { log("Already init"); return; }
        initDone = true;

        // ---- Application.onCreate hook ----
        try {
            XposedHelpers.findAndHookMethod(
                Application.class, "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Application app = (Application) param.thisObject;
                        if (!YOUTUBE_PKG.equals(app.getPackageName())) return;
                        log("App.onCreate - registering callbacks");

                        Toast.makeText(app, "YouSlide 已注入 ✓", Toast.LENGTH_LONG).show();

                        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                            @Override
                            public void onActivityCreated(Activity a, Bundle b) {
                                if (!isYouTubeActivity(a)) return;
                                log("Activity: " + a.getClass().getSimpleName());
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
            log("App hook OK");
        } catch (Throwable t) {
            log("App hook FAIL: " + t.getMessage());
        }

        // ---- Gesture hook on ViewGroup.dispatchTouchEvent ----
        try {
            XposedHelpers.findAndHookMethod(
                ViewGroup.class, "dispatchTouchEvent", MotionEvent.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        ViewGroup view = (ViewGroup) param.thisObject;
                        Context ctx = view.getContext();
                        if (!(ctx instanceof Activity)) return;
                        Activity a = (Activity) ctx;
                        if (!isYouTubeActivity(a)) return;

                        MotionEvent ev = (MotionEvent) param.args[0];
                        GestureHandler h = handlers.get(a);
                        if (h == null) {
                            h = new GestureHandler(a);
                            handlers.put(a, h);
                            log("Gesture -> " + a.getClass().getSimpleName());
                        }
                        if (h.onTouch(null, ev)) param.setResult(true);
                    }
                }
            );
            log("Gesture hook OK");
        } catch (Throwable t) {
            log("Gesture hook FAIL: " + t.getMessage());
        }

        log("=== Setup done ===");
    }
}