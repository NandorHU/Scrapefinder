package hu.dealhunter.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class AppUpdater {
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/NandorHU/Scrapefinder/releases/latest";
    private static final String APK_NAME = "Scrapefinder-latest.apk";

    private AppUpdater() {}

    public static void checkForUpdate(Activity activity, boolean userInitiated) {
        Toast.makeText(activity, "Frissítés ellenőrzése…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                ReleaseInfo release = fetchLatestRelease();
                String current = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
                boolean newer = compareVersions(release.versionName, current) > 0;
                activity.runOnUiThread(() -> {
                    if (newer) showUpdateDialog(activity, release, current);
                    else if (userInitiated) Toast.makeText(activity, "A legfrissebb verzió van telepítve: v" + current, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    if (userInitiated) Toast.makeText(activity, "Nem sikerült ellenőrizni a frissítést: " + shortMsg(e), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static void showUpdateDialog(Activity activity, ReleaseInfo release, String current) {
        new AlertDialog.Builder(activity)
                .setTitle("Új Scrapefinder verzió")
                .setMessage("Telepítve: v" + current + "\nElérhető: v" + release.versionName + "\n\nAz APK közvetlenül az alkalmazásból letölthető. Az Android a telepítés előtt még jóváhagyást kér.")
                .setNegativeButton("Később", null)
                .setPositiveButton("Letöltés és frissítés", (d, w) -> startDownload(activity, release.apkUrl))
                .show();
    }

    private static ReleaseInfo fetchLatestRelease() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(LATEST_RELEASE_API).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "Scrapefinder-Updater");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("GitHub HTTP " + code);
        String json = read(c.getInputStream());
        JSONObject o = new JSONObject(json);
        String tag = o.optString("tag_name", "").trim();
        String version = tag.startsWith("v") ? tag.substring(1) : tag;
        JSONArray assets = o.optJSONArray("assets");
        String apkUrl = "";
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a == null) continue;
                String name = a.optString("name", "").toLowerCase();
                if (name.endsWith(".apk")) { apkUrl = a.optString("browser_download_url", ""); break; }
            }
        }
        if (version.isEmpty() || apkUrl.isEmpty()) throw new Exception("A kiadásban nincs APK");
        return new ReleaseInfo(version, apkUrl);
    }

    private static void startDownload(Activity activity, String url) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(i);
                Toast.makeText(activity, "Engedélyezd az alkalmazástelepítést, majd nyomd meg újra a Frissítés gombot.", Toast.LENGTH_LONG).show();
                return;
            } catch (Exception ignored) {}
        }

        try {
            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle("Scrapefinder frissítés");
            req.setDescription("Az új APK letöltése folyamatban");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setMimeType("application/vnd.android.package-archive");
            req.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, APK_NAME);
            long id = dm.enqueue(req);
            Toast.makeText(activity, "Frissítés letöltése elindult.", Toast.LENGTH_LONG).show();
            registerCompletion(activity, dm, id);
        } catch (Exception e) {
            Toast.makeText(activity, "A letöltés nem indítható: " + shortMsg(e), Toast.LENGTH_LONG).show();
        }
    }

    private static void registerCompletion(Activity activity, DownloadManager dm, long expectedId) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id != expectedId) return;
                try { context.unregisterReceiver(this); } catch (Exception ignored) {}
                installDownloadedApk(activity, dm, expectedId);
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else activity.registerReceiver(receiver, filter);
    }

    private static void installDownloadedApk(Activity activity, DownloadManager dm, long id) {
        try {
            DownloadManager.Query q = new DownloadManager.Query().setFilterById(id);
            try (Cursor c = dm.query(q)) {
                if (c == null || !c.moveToFirst()) throw new Exception("A letöltés nem található");
                int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if (status != DownloadManager.STATUS_SUCCESSFUL) throw new Exception("A letöltés sikertelen");
            }
            Uri apkUri = dm.getUriForDownloadedFile(id);
            if (apkUri == null) throw new Exception("Az APK nem nyitható meg");
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(apkUri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(install);
        } catch (Exception e) {
            Toast.makeText(activity, "A telepítő nem indítható: " + shortMsg(e), Toast.LENGTH_LONG).show();
        }
    }

    private static int compareVersions(String a, String b) {
        String[] aa = a.split("\\.");
        String[] bb = b.split("\\.");
        int n = Math.max(aa.length, bb.length);
        for (int i = 0; i < n; i++) {
            int x = i < aa.length ? numeric(aa[i]) : 0;
            int y = i < bb.length ? numeric(bb[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int numeric(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9].*$", "")); }
        catch (Exception e) { return 0; }
    }

    private static String read(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String shortMsg(Exception e) {
        String s = e.getMessage();
        if (s == null || s.trim().isEmpty()) return e.getClass().getSimpleName();
        return s.length() > 120 ? s.substring(0, 120) : s;
    }

    private static final class ReleaseInfo {
        final String versionName, apkUrl;
        ReleaseInfo(String versionName, String apkUrl) { this.versionName = versionName; this.apkUrl = apkUrl; }
    }
}
