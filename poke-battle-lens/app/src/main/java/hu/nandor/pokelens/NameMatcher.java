package hu.nandor.pokelens;
import java.text.Normalizer;
import java.util.*;

/** Conservative OCR matching: ambiguous candidates must be corrected by the user. */
public final class NameMatcher {
    public static String normalize(String value) {
        return Normalizer.normalize(value.replace('♀','f').replace('♂','m'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
    public static String match(String text, Collection<String> names) {
        String n = normalize(text.replaceAll("(?i)\\b(?:lv|lvl|level)\\.?\\s*\\d+", "").replaceAll("\\d+$", ""));
        if (n.length() < 3) return null;
        String best = null;
        int bestDistance = Integer.MAX_VALUE, second = Integer.MAX_VALUE;
        for (String name : names) {
            String k = normalize(name);
            if (k.equals(n)) return name;
            int d = distance(n, k);
            if (d < bestDistance) { second = bestDistance; bestDistance = d; best = name; }
            else if (d < second) second = d;
        }
        int limit = n.length() >= 8 ? 2 : (n.length() >= 5 ? 1 : 0);
        return bestDistance <= limit && second > bestDistance ? best : null;
    }
    private static int distance(String a, String b) {
        int[] previous = new int[b.length()+1];
        for (int j=0;j<=b.length();j++) previous[j]=j;
        for (int i=1;i<=a.length();i++) {
            int[] next=new int[b.length()+1]; next[0]=i;
            for (int j=1;j<=b.length();j++) next[j]=Math.min(Math.min(next[j-1]+1,previous[j]+1),previous[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));
            previous=next;
        }
        return previous[b.length()];
    }
}
