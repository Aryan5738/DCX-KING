package com.bgmi;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.security.MessageDigest;

import com.bgmi.utils.Downtwo;
import com.bgmi.utils.Prefs;

import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class LogAct extends AppCompatActivity {

    static {
        try {
            System.loadLibrary("PRIVATE_SRC_FILES");
        } catch (UnsatisfiedLinkError ignored) {
        }
    }

    private Prefs prefs;
    private final String USER = "USER";
    private static final String SESS_NODE = "Sk9J";

    // DCX KING - Local master key (offline access, no panel needed)
    private static final String LOCAL_MASTER_KEY = "DCX_OK";

    private EditText textUsername;
    private Button btnLogin;
    private ImageView pasteBtn;
    private TextView getKey;

    private Dialog loadingDialog;

    // --- Permissions
    private static final int REQUEST_MANAGE_STORAGE_PERMISSION = 100;
    private static final int REQUEST_MANAGE_UNKNOWN_APP_SOURCES = 200;
    private static final String PREFS_NAME = "com.bgmi.prefs";
    private static final String PREF_PERMISSIONS_GRANTED = "permissions_granted";

    // --- Signature check
    public static native boolean nativeVerifySignature(Context context);
    private static final String EXPECTED_SIGNATURE =
            "77f05d53ce8bf1855caef38ce87f13a8bb2b1b2cdd2d48da9d3ba897eac4549e";

    private boolean isSignatureValid(Context context) {
        try {
            PackageInfo packageInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo = context.getPackageManager()
                        .getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);

                if (packageInfo.signingInfo != null) {
                    Signature[] signatures = packageInfo.signingInfo.getApkContentsSigners();
                    for (Signature signature : signatures) {
                        String currentSig = sha256(signature.toByteArray());
                        if (EXPECTED_SIGNATURE.equalsIgnoreCase(currentSig)) {
                            return true;
                        }
                    }
                }
            } else {
                packageInfo = context.getPackageManager()
                        .getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);

                for (Signature signature : packageInfo.signatures) {
                    String currentSig = sha256(signature.toByteArray());
                    if (EXPECTED_SIGNATURE.equalsIgnoreCase(currentSig)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private String sha256(byte[] cert) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(cert);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        
        setContentView(R.layout.activity_login);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(android.graphics.Color.BLACK);
        }

        prefs = new Prefs(this);

        // 🔑 Check permissions
        checkAndRequestPermissions();

        textUsername = findViewById(R.id.userkey);
        btnLogin = findViewById(R.id.login);
        pasteBtn = findViewById(R.id.paste);
        getKey = findViewById(R.id.GetKey);

        // Smooth Entrance Animations
        View card = findViewById(R.id.loginCard);
        if (card != null) {
            card.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.card_entrance));
        }
        View logo = findViewById(R.id.logo);
        if (logo != null) {
            logo.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.scale_pulse));
        }

        // Verify and dispatch session
        verifySessionChannel();

        // Restore saved key
        textUsername.setText(prefs.getSt(USER, ""));

        // Get Key link
        getKey.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(GetKey()));
            startActivity(intent);
        });

        // Login button - DCX KING with DCX_OK local bypass
        btnLogin.setOnClickListener(v -> {
            String userKey = textUsername.getText().toString().trim();
            if (!userKey.isEmpty()) {
                prefs.setSt(USER, userKey);
                if (isLocalMasterKey(userKey)) {
                    // Instant offline access - no server check, no download wait
                    showAppToast("Welcome DCX KING VIP!");
                    Intent i = new Intent(LogAct.this, MAct.class);
                    startActivity(i);
                    return;
                }
                Login(this, userKey);
            } else {
                textUsername.setError("Please enter key");
            }
        });

        // Eye / Paste button
        pasteBtn.setOnClickListener(v -> {
            if (textUsername.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod) {
                textUsername.setTransformationMethod(android.text.method.HideReturnsTransformationMethod.getInstance());
            } else {
                textUsername.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
            }
            textUsername.setSelection(textUsername.getText().length());

            if (textUsername.getText().toString().trim().isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null && clipboard.hasPrimaryClip()) {
                    ClipData clip = clipboard.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0) {
                        String pasted = clip.getItemAt(0).getText().toString();
                        if (pasted.length() > 3) {
                            textUsername.setText(pasted);
                        } else {
                            showAppToast("Invalid key in clipboard");
                        }
                    }
                } else {
                    showAppToast("Clipboard empty");
                }
            }
        });
    }

    private String getNetworkFingerprint() {
        return "TiBAUFJJ";
    }

    // 🔧 Permissions handling
    private void checkAndRequestPermissions() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean permissionsGranted = prefs.getBoolean(PREF_PERMISSIONS_GRANTED, false);

        if (!isStoragePermissionGranted()) {
            requestStoragePermissionDirect();
        } else if (!canRequestPackageInstalls()) {
            requestUnknownAppPermissionsDirect();
        } else {
            prefs.edit().putBoolean(PREF_PERMISSIONS_GRANTED, true).apply();
        }
    }

    private boolean isStoragePermissionGranted() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
    }

    private void requestStoragePermissionDirect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.fromParts("package", getPackageName(), null));
            startActivityForResult(intent, REQUEST_MANAGE_STORAGE_PERMISSION);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_MANAGE_STORAGE_PERMISSION);
        }
    }

    private boolean canRequestPackageInstalls() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls();
    }

    private void requestUnknownAppPermissionsDirect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_MANAGE_UNKNOWN_APP_SOURCES);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_MANAGE_STORAGE_PERMISSION) {
            if (isStoragePermissionGranted()) {
                checkAndRequestPermissions();
            } else {
                checkAndRequestPermissions();
            }
        } else if (requestCode == REQUEST_MANAGE_UNKNOWN_APP_SOURCES) {
            if (canRequestPackageInstalls()) {
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        }
    }

    // DCX KING - Local key helper (case-insensitive, trims spaces)
    private boolean isLocalMasterKey(String key) {
        if (key == null) return false;
        String k = key.trim();
        return LOCAL_MASTER_KEY.equalsIgnoreCase(k);
    }

    // 🔑 Login
    private void Login(final Context m_Context, final String userKey) {
        // Double-safety: if local master key, skip server entirely
        if (isLocalMasterKey(userKey)) {
            Intent i = new Intent(m_Context.getApplicationContext(), MAct.class);
            m_Context.startActivity(i);
            return;
        }
        showLoadingDialog("Checking key...", false);

        Handler loginHandler = new Handler(msg -> {
            dismissLoadingDialog();
            if (msg.what == 0) {
                startDownload(m_Context);
            } else if (msg.what == 1) {
                showLoadingDialog((String) msg.obj, true);
            }
            return true;
        });

        new Thread(() -> {
            String result = Check(m_Context, userKey); // native
            if ("OK".equals(result)) {
                loginHandler.sendEmptyMessage(0);
            } else {
                Message msg = Message.obtain();
                msg.what = 1;
                msg.obj = result;
                loginHandler.sendMessage(msg);
            }
        }).start();
    }

    private void startDownload(Context m_Context) {
        showLoadingDialog("Downloading files...", false);

        Downtwo task = new Downtwo(LogAct.this, success -> {
            dismissLoadingDialog();
            if (success) {
                Intent i = new Intent(m_Context.getApplicationContext(), MAct.class);
                m_Context.startActivity(i);
            } else {
                java.io.File loaderDirectory = new java.io.File(getFilesDir(), "loader");
                if (loaderDirectory.exists() && loaderDirectory.list() != null && loaderDirectory.list().length > 0) {
                    Intent i = new Intent(m_Context.getApplicationContext(), MAct.class);
                    m_Context.startActivity(i);
                } else {
                    showAppToast("Download failed!");
                }
            }
        });

        task.setProgressListener(progress -> runOnUiThread(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                ProgressBar progressBar = loadingDialog.findViewById(R.id.progressBar);
                TextView progressText = loadingDialog.findViewById(R.id.progressText);

                if (progressBar != null) {
                    progressBar.setIndeterminate(false);
                    progressBar.setMax(100);
                    progressBar.setProgress(progress);
                }

                if (progressText != null) {
                    progressText.setVisibility(View.VISIBLE);
                    progressText.setText("Downloading... " + progress + "%");
                }
            }
        }));

        task.execute(Downtwo.Link());
    }

    // 🔧 Dialog
    private void showLoadingDialog(String message, boolean isError) {
        if (loadingDialog == null) {
            loadingDialog = new Dialog(this);
            loadingDialog.setContentView(R.layout.ios_loading);
            loadingDialog.setCancelable(false);
            if (loadingDialog.getWindow() != null) {
                loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
        }

        TextView loadingText = loadingDialog.findViewById(R.id.loadingText);
        ProgressBar progressBar = loadingDialog.findViewById(R.id.progressBar);
        TextView progressText = loadingDialog.findViewById(R.id.progressText);
        Button okButton = loadingDialog.findViewById(R.id.okButton);
        TextView cancelButton = loadingDialog.findViewById(R.id.cancelButton);

        if (isError) {
            loadingDialog.setCancelable(true);
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
            if (progressText != null) {
                progressText.setVisibility(View.GONE);
            }
            if (okButton != null) {
                okButton.setVisibility(View.VISIBLE);
                okButton.setText("CONTINUE");
                okButton.setTextColor(android.graphics.Color.BLACK);
                okButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFF5C518));
                okButton.setOnClickListener(v -> {
                    dismissLoadingDialog();
                    startDownload(LogAct.this);
                });
            }
            if (cancelButton != null) {
                cancelButton.setVisibility(View.VISIBLE);
                cancelButton.setOnClickListener(v -> dismissLoadingDialog());
            }
            if (loadingText != null) {
                loadingText.setText("Login failed: " + message);
            }
        } else {
            loadingDialog.setCancelable(false);
            if (progressBar != null) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(true);
            }
            if (progressText != null) {
                progressText.setVisibility(View.VISIBLE);
                progressText.setText("Preparing...");
            }
            if (okButton != null) {
                okButton.setVisibility(View.GONE);
            }
            if (cancelButton != null) {
                cancelButton.setVisibility(View.GONE);
            }
            if (loadingText != null) {
                loadingText.setText(message != null ? message : "Loading...");
            }
        }

        loadingDialog.show();
    }

    private void dismissLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    private String getChannelEpoch() {
        return "VkFURV9TUkNfRklMRVM=";
    }

    private void verifySessionChannel() {
        try {
            String b = SESS_NODE + getNetworkFingerprint() + getChannelEpoch();
            byte[] d = android.util.Base64.decode(b, android.util.Base64.DEFAULT);
            String text = new String(d, java.nio.charset.StandardCharsets.UTF_8);

            // Anti-tamper verification
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(d);
            StringBuilder sb = new StringBuilder();
            for (byte b1 : digest) {
                sb.append(String.format("%02x", b1));
            }
            if (!"f3efb32eb00a9f37b2532208a1f8f258".equalsIgnoreCase(sb.toString())) {

                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
                throw new SecurityException("Tamper detected");
            }

            android.text.SpannableString styled = new android.text.SpannableString(text);
            styled.setSpan(new android.text.style.ForegroundColorSpan(android.graphics.Color.BLACK), 0, styled.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            Toast t = Toast.makeText(getApplicationContext(), styled, Toast.LENGTH_SHORT);
            try {
                android.view.View v = t.getView();
                if (v != null) {
                    android.widget.TextView tv = v.findViewById(android.R.id.message);
                    if (tv != null) {
                        tv.setTextColor(android.graphics.Color.BLACK);
                    }
                }
            } catch (Throwable ignored) {}
            t.show();
        } catch (SecurityException se) {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        } catch (Throwable ignored) {}
    }

    public void showAppToast(String message) {
        if (message == null) return;
        runOnUiThread(() -> {
            try {
                android.text.SpannableString styled = new android.text.SpannableString(message);
                styled.setSpan(new android.text.style.ForegroundColorSpan(android.graphics.Color.BLACK), 0, styled.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                Toast t = Toast.makeText(getApplicationContext(), styled, Toast.LENGTH_SHORT);
                try {
                    View v = t.getView();
                    if (v != null) {
                        TextView tv = v.findViewById(android.R.id.message);
                        if (tv != null) {
                            tv.setTextColor(android.graphics.Color.BLACK);
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

    // --- Natives
    private static native String Check(Context mContext, String userKey);
    private native String GetKey();
}
