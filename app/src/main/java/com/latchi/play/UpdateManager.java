package com.latchi.play;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Checks public GitHub Releases and installs a newer LATCHI PLAY APK with user consent. */
public final class UpdateManager {
    private static final String RELEASE_API =
            "https://api.github.com/repos/latchidz/LATCHI-PLAY/releases/latest";
    private static final String PREFS = "latchi_updates";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final String KEY_DOWNLOAD_ID = "download_id";
    private static final long CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L;

    private final Activity activity;
    private final SharedPreferences preferences;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final DownloadManager downloadManager;
    private boolean receiverRegistered;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long expected = preferences.getLong(KEY_DOWNLOAD_ID, -1L);
            long completed = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -2L);
            if (expected > 0 && expected == completed) installDownloadedApk(expected);
        }
    };

    public UpdateManager(Activity activity) {
        this.activity = activity;
        this.preferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.downloadManager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        registerReceiver();
    }

    public void checkAutomatically() {
        long lastCheck = preferences.getLong(KEY_LAST_CHECK, 0L);
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return;
        preferences.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
        check(false);
    }

    public void checkManually() {
        check(true);
    }

    private void check(boolean notifyIfCurrent) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(RELEASE_API).openConnection();
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(15_000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "LATCHI-PLAY-Android");
                if (connection.getResponseCode() != 200) throw new Exception("HTTP " + connection.getResponseCode());

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder jsonText = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) jsonText.append(line);
                reader.close();

                JSONObject release = new JSONObject(jsonText.toString());
                String tag = release.optString("tag_name", "").replaceFirst("^[vV]", "");
                String notes = release.optString("body", "");
                String apkUrl = findApk(release.optJSONArray("assets"));
                String current = BuildConfig.VERSION_NAME.replace("-debug", "");

                activity.runOnUiThread(() -> {
                    if (isNewer(tag, current) && apkUrl != null) {
                        showUpdateDialog(tag, notes, apkUrl);
                    } else if (notifyIfCurrent) {
                        Toast.makeText(activity, "لديك أحدث إصدار", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception error) {
                if (notifyIfCurrent) {
                    activity.runOnUiThread(() -> Toast.makeText(
                            activity, "تعذر التحقق من التحديث الآن", Toast.LENGTH_SHORT).show());
                }
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private String findApk(JSONArray assets) {
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            String name = asset.optString("name", "").toLowerCase();
            String url = asset.optString("browser_download_url", "");
            if (name.endsWith(".apk") && url.startsWith("https://github.com/")) return url;
        }
        return null;
    }

    private void showUpdateDialog(String version, String notes, String apkUrl) {
        if (activity.isFinishing()) return;
        String message = "يتوفر إصدار جديد " + version + ".";
        if (!notes.trim().isEmpty()) message += "\n\n" + notes.trim();
        new AlertDialog.Builder(activity)
                .setTitle("تحديث LATCHI PLAY")
                .setMessage(message)
                .setNegativeButton("لاحقًا", null)
                .setPositiveButton("تحديث الآن", (dialog, which) -> download(apkUrl, version))
                .show();
    }

    private void download(String apkUrl, String version) {
        if (downloadManager == null) {
            Toast.makeText(activity, "خدمة التنزيل غير متاحة", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setTitle("LATCHI PLAY " + version);
            request.setDescription("جارٍ تنزيل التحديث");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS,
                    "LATCHI-PLAY-update.apk");
            long id = downloadManager.enqueue(request);
            preferences.edit().putLong(KEY_DOWNLOAD_ID, id).apply();
            Toast.makeText(activity, "بدأ تنزيل التحديث", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(activity, "تعذر بدء تنزيل التحديث", Toast.LENGTH_SHORT).show();
        }
    }

    public void resumePendingInstall() {
        long id = preferences.getLong(KEY_DOWNLOAD_ID, -1L);
        if (id > 0 && canInstallPackages()) installDownloadedApk(id);
    }

    private void installDownloadedApk(long id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canInstallPackages()) {
            Toast.makeText(activity, "اسمح للتطبيق بتثبيت التحديث ثم ارجع", Toast.LENGTH_LONG).show();
            Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(settingsIntent);
            return;
        }
        try {
            Uri uri = downloadManager.getUriForDownloadedFile(id);
            if (uri == null) return;
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(install);
            preferences.edit().remove(KEY_DOWNLOAD_ID).apply();
        } catch (Exception error) {
            Toast.makeText(activity, "تعذر فتح مثبت التحديث", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean canInstallPackages() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                activity.getPackageManager().canRequestPackageInstalls();
    }

    private boolean isNewer(String remote, String current) {
        if (remote == null || remote.isEmpty()) return false;
        String[] a = remote.split("[^0-9]+");
        String[] b = current.split("[^0-9]+");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int av = i < a.length && !a[i].isEmpty() ? Integer.parseInt(a[i]) : 0;
            int bv = i < b.length && !b[i].isEmpty() ? Integer.parseInt(b[i]) : 0;
            if (av != bv) return av > bv;
        }
        return false;
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(downloadReceiver, filter);
        }
        receiverRegistered = true;
    }

    public void destroy() {
        if (receiverRegistered) {
            try {
                activity.unregisterReceiver(downloadReceiver);
            } catch (Exception ignored) {
                // Activity was already destroyed.
            }
            receiverRegistered = false;
        }
        executor.shutdownNow();
    }
}
