package hu.dealhunter.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScanWorker extends Worker {
    private static final String KEY_SEEN = "seen_ids";

    public ScanWorker(@NonNull Context appContext, @NonNull WorkerParameters params) {
        super(appContext, params);
    }

    @NonNull @Override public Result doWork() {
        Context c = getApplicationContext();
        SharedPreferences p = c.getSharedPreferences(MarketScanner.PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean(MarketScanner.KEY_AUTO, true)) return Result.success();

        List<MarketScanner.SearchRule> rules = MarketScanner.loadRules(c);
        String token = p.getString(MarketScanner.KEY_APIFY_TOKEN, "");
        String location = p.getString(MarketScanner.KEY_LOCATION, "budapest");
        Set<String> blacklist = new HashSet<>(p.getStringSet(MainActivity.KEY_BLACKLIST, new HashSet<>()));

        try {
            MarketScanner.ScanResult result = MarketScanner.scan(c, rules, token, location);
            Set<String> oldSeen = new HashSet<>(p.getStringSet(KEY_SEEN, new HashSet<>()));
            Set<String> newSeen = new HashSet<>(oldSeen);
            int sent = 0;
            for (MarketScanner.Deal d : result.deals) {
                boolean blocked = blacklist.contains(d.id) || (d.url != null && blacklist.contains(d.url));
                if (blocked) continue;
                if (!oldSeen.contains(d.id) && d.score >= 70 && sent < 5) {
                    notifyDeal(c, d);
                    sent++;
                }
                newSeen.add(d.id);
            }
            if (newSeen.size() > 500) {
                newSeen.clear();
                int keep = 0;
                for (MarketScanner.Deal d : result.deals) {
                    boolean blocked = blacklist.contains(d.id) || (d.url != null && blacklist.contains(d.url));
                    if (blocked) continue;
                    newSeen.add(d.id);
                    if (++keep >= 250) break;
                }
            }
            p.edit().putStringSet(KEY_SEEN, newSeen).putString("last_scan_status", result.status).apply();
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private static void notifyDeal(Context c, MarketScanner.Deal d) {
        NotificationManager nm = (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        String channel = "deals";
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(channel, "Új találatok", NotificationManager.IMPORTANCE_HIGH));
        }
        String text = d.title + " – " + d.price + " Ft";
        Notification.Builder b = new Notification.Builder(c, channel)
                .setContentTitle("Scrapefinder: " + d.score + "/100")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setSmallIcon(android.R.drawable.star_big_on)
                .setAutoCancel(true);
        nm.notify(Math.abs(d.id.hashCode()), b.build());
    }
}
