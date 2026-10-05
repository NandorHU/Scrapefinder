package hu.nandor.pokelens;
import java.util.*;

/** A base-stat strength index, not exact damage or a battle-strategy solver. */
final class MoveRecommendation {
    static final double CLOSE_RATIO=.90;
    static final class Result {
        final Set<String> best;final Map<String,Double> scores;final String reason;
        Result(Set<String> best,Map<String,Double> scores,String reason){this.best=best;this.scores=scores;this.reason=reason;}
        boolean available(){return !best.isEmpty();}
    }
    private static Result unavailable(String reason){return new Result(Collections.emptySet(),Collections.emptyMap(),reason);}
    static Result forBattle(Dex dex,BattleReader.Result battle,int generation){
        for(MovePosition position:battle.positions)if(position.name==null)return unavailable("Nem minden támadás olvasható.");
        return rank(dex,battle.own,battle.enemy,battle.moves,generation);
    }
    static Result rank(Dex dex,String own,String enemy,List<String> moves,int generation){
        int[] ownTypes=dex.types(own,generation),enemyTypes=dex.types(enemy,generation);
        if(ownTypes.length==0||enemyTypes.length==0)return unavailable("Saját Pokémon és ellenfél felismerése szükséges.");
        Map<String,Double> scores=new LinkedHashMap<>();double max=0;
        for(String name:moves){
            Dex.Move move=dex.move(name,generation);
            if(move==null)return unavailable("Nem minden támadás olvasható.");
            if(move.category==1)continue;
            if(!dex.regularDamage(name)||move.power<=0||move.type<1||move.type>18||
                    (move.accuracy<=0&&!dex.neverMisses(name)))
                return unavailable("Különleges vagy feltételes sebző támadás: nincs megbízható összehasonlítás.");
            int attack=dex.baseStat(own,move.category==2?2:4,generation),defense=dex.baseStat(enemy,move.category==2?3:5,generation);
            if(attack<=0||defense<=0)return unavailable("Hiányzó Pokémon-bázisadat.");
            double accuracy=move.accuracy>0?move.accuracy/100.0:1;
            double score=move.power*BattleMath.effectiveness(move.type,enemyTypes,generation,dex.chart)*
                    BattleMath.stab(move.type,ownTypes)*attack/defense*accuracy;
            scores.put(name,score);max=Math.max(max,score);
        }
        if(max<=0)return unavailable("Nincs összehasonlítható, hatásos sebző támadás.");
        Set<String> best=new LinkedHashSet<>();for(Map.Entry<String,Double> entry:scores.entrySet())if(entry.getValue()>=max*CLOSE_RATIO)best.add(entry.getKey());
        return new Result(Collections.unmodifiableSet(best),Collections.unmodifiableMap(scores),"");
    }
    static String explanation(Result result){
        return result.available()?"★ = becsült legerősebb sebző támadás(ok); 10%-on belül több jelölés. Bázisadat, típus, STAB és alap-pontosság alapján; nem pontos sebzés. Szint, IV/EV, nature, statváltozás, képesség, tárgy, időjárás és terep nincs beleszámítva.":"Nincs csillagjelzés: "+result.reason;
    }
}
