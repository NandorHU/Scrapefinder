package hu.nandor.pokelens;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

public class BadgeRulesTest {
    private Dex dex() throws Exception{return new Dex(new String(Files.readAllBytes(Paths.get("src/main/assets/dex.json")),java.nio.charset.StandardCharsets.UTF_8));}
    @Test public void onlyExceptionsShowNormallyAndUnknownIsNotNeutral() throws Exception{
        Dex d=dex();assertFalse(BadgeValue.of(d,"tackle","charizard",9).exceptional());
        assertTrue(BadgeValue.of(d,"rock-slide","charizard",9).exceptional());assertEquals("4×",BadgeValue.of(d,"rock-slide","charizard",9).label);
        assertEquals("0×",BadgeValue.of(d,"thunderbolt","swampert",9).label);assertEquals("0,25×",BadgeValue.of(d,"bug-bite","charizard",9).label);
        assertFalse(BadgeValue.of(d,"tail-whip","charizard",9).exceptional());assertEquals("áll.",BadgeValue.of(d,"tail-whip","charizard",9).label);
        assertTrue(BadgeValue.of(d,"tackle",null,9).unknown);assertEquals("?",BadgeValue.of(d,null,"charizard",9).label);
    }
    @Test public void revealExpiresAfterFiveSecondsAndRepeatedTapExtendsIt(){RevealWindow r=new RevealWindow();assertFalse(r.showing(0));r.reveal(100);assertTrue(r.showing(5099));assertFalse(r.showing(5100));r.reveal(5000);assertTrue(r.showing(9999));r.reset();assertFalse(r.showing(5000));}
    @Test public void contactSheetBoxesMapBackToOriginalCoordinates(){
        MovePosition p=MovePosition.map("tackle",84,560,204,600,30,700,600,100,24,500,1200,200,1000,1600);
        assertEquals(.06f,p.left,.00001f);assertEquals(.45625f,p.top,.00001f);assertEquals(.12f,p.right,.00001f);assertEquals(.46875f,p.bottom,.00001f);
    }
    @Test public void placementDoesNotCoverEitherColumnOrOtherBadges(){
        MovePosition left=new MovePosition("left",.05f,.5f,.4f,.55f),right=new MovePosition("right",.44f,.5f,.9f,.55f);
        List<MovePosition> names=Arrays.asList(left,right);List<BadgePlacement.Box> used=new ArrayList<>();
        BadgePlacement.Box a=BadgePlacement.place(left,80,25,1000,1000,4,names,used);assertNotNull(a);assertTrue("Narrow gap must use another side",a.left<50||a.top>=550||a.bottom<=500);used.add(a);
        BadgePlacement.Box b=BadgePlacement.place(right,80,25,1000,1000,4,names,used);assertNotNull(b);assertFalse(a.intersects(b));
        for(MovePosition n:names){BadgePlacement.Box word=new BadgePlacement.Box(n.left*1000,n.top*1000,n.right*1000,n.bottom*1000);assertFalse(word.intersects(a));assertFalse(word.intersects(b));}
    }
    @Test public void noSpaceReturnsNoBadgeInsteadOfCoveringTheWord(){MovePosition full=new MovePosition("full",0,0,1,1);assertNull(BadgePlacement.place(full,30,20,100,100,4,Arrays.asList(full),new ArrayList<>()));}
    @Test public void renderedBadgesAreExcludedButAttackChangesStillInvalidate(){
        ByteBuffer pixels=ByteBuffer.allocate(80*20);int[][] areas={{0,0,20,20}},masks={{8,8,16,16}};long clean=SceneFingerprint.rgba(pixels,80,4,areas,masks);
        pixels.put(8*80+8*4,(byte)128);assertEquals(clean,SceneFingerprint.rgba(pixels,80,4,areas,masks));pixels.put(0,(byte)128);assertNotEquals(clean,SceneFingerprint.rgba(pixels,80,4,areas,masks));
        ScanGate gate=new ScanGate();gate.observe(1);assertTrue(gate.finish(gate.begin(0)));gate.rebase(2);assertFalse(gate.observe(2));assertEquals(-1,gate.begin(300));assertTrue(gate.observe(3));
    }
    @Test public void captureWaitsForNewComposedImageAndCanBeCancelled(){CleanCapture c=new CleanCapture();c.begin(100,42);assertFalse(c.ready(199,43));assertFalse(c.ready(200,42));assertTrue(c.ready(200,43));assertTrue(c.timedOut(1701));c.cancel();assertFalse(c.ready(1800,99));}
}
