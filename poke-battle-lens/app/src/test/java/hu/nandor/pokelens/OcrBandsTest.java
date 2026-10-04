package hu.nandor.pokelens;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class OcrBandsTest {
    private int[] background(int color){int[] p=new int[400*300];Arrays.fill(p,color);return p;}
    private void ink(int[] p,int x,int y,int width,int height,int color){for(int row=y;row<y+height;row++)for(int col=x;col<x+width;col++)p[row*400+col]=color;}
    @Test public void twoColumnsAndSeparatedRowsKeepTheirOriginalLocations(){
        int[] p=background(0xffeeeeee);for(int y:new int[]{100,190})for(int x:new int[]{40,300})ink(p,x,y,10,20,0xff000000);
        List<OcrBands.Band> b=OcrBands.find(p,400,300);assertEquals(2,b.size());
        assertEquals(36,b.get(0).left);assertEquals(314,b.get(0).right);assertEquals(96,b.get(0).top);assertEquals(124,b.get(0).bottom);assertEquals(20,b.get(0).inkHeight);
        assertEquals(186,b.get(1).top);assertTrue((b.get(0).bottom-b.get(0).top)*278*2<400*300/4);
    }
    @Test public void darkBackgroundRetainsLightLetters(){
        int[] p=background(0xff102030);ink(p,40,100,10,20,0xffeeeeee);List<OcrBands.Band> b=OcrBands.find(p,400,300);assertEquals(1,b.size());assertEquals(96,b.get(0).top);assertEquals(20,b.get(0).inkHeight);
    }
    @Test public void changingSolidBackgroundDoesNotChangeTextBounds(){
        int[] a=background(0xffe6faeb),b=background(0xfff5ebeb);ink(a,40,100,10,20,0xff000000);ink(b,40,100,10,20,0xff000000);
        OcrBands.Band x=OcrBands.find(a,400,300).get(0),y=OcrBands.find(b,400,300).get(0);assertEquals(x.top,y.top);assertEquals(x.left,y.left);assertEquals(x.bottom,y.bottom);assertEquals(x.right,y.right);
    }
    @Test public void texturedRegionsAndTallObjectsKeepFullRegion(){
        int[] p=background(0xffffffff);for(int y=0;y<300;y++)for(int x=0;x<400;x++)if(x%8<4)p[y*400+x]=0xff405060;assertTrue(OcrBands.find(p,400,300).isEmpty());
        p=background(0xffffffff);ink(p,40,40,10,100,0xff000000);assertTrue(OcrBands.find(p,400,300).isEmpty());
    }
    @Test public void lowContrastBlankAndBorderRegionsUseFallback(){
        int[] p=background(0xffffffff);assertTrue(OcrBands.find(p,400,300).isEmpty());ink(p,40,100,10,20,0xffeeeeee);assertTrue(OcrBands.find(p,400,300).isEmpty());
        ink(p,40,50,200,2,0xff000000);assertTrue(OcrBands.find(p,400,300).isEmpty());
    }
    @Test public void shortGapsInsideLettersAreNotSeparateTextRows(){
        int[] p=background(0xffffffff);ink(p,40,100,10,10,0xff000000);ink(p,40,114,10,10,0xff000000);List<OcrBands.Band> b=OcrBands.find(p,400,300);assertEquals(1,b.size());assertEquals(24,b.get(0).inkHeight);
    }
    @Test public void TooManyRowsAndInvalidBuffersUseFallback(){
        int[] p=background(0xffffffff);for(int y=5;y<270;y+=28)ink(p,40,y,10,10,0xff000000);assertTrue(OcrBands.find(p,400,300).isEmpty());assertTrue(OcrBands.find(new int[0],10,10).isEmpty());
    }
}
