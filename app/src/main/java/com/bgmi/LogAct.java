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
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.json.JSONArray;
import org.json.JSONObject;

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

    // DCX KING - Licence OFF: true = direct entry, no key screen needed.
    // Files still download/update automatically via startDownload().
    private static final boolean LICENSE_CHECK_ENABLED = true;

    // DCX KING - Official key system URL (GET LICENSE KEY button opens this)
    private static final String KEY_SYSTEM_URL = "https://venomkey.com/connect";

    // DCX KING - Pastebin key database (JSON or text list, managed online)
    private static final String KEY_DB_URL = "https://pastebin.com/raw/fidS5mY1";

    // Expiry value stored in prefs for dashboard
    private static final String PREF_EXPIRY = "EXPIRY";

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

        // DCX KING - Licence OFF: skip key screen, auto-enter (files still update)
        if (!LICENSE_CHECK_ENABLED) {
            showAppToast("Welcome DCX KING VIP!");
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                try {
                    startDownload(LogAct.this);
                } catch (Throwable t) {
                    Intent i = new Intent(LogAct.this, MAct.class);
                    startActivity(i);
                    finish();
                }
            }, 800);
            // Fall through to init UI in background (hidden behind loading dialog)
        }

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

        // Get Key link - DCX KING official key system
        getKey.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(KEY_SYSTEM_URL));
                startActivity(intent);
            } catch (Throwable t) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(GetKey()));
                    startActivity(intent);
                } catch (Throwable ignored) {}
            }
        });

        // Login button - DCX KING with DCX_OK local bypass
        btnLogin.setOnClickListener(v -> {
            String userKey = textUsername.getText().toString().trim();
            if (!userKey.isEmpty()) {
                prefs.setSt(USER, userKey);
                if (isLocalMasterKey(userKey)) {
                    // Instant offline access - no server check, no download wait
                    prefs.setSt(PREF_EXPIRY, "Lifetime");
                    showAppToast("Welcome DCX KING VIP!");
                    Intent i = new Intent(LogAct.this, MAct.class);
                    startActivity(i);
                    finish();
                    return;
                }
                checkKeyOnline(userKey);
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

    // DCX KING - Pastebin key system check (JSON or text list).
    // Returns expiry string on success, null + error set on failure.
    // Server unreachable -> offline entry ("Offline Mode") so licence never blocks.
    private void checkKeyOnline(final String userKey) {
        showLoadingDialog("Connecting to licence server...", false);
        new Thread(() -> {
            String expiry = null;
            String error = null;
            try {
                expiry = fetchKeyExpiry(userKey);
                if (expiry == null) {
                    error = lastKeyError != null ? lastKeyError : "Invalid key";
                }
            } catch (Exception e) {
                // Licence server issue fix: never hard-block, enter offline
                expiry = "Offline Mode";
            }
            final String fExpiry = expiry;
            final String fError = error;
            runOnUiThread(() -> {
                dismissLoadingDialog();
                if (fExpiry != null) {
                    prefs.setSt(USER, userKey);
                    prefs.setSt(PREF_EXPIRY, fExpiry);
                    showAppToast("Welcome DCX KING VIP!");
                    startDownload(LogAct.this);
                } else {
                    showLoadingDialog(fError, true);
                }
            });
        }).start();
    }

    private String lastKeyError = null;

    private String fetchKeyExpiry(String userKey) throws Exception {
        lastKeyError = null;
        String key = userKey == null ? "" : userKey.trim();
        if (key.isEmpty()) {
            lastKeyError = "Please enter key";
            return null;
        }
        String body;
        try {
            body = httpGet(KEY_DB_URL);
        } catch (Exception e) {
            throw e; // caller -> offline entry
        }
        if (body == null || body.trim().isEmpty()) {
            lastKeyError = "Licence server empty response";
            return null;
        }
        body = body.trim();

        // 1) Try JSON formats
        if (body.startsWith("{") || body.startsWith("[")) {
            try {
                String exp = parseJsonKeys(body, key);
                if (exp != null) return checkNotExpired(exp);
            } catch (Exception je) {
                // fall through to text parsing
            }
        }

        // 2) Text formats: KEY=expiry | KEY: expiry | KEY,expiry | bare KEY per line
        String[] lines = body.split("\\r?\\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            String k, exp = "Lifetime";
            int sep = -1;
            for (String s : new String[]{"=", ":", ","}) {
                int i = line.indexOf(s);
                if (i > 0) { sep = i; break; }
            }
            if (sep > 0) {
                k = line.substring(0, sep).trim();
                exp = line.substring(sep + 1).trim();
                if (exp.isEmpty()) exp = "Lifetime";
            } else {
                k = line;
            }
            if (k.equalsIgnoreCase(key)) {
                return checkNotExpired(exp);
            }
        }

        // 3) Substring fallback (paste contains the key anywhere)
        if (body.toLowerCase().contains(key.toLowerCase()) && key.length() >= 3) {
            return "Lifetime";
        }

        lastKeyError = "Invalid key - get key from key system";
        return null;
    }

    private String parseJsonKeys(String body, String key) throws Exception {
        if (body.startsWith("[")) {
            JSONArray arr = new JSONArray(body);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    if (key.equalsIgnoreCase(arr.optString(i, "").trim())) return "Lifetime";
                    continue;
                }
                String k = o.optString("key", o.optString("user_key", ""));
                if (key.equalsIgnoreCase(k.trim())) {
                    return o.optString("expiry", o.optString("exp", o.optString("EXP", "Lifetime")));
                }
            }
            return null;
        }
        JSONObject obj = new JSONObject(body);
        // {"keys": {...} / [...]}, {"data": ...} wrappers
        for (String wrap : new String[]{"keys", "data", "users"}) {
            if (obj.has(wrap)) {
                Object inner = obj.get(wrap);
                if (inner instanceof JSONObject) {
                    String exp = matchJsonMap((JSONObject) inner, key);
                    if (exp != null) return exp;
                } else if (inner instanceof JSONArray) {
                    String exp = matchJsonArray((JSONArray) inner, key);
                    if (exp != null) return exp;
                }
            }
        }
        return matchJsonMap(obj, key);
    }

    private String matchJsonMap(JSONObject obj, String key) throws Exception {
        java.util.Iterator<String> it = obj.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (k.equalsIgnoreCase(key.trim())) {
                Object v = obj.get(k);
                if (v instanceof JSONObject) {
                    JSONObject o = (JSONObject) v;
                    return o.optString("expiry", o.optString("exp", "Lifetime"));
                }
                String s = String.valueOf(v).trim();
                return s.isEmpty() ? "Lifetime" : s;
            }
        }
        return null;
    }

    private String matchJsonArray(JSONArray arr, String key) throws Exception {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) {
                if (key.equalsIgnoreCase(arr.optString(i, "").trim())) return "Lifetime";
                continue;
            }
            String k = o.optString("key", o.optString("user_key", ""));
            if (key.equalsIgnoreCase(k.trim())) {
                return o.optString("expiry", o.optString("exp", "Lifetime"));
            }
        }
        return null;
    }

    private String checkNotExpired(String exp) {
        if (exp == null || exp.trim().isEmpty()) return "Lifetime";
        exp = exp.trim();
        // yyyy-MM-dd expiry check (also accepts dd-MM-yyyy)
        try {
            java.text.SimpleDateFormat[] fmts = new java.text.SimpleDateFormat[]{
                    new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US),
                    new java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US),
                    new java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.US)};
            for (java.text.SimpleDateFormat f : fmts) {
                f.setLenient(false);
                try {
                    java.util.Date d = f.parse(exp);
                    if (d != null && d.getTime() < System.currentTimeMillis() - 86400000L) {
                        lastKeyError = "Key expired on " + exp;
                        return null;
                    }
                    return exp;
                } catch (java.text.ParseException ignored) {}
            }
        } catch (Throwable ignored) {}
        return exp; // non-date values (Lifetime etc.) pass through
    }

    private String httpGet(String urlString) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile)");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            conn.connect();
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new java.io.IOException("Server HTTP " + code);
            }
            InputStream in = conn.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            br.close();
            return sb.toString();
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored) {}
            }
        }
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
                if (m_Context instanceof android.app.Activity) {
                    ((android.app.Activity) m_Context).finish();
                }
            } else {
                java.io.File loaderDirectory = new java.io.File(getFilesDir(), "loader");
                if (loaderDirectory.exists() && loaderDirectory.list() != null && loaderDirectory.list().length > 0) {
                    Intent i = new Intent(m_Context.getApplicationContext(), MAct.class);
                    m_Context.startActivity(i);
                    if (m_Context instanceof android.app.Activity) {
                        ((android.app.Activity) m_Context).finish();
                    }
                } else if (!LICENSE_CHECK_ENABLED) {
                    // DCX KING Licence OFF: server/download fail -> offline lobby anyway
                    showAppToast("Offline mode - entering lobby");
                    Intent i = new Intent(m_Context.getApplicationContext(), MAct.class);
                    m_Context.startActivity(i);
                    if (m_Context instanceof android.app.Activity) {
                        ((android.app.Activity) m_Context).finish();
                    }
                } else {
                    showAppToast("Download failed!");
                }
            }
        });
        // Licence OFF: never block entry with server error popups
        task.silentOnError = !LICENSE_CHECK_ENABLED;

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
