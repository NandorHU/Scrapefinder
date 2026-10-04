package hu.nandor.pokelens;
import android.content.Context;
import android.graphics.Rect;
import org.json.*;
import java.util.*;

public final class Profile {
    public String name; public int generation=9;
    public float[] enemy={.04f,.12f,.48f,.23f}, own={.54f,.26f,.97f,.34f}, moves={.03f,.46f,.97f,.57f};
    public String manualEnemy="",manualOwn="",manualMoves="";
    public Profile(String name){this.name=name;}
    public Rect crop(String region,int w,int h){
        float[] r=region.equals("enemy")?enemy:region.equals("own")?own:moves;
        int l=Math.max(0,Math.min(w-1,Math.round(r[0]*w))), t=Math.max(0,Math.min(h-1,Math.round(r[1]*h)));
        return new Rect(l,t,Math.max(l+1,Math.min(w,Math.round(r[2]*w))),Math.max(t+1,Math.min(h,Math.round(r[3]*h))));
    }
    public JSONObject json() {
        JSONObject j=new JSONObject();try{j.put("name",name).put("generation",generation).put("enemy",array(enemy)).put("own",array(own)).put("moves",array(moves)).put("manualEnemy",manualEnemy).put("manualOwn",manualOwn).put("manualMoves",manualMoves);}catch(JSONException ignored){}return j;
    }
    static JSONArray array(float[] r) throws JSONException {JSONArray a=new JSONArray();for(float n:r)a.put(n);return a;}
    static float[] region(JSONObject j,String key,float[] fallback){JSONArray a=j.optJSONArray(key);if(a==null||a.length()!=4)return fallback;float[] r=new float[4];for(int i=0;i<4;i++){r[i]=(float)a.optDouble(i,fallback[i]);if(!Float.isFinite(r[i]))return fallback;r[i]=Math.max(0,Math.min(1,r[i]));}return r[2]>r[0]&&r[3]>r[1]?r:fallback;}
    public static Profile from(JSONObject j){Profile p=new Profile(j.optString("name","Profil"));p.generation=Math.max(1,Math.min(9,j.optInt("generation",9)));p.enemy=region(j,"enemy",p.enemy);p.own=region(j,"own",p.own);p.moves=region(j,"moves",p.moves);p.manualEnemy=j.optString("manualEnemy","");p.manualOwn=j.optString("manualOwn","");p.manualMoves=j.optString("manualMoves","");return p;}
    public static List<Profile> load(Context c){
        List<Profile> list=new ArrayList<>();try{JSONArray a=new JSONArray(c.getSharedPreferences("lens",0).getString("profiles","[]"));for(int i=0;i<a.length();i++)list.add(from(a.getJSONObject(i)));}catch(Exception ignored){}
        if(list.isEmpty()){
            list.add(new Profile("Dungeons & Pokémon – álló"));
            Profile gba=new Profile("GBA – fekvő (beállítandó)");gba.generation=3;gba.enemy=new float[]{.02f,.02f,.49f,.2f};gba.own=new float[]{.51f,.45f,.97f,.62f};gba.moves=new float[]{.40f,.72f,.98f,.97f};list.add(gba);
        }return list;
    }
    public static void save(Context c,List<Profile> list){JSONArray a=new JSONArray();for(Profile p:list)a.put(p.json());c.getSharedPreferences("lens",0).edit().putString("profiles",a.toString()).apply();}
    public static Profile active(Context c){List<Profile> list=load(c);int i=c.getSharedPreferences("lens",0).getInt("active",0);return list.get(Math.min(Math.max(0,i),list.size()-1));}
}
