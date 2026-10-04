package hu.nandor.pokelens;
import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.SystemClock;
import android.view.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RegionEditorTest {
    private static void event(RegionEditor view,int action,float x,float y){long now=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(now,now,action,x,y,0);view.onTouchEvent(e);e.recycle();}
    private static void drag(RegionEditor view,float x1,float y1,float x2,float y2){event(view,MotionEvent.ACTION_DOWN,x1,y1);event(view,MotionEvent.ACTION_MOVE,x2,y2);event(view,MotionEvent.ACTION_UP,x2,y2);}
    private static RegionEditor canvas(Profile profile){Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();RegionEditor v=new RegionEditor(c,Bitmap.createBitmap(800,1600,Bitmap.Config.ARGB_8888),profile);v.layout(0,0,800,1600);v.fitImage();return v;}
    @Test public void zoomedSelectionSavesImageCoordinatesAndUndoRestoresThem(){
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Profile p=new Profile("zoom");float[] before=p.enemy.clone();RegionEditor v=canvas(p);v.zoomBy(2);
            drag(v,100,100,300,300);assertArrayEquals(new float[]{.3125f,.28125f,.4375f,.34375f},p.enemy,.0001f);
            assertTrue(v.canUndo());v.undo();assertArrayEquals(before,p.enemy,0);assertFalse(v.canUndo());
        });
    }
    @Test public void panMovesImageWithoutChangingRegionsAndNextSelectionUsesNewPosition(){
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Profile p=new Profile("pan");float[] before=p.enemy.clone();RegionEditor v=canvas(p);v.zoomBy(2);v.setPanMode(true);
            drag(v,400,800,500,900);assertArrayEquals(before,p.enemy,0);assertFalse(v.canUndo());
            v.setPanMode(false);drag(v,100,100,300,300);assertArrayEquals(new float[]{.25f,.25f,.375f,.3125f},p.enemy,.0001f);
        });
    }
    @Test public void cancelAndTwoFingerGestureDoNotOverwriteSelection(){
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Profile p=new Profile("pinch");float[] before=p.enemy.clone();RegionEditor v=canvas(p);
            event(v,MotionEvent.ACTION_DOWN,100,100);event(v,MotionEvent.ACTION_MOVE,300,300);event(v,MotionEvent.ACTION_CANCEL,300,300);assertArrayEquals(before,p.enemy,0);
            long down=SystemClock.uptimeMillis();event(v,MotionEvent.ACTION_DOWN,300,700);
            multi(v,down,down+10,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),300,700,500,900);
            multi(v,down,down+30,MotionEvent.ACTION_MOVE,250,650,550,950);
            multi(v,down,down+60,MotionEvent.ACTION_MOVE,200,600,600,1000);
            multi(v,down,down+80,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),200,600,600,1000);
            event(v,MotionEvent.ACTION_UP,200,600);assertArrayEquals(before,p.enemy,0);assertFalse(v.canUndo());assertTrue(v.zoomLevel()>1);
        });
    }
    private static void multi(RegionEditor v,long down,long when,int action,float x1,float y1,float x2,float y2){
        MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[2];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[2];
        for(int i=0;i<2;i++){properties[i]=new MotionEvent.PointerProperties();properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=i==0?x1:x2;coords[i].y=i==0?y1:y2;coords[i].pressure=1;coords[i].size=1;}
        MotionEvent e=MotionEvent.obtain(down,when,action,2,properties,coords,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);v.onTouchEvent(e);e.recycle();
    }
    @Test public void fullEditorGivesImageMostOfWindow() throws Exception {
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();Bitmap source=Bitmap.createBitmap(800,1600,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(source);c.drawColor(Color.rgb(244,234,207));Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);ink.setColor(Color.rgb(218,201,139));c.drawRect(20,185,780,545,ink);
        ink.setColor(Color.WHITE);c.drawRect(38,205,370,320,ink);c.drawRect(430,420,775,530,ink);c.drawRect(20,735,780,905,ink);ink.setColor(Color.BLACK);ink.setTextSize(24);ink.setTypeface(Typeface.MONOSPACE);c.drawText("ZIGZAGOON",55,255,ink);c.drawText("CHIMCHAR",460,465,ink);c.drawText("SCRATCH",55,790,ink);c.drawText("TACKLE",440,790,ink);c.drawText("LEER",55,835,ink);
        File input=new File(app.getCacheDir(),"editor-input.png");try(FileOutputStream out=new FileOutputStream(input)){source.compress(Bitmap.CompressFormat.PNG,100,out);}source.recycle();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(activity->activity.onActivityResult(11,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(input))));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity->{View decor=activity.getWindow().getDecorView();RegionEditor editor=decor.findViewWithTag("region-editor");assertNotNull(editor);assertTrue("Image should use over half the window",editor.getHeight()>decor.getHeight()*.55f);assertTrue("Image should use nearly full width",editor.getWidth()>decor.getWidth()*.90f);
                Bitmap screen=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);decor.draw(new Canvas(screen));File output=new File(app.getExternalFilesDir(null),"editor-preview.png");try(FileOutputStream out=new FileOutputStream(output)){screen.compress(Bitmap.CompressFormat.PNG,100,out);}catch(IOException e){throw new RuntimeException(e);}finally{screen.recycle();}
            });
        }finally{input.delete();}
    }
}
