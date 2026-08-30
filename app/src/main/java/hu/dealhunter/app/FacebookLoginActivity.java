package hu.dealhunter.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.List;

public class FacebookLoginActivity extends Activity {
    public static final String KEY_FB_LOGGED_IN = "fb_logged_in";
    public static final String KEY_FB_RESULTS = "fb_results_json";
    private WebView webView;
    private TextView info;
    private Button marketplace;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean scanning = false;
    private boolean autoSearch = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(MarketScanner.PREFS, Context.MODE_PRIVATE);
        autoSearch = getIntent().getBooleanExtra("auto_search", false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(16,19,24));
        root.setPadding(0, getStatusBarHeight(), 0, 0);

        info = new TextView(this);
        info.setText("Jelentkezz be a Facebookba. Belépés után a Scrapefinder közvetlenül a Marketplace keresőoldalait nyitja meg.");
        info.setTextColor(Color.LTGRAY);
        info.setTextSize(14);
        info.setPadding(24,16,24,12);
        root.addView(info, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        marketplace = new Button(this);
        marketplace.setText("MARKETPLACE KERESÉS INDÍTÁSA");
        root.addView(marketplace, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        setContentView(root);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (android.os.Build.VERSION.SDK_INT >= 21) cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view,url);
                CookieManager.getInstance().flush();
                if(url.contains("facebook.com") && !url.contains("/login")) {
                    prefs.edit().putBoolean(KEY_FB_LOGGED_IN,true).apply();
                    marketplace.setText("✓ MARKETPLACE KERESÉS INDÍTÁSA");
                    info.setText("Facebook bejelentkezve. A Marketplace keresés indítható.");
                    if(autoSearch && !scanning){ autoSearch=false; handler.postDelayed(() -> startMarketplaceSearch(), 500); }
                }
                if(scanning && url.contains("/marketplace/") && url.contains("query=")) {
                    handler.postDelayed(() -> extractMarketplaceResults(view), 4000);
                }
            }
        });

        marketplace.setOnClickListener(v -> startMarketplaceSearch());

        if(prefs.getBoolean(KEY_FB_LOGGED_IN,false)) {
            info.setText("Facebook session megtalálva.");
            marketplace.setText("✓ MARKETPLACE KERESÉS INDÍTÁSA");
            if(autoSearch){ autoSearch=false; handler.postDelayed(() -> startMarketplaceSearch(), 250); }
            else webView.loadUrl("https://www.facebook.com/marketplace/");
        } else {
            webView.loadUrl("https://www.facebook.com/login/");
        }
    }

    private void startMarketplaceSearch(){
        List<MarketScanner.SearchRule> rules = MarketScanner.loadRules(this);
        if(rules.isEmpty()){ Toast.makeText(this,"Nincs aktív keresés.",Toast.LENGTH_SHORT).show(); return; }
        MarketScanner.SearchRule r=rules.get(0);
        try {
            String q=URLEncoder.encode(r.query,"UTF-8");
            String url="https://www.facebook.com/marketplace/search/?query="+q+"&exact=false";
            if(r.maxPrice>0) url += "&maxPrice="+r.maxPrice;
            scanning=true;
            info.setText("Marketplace keresés: "+r.query+" …");
            webView.loadUrl(url);
        } catch(Exception e){ Toast.makeText(this,"A keresés nem indítható.",Toast.LENGTH_LONG).show(); }
    }

    private void extractMarketplaceResults(WebView view){
        String js="(function(){var out=[];var seen={};var as=document.querySelectorAll('a[href*=\\\"/marketplace/item/\\\"]');for(var i=0;i<as.length&&out.length<40;i++){var a=as[i];var href=a.href;if(!href||seen[href])continue;seen[href]=1;var p=a;for(var n=0;n<4&&p.parentElement;n++)p=p.parentElement;var txt=(p.innerText||a.innerText||'').trim();if(txt.length<3)continue;out.push({url:href,text:txt});}return JSON.stringify(out);})()";
        view.evaluateJavascript(js, value -> {
            try {
                String decoded=value;
                if(decoded!=null && decoded.length()>=2 && decoded.startsWith("\"") && decoded.endsWith("\"")) decoded=new JSONArray("["+decoded+"]").getString(0);
                JSONArray arr=new JSONArray(decoded==null?"[]":decoded);
                JSONArray clean=new JSONArray();
                List<MarketScanner.SearchRule> rules=MarketScanner.loadRules(this);
                MarketScanner.SearchRule rule=rules.isEmpty()?new MarketScanner.SearchRule("",0):rules.get(0);
                for(int i=0;i<arr.length();i++){
                    JSONObject o=arr.optJSONObject(i); if(o==null)continue;
                    String text=o.optString("text",""); String url=o.optString("url","");
                    int price=extractPrice(text);
                    if(rule.maxPrice>0 && price>rule.maxPrice && price>0) continue;
                    clean.put(new JSONObject().put("title",firstLine(text)).put("text",text).put("url",url).put("price",price));
                }
                prefs.edit().putString(KEY_FB_RESULTS,clean.toString()).apply();
                info.setText("Marketplace találatok: "+clean.length()+". Visszaléphetsz a Scrapefinderbe.");
                Toast.makeText(this,"Marketplace: "+clean.length()+" találat elmentve.",Toast.LENGTH_LONG).show();
            } catch(Exception e){ info.setText("A Marketplace betöltődött, de a találatokat most nem sikerült kiolvasni."); }
            scanning=false;
        });
    }

    private int extractPrice(String s){
        try{ java.util.regex.Matcher m=java.util.regex.Pattern.compile("([0-9][0-9 .\\u00A0]{2,})\\s*(Ft|HUF)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(s); if(m.find()){ String d=m.group(1).replaceAll("[^0-9]",""); return d.isEmpty()?0:Integer.parseInt(d); } }catch(Exception ignored){} return 0;
    }
    private String firstLine(String s){ int n=s.indexOf('\n'); return (n>0?s.substring(0,n):s).trim(); }
    private int getStatusBarHeight(){ int id=getResources().getIdentifier("status_bar_height","dimen","android"); return id>0?getResources().getDimensionPixelSize(id):0; }

    @Override protected void onPause(){ super.onPause(); CookieManager.getInstance().flush(); }
    @Override public void onBackPressed(){ if(webView!=null&&webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }
}
