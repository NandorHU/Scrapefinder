package hu.dealhunter.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private LinearLayout rulesContainer, resultsContainer;
    private EditText queryInput, maxPriceInput, locationInput;
    private CheckBox autoScanCheck;
    private TextView statusText;
    private Button scanButton;
    private final List<MarketScanner.SearchRule> rules = new ArrayList<>();
    private final List<MarketScanner.Deal> deals = new ArrayList<>();
    private SharedPreferences prefs;

    private int dp(float v){ return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(MarketScanner.PREFS, Context.MODE_PRIVATE);

        queryInput=findViewById(R.id.queryInput);
        maxPriceInput=findViewById(R.id.maxPriceInput);
        locationInput=findViewById(R.id.locationInput);
        autoScanCheck=findViewById(R.id.autoScanCheck);
        statusText=findViewById(R.id.statusText);
        scanButton=findViewById(R.id.scanButton);
        rulesContainer=findViewById(R.id.rulesContainer);
        resultsContainer=findViewById(R.id.resultsContainer);

        rules.addAll(MarketScanner.loadRules(this));
        locationInput.setText(prefs.getString(MarketScanner.KEY_LOCATION, "budapest"));
        autoScanCheck.setChecked(prefs.getBoolean(MarketScanner.KEY_AUTO, true));

        findViewById(R.id.facebookLoginButton).setOnClickListener(v -> startActivity(new Intent(this, FacebookLoginActivity.class)));
        findViewById(R.id.addRuleButton).setOnClickListener(v -> addRule());
        findViewById(R.id.saveSettingsButton).setOnClickListener(v -> saveSettings(true));
        scanButton.setOnClickListener(v -> runLiveScan());
        findViewById(R.id.updateButton).setOnClickListener(v -> AppUpdater.checkForUpdate(this, true));
        autoScanCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(MarketScanner.KEY_AUTO, isChecked).apply();
            scheduleAutoScan(isChecked);
        });

        renderRules();
        renderDeals();
        requestNotifications();
        scheduleAutoScan(autoScanCheck.isChecked());
        statusText.setText("v0.6 • új UI • alkalmazáson belüli frissítés");
        AppUpdater.checkForUpdate(this, false);
    }

    private void addRule(){
        String q=queryInput.getText().toString().trim();
        if(q.isEmpty()){ Toast.makeText(this,"Adj meg keresést.",Toast.LENGTH_SHORT).show(); return; }
        int p=0;
        try { p=Integer.parseInt(maxPriceInput.getText().toString().trim()); } catch(Exception ignored){}
        rules.add(0,new MarketScanner.SearchRule(q,p));
        MarketScanner.saveRules(this, rules);
        queryInput.setText(""); maxPriceInput.setText(""); renderRules();
    }

    private void saveSettings(boolean showToast){
        String location=locationInput.getText().toString().trim();
        if(location.isEmpty()) location="budapest";
        prefs.edit().remove(MarketScanner.KEY_APIFY_TOKEN).putString(MarketScanner.KEY_LOCATION, location)
                .putBoolean(MarketScanner.KEY_AUTO, autoScanCheck.isChecked()).apply();
        MarketScanner.saveRules(this, rules);
        scheduleAutoScan(autoScanCheck.isChecked());
        if(showToast) Toast.makeText(this, "Beállítások mentve.",Toast.LENGTH_SHORT).show();
    }

    private void runLiveScan(){
        saveSettings(false);
        scanButton.setEnabled(false);
        scanButton.setText("⌕  Keresés folyamatban…");
        statusText.setText("HardverApró hirdetések lekérése…");
        final String location=locationInput.getText().toString().trim();
        final List<MarketScanner.SearchRule> snapshot=new ArrayList<>(rules);

        new Thread(() -> {
            MarketScanner.ScanResult result = MarketScanner.scan(this, snapshot, "", location);
            runOnUiThread(() -> {
                deals.clear(); deals.addAll(result.deals);
                renderDeals();
                statusText.setText("v0.6 • " + result.status.replace(" • Marketplace token hiányzik", ""));
                scanButton.setEnabled(true);
                scanButton.setText("⌕  HardverApró keresés most");
                if(result.deals.isEmpty()) Toast.makeText(this,"Most nem jött HardverApró találat.",Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void scheduleAutoScan(boolean enabled){
        WorkManager wm=WorkManager.getInstance(this);
        if(!enabled){ wm.cancelUniqueWork("ScrapefinderScan"); return; }
        Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(ScanWorker.class,15, TimeUnit.MINUTES)
                .setConstraints(constraints).build();
        wm.enqueueUniquePeriodicWork("ScrapefinderScan", ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    private TextView tv(String text,int size,int color){
        TextView t=new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(color); return t;
    }

    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(16),dp(14),dp(16),dp(14));
        c.setBackgroundResource(R.drawable.card_bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,dp(10),0,0); c.setLayoutParams(lp); return c;
    }

    private void renderRules(){
        rulesContainer.removeAllViews();
        for(MarketScanner.SearchRule r:new ArrayList<>(rules)){
            LinearLayout c=card();
            c.setOrientation(LinearLayout.HORIZONTAL);
            c.setGravity(Gravity.CENTER_VERTICAL);

            TextView dot=tv("●",18,Color.rgb(73,209,125));
            LinearLayout.LayoutParams dotLp=new LinearLayout.LayoutParams(dp(28),-2);
            dot.setLayoutParams(dotLp);
            c.addView(dot);

            LinearLayout info=new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoLp=new LinearLayout.LayoutParams(0,-2,1f);
            info.setLayoutParams(infoLp);
            TextView title=tv(r.query,16,Color.WHITE); title.setTypeface(null, android.graphics.Typeface.BOLD);
            info.addView(title);
            info.addView(tv(r.maxPrice>0?"Max: "+formatFt(r.maxPrice):"Nincs árkorlát",13,Color.LTGRAY));
            c.addView(info);

            TextView menu=tv("⋮",28,Color.LTGRAY);
            menu.setGravity(Gravity.CENTER);
            menu.setPadding(dp(10),0,0,0);
            c.addView(menu,new LinearLayout.LayoutParams(dp(36),dp(48)));

            android.view.View.OnLongClickListener remove=v->{ rules.remove(r); MarketScanner.saveRules(this,rules); renderRules(); return true; };
            c.setOnLongClickListener(remove); menu.setOnLongClickListener(remove);
            rulesContainer.addView(c);
        }
    }

    private void renderDeals(){
        resultsContainer.removeAllViews();
        if(deals.isEmpty()){
            TextView empty=tv("⌕  A találatok itt jelennek meg. A Facebookhoz használd a bejelentkezés gombot.",14,Color.LTGRAY);
            empty.setPadding(0,dp(10),0,0); resultsContainer.addView(empty); return;
        }
        for(MarketScanner.Deal d:deals){
            LinearLayout c=card();
            c.addView(tv((d.score>=85?"●  ":"")+d.score+"/100",16,d.score>=85?Color.rgb(73,209,125):Color.LTGRAY));
            TextView title=tv(d.title,17,Color.WHITE); title.setTypeface(null, android.graphics.Typeface.BOLD); c.addView(title);
            c.addView(tv(formatFt(d.price)+" • "+d.source,14,Color.LTGRAY));
            if(d.location!=null&&!d.location.isEmpty()) c.addView(tv("⌖  "+d.location,13,Color.LTGRAY));
            c.addView(tv("Hiba: "+d.issue,13,Color.LTGRAY));
            Button open=new Button(this); open.setText("Hirdetés megnyitása");
            open.setOnClickListener(v->{ try{ startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(d.url))); }catch(Exception ignored){} });
            c.addView(open); resultsContainer.addView(c);
        }
    }

    private String formatFt(int value){ return NumberFormat.getIntegerInstance(new Locale("hu","HU")).format(value)+" Ft"; }

    private void requestNotifications(){
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},77);
    }
}
