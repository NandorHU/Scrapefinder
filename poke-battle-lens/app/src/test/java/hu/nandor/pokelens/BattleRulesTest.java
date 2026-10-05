package hu.nandor.pokelens;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.*;

public class BattleRulesTest {
    private Dex dex() throws Exception {return new Dex(new String(Files.readAllBytes(Paths.get("src/main/assets/dex.json")),java.nio.charset.StandardCharsets.UTF_8));}
    @Test public void toucannonGroundImmunityAndFightingCancellation() throws Exception {
        Dex d=dex();int[] t=d.types("toucannon",9);
        assertEquals(0,BattleMath.effectiveness(5,t,9,d.chart),0);
        assertEquals(1,BattleMath.effectiveness(2,t,9,d.chart),0);
        assertEquals(2,BattleMath.effectiveness(15,t,9,d.chart),0);
        assertEquals(1,BattleMath.effectiveness(11,t,9,d.chart),0);
    }
    @Test public void dualWeaknessAndQuarterResistance() throws Exception {
        Dex d=dex();assertEquals(4,BattleMath.effectiveness(12,d.types("swampert",9),9,d.chart),0);
        assertEquals(.25,BattleMath.effectiveness(12,d.types("charizard",9),9,d.chart),0);
    }
    @Test public void stabAndHistoricalRules() throws Exception {
        Dex d=dex();assertEquals(1.5,BattleMath.stab(11,d.types("swampert",9)),0);
        assertEquals(1,BattleMath.stab(1,d.types("swampert",9)),0);
        assertEquals(.5,BattleMath.effectiveness(17,new int[]{9},5,d.chart),0);
        assertEquals(1,BattleMath.effectiveness(17,new int[]{9},6,d.chart),0);
        assertEquals(0,BattleMath.effectiveness(8,new int[]{14},1,d.chart),0);
        assertArrayEquals(new int[]{1},d.types("clefairy",5));
        assertArrayEquals(new int[]{18},d.types("clefairy",6));
        assertEquals(3,d.move("muddy-water",9).category);
        assertEquals(2,d.move("chip-away",9).category);
        assertEquals(35,d.move("tackle",4).power);
        assertEquals(40,d.move("tackle",9).power);
        assertEquals(0,d.move("curse",3).type);
        assertEquals(1,d.move("curse",3).category);
    }
    @Test public void ocrMatchesOnlyPlausibleNames() throws Exception {
        Dex d=dex();assertEquals("hariyama",NameMatcher.match("HARIYAMA Lv48",d.pokemon.keySet()));
        assertEquals("swampert",NameMatcher.match("SWAMPERT",d.pokemon.keySet()));
        assertEquals("muddy-water",NameMatcher.match("MUDDY WATER",d.moves.keySet()));
        assertEquals("muddy-water",NameMatcher.match("MUDDY WATEK",d.moves.keySet()));
        assertNull(NameMatcher.match("HP 145/145",d.pokemon.keySet()));
        assertNull(NameMatcher.match("What will you do?",d.pokemon.keySet()));
        assertNull(NameMatcher.match("M",d.pokemon.keySet()));
        assertEquals("deoxys-normal",d.matchPokemon("DEOXYS"));
        assertEquals("mr-mime",NameMatcher.match("Mr. Mime",d.pokemon.keySet()));
    }
    @Test public void fullDatabaseIntegrity() throws Exception {
        Dex d=dex();assertTrue(d.pokemon.size()>1000);assertTrue(d.moves.size()>800);
        for(String name:d.pokemon.keySet())for(int g=1;g<=9;g++)for(int t:d.types(name,g))assertTrue(t>=1&&t<=18);
        for(String name:d.moves.keySet())for(int g=1;g<=9;g++){Dex.Move m=d.move(name,g);assertTrue(name+" generation "+g,m.type>=0&&m.type<=18);if(m.type==0)assertEquals(1,m.category);assertTrue(m.power>=0);}
    }
}
