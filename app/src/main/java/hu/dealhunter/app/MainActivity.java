package hu.dealhunter.app;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.NumberFormat;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout rulesContainer, resultsContainer;
    private EditText queryInput, maxPriceInput;
    private final List<SearchRule> rules = new ArrayList<>();
    private final List<Deal> deals = new ArrayList<>();
    private int dp(float v){ return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b); setContentView(R.layout.activity_main);
        queryInput=findViewById(R.id.queryInput); maxPriceInput=findViewById(R.id.maxPriceInput);
        rulesContainer=findViewById(R.id.rulesContainer); resultsContainer=findViewById(R.id.resultsContainer);
        findViewById(R.id.addRuleButton).setOnClickListener(v -> addRule());
        findViewById(R.id.testButton).setOnClickListener(v -> addTestDeal());
        findViewById(R.id.updateButton).setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/NandorHU/Scrapefinder/releases/latest"))); }
            catch (Exception e) { Toast.makeText(this, "A frissítési oldal nem nyitható meg.", Toast.LENGTH_SHORT).show(); }
        });
        seed(); renderAll(); requestNotifications();
    }

    private void seed(){
        rules.add(new SearchRule("PS5 hibás",70000));
        rules.add(new SearchRule("Xbox Series X hibás",60000));
        rules.add(new SearchRule("laptop hibás",80000));
        deals.add(new Deal("PS5 Disc – nincs kép",42990,"HardverApró","HDMI / HDMI IC gyanú",92,"https://hardverapro.hu/"));
        deals.add(new Deal("Xbox Series X – nem kapcsol be",49900,"Marketplace","PSU / alaplap",76,"https://www.facebook.com/marketplace/"));
    }

    private void addRule(){
        String q=queryInput.getText().toString().trim();
        if(q.isEmpty()){ Toast.makeText(this,"Adj meg keresést.",Toast.LENGTH_SHORT).show(); return; }
        int p=0; try { p=Integer.parseInt(maxPriceInput.getText().toString().trim()); } catch(Exception ignored){}
        rules.add(0,new SearchRule(q,p)); queryInput.setText(""); maxPriceInput.setText(""); renderRules();
    }

    private void addTestDeal(){
        int[] prices={35990,44990,57990,69990}; String[] titles={"PS5 Slim hibás – csak pittyen","PS5 nincs kép, leesett","Xbox Series S alkatrésznek","Gaming laptop nem tölt"};
        int i=new Random().nextInt(titles.length); int score=65+new Random().nextInt(34);
        deals.add(0,new Deal(titles[i],prices[i], i%2==0?"HardverApró":"Marketplace", score>85?"jó javítási esély":"ellenőrizendő",score, i%2==0?"https://hardverapro.hu/":"https://www.facebook.com/marketplace/"));
        renderDeals(); notifyDeal(deals.get(0));
    }

    private void renderAll(){ renderRules(); renderDeals(); }
    private TextView tv(String text,int size,int color){ TextView t=new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(color); return t; }
    private LinearLayout card(){ LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(16),dp(14),dp(16),dp(14)); c.setBackgroundResource(R.drawable.card_bg); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,dp(10),0,0); c.setLayoutParams(lp); return c; }

    private void renderRules(){
        rulesContainer.removeAllViews();
        for(SearchRule r:rules){ LinearLayout c=card(); c.addView(tv(r.query,16,Color.WHITE)); c.addView(tv(r.maxPrice>0?"Max: "+formatFt(r.maxPrice):"Nincs árkorlát",13,Color.LTGRAY)); c.setOnLongClickListener(v->{ rules.remove(r); renderRules(); return true; }); rulesContainer.addView(c); }
    }

    private void renderDeals(){
        resultsContainer.removeAllViews();
        for(Deal d:deals){ LinearLayout c=card(); TextView score=tv((d.score>=85?"🔥 ":"")+d.score+"/100",16,d.score>=85?Color.rgb(84,208,139):Color.LTGRAY); c.addView(score); c.addView(tv(d.title,17,Color.WHITE)); c.addView(tv(formatFt(d.price)+" • "+d.source,14,Color.LTGRAY)); c.addView(tv("Hiba: "+d.issue,13,Color.LTGRAY)); Button open=new Button(this); open.setText("Hirdetés megnyitása"); open.setOnClickListener(v->{ try{ startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(d.url))); }catch(Exception ignored){} }); c.addView(open); resultsContainer.addView(c); }
    }

    private String formatFt(int value){ return NumberFormat.getIntegerInstance(new Locale("hu","HU")).format(value)+" Ft"; }

    private void requestNotifications(){ if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},77); }
    private void notifyDeal(Deal d){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE); String ch="deals";
        if(android.os.Build.VERSION.SDK_INT>=26) nm.createNotificationChannel(new NotificationChannel(ch,"Új találatok",NotificationManager.IMPORTANCE_HIGH));
        Notification n=new Notification.Builder(this,ch).setContentTitle("Új találat: "+d.score+"/100").setContentText(d.title+" – "+formatFt(d.price)).setSmallIcon(android.R.drawable.star_big_on).setAutoCancel(true).build(); nm.notify((int)(System.currentTimeMillis()%100000),n);
    }

    static class SearchRule { String query; int maxPrice; SearchRule(String q,int p){query=q;maxPrice=p;} }
    static class Deal { String title,source,issue,url; int price,score; Deal(String t,int p,String s,String i,int sc,String u){title=t;price=p;source=s;issue=i;score=sc;url=u;} }
}
