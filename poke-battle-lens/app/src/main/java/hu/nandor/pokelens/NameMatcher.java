package hu.nandor.pokelens;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Conservative OCR matching: ambiguous candidates must be corrected by the user. */
public final class NameMatcher {
    private static final Pattern MARKS=Pattern.compile("\\p{M}"), OTHER=Pattern.compile("[^a-z0-9]"), LEVEL=Pattern.compile("(?i)\\b(?:lv|lvl|level)\\.?\\s*\\d+");
    public static String normalize(String value) {
        return OTHER.matcher(MARKS.matcher(Normalizer.normalize(value.replace('♀','f').replace('♂','m'),Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT)).replaceAll("");
    }
    public static String match(String text, Collection<String> names) { return new Index(names).match(text); }
    /** Normalize the database once; each frame only normalizes the OCR strings. */
    public static final class Index {
        private final Map<String,String> exact=new LinkedHashMap<>();
        public Index(Collection<String> names){for(String name:names)exact.putIfAbsent(normalize(name),name);}
        public String match(String text){
            String n=normalize(LEVEL.matcher(text).replaceAll(""));
            if(n.length()<3)return null;
            if(exact.containsKey(n))return exact.get(n);
            int limit=n.length()>=8?2:(n.length()>=5?1:0),bestDistance=limit+1;
            String best=null;boolean ambiguous=false;
            for(Map.Entry<String,String> candidate:exact.entrySet()){
                if(Math.abs(n.length()-candidate.getKey().length())>limit)continue;
                int d=distance(n,candidate.getKey(),limit);
                if(d<bestDistance){bestDistance=d;best=candidate.getValue();ambiguous=false;}
                else if(d==bestDistance)ambiguous=true;
            }
            return bestDistance<=limit&&!ambiguous?best:null;
        }
    }
    private static int distance(String a,String b,int limit){
        int[] previous=new int[b.length()+1],next=new int[b.length()+1];
        for(int j=0;j<=b.length();j++)previous[j]=j;
        for(int i=1;i<=a.length();i++){
            next[0]=i;int minimum=i;
            for(int j=1;j<=b.length();j++){
                next[j]=Math.min(Math.min(next[j-1]+1,previous[j]+1),previous[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));
                minimum=Math.min(minimum,next[j]);
            }
            if(minimum>limit)return limit+1;
            int[] swap=previous;previous=next;next=swap;
        }
        return previous[b.length()];
    }
}
