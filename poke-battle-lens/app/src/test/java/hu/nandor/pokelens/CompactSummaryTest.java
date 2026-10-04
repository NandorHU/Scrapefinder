package hu.nandor.pokelens;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.*;

public class CompactSummaryTest {
    private Dex dex() throws Exception{return new Dex(new String(Files.readAllBytes(Paths.get("src/main/assets/dex.json")),java.nio.charset.StandardCharsets.UTF_8));}
    @Test public void factorsAreBesideTheirAttackAndStatusMovesAreNotDamage() throws Exception{
        BattleReader.Result r=new BattleReader.Result();r.enemy="charizard";r.own="squirtle";
        r.moves=Arrays.asList("rock-slide","water-gun","tackle","tail-whip");
        String text=BattleSummary.compact(dex(),r,new Profile("test"));
        assertTrue(text.contains("Rock Slide · 4×"));assertTrue(text.contains("Water Gun · 2×"));
        assertTrue(text.contains("Tackle · 1×"));assertTrue(text.contains("Tail Whip · állapot"));assertEquals(5,text.split("\n").length);
    }
    @Test public void immunityResistanceAndMissingNamesAreClear() throws Exception{
        Dex d=dex();BattleReader.Result r=new BattleReader.Result();r.enemy="toucannon";
        r.moves=Arrays.asList("earthquake","bug-bite");String text=BattleSummary.compact(d,r,new Profile("test"));
        assertTrue(text.contains("Earthquake · 0×"));assertTrue(text.contains("Bug Bite · 0,5×"));
        r.enemy=null;assertFalse(BattleSummary.compact(d,r,new Profile("test")).contains("Earthquake"));
    }
}
