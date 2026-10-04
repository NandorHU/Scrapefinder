package hu.nandor.pokelens;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;

public final class Dex {
    public final Map<String, JSONObject> pokemon = new LinkedHashMap<>(), moves = new LinkedHashMap<>();
    private final Map<String,String> aliases=new LinkedHashMap<>();
    public final Map<Integer,String> typeNames = new HashMap<>();
    public final int[][] chart = new int[18][18];
    public Dex(Context context) throws Exception { this(read(context)); }
    public Dex(String json) throws Exception {
        JSONObject data = new JSONObject(json), types=data.getJSONObject("types");
        Iterator<String> keys=types.keys(); while(keys.hasNext()){String k=keys.next();typeNames.put(Integer.parseInt(k),types.getString(k));}
        JSONArray p=data.getJSONArray("pokemon"), m=data.getJSONArray("moves"), c=data.getJSONArray("chart");
        for(int i=0;i<p.length();i++){JSONObject r=p.getJSONObject(i);pokemon.put(r.getString("name"),r);}
        JSONObject aliasesJson=data.optJSONObject("aliases");if(aliasesJson!=null){Iterator<String> ak=aliasesJson.keys();while(ak.hasNext()){String key=ak.next();aliases.put(key,aliasesJson.getString(key));}}
        for(int i=0;i<m.length();i++){JSONObject r=m.getJSONObject(i);moves.put(r.getString("name"),r);}
        for(int i=0;i<18;i++) for(int j=0;j<18;j++) chart[i][j]=c.getJSONArray(i).getInt(j);
    }
    private static String read(Context c) throws IOException {
        try(InputStream in=c.getAssets().open("dex.json")){ ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);return out.toString("UTF-8"); }
    }
    public Set<String> pokemonNames(){Set<String> names=new LinkedHashSet<>(pokemon.keySet());names.addAll(aliases.keySet());return names;}
    public String matchPokemon(String text){String n=NameMatcher.match(text,pokemonNames());return n==null?null:aliases.getOrDefault(n,n);}
    public int[] types(String name, int generation) {
        JSONObject p=pokemon.get(name); if(p==null)return new int[0];
        JSONArray selected=p.optJSONArray("types"); JSONObject past=p.optJSONObject("past"); int best=100;
        if(past!=null){Iterator<String> keys=past.keys();while(keys.hasNext()){String key=keys.next();int g=Integer.parseInt(key);if(g>=generation && g<best){best=g;selected=past.optJSONArray(key);}}}
        int[] result=new int[selected.length()];for(int i=0;i<result.length;i++)result[i]=selected.optInt(i);return result;
    }
    public Move move(String name,int generation) {
        JSONObject row=moves.get(name); if(row==null)return null;
        int t=row.optInt("type"),p=row.optInt("power"),a=row.optInt("accuracy");
        JSONArray history=row.optJSONArray("history");
        // Changelog entries describe values before changed_in_version_group_id.
        // Apply newest backwards to reconstruct the selected generation.
        for(int i=history.length()-1;i>=0;i--){JSONObject h=history.optJSONObject(i);if(h.optInt("generation")>generation){t=h.optInt("type_id",t);p=h.optInt("power",p);a=h.optInt("accuracy",a);}}
        if(t<1||t>18)t=0; // Historical ??? type (Curse) is not a damaging type.
        return new Move(name,t,p,a,BattleMath.damageClass(t,row.optInt("class"),generation));
    }
    public String typesText(int[] types){StringJoiner j=new StringJoiner(" / ");for(int t:types)j.add(display(typeNames.getOrDefault(t,"?")));return j.toString();}
    public static String display(String s){if(s==null)return "?";String[] parts=s.replace('-', ' ').split(" ");StringJoiner out=new StringJoiner(" ");for(String p:parts)if(!p.isEmpty())out.add(Character.toUpperCase(p.charAt(0))+p.substring(1));return out.toString();}
    public static final class Move {
        public final String name;public final int type,power,accuracy,category;
        Move(String n,int t,int p,int a,int c){name=n;type=t;power=p;accuracy=a;category=c;}
    }
}
