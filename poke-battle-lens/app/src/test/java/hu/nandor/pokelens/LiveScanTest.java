package hu.nandor.pokelens;
import org.junit.Test;
import java.nio.ByteBuffer;
import java.util.Arrays;
import static org.junit.Assert.*;

public class LiveScanTest {
    @Test public void switchDuringOcrRejectsOldResultAndReadsNewestScene(){
        ScanGate gate=new ScanGate();assertTrue(gate.observe(1));long old=gate.begin(0);
        assertTrue(gate.observe(2));assertTrue(gate.observe(3));assertEquals(-1,gate.begin(300));
        assertFalse(gate.finish(old));long latest=gate.begin(300);assertTrue(latest>old);assertTrue(gate.finish(latest));
        assertFalse(gate.observe(3));assertEquals(-1,gate.begin(600));
    }
    @Test public void stableScreenHasFallbackAndChangedScreenIsThrottled(){
        ScanGate gate=new ScanGate();gate.observe(1);assertTrue(gate.finish(gate.begin(0)));
        assertEquals(-1,gate.begin(1999));assertTrue(gate.finish(gate.begin(2000)));
        gate.observe(2);assertEquals(-1,gate.begin(2299));assertTrue(gate.finish(gate.begin(2300)));
    }
    @Test public void pauseResumeProfileChangeAndResizeInvalidatePendingResult(){
        ScanGate gate=new ScanGate();gate.observe(1);long pending=gate.begin(0);
        gate.refresh();assertFalse(gate.finish(pending));assertTrue(gate.finish(gate.begin(300)));
        pending=gate.begin(2300);gate.invalidate();assertFalse(gate.finish(pending));assertEquals(-1,gate.begin(2600));
        gate.observe(1);assertTrue(gate.finish(gate.begin(2600)));
    }
    @Test public void failedOcrRetriesWithoutStartingConcurrentRequests(){
        ScanGate gate=new ScanGate();gate.observe(1);assertTrue(gate.begin(0)>=0);assertEquals(-1,gate.begin(400));
        gate.failed();assertTrue(gate.begin(400)>=0);
    }
    @Test public void onlyCalibratedRegionsChangeFingerprint(){
        ByteBuffer rgba=ByteBuffer.allocate(32*8);int[][] regions={{0,0,4,4},{4,4,8,8}};
        long original=SceneFingerprint.rgba(rgba,32,4,regions);
        rgba.put(4*4,(byte)128);assertEquals(original,SceneFingerprint.rgba(rgba,32,4,regions)); // outside
        rgba.put(0,(byte)7);assertEquals(original,SceneFingerprint.rgba(rgba,32,4,regions)); // small noise
        rgba.put(0,(byte)24);assertNotEquals(original,SceneFingerprint.rgba(rgba,32,4,regions));
    }
    @Test public void fingerprintUsesStrideAndIgnoresAlpha(){
        ByteBuffer rgba=ByteBuffer.allocate(48*8);int[][] regions={{4,4,8,8}};
        long original=SceneFingerprint.rgba(rgba,48,4,regions);
        rgba.put(4*48+4*4+3,(byte)255);assertEquals(original,SceneFingerprint.rgba(rgba,48,4,regions));
        rgba.put(4*48+4*4+1,(byte)128);assertNotEquals(original,SceneFingerprint.rgba(rgba,48,4,regions));
    }
    @Test public void indexedNamesPreserveDigitsAndRejectAmbiguousTypos(){
        NameMatcher.Index names=new NameMatcher.Index(Arrays.asList("porygon","porygon2","porygon-z","catfish","batfish"));
        assertEquals("porygon2",names.match("PORYGON2 Lv 20"));assertEquals("porygon-z",names.match("PORYGON Z"));
        assertNull(names.match("ratfish"));assertEquals("catfish",names.match("catfisk"));
    }
}
