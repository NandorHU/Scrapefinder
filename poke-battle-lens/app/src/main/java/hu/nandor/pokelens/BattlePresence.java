package hu.nandor.pokelens;
import java.util.*;

/** Confirm names rather than requiring a pixel-perfect, motionless game screen. */
final class BattlePresence {
    enum Mode { WAITING, VERIFYING, UNCERTAIN, READY }
    private String candidate="";private int matches;private Mode mode=Mode.WAITING;
    Mode mode(){return mode;}
    void reset(){candidate="";matches=0;mode=Mode.WAITING;}
    Mode observe(boolean battleVisible,String enemy,String own,List<String> moves){
        if(!battleVisible){reset();return mode;}
        if(enemy==null||moves.isEmpty()){candidate="";matches=0;return mode=Mode.UNCERTAIN;}
        List<String> ordered=new ArrayList<>(moves);Collections.sort(ordered);
        String key=enemy+"\n"+String.valueOf(own)+"\n"+String.join("\n",ordered);
        if(!key.equals(candidate)){candidate=key;matches=1;}else matches++;
        return mode=matches>=2?Mode.READY:Mode.VERIFYING;
    }
}
