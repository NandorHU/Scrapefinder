package hu.nandor.pokelens;

import android.content.*;
import android.graphics.*;
import android.os.ParcelFileDescriptor;
import android.view.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;

public class OverlayProfileTest {
    private Context app(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private void measure(OverlayPanel panel){int width=Ui.dp(app(),OverlayPanel.WIDTH_DP);panel.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Ui.dp(app(),500),View.MeasureSpec.AT_MOST));panel.layout(0,0,width,panel.getMeasuredHeight());}

    @Test public void twoAttackPanelHasNoBlankAreaAndForceLoadStaysVisibleWhenFolded() throws Exception{
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            int[] reloads={0};OverlayPanel p=new OverlayPanel(app(),()->{},()->reloads[0]++,()->{},()->{});
            p.status.setText("Élő · Dungeons & Pokémon – álló");p.body.setText("Shelmet · Bug\nTackle · 1×\nTail Whip · állapot");measure(p);
            int expanded=p.getMeasuredHeight();assertTrue("Two moves must fit in a small panel: "+expanded,expanded<Ui.dp(app(),150));assertTrue(p.forceLoad.getWidth()>Ui.dp(app(),80));
            p.forceLoad.performClick();assertEquals(1,reloads[0]);
            android.widget.LinearLayout tools=(android.widget.LinearLayout)p.getChildAt(1);tools.getChildAt(1).performClick();measure(p);
            assertEquals(View.GONE,p.body.getVisibility());assertTrue(p.getMeasuredHeight()<expanded);assertEquals(View.VISIBLE,p.forceLoad.getVisibility());p.forceLoad.performClick();assertEquals(2,reloads[0]);
            tools.getChildAt(1).performClick();measure(p);assertEquals(View.VISIBLE,p.body.getVisibility());
            Bitmap preview=Bitmap.createBitmap(p.getWidth(),p.getHeight(),Bitmap.Config.ARGB_8888);p.draw(new Canvas(preview));File file=new File(app().getExternalFilesDir(null),"overlay-preview.png");
            try(FileOutputStream out=new FileOutputStream(file)){preview.compress(Bitmap.CompressFormat.PNG,100,out);}catch(IOException e){throw new RuntimeException(e);}finally{preview.recycle();}
            try(InputStream done=new ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("cp "+file.getAbsolutePath()+" /data/local/tmp/overlay-preview.png"))){while(done.read()!=-1){}}catch(IOException e){throw new RuntimeException(e);}
        });
    }
    @Test public void fourAttacksFitWithoutFixedHeightOrClipping(){
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            OverlayPanel p=new OverlayPanel(app(),()->{},()->{},()->{},()->{});p.body.setText("Swampert · Water / Ground\nEnergy Ball · 4×\nThunderbolt · 0×\nTackle · 1×\nTail Whip · állapot");measure(p);
            assertTrue("Four moves should fit under 190dp",p.getMeasuredHeight()<Ui.dp(app(),190));assertEquals(5,p.body.getLineCount());assertTrue(p.body.getBottom()<=p.getHeight());
        });
    }
    @Test public void savedProfileSurvivesReloadWithSelectedIndexAndIndependentCopy(){
        SharedPreferences prefs=app().getSharedPreferences("lens",0);String old=prefs.getString("profiles",null);int oldIndex=prefs.getInt("active",0);long oldReload=prefs.getLong("reload",0);
        try{
            Profile first=new Profile("First"),custom=new Profile("Custom");custom.generation=3;custom.enemy=new float[]{.11f,.22f,.33f,.44f};custom.manualMoves="water-gun";
            Profile.save(app(),Arrays.asList(first,custom));prefs.edit().putInt("active",1).commit();
            // Local edits must not overwrite the stored profile during a forced reload.
            custom.enemy[0]=.2f;custom.manualMoves="tackle";Profile.requestReload(app());
            Profile loaded=Profile.active(app());assertEquals("Custom",loaded.name);assertEquals(3,loaded.generation);assertEquals(.11f,loaded.enemy[0],0);assertEquals("water-gun",loaded.manualMoves);assertNotEquals(oldReload,prefs.getLong("reload",0));
            Profile copy=Profile.from(loaded.json());copy.name="Copy";copy.enemy[0]=.05f;assertEquals(.11f,loaded.enemy[0],0);
            Profile.save(app(),Arrays.asList(first,loaded,copy));prefs.edit().putInt("active",2).commit();assertEquals("Copy",Profile.active(app()).name);assertEquals(.05f,Profile.active(app()).enemy[0],0);
        }finally{SharedPreferences.Editor e=prefs.edit().putInt("active",oldIndex).putLong("reload",oldReload);if(old==null)e.remove("profiles");else e.putString("profiles",old);e.commit();}
    }
}
