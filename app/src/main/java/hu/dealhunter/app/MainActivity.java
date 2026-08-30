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

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    public static final String KEY_BLACKLIST = "blacklisted_deal_ids";

    private LinearLayout rulesContainer, resultsContainer;
    private EditText queryInput, maxPriceInput, locationInput;
    private CheckBox autoScanCheck;
    private TextView statusText;
    private Button scanButton, facebookLoginButton;
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
        facebookLoginButton=findViewById(R.id.facebookLoginButton);
        rulesContainer=findViewById(R.id.rulesContainer);
        resultsContainer=findViewById(R.id.resultsContainer);

        rules.addAll(MarketScanner.loadRules(this));
        locationInput.setText(prefs.getString(MarketScanner.KEY_LOCATION, "budapest"));
        autoScanCheck.setChecked(prefs.getBoolean(MarketScanner.KEY_AUTO, true));

        facebookLoginButton.setOnClickListener(v -> startActivity(new Intent(this, FacebookLoginActivity.class)));
        findViewById(R.id.addRuleButton).setOnClickListener(v -> addRule());
        findViewById(R.id.saveSettingsButton).setOnClickListener(v -> saveSettings(true));
        scanButton.setOnClickListener(v -> runLiveScan());
        findViewById(R.id.updateButton).setOnClickListener(v -> AppUpdater.checkForUpdate(this, true));
        autoScanCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(MarketScanner.KEY_AUTO, isChecked).apply();
            scheduleAutoScan(isChecked);
        });

        renderRules();
        requestNotifications();
        scheduleAutoScan(autoScanCheck.isChecked());
        updateFacebookState();
        loadFacebookResults();
        removeBlacklistedFromDeals();
        renderDeals();
        statusText.setText("v0.9 • hirdetés feketelista");
        AppUpdater.checkForUpdate(this, false);
    }

    @Override protected void onResume(){
        super.onResume();
        if(prefs==null) return;
        updateFacebookState();
        loadFacebookResults();
        removeBlacklistedFromDeals();
        renderDeals();
    }

    private Set<String> blacklist(){
        return new HashSet<>(prefs.getStringSet(KEY_BLACKLIST, new HashSet<>()));
    }

    private boolean isBlacklisted(MarketScanner.Deal d){
        Set<String> blocked=blacklist();
        return blocked.contains(d.id) || blocked.contains(d.url);
    }

    private void addToBlacklist(MarketScanner.Deal d){
        Set<String> blocked=blacklist();
        blocked.add(d.id);
        if(d.url!=null && !d.url.isEmpty()) blocked.add(d.url);
        prefs.edit().putStringSet(KEY_BLACKLIST, blocked).apply();
        deals.remove(d);
        renderDeals();
        Toast.makeText(this,"Hirdetés feketelistára téve.",Toast.LENGTH_SHORT).show();
    }

    private void removeBlacklistedFromDeals(){
        for(int i=deals.size()-1;i>=0;i--) if(isBlacklisted(deals.get(i))) deals.remove(i);
    }

    private void updateFacebookState(){
        boolean logged=prefs.getBoolean(FacebookLoginActivity.KEY_FB_LOGGED_IN,false);
        facebookLoginButton.setText(logged?"✓ FACEBOOK BEJELENTKEZVE":"ⓕ  BEJELENTKEZÉS FACEBOOKBA");
    }

    private void loadFacebookResults(){
        for(int i=deals.size()-1;i>=0;i--) if("Marketplace".equals(deals.get(i).source)) deals.remove(i);
        String raw=prefs.getString(FacebookLoginActivity.KEY_FB_RESULTS,"[]");
        try{
            JSONArray a=new JSONArray(raw);
            for(int i=0;i<a.length();i++){
                JSONObject o=a.optJSONObject(i); if(o==null) continue;
                String title=o.optString("title","Marketplace hirdetés");
                String url=o.optString("url","");
                int price=o.optInt("price",0);
                if(url.isEmpty()) continue;
                MarketScanner.Deal d=new MarketScanner.Deal("fb:"+url,title,price,"Marketplace","Ellenőrizendő",72,url,"Facebook Marketplace");
                if(!isBlacklisted(d)) deals.add(d);
            }
        }catch(Exception ignored){}
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
        statusText.setText("HardverApró keresés…");
        final String location=locationInput.getText().toString().trim();
        final List<MarketScanner.SearchRule> snapshot=new ArrayList<>(rules);

        new Thread(() -> {
            MarketScanner.ScanResult result = MarketScanner.scan(this, snapshot, "", location);
            runOnUiThread(() -> {
                for(int i=deals.size()-1;i>=0;i--) if("HardverApró".equals(deals.get(i).source)) deals.remove(i);
                for(MarketScanner.Deal d:result.deals) if(!isBlacklisted(d)) deals.add(d);
                loadFacebookResults();
                removeBlacklistedFromDeals();
                renderDeals();
                scanButton.setEnabled(true);
                scanButton.setText("⌕  KERESÉS MOST");
                if(prefs.getBoolean(FacebookLoginActivity.KEY_FB_LOGGED_IN,false)) {
                    statusText.setText("HardverApró kész • Marketplace keresés megnyitása…");
                    Intent fb=new Intent(this,FacebookLoginActivity.class);
                    fb.putExtra("auto_search",true);
                    startActivity(fb);
                } else {
                    statusText.setText("HardverApró kész • Facebookhoz jelentkezz be");
                    Toast.makeText(this,"A Marketplace kereséshez előbb jelentkezz be Facebookba.",Toast.LENGTH_LONG).show();
                }
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

    private TextView tv(String text,int size,int color){ TextView t=new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(color); return t; }
    private LinearLayout card(){ LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(16),dp(14),dp(16),dp(14)); c.setBackgroundResource(R.drawable.card_bg); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,dp(10),0,0); c.setLayoutParams(lp); return c; }

    private void renderRules(){
        rulesContainer.removeAllViews();
        for(MarketScanner.SearchRule r:new ArrayList<>(rules)){
            LinearLayout c=card(); c.setOrientation(LinearLayout.HORIZONTAL); c.setGravity(Gravity.CENTER_VERTICAL);
            TextView dot=tv("●",18,Color.rgb(73,209,125)); dot.setLayoutParams(new LinearLayout.LayoutParams(dp(28),-2)); c.addView(dot);
            LinearLayout info=new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL); info.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
            TextView title=tv(r.query,16,Color.WHITE); title.setTypeface(null, android.graphics.Typeface.BOLD); info.addView(title);
            info.addView(tv(r.maxPrice>0?"Max: "+formatFt(r.maxPrice):"Nincs árkorlát",13,Color.LTGRAY)); c.addView(info);
            TextView menu=tv("⋮",28,Color.LTGRAY); menu.setGravity(Gravity.CENTER); menu.setPadding(dp(10),0,0,0); c.addView(menu,new LinearLayout.LayoutParams(dp(36),dp(48)));
            android.view.View.OnLongClickListener remove=v->{ rules.remove(r); MarketScanner.saveRules(this,rules); renderRules(); return true; };
            c.setOnLongClickListener(remove); menu.setOnLongClickListener(remove); rulesContainer.addView(c);
        }
    }

    private void renderDeals(){
        resultsContainer.removeAllViews();
        if(deals.isEmpty()){
            TextView empty=tv("⌕  A találatok itt jelennek meg.",14,Color.LTGRAY); empty.setPadding(0,dp(10),0,0); resultsContainer.addView(empty); return;
        }
        for(MarketScanner.Deal d:new ArrayList<>(deals)){
            if(isBlacklisted(d)) continue;
            LinearLayout c=card();
            c.addView(tv((d.score>=85?"●  ":"")+d.score+"/100",16,d.score>=85?Color.rgb(73,209,125):Color.LTGRAY));
            TextView title=tv(d.title,17,Color.WHITE); title.setTypeface(null, android.graphics.Typeface.BOLD); c.addView(title);
            c.addView(tv((d.price>0?formatFt(d.price):"Ár nem olvasható")+" • "+d.source,14,Color.LTGRAY));
            if(d.location!=null&&!d.location.isEmpty()) c.addView(tv("⌖  "+d.location,13,Color.LTGRAY));

            LinearLayout actions=new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0,dp(8),0,0);

            Button open=new Button(this); open.setText("MEGNYITÁS");
            open.setOnClickListener(v->{ try{ startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(d.url))); }catch(Exception ignored){} });
            actions.addView(open,new LinearLayout.LayoutParams(0,dp(52),1f));

            Button block=new Button(this); block.setText("⊘ FEKETELISTA");
            LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(0,dp(52),1f); blp.setMargins(dp(8),0,0,0);
            block.setOnClickListener(v->addToBlacklist(d));
            actions.addView(block,blp);
            c.addView(actions);

            resultsContainer.addView(c);
        }
    }

    private String formatFt(int value){ return NumberFormat.getIntegerInstance(new Locale("hu","HU")).format(value)+" Ft"; }
    private void requestNotifications(){ if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},77); }
}
