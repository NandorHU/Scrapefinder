package hu.nandor.pokelens;
import java.util.*;

public final class BattleSummary {
    /** One attack per line, with its type multiplier immediately beside its name. */
    public static String compact(Dex dex,BattleReader.Result r,Profile profile){
        if(r.enemy==null)return "Ellenfél nem olvasható.\nEllenőrizd a név kijelölését (⚙).";
        int[] enemy=dex.types(r.enemy,profile.generation);
        StringBuilder s=new StringBuilder(Dex.display(r.enemy)).append(" · ").append(dex.typesText(enemy));
        if(r.moves.isEmpty())return s.append("\nNyisd meg a támadásmenüt.").toString();
        for(String name:r.moves){
            Dex.Move m=dex.move(name,profile.generation);if(m==null)continue;
            s.append("\n").append(Dex.display(name)).append(" · ");
            s.append(m.category==1?"állapot":multiplier(BattleMath.effectiveness(m.type,enemy,profile.generation,dex.chart))+"×");
        }
        return s.toString();
    }
    public static String describe(Dex dex,BattleReader.Result r,Profile profile) {
        if(r.enemy==null)return "Nem olvasható az ellenfél neve.\nÁllítsd be a név területét a profilban, vagy javítsd kézzel.";
        int[] enemy=dex.types(r.enemy,profile.generation),own=dex.types(r.own,profile.generation);
        StringBuilder s=new StringBuilder(Dex.display(r.enemy)).append(" · ").append(dex.typesText(enemy)).append("\n");
        if(r.own!=null)s.append("Saját: ").append(Dex.display(r.own)).append("\n");
        if(r.moves.isEmpty())return s.append("A támadások nem olvashatók.\nNyisd meg a támadásmenüt, vagy állítsd be a területét.").toString();
        for(String name:r.moves){
            Dex.Move m=dex.move(name,profile.generation);if(m==null)continue;
            double eff=m.category==1?1:BattleMath.effectiveness(m.type,enemy,profile.generation,dex.chart),stab=BattleMath.stab(m.type,own);
            s.append("\n").append(Dex.display(name)).append("   ").append(m.category==1?"—":multiplier(eff)+"×").append("\n");
            if(m.category==1){s.append("Állapottámadás · nincs sebzés");continue;}
            s.append(Dex.display(dex.typeNames.get(m.type))).append(m.category==2?" · fizikai":" · speciális");
            if(m.power>0){s.append(" · erő ").append(m.power);if(own.length>0)s.append(" · STAB ").append(multiplier(stab)).append("×");
                s.append("\nErő × típus × STAB: ").append(own.length>0?String.format(Locale.ROOT,"%.0f",BattleMath.comparison(m.power,eff,stab)):"? (saját Pokémon hiányzik)");
            }else s.append(" · változó / különleges sebzés");
            s.append(" · pontosság ").append(m.accuracy==0?"—":m.accuracy+"%");
        }
        s.append("\n\nTípusszorzó és erőmutató; nem pontos sebzés. Képesség, tárgy, stat és terep nincs beleszámítva.");return s.toString();
    }
    static String multiplier(double value){if(value==Math.floor(value))return String.valueOf((int)value);return String.format(Locale.ROOT,"%.2f",value).replaceAll("0+$","").replace('.',',');}
}
