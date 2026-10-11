package com.bgmi;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.util.Log;

import com.sbox.SboxCore;
import com.sbox.app.configuration.AppLifecycleCallback;
import com.sbox.app.configuration.ClientConfiguration;
import com.sbox.core.system.api.BoxActivate;
import java.io.File;
import com.bgmi.utils.Prefs;  // Changed import
public class BoxApplication extends Application {

static {
        System.loadLibrary("PRIVATE_SRC_FILES"); 
    }
    public static native String getSdkKey();
    private static final String TAG = "BoxApplication";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Log.d(TAG, "BoxApplication attachBaseContext started");
        Prefs prefs = new Prefs(base);  // Changed from FPrefs.with(base) to new Prefs(base)
        try {
            Log.d(TAG, "Initializing SboxCore...");
            SboxCore.get().doAttachBaseContext(base, new ClientConfiguration() {
                @Override
                public String getHostPackageName() {
                    return base.getPackageName();
                }

               @Override
                public boolean isHideRoot() {
                    return true;
                }

               @Override
                public boolean isHideXposed() {
                    return true;
                }

                @Override
                public boolean isEnableDaemonService() {
                    return false;
                }

               @Override
                public boolean requestInstallPackage(File file){
                    PackageInfo packageInfo = base.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),0);
                    return false;
                }
            });
            Log.d(TAG, "SboxCore initialization completed");
            checkAppName(base); // Verify the app's name hasn't been tampered
            Log.d(TAG, "App name check completed");
        } catch (Exception e) {
            Log.e(TAG, "Error in attachBaseContext", e);
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        SboxCore.get().doCreate();
        BoxActivate.activateBox(getSdkKey());
        SboxCore.get().addAppLifecycleCallback(new AppLifecycleCallback() {
            @Override
            public void beforeCreateApplication(String packageName, String processName, Context context, int userId) {
                // Handle before application creation
            }

            @Override
            public void beforeApplicationOnCreate(String packageName, String processName, Application application, int userId) {
                // Handle before application onCreate
            }

            @Override
            public void afterApplicationOnCreate(String packageName, String processName, Application application, int userId) {
                // Handle after application onCreate
            }
        });
    }

    private void checkAppName(Context context) {
        String expectedAppName = "Vip Loader";
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
            String appName = context.getPackageManager().getApplicationLabel(appInfo).toString();

            if (!expectedAppName.equals(appName)) {
                Log.e(TAG, "App name tampered! Expected: " + expectedAppName + ", Found: " + appName);
                throw new SecurityException("App name has been tampered! Expected: " + expectedAppName + ", Found: " + appName);
            }
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException("Failed to verify app name", e);
        }
    }
}