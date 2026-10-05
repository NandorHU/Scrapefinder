package hu.nandor.pokelens;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.*;

public class MoveRecommendationTest {
    private Dex dex() throws Exception{
        String root="src/main/assets/";
        return new Dex(Files.readString(Paths.get(root+"dex.json")),Files.readString(Paths.get(root+"ranking.json")));
    }
    @Test public void starmieComparisonIncludesStatsStabAccuracyAndShowsCloseAlternatives() throws Exception{
        Dex d=dex();MoveRecommendation.Result r=MoveRecommendation.rank(d,"swampert","starmie",Arrays.asList("mud-bomb","rock-slide","muddy-water","rock-smash"),9);
        assertEquals(new LinkedHashSet<>(Arrays.asList("mud-bomb","rock-slide")),r.best);
        assertEquals(65*1.5*85.0/85*.85,r.scores.get("mud-bomb"),.00001);
        assertEquals(75*110.0/85*.90,r.scores.get("rock-slide"),.00001);
        assertTrue(r.scores.get("rock-slide")>r.scores.get("mud-bomb"));
        assertFalse(r.best.contains("muddy-water"));assertFalse(r.best.contains("rock-smash"));
        BadgeValue value=BadgeValue.of(d,"rock-slide","starmie",9).withRecommendation();
        assertEquals("1× ★\n90%",value.text(false));
    }
    @Test public void immunityAndStatusMovesNeverGetAStar() throws Exception{
        MoveRecommendation.Result r=MoveRecommendation.rank(dex(),"venusaur","swampert",Arrays.asList("energy-ball","thunderbolt","tackle","tail-whip"),9);
        assertEquals(Collections.singleton("energy-ball"),r.best);assertEquals(0,r.scores.get("thunderbolt"),0);assertFalse(r.scores.containsKey("tail-whip"));
        assertFalse(MoveRecommendation.rank(dex(),"pikachu","swampert",Arrays.asList("thunderbolt","thunder-wave"),9).available());
    }
    @Test public void incompleteOrConditionalRostersHaveNoMisleadingWinner() throws Exception{
        Dex d=dex();
        for(String special:Arrays.asList("hyper-beam","solar-beam","foul-play","body-press","psyshock","eruption","double-kick","dragon-rage","fissure","unknown"))
            assertFalse(special,MoveRecommendation.rank(d,"swampert","starmie",Arrays.asList("tackle",special),9).available());
        assertFalse(MoveRecommendation.rank(d,null,"starmie",Arrays.asList("tackle"),9).available());
        BattleReader.Result b=new BattleReader.Result();b.own="swampert";b.enemy="starmie";b.moves=Arrays.asList("tackle");b.positions=Arrays.asList(new MovePosition(null,0,0,1,1));
        assertFalse(MoveRecommendation.forBattle(d,b,9).available());
    }
    @Test public void historicalStatsAndDamageCategoriesAreUsed() throws Exception{
        Dex d=dex();assertEquals(100,d.baseStat("starmie",5,1));assertEquals(85,d.baseStat("starmie",5,9));
        MoveRecommendation.Result old=MoveRecommendation.rank(d,"charizard","starmie",Arrays.asList("bite"),3),modern=MoveRecommendation.rank(d,"charizard","starmie",Arrays.asList("bite"),9);
        assertTrue(old.available());assertTrue(modern.available());
        assertEquals(60*2*109.0/85,old.scores.get("bite"),.00001);
        assertEquals(60*2*84.0/85,modern.scores.get("bite"),.00001);
    }
    @Test public void NeverMissesIsDistinctFromMissingAccuracyAndStatusOnlyRosters() throws Exception{
        Dex d=dex();assertTrue(d.neverMisses("swift"));assertFalse(d.neverMisses("nasty-plot"));
        assertTrue(MoveRecommendation.rank(d,"swampert","starmie",Arrays.asList("swift"),9).available());
        assertFalse(MoveRecommendation.rank(d,"pikachu","ditto",Arrays.asList("charm","nasty-plot"),9).available());
    }
}
