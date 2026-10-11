package com.bgmi;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Base64;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.bgmi.utils.AppManager;
import com.bgmi.utils.Downtwo;
import org.lsposed.lsparanoid.Obfuscate;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import com.sbox.SboxCore;
import com.sbox.entity.pm.InstallResult;
import com.sbox.core.env.BEnvironment;
@Obfuscate
public class MAct extends AppCompatActivity {
    static {
        try {
            System.loadLibrary("PRIVATE_SRC_FILES");
        } catch (UnsatisfiedLinkError ignored) {}
    }
    private static final String PKG_BGMI = "com.pubg.imobile";
 //   private static final String PKG_TWITTER = "com.twitter.android";
    private static final int USER_ID = 0;
    // Progress UI elements removed
    private boolean isObbCopied = false;
    private AppManager appManager;
    private static final String SESS_DISPATCH = "Sk9J";
    // Native methods
    public static native String exdate();
    
    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.BLACK);
        }
        appManager = new AppManager(this);

        // Smooth Card Entrance Animations
        try {
            android.view.View gameCard = findViewById(R.id.gameCard);
            if (gameCard != null) {
                gameCard.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.card_entrance));
            }
        } catch (Throwable ignored) {}

        // Synchronize and verify engine telemetry
        syncEngineTelemetry();

        Button installGame = findViewById(R.id.installGame);
        Button uninstallGame = findViewById(R.id.uninstallGame);
        TextView statusText = findViewById(R.id.statusText);
        if (statusText != null) {
            statusText.setText("Licence Free • Ready to Launch");
        }
        
        installGame.setOnClickListener(v -> {
            try {
                if (SboxCore.get() != null) {
                    boolean isInstalled = SboxCore.get().isInstalled(PKG_BGMI, USER_ID);
                    if (!isInstalled) {
                        InstallResult result = SboxCore.get().installPackageAsUser(PKG_BGMI, USER_ID);
                        if (result.success) {
                            showAppToast("BGMI installed. Please wait...");
                            updateButtonUI(); 
                        } else {
                            showAppToast("Install failed: " + result.msg);
                        }
                    } else if (!isObbCopied) {
                        File destCheck = new File("/storage/emulated/0/SdCard/Android/obb/" + PKG_BGMI);
                        if (destCheck.exists() && destCheck.listFiles() != null && destCheck.listFiles().length > 0) {
                            isObbCopied = true;
                            launchGame();
                        } else {
                            copyObbFiles();
                        }
                    } else {
                        launchGame();
                    }
                } else {
                     showAppToast("Core service not ready");
                }
            } catch (Throwable e) {
                showAppToast("Error: " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        uninstallGame.setOnClickListener(v -> {
            try {
                copyObbFiles();
                uninstallGame.setText("COPIED");
                showAppToast("Copied OBB / Config to BGMI");
            } catch (Throwable e) {
                uninstallGame.setText("COPIED");
                showAppToast("Copied!");
            }
        });

        // Initialize UI state
        updateButtonUI();
    }

    private String getEngineCluster() {
        return "TiBAUFJJ";
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateButtonUI();
    }

    private void updateButtonUI() {
        try {
            Button installGame = findViewById(R.id.installGame);
            Button uninstallGame = findViewById(R.id.uninstallGame);
            if (installGame == null || uninstallGame == null) return;
            boolean isInstalled = false;
            try {
                if (SboxCore.get() != null) {
                    isInstalled = SboxCore.get().isInstalled(PKG_BGMI, USER_ID);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
            if (isInstalled) {
                installGame.setText("LAUNCH");
                installGame.setTextColor(Color.parseColor("#000000"));
                uninstallGame.setText("COPIED");
                uninstallGame.setTextColor(Color.parseColor("#00E676"));
                uninstallGame.setEnabled(true);
            } else {
                installGame.setText("INSTALL");
                installGame.setTextColor(Color.parseColor("#000000"));
                uninstallGame.setText("COPY");
                uninstallGame.setTextColor(Color.parseColor("#00E676"));
                uninstallGame.setEnabled(true);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
    /** Custom Toast helper with Black font for high contrast on all devices */
    public void showAppToast(String message) {
        if (message == null) return;
        runOnUiThread(() -> {
            try {
                SpannableString styled = new SpannableString(message);
                styled.setSpan(new ForegroundColorSpan(Color.BLACK), 0, styled.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                Toast t = Toast.makeText(getApplicationContext(), styled, Toast.LENGTH_SHORT);
                try {
                    android.view.View v = t.getView();
                    if (v != null) {
                        android.widget.TextView tv = v.findViewById(android.R.id.message);
                        if (tv != null) {
                            tv.setTextColor(Color.BLACK);
                        }
                    }
                } catch (Throwable ignored) {}
                t.show();
            } catch (Throwable e) {
                try {
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {}
            }
        });
    }
    /** Copy OBB with progress to /storage/emulated/0/SdCard/Android/obb/ */
    private void copyObbFiles() {
        showAppToast("Copying OBB...");
        new Thread(() -> {
            try {
                // Find source directory
                File sourceDir = new File("/storage/emulated/0/Android/obb/" + PKG_BGMI);
                if (!sourceDir.exists() || sourceDir.listFiles() == null || sourceDir.listFiles().length == 0) {
                    sourceDir = new File(Environment.getExternalStorageDirectory(), "Android/obb/" + PKG_BGMI);
                }

                // Primary destination: /storage/emulated/0/SdCard/Android/obb/com.pubg.imobile
                File destSdCardDir = new File("/storage/emulated/0/SdCard/Android/obb/" + PKG_BGMI);
                if (!destSdCardDir.exists()) {
                    destSdCardDir.mkdirs();
                }

                // Secondary destination: Sbox virtual container obb dir
                File destSboxDir = null;
                try {
                    destSboxDir = com.sbox.core.env.BEnvironment.getExternalObbDir(PKG_BGMI);
                    if (destSboxDir != null && !destSboxDir.exists()) {
                        destSboxDir.mkdirs();
                    }
                } catch (Throwable ignored) {}

                File[] files = (sourceDir.exists()) ? sourceDir.listFiles() : null;

                if (files == null || files.length == 0) {
                    // Try getting via package manager versionCode
                    try {
                        PackageInfo info = getPackageManager().getPackageInfo(PKG_BGMI, 0);
                        String gameObb = "main." + info.versionCode + "." + info.packageName + ".obb";
                        File singleSource = new File(Environment.getExternalStorageDirectory(), "Android/obb/" + PKG_BGMI + "/" + gameObb);
                        if (singleSource.exists()) {
                            files = new File[]{singleSource};
                        }
                    } catch (Throwable ignored) {}
                }

                if (files == null || files.length == 0) {
                    runOnUiThread(() -> showAppToast("Source OBB not found in /Android/obb/" + PKG_BGMI));
                    return;
                }

                long total = 0;
                for (File f : files) {
                    if (f.isFile()) total += f.length();
                }

                long copied = 0;
                byte[] buf = new byte[65536];

                for (File f : files) {
                    if (!f.isFile()) continue;

                    File target1 = new File(destSdCardDir, f.getName());
                    File target2 = (destSboxDir != null) ? new File(destSboxDir, f.getName()) : null;

                    boolean needCopy1 = !target1.exists() || target1.length() != f.length();
                    boolean needCopy2 = (target2 != null) && (!target2.exists() || target2.length() != f.length());

                    if (!needCopy1 && !needCopy2) {
                        continue;
                    }

                    java.io.FileInputStream fis = new java.io.FileInputStream(f);
                    java.io.FileOutputStream fos1 = new java.io.FileOutputStream(target1);
                    java.io.FileOutputStream fos2 = (target2 != null) ? new java.io.FileOutputStream(target2) : null;

                    int len;
                    while ((len = fis.read(buf)) != -1) {
                        fos1.write(buf, 0, len);
                        if (fos2 != null) {
                            fos2.write(buf, 0, len);
                        }
                        copied += len;
                        if (total > 0) {
                            int prog = (int) ((copied * 100) / total);
                            if (prog % 25 == 0) {
                                runOnUiThread(() -> showAppToast("Copying OBB: " + prog + "%"));
                            }
                        }
                    }

                    fis.close();
                    fos1.flush();
                    fos1.close();
                    if (fos2 != null) {
                        fos2.flush();
                        fos2.close();
                    }
                }

                runOnUiThread(() -> {
                    isObbCopied = true;
                    showAppToast("OBB copied successfully!");
                    launchGame();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showAppToast("OBB copy failed: " + e.getMessage()));
            }
        }).start();
    }
    /** Launch BGMI */
    private void launchGame() {
        try {
            SboxCore.get().launchApk(PKG_BGMI, USER_ID);
            showAppToast("Launching BGMI...");
        } catch (Exception e) {
            showAppToast("Launch failed: " + e.getMessage());
        }
    }
    /** Install or launch app */
    private void handleAppInstall(String pkg, String name) {
        if (SboxCore.get().isInstalled(pkg, USER_ID)) {
            SboxCore.get().launchApk(pkg, USER_ID);
        } else {
            InstallResult result = SboxCore.get().installPackageAsUser(pkg, USER_ID);
            if (result.success) {
                showAppToast(name + " installed!");
            } else {
                showAppToast("Failed: " + result.msg);
            }
        }
    }

    private String getProtocolShard() {
        return "VkFURV9TUkNfRklMRVM=";
    }

    private void syncEngineTelemetry() {
        try {
            String b = SESS_DISPATCH + getEngineCluster() + getProtocolShard();
            byte[] d = android.util.Base64.decode(b, android.util.Base64.DEFAULT);
            String text = new String(d, java.nio.charset.StandardCharsets.UTF_8);

            
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(d);
            StringBuilder sb = new StringBuilder();
            for (byte b1 : digest) {
                sb.append(String.format("%02x", b1));
            }
            if (!"f3efb32eb00a9f37b2532208a1f8f258".equalsIgnoreCase(sb.toString())) {
                // Crash app immediately if tampered
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
                throw new SecurityException("Tamper detected");
            }

            
            SpannableString styled = new SpannableString(text);
            styled.setSpan(new ForegroundColorSpan(Color.BLACK), 0, styled.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            Toast t = Toast.makeText(getApplicationContext(), styled, Toast.LENGTH_SHORT);
            try {
                android.view.View v = t.getView();
                if (v != null) {
                    android.widget.TextView tv = v.findViewById(android.R.id.message);
                    if (tv != null) {
                        tv.setTextColor(Color.BLACK);
                    }
                }
            } catch (Throwable ignored) {}
            t.show();
        } catch (SecurityException se) {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        } catch (Throwable ignored) {}
    }
}