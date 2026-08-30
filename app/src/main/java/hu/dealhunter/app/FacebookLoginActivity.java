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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private boolean pageActionScheduled = false;
    private int currentRuleIndex = 0;
    private int extractionRetry = 0;
    private List<MarketScanner.SearchRule> scanRules;
    private final JSONArray accumulatedResults = new JSONArray();
    private final Set<String> accumulatedUrls = new HashSet<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(MarketScanner.PREFS, Context.MODE_PRIVATE);
        autoSearch = getIntent().getBooleanExtra("auto_search", false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(16,19,24));
        root.setPadding(0, getStatusBarHeight(), 0, 0);

        info = new TextView(this);
        info.setText("Jelentkezz be a Facebookba. Belépés után a Scrapefinder sorban lefuttatja az aktív Marketplace kereséseket.");
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
                    if(!scanning) info.setText("Facebook bejelentkezve. A Marketplace keresés indítható.");
                    if(autoSearch && !scanning){
                        autoSearch=false;
                        handler.postDelayed(() -> startMarketplaceSearch(), 500);
                    }
                }

                if(scanning && url.contains("facebook.com/marketplace")) schedulePageAction();
            }
        });

        marketplace.setOnClickListener(v -> startMarketplaceSearch());

        if(prefs.getBoolean(KEY_FB_LOGGED_IN,false)) {
            info.setText("Facebook session megtalálva.");
            marketplace.setText("✓ MARKETPLACE KERESÉS INDÍTÁSA");
            if(autoSearch){
                autoSearch=false;
                handler.postDelayed(() -> startMarketplaceSearch(), 300);
            } else {
                webView.loadUrl("https://www.facebook.com/marketplace/");
            }
        } else {
            webView.loadUrl("https://www.facebook.com/login/");
        }
    }

    private void startMarketplaceSearch(){
        scanRules = MarketScanner.loadRules(this);
        if(scanRules.isEmpty()){
            Toast.makeText(this,"Nincs aktív keresés.",Toast.LENGTH_SHORT).show();
            return;
        }

        scanning=true;
        currentRuleIndex=0;
        extractionRetry=0;
        accumulatedUrls.clear();
        while(accumulatedResults.length()>0) accumulatedResults.remove(0);
        marketplace.setEnabled(false);
        openCurrentRule();
    }

    private void openCurrentRule(){
        if(!scanning) return;
        if(currentRuleIndex>=scanRules.size()){
            finishMarketplaceSearch();
            return;
        }

        MarketScanner.SearchRule r=scanRules.get(currentRuleIndex);
        extractionRetry=0;
        pageActionScheduled=false;
        info.setText("Marketplace keresés "+(currentRuleIndex+1)+"/"+scanRules.size()+": "+r.query+" …");

        try {
            String q=URLEncoder.encode(r.query,"UTF-8");
            String url="https://www.facebook.com/marketplace/search/?query="+q+"&exact=false";
            if(r.maxPrice>0) url += "&maxPrice="+r.maxPrice;
            webView.loadUrl(url);

            // Ha a Facebook a kereső kezdőoldalára dob vissza, a JS fallback beírja és Enterrel elküldi a keresést.
            handler.postDelayed(this::schedulePageAction, 3500);
        } catch(Exception e){
            currentRuleIndex++;
            openCurrentRule();
        }
    }

    private void schedulePageAction(){
        if(!scanning || pageActionScheduled) return;
        pageActionScheduled=true;
        handler.postDelayed(() -> {
            pageActionScheduled=false;
            if(!scanning) return;
            ensureSearchSubmittedThenExtract();
        }, 1800);
    }

    private void ensureSearchSubmittedThenExtract(){
        if(currentRuleIndex>=scanRules.size()) return;
        MarketScanner.SearchRule r=scanRules.get(currentRuleIndex);
        String escaped=jsEscape(r.query);

        String js="(function(){"
                +"var links=document.querySelectorAll('a[href*=\\\"/marketplace/item/\\\"]');"
                +"if(links&&links.length>0)return 'HAS_RESULTS';"
                +"var selectors=['input[placeholder*=\\\"Mit szeretnél venni\\\"]','input[aria-label*=\\\"Keresés\\\"]','input[aria-label*=\\\"Search Marketplace\\\"]','input[role=\\\"combobox\\\"]','input[type=\\\"search\\\"]'];"
                +"var input=null;for(var i=0;i<selectors.length&&!input;i++){try{input=document.querySelector(selectors[i]);}catch(e){}}"
                +"if(!input){var ins=document.querySelectorAll('input');for(var j=0;j<ins.length;j++){var p=((ins[j].placeholder||'')+' '+(ins[j].getAttribute('aria-label')||'')).toLowerCase();if(p.indexOf('venni')>=0||p.indexOf('marketplace')>=0||p.indexOf('keres')>=0){input=ins[j];break;}}}"
                +"if(!input)return 'NO_INPUT';"
                +"try{var setter=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;setter.call(input,'"+escaped+"');}catch(e){input.value='"+escaped+"';}"
                +"input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));input.focus();"
                +"['keydown','keypress','keyup'].forEach(function(t){input.dispatchEvent(new KeyboardEvent(t,{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));});"
                +"if(input.form){try{if(input.form.requestSubmit)input.form.requestSubmit();else input.form.submit();}catch(e){}}"
                +"return 'SUBMITTED';})()";

        webView.evaluateJavascript(js, value -> {
            String result=value==null?"":value.replace("\"","");
            if("SUBMITTED".equals(result)) {
                info.setText("Marketplace keresés "+(currentRuleIndex+1)+"/"+scanRules.size()+": "+r.query+" • elküldve…");
                handler.postDelayed(() -> extractMarketplaceResults(webView), 4500);
            } else if("HAS_RESULTS".equals(result)) {
                handler.postDelayed(() -> extractMarketplaceResults(webView), 1800);
            } else {
                // A Facebook DOM néha később épül fel, ezért még egyszer megpróbáljuk.
                handler.postDelayed(() -> extractMarketplaceResults(webView), 3000);
            }
        });
    }

    private void extractMarketplaceResults(WebView view){
        if(!scanning || currentRuleIndex>=scanRules.size()) return;

        String js="(function(){var out=[];var seen={};var as=document.querySelectorAll('a[href*=\\\"/marketplace/item/\\\"]');"
                +"for(var i=0;i<as.length&&out.length<60;i++){var a=as[i];var href=a.href;if(!href||seen[href])continue;seen[href]=1;"
                +"var p=a;for(var n=0;n<5&&p.parentElement;n++)p=p.parentElement;var txt=(p.innerText||a.innerText||'').trim();"
                +"if(txt.length<3)continue;out.push({url:href,text:txt});}return JSON.stringify(out);})()";

        view.evaluateJavascript(js, value -> {
            try {
                String decoded=value;
                if(decoded!=null && decoded.length()>=2 && decoded.startsWith("\"") && decoded.endsWith("\""))
                    decoded=new JSONArray("["+decoded+"]").getString(0);
                JSONArray arr=new JSONArray(decoded==null?"[]":decoded);

                if(arr.length()==0 && extractionRetry<1){
                    extractionRetry++;
                    info.setText("Marketplace: találatok betöltése… újrapróbálom");
                    handler.postDelayed(this::ensureSearchSubmittedThenExtract, 2500);
                    return;
                }

                MarketScanner.SearchRule rule=scanRules.get(currentRuleIndex);
                int added=0;
                for(int i=0;i<arr.length();i++){
                    JSONObject o=arr.optJSONObject(i); if(o==null)continue;
                    String text=o.optString("text","");
                    String url=o.optString("url","");
                    if(url.isEmpty() || accumulatedUrls.contains(url)) continue;
                    int price=extractPrice(text);
                    if(rule.maxPrice>0 && price>rule.maxPrice && price>0) continue;
                    accumulatedUrls.add(url);
                    accumulatedResults.put(new JSONObject()
                            .put("title",firstLine(text))
                            .put("text",text)
                            .put("url",url)
                            .put("price",price)
                            .put("query",rule.query));
                    added++;
                }

                info.setText("Marketplace "+(currentRuleIndex+1)+"/"+scanRules.size()+": "+added+" új találat");
            } catch(Exception ignored) {}

            currentRuleIndex++;
            handler.postDelayed(this::openCurrentRule, 700);
        });
    }

    private void finishMarketplaceSearch(){
        prefs.edit().putString(KEY_FB_RESULTS,accumulatedResults.toString()).apply();
        scanning=false;
        marketplace.setEnabled(true);
        info.setText("Marketplace kész: "+accumulatedResults.length()+" találat. Visszatérés a Scrapefinderbe…");
        Toast.makeText(this,"Marketplace: "+accumulatedResults.length()+" találat elmentve.",Toast.LENGTH_LONG).show();
        handler.postDelayed(this::finish, 1200);
    }

    private int extractPrice(String s){
        try{
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("([0-9][0-9 .\\u00A0]{2,})\\s*(Ft|HUF)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(s);
            if(m.find()){
                String d=m.group(1).replaceAll("[^0-9]","");
                return d.isEmpty()?0:Integer.parseInt(d);
            }
        }catch(Exception ignored){}
        return 0;
    }

    private String firstLine(String s){
        int n=s.indexOf('\n');
        return (n>0?s.substring(0,n):s).trim();
    }

    private String jsEscape(String s){
        return s.replace("\\","\\\\").replace("'","\\'").replace("\"","\\\"").replace("\n"," ").replace("\r"," ");
    }

    private int getStatusBarHeight(){
        int id=getResources().getIdentifier("status_bar_height","dimen","android");
        return id>0?getResources().getDimensionPixelSize(id):0;
    }

    @Override protected void onPause(){
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override public void onBackPressed(){
        if(scanning){
            scanning=false;
            marketplace.setEnabled(true);
            info.setText("Marketplace keresés megszakítva.");
            return;
        }
        if(webView!=null&&webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
