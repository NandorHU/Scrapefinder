package hu.dealhunter.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MarketScanner {
    public static final String PREFS = "scrapefinder";
    public static final String KEY_APIFY_TOKEN = "apify_token";
    public static final String KEY_LOCATION = "marketplace_location";
    public static final String KEY_RULES = "rules_json";
    public static final String KEY_AUTO = "auto_scan";

    private MarketScanner() {}

    public static final class SearchRule {
        public String query;
        public int maxPrice;
        public SearchRule(String q, int p) { query = q; maxPrice = p; }
    }

    public static final class Deal {
        public String id, title, source, issue, url, location;
        public int price, score;
        Deal(String id, String title, int price, String source, String issue, int score, String url, String location) {
            this.id=id; this.title=title; this.price=price; this.source=source; this.issue=issue;
            this.score=score; this.url=url; this.location=location;
        }
    }

    public static final class ScanResult {
        public final List<Deal> deals;
        public final String status;
        ScanResult(List<Deal> d, String s) { deals=d; status=s; }
    }

    public static ScanResult scan(Context context, List<SearchRule> rules, String apifyToken, String locationSlug) {
        Map<String, Deal> unique = new LinkedHashMap<>();
        int haCount = 0;
        int fbCount = 0;
        StringBuilder errors = new StringBuilder();

        for (SearchRule rule : rules) {
            try {
                List<Deal> found = scanHardverApro(rule);
                haCount += found.size();
                for (Deal d : found) unique.put(d.id, d);
            } catch (Exception e) {
                if (errors.length() > 0) errors.append(" • ");
                errors.append("HardverApró: ").append(shortMessage(e));
            }
        }

        if (apifyToken != null && !apifyToken.trim().isEmpty()) {
            try {
                List<Deal> found = scanMarketplace(rules, apifyToken.trim(), locationSlug);
                fbCount += found.size();
                for (Deal d : found) unique.put(d.id, d);
            } catch (Exception e) {
                if (errors.length() > 0) errors.append(" • ");
                errors.append("Marketplace: ").append(shortMessage(e));
            }
        }

        List<Deal> all = new ArrayList<>(unique.values());
        Collections.sort(all, Comparator.comparingInt((Deal d) -> d.score).reversed());
        String status = "HardverApró: " + haCount + " • Marketplace: " + fbCount;
        if (apifyToken == null || apifyToken.trim().isEmpty()) status += " • Marketplace token hiányzik";
        if (errors.length() > 0) status += "\n" + errors;
        return new ScanResult(all, status);
    }

    private static List<Deal> scanHardverApro(SearchRule rule) throws Exception {
        String url = "https://hardverapro.hu/aprok/keres.php?stext=" + enc(rule.query);
        String html = httpGet(url, null);
        List<Deal> out = new ArrayList<>();

        Pattern linkPattern = Pattern.compile("<a[^>]+href\\s*=\\s*[\\\"']([^\\\"']*/apro/[^\\\"']+)[\\\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = linkPattern.matcher(html);
        int guard = 0;
        while (m.find() && guard++ < 140) {
            String href = decodeEntities(m.group(1));
            String title = cleanHtml(m.group(2));
            if (title.length() < 3 || isNoiseTitle(title)) continue;
            if (!href.startsWith("http")) href = "https://hardverapro.hu" + (href.startsWith("/") ? href : "/" + href);

            int from = Math.max(0, m.start() - 500);
            int to = Math.min(html.length(), m.end() + 2200);
            String neighborhood = cleanHtml(html.substring(from, to));
            int price = extractFt(neighborhood);
            if (price <= 0) continue;
            if (rule.maxPrice > 0 && price > rule.maxPrice) continue;

            // A modell/termék azonosító kulcsszavaknak a címben kell lenniük.
            // A hibára utaló szavak továbbra is lehetnek a címben vagy a leírásban.
            // Példa: "PS5 Slim" cím + "nem kapcsol be" leírás = találat,
            // de egy általános szervizhirdetés, amely csak a leírásban említi a PS5-öt, kiesik.
            if (!hardverAproRelevant(title, neighborhood, rule.query)) continue;

            String id = "ha:" + href.replaceAll("[?#].*$", "");
            String searchable = title + " " + neighborhood;
            out.add(new Deal(id, title, price, "HardverApró", classifyIssue(searchable), score(searchable, price, rule.maxPrice), href, ""));
        }
        return out;
    }

    private static boolean hardverAproRelevant(String title, String neighborhood, String query) {
        String t = normalize(title);
        String q = normalize(query);
        if (t.isEmpty() || q.isEmpty()) return false;

        String[] parts = q.split("\\s+");
        int identityCount = 0;
        for (String p : parts) {
            if (p.length() < 3 || isFaultQueryWord(p)) continue;
            identityCount++;
            if (!t.contains(p)) return false;
        }

        // Ha a keresés csak hibaszavakból állna, ne blokkoljuk teljesen.
        if (identityCount == 0) {
            String all = normalize(title + " " + neighborhood);
            return matchesRule(all, query);
        }
        return true;
    }

    private static boolean isFaultQueryWord(String p) {
        return p.equals("hibas") || p.equals("hiba") || p.equals("rossz") ||
                p.equals("nem") || p.equals("kapcsol") || p.equals("indul") ||
                p.equals("mukodik") || p.equals("tolti") || p.equals("tolt") ||
                p.equals("kep") || p.equals("hdmi") || p.equals("donor") ||
                p.equals("alkatresz") || p.equals("beazott") || p.equals("vizes") ||
                p.equals("pittyen") || p.equals("beep") || p.equals("hibasodott");
    }

    private static List<Deal> scanMarketplace(List<SearchRule> rules, String token, String locationSlug) throws Exception {
        String slug = sanitizeLocation(locationSlug);
        JSONArray starts = new JSONArray();
        for (SearchRule rule : rules) {
            StringBuilder u = new StringBuilder("https://www.facebook.com/marketplace/")
                    .append(slug).append("/search/?query=").append(enc(rule.query)).append("&exact=false&radius=500");
            if (rule.maxPrice > 0) u.append("&maxPrice=").append(rule.maxPrice);
            starts.put(new JSONObject().put("url", u.toString()));
        }
        JSONObject body = new JSONObject();
        body.put("startUrls", starts);
        body.put("resultsLimit", Math.max(20, Math.min(80, rules.size() * 20)));
        body.put("includeListingDetails", false);

        String endpoint = "https://api.apify.com/v2/acts/apify~facebook-marketplace-scraper/run-sync-get-dataset-items?clean=true&format=json";
        String json = httpPostJson(endpoint, body.toString(), token);
        JSONArray arr = new JSONArray(json);
        List<Deal> out = new ArrayList<>();
        for (int i=0; i<arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String title = first(o, "marketplace_listing_title", "listingTitle", "title");
            String listingUrl = first(o, "listingUrl", "itemUrl", "url");
            String rawId = first(o, "id", "listing_id");
            if (title.isEmpty() || listingUrl.isEmpty()) continue;
            int price = marketplacePrice(o);
            if (price <= 0) continue;

            SearchRule matched = bestRule(title, rules);
            if (matched == null) continue;
            if (matched.maxPrice > 0 && price > matched.maxPrice) continue;

            String loc = marketplaceLocation(o);
            String id = "fb:" + (!rawId.isEmpty() ? rawId : listingUrl);
            out.add(new Deal(id, title, price, "Marketplace", classifyIssue(title), score(title, price, matched.maxPrice), listingUrl, loc));
        }
        return out;
    }

    private static SearchRule bestRule(String title, List<SearchRule> rules) {
        for (SearchRule r : rules) if (matchesRule(title, r.query)) return r;
        return null;
    }

    private static boolean matchesRule(String title, String query) {
        String t = normalize(title);
        String q = normalize(query);
        String[] parts = q.split("\\s+");
        int matched = 0;
        for (String p : parts) if (p.length() >= 2 && t.contains(p)) matched++;
        return matched >= Math.max(1, Math.min(parts.length, 2));
    }

    private static int score(String title, int price, int maxPrice) {
        String t = normalize(title);
        int s = 48;
        if (containsAny(t, "hibas", "nem kapcsol", "nem ad kep", "nincs kep", "alkatresz", "pittyen", "beep", "hdmi", "nem tolt")) s += 22;
        if (containsAny(t, "ps5", "playstation 5", "series x", "series s", "gaming laptop")) s += 10;
        if (containsAny(t, "hdmi", "tap", "psu", "csatlakozo", "port", "nem tolt")) s += 8;
        if (containsAny(t, "folyadek", "vizes", "beazott", "apu", "cpu", "gpu", "alaplap hibas", "donor")) s -= 12;
        if (containsAny(t, "kontroller", "jatek", "doboz", "keresek", "csere")) s -= 30;
        if (maxPrice > 0 && price > 0) {
            double ratio = (double)price / (double)maxPrice;
            if (ratio <= .55) s += 12;
            else if (ratio <= .75) s += 7;
            else if (ratio <= .9) s += 3;
        }
        return Math.max(0, Math.min(100, s));
    }

    private static String classifyIssue(String title) {
        String t = normalize(title);
        if (containsAny(t, "hdmi", "nincs kep", "nem ad kep")) return "HDMI / kép hiba";
        if (containsAny(t, "nem kapcsol", "nem indul", "pittyen", "beep")) return "Táp / alaplap";
        if (containsAny(t, "nem tolt", "tolto", "usb c")) return "Töltés / csatlakozó";
        if (containsAny(t, "folyadek", "beazott", "vizes")) return "Folyadékkár";
        if (containsAny(t, "alkatresz", "donor")) return "Alkatrész / donor";
        return "Ellenőrizendő";
    }

    private static int marketplacePrice(JSONObject o) {
        JSONObject p = o.optJSONObject("listing_price");
        if (p == null) p = o.optJSONObject("listingPrice");
        if (p != null) {
            String amount = p.optString("amount", "");
            if (!amount.isEmpty()) {
                try { return (int)Math.round(Double.parseDouble(amount.replace(',', '.'))); } catch (Exception ignored) {}
            }
            String formatted = p.optString("formatted_amount", p.optString("formatted_amount_zeros_stripped", ""));
            int x = digitsToInt(formatted); if (x > 0) return x;
        }
        return digitsToInt(o.optString("price", ""));
    }

    private static String marketplaceLocation(JSONObject o) {
        try {
            JSONObject loc = o.optJSONObject("location");
            if (loc != null) {
                JSONObject rev = loc.optJSONObject("reverse_geocode");
                if (rev != null) {
                    String city = rev.optString("city", "");
                    String state = rev.optString("state", "");
                    return (city + (state.isEmpty() ? "" : ", " + state)).trim();
                }
            }
            return o.optString("locationText", "");
        } catch (Exception e) { return ""; }
    }

    public static void saveRules(Context c, List<SearchRule> rules) {
        JSONArray arr = new JSONArray();
        for (SearchRule r : rules) arr.put(new JSONObjectSafe().putSafe("q", r.query).putSafe("p", r.maxPrice).obj);
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_RULES, arr.toString()).apply();
    }

    public static List<SearchRule> loadRules(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = p.getString(KEY_RULES, "");
        List<SearchRule> out = new ArrayList<>();
        if (!raw.isEmpty()) {
            try {
                JSONArray a = new JSONArray(raw);
                for (int i=0;i<a.length();i++) {
                    JSONObject o=a.getJSONObject(i); out.add(new SearchRule(o.optString("q"), o.optInt("p")));
                }
            } catch (Exception ignored) {}
        }
        if (out.isEmpty()) {
            out.add(new SearchRule("PS5 hibás", 70000));
            out.add(new SearchRule("PS5 nem kapcsol be", 70000));
            out.add(new SearchRule("PS5 HDMI", 70000));
            out.add(new SearchRule("Xbox Series X hibás", 60000));
            out.add(new SearchRule("Series S hibás", 45000));
            out.add(new SearchRule("laptop hibás", 80000));
        }
        return out;
    }

    private static String httpGet(String url, String bearer) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(18000); c.setRequestMethod("GET");
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Scrapefinder/0.11");
        c.setRequestProperty("Accept-Language", "hu-HU,hu;q=0.9,en;q=0.7");
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
        return readResponse(c);
    }

    private static String httpPostJson(String url, String body, String token) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(120000); c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + token);
        try(OutputStream os=c.getOutputStream()){ os.write(body.getBytes(StandardCharsets.UTF_8)); }
        return readResponse(c);
    }

    private static String readResponse(HttpURLConnection c) throws Exception {
        int code=c.getResponseCode(); InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
        StringBuilder sb=new StringBuilder();
        if(in!=null) try(BufferedReader br=new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))){ String line; while((line=br.readLine())!=null) sb.append(line).append('\n'); }
        if(code<200||code>=300) throw new Exception("HTTP "+code+" "+trim(sb.toString(),180));
        return sb.toString();
    }

    private static int extractFt(String s) {
        Matcher m=Pattern.compile("([0-9][0-9\\s\\u00A0.]{2,})\\s*Ft", Pattern.CASE_INSENSITIVE).matcher(s);
        if(m.find()) return digitsToInt(m.group(1)); return 0;
    }
    private static int digitsToInt(String s) { try { String d=s.replaceAll("[^0-9]",""); return d.isEmpty()?0:Integer.parseInt(d); } catch(Exception e){ return 0; } }
    private static String first(JSONObject o, String... keys){ for(String k:keys){ String v=o.optString(k,""); if(!v.isEmpty()) return v; } return ""; }
    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }
    private static String sanitizeLocation(String s){ if(s==null||s.trim().isEmpty()) return "budapest"; String x=s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]",""); return x.isEmpty()?"budapest":x; }
    private static boolean containsAny(String text,String... xs){ for(String x:xs) if(text.contains(x)) return true; return false; }
    private static String normalize(String s){ String x=s.toLowerCase(new Locale("hu","HU")); x=x.replace('á','a').replace('é','e').replace('í','i').replace('ó','o').replace('ö','o').replace('ő','o').replace('ú','u').replace('ü','u').replace('ű','u'); return x.replaceAll("[^a-z0-9]+"," ").trim(); }
    private static boolean isNoiseTitle(String t){ String n=normalize(t); return n.equals("friss")||n.equals("jegelt")||n.contains("hirdetes feladas"); }
    private static String cleanHtml(String s){ return decodeEntities(s.replaceAll("(?s)<script.*?</script>"," ").replaceAll("(?s)<style.*?</style>"," ").replaceAll("<[^>]+>"," ").replaceAll("\\s+"," ").trim()); }
    private static String decodeEntities(String s){ return s.replace("&amp;","&").replace("&quot;","\"").replace("&#39;","'").replace("&nbsp;"," ").replace("&lt;","<").replace("&gt;",">"); }
    private static String shortMessage(Exception e){ String x=e.getMessage(); return trim(x==null?e.getClass().getSimpleName():x,120); }
    private static String trim(String s,int max){ s=s.replaceAll("\\s+"," ").trim(); return s.length()<=max?s:s.substring(0,max)+"…"; }

    private static final class JSONObjectSafe {
        final JSONObject obj=new JSONObject();
        JSONObjectSafe putSafe(String k,Object v){ try{ obj.put(k,v); }catch(Exception ignored){} return this; }
    }
}
