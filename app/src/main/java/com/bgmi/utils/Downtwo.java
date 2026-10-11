package com.bgmi.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.lingala.zip4j.ZipFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class Downtwo extends AsyncTask<String, String, String> {
    private static final String TAG = "Downtwo";

    public static native String Version();
    public static native String Link();

    public interface Callback {
        void onComplete(boolean success);
    }

    // 🔹 NEW: Progress listener for % updates
    public interface ProgressListener {
        void onProgress(int percent);
    }

    private final Context context;
    private final Callback callback; // may be null
    private ProgressListener progressListener; // 🔹 Added
    private androidx.appcompat.app.AlertDialog progressDialog;
    private String serverVersion = "0.0";
    private static final String PREF_NAME = "com.bgmi.download";
    private static final String PREF_VERSION_KEY = "version";
    private static final String HOSTOP_ZIP = "sigma.zip";
    private static final String LOADER_DIR_NAME = "loader";

    // Backwards-compatible constructor (no callback)
    public Downtwo(Context context) {
        this(context, null);
    }

    // New constructor with callback
    public Downtwo(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
    }

    // 🔹 Setter for progress listener
    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();

        // NOTE: We no longer show a built-in dialog, LogAct will show its own custom one
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
        }
    }

    @Override
    protected String doInBackground(String... params) {
        try {
            if (params.length == 0 || params[0] == null) {
                return "Invalid URL provided";
            }

            String downloadUrl = params[0];
            serverVersion = getServerVersion();

            if (serverVersion == null) {
                return "Failed to retrieve server version";
            }

            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String localVersion = prefs.getString(PREF_VERSION_KEY, "0.0");

            File loaderDirectory = new File(context.getFilesDir(), LOADER_DIR_NAME);
            boolean hasFiles = loaderDirectory.exists() && loaderDirectory.list() != null && loaderDirectory.list().length > 0;

            if (!localVersion.equals(serverVersion) || !hasFiles) {
                publishProgress("Downloading update...");
                return downloadAndExtract(downloadUrl);
            } else {
                return "No Update Available";
            }
        } catch (Exception e) {
            Log.e(TAG, "doInBackground error: ", e);
            return "Background error: " + e.getMessage();
        }
    }

    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
        // This was for internal dialog → now handled in LogAct, so keep silent
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);

        // Determine success: null == download applied; "No Update Available" treated as success too
        boolean success = (result == null) || "No Update Available".equals(result);

        // invoke callback if provided
        try {
            if (callback != null) {
                callback.onComplete(success);
            }
        } catch (Exception e) {
            Log.e(TAG, "Callback threw exception: ", e);
        }

        // Show final message only if activity is alive
        if (!success) {
            showFinalMessage("Error: " + result);
        }
    }

    private HttpURLConnection openConnection(String urlString) throws IOException {
        String currentUrl = urlString;
        for (int i = 0; i < 5; i++) {
            URL url = new URL(currentUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile)");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == HttpURLConnection.HTTP_SEE_OTHER ||
                status == 307 || status == 308) {
                String redirectUrl = conn.getHeaderField("Location");
                if (redirectUrl != null && !redirectUrl.isEmpty()) {
                    conn.disconnect();
                    currentUrl = redirectUrl;
                    continue;
                }
            }
            return conn;
        }
        URL url = new URL(currentUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile)");
        return conn;
    }

    private String getServerVersion() {
        HttpURLConnection connection = null;
        try {
            String vUrl = Version();
            if (vUrl == null || vUrl.trim().isEmpty()) {
                Log.e(TAG, "Version URL is empty");
                return null;
            }
            connection = openConnection(vUrl.trim());
            connection.connect();

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Server returned HTTP " + code + " for version URL");
                return null;
            }

            InputStream inputStream = connection.getInputStream();
            Scanner scanner = new Scanner(inputStream);
            String version = scanner.hasNextLine() ? scanner.nextLine().trim() : null;
            scanner.close();
            inputStream.close();
            if (version != null && version.startsWith("\uFEFF")) {
                version = version.substring(1).trim();
            }
            return version;
        } catch (Exception e) {
            Log.e(TAG, "Error fetching server version: ", e);
            return null;
        } finally {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Exception ignored) {}
            }
        }
    }

    private String downloadAndExtract(String urlString) {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(urlString);
            connection.connect();

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                return "Download failed: Server returned HTTP " + code;
            }

            int totalSize = connection.getContentLength();
            int downloaded = 0;

            InputStream input = connection.getInputStream();
            File pathBase = new File(context.getFilesDir().getPath());

            if (!pathBase.exists()) {
                pathBase.mkdirs();
            }

            File pathOutput = new File(pathBase, HOSTOP_ZIP);
            FileOutputStream output = new FileOutputStream(pathOutput);
            byte[] data = new byte[4096];
            int count;

            while ((count = input.read(data)) != -1) {
                output.write(data, 0, count);
                downloaded += count;

                // 🔹 Send progress % to listener
                if (totalSize > 0 && progressListener != null) {
                    int percent = (int) ((downloaded * 100L) / totalSize);
                    progressListener.onProgress(percent);
                }
            }

            output.flush();
            output.close();
            input.close();

            File loaderDirectory = new File(pathBase, LOADER_DIR_NAME);
            if (!loaderDirectory.exists()) {
                loaderDirectory.mkdirs();
            }

            // extract zip (handling both encrypted and unencrypted zips)
            ZipFile zipFile = new ZipFile(pathOutput);
            if (zipFile.isEncrypted()) {
                zipFile.setPassword("PRIVATE_SRC_FILES".toCharArray());
            }
            zipFile.extractAll(loaderDirectory.getAbsolutePath());

            setPermissions(loaderDirectory);

            if (pathOutput.exists()) {
                pathOutput.delete();
            }

            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(PREF_VERSION_KEY, serverVersion).apply();

            return null; // Success
        } catch (Exception e) {
            Log.e(TAG, "Error downloading or extracting file: ", e);
            return "Download failed: " + e.getMessage();
        } finally {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Exception ignored) {}
            }
        }
    }

    private void setPermissions(File directory) {
        if (directory == null) return;
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    setPermissions(file);
                }
            }
        } else {
            directory.setReadable(true, false);
            directory.setWritable(true, false);
            directory.setExecutable(true, false);
        }
    }

    private void showFinalMessage(String message) {
        // Only show if activity/context alive
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
        }

        try {
            new MaterialAlertDialogBuilder(context)
                    .setTitle("Update Status")
                    .setMessage(message)
                    .setPositiveButton("OK", (dialog, which) -> {
                        try {
                            dialog.dismiss();
                        } catch (Exception ignored) {}
                    })
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to show final message dialog: ", e);
        }
    }
}
