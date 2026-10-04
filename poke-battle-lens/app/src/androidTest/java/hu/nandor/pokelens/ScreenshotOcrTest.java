package hu.nandor.pokelens;
import android.content.Context;
import android.graphics.*;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;

/** Reference images are local-only user fixtures, deliberately excluded from Git. */
public class ScreenshotOcrTest {
    @Test public void userBattleScreenshots() throws Exception {
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext(),test=InstrumentationRegistry.getInstrumentation().getContext();
        String[] files=test.getAssets().list("");boolean found=false;
        Dex dex=new Dex(app);
        for(String file:files){if(!file.endsWith(".png"))continue;found=true;
            Bitmap frame;try(java.io.InputStream in=test.getAssets().open(file)){frame=BitmapFactory.decodeStream(in);}
            CountDownLatch latch=new CountDownLatch(1);BattleReader.Result[] result=new BattleReader.Result[1];Exception[] error=new Exception[1];BattleReader reader=new BattleReader(dex);Profile p=new Profile("reference");
            reader.scan(frame,p,new BattleReader.Callback(){public void done(BattleReader.Result r){result[0]=r;latch.countDown();}public void error(Exception e){error[0]=e;latch.countDown();}});
            assertTrue(file+" OCR timeout",latch.await(90,TimeUnit.SECONDS));assertNull(file+" OCR error",error[0]);assertNotNull(result[0]);Log.i("LensOCR",file+" enemy="+result[0].enemy+" own="+result[0].own+" moves="+result[0].moves+" raw="+result[0].raw);
            assertEquals(file,file.startsWith("01")?"hariyama":"toucannon",result[0].enemy);
            assertEquals(file,file.startsWith("04")?"lickitung":"swampert",result[0].own);
            assertEquals(file,4,result[0].moves.size());
            assertTrue(file,result[0].moves.contains(file.startsWith("04")?"chip-away":"muddy-water"));
            reader.close();frame.recycle();
        }
        // Optional local reference images; CI source checkout has none.
        if(!found)Log.i("LensOCR","No local reference PNGs. Use synthetic test below.");
    }
    @Test public void cleanEnglishBattleLayout() throws Exception {
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();Dex dex=new Dex(app);Bitmap frame=Bitmap.createBitmap(900,1600,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(frame);c.drawColor(Color.WHITE);Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);ink.setColor(Color.BLACK);ink.setTextSize(38);ink.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText("TOUCANNON",60,270,ink);c.drawText("SWAMPERT",510,480,ink);c.drawText("MUD BOMB",40,800,ink);c.drawText("MUDDY WATER",40,850,ink);c.drawText("MEGA PUNCH",500,800,ink);c.drawText("ROCK SMASH",500,850,ink);
        CountDownLatch latch=new CountDownLatch(1);BattleReader.Result[] result=new BattleReader.Result[1];Exception[] errors=new Exception[1];BattleReader reader=new BattleReader(dex);reader.scan(frame,new Profile("synthetic"),new BattleReader.Callback(){public void done(BattleReader.Result r){result[0]=r;latch.countDown();}public void error(Exception e){errors[0]=e;latch.countDown();}});
        assertTrue(latch.await(90,TimeUnit.SECONDS));assertNull(errors[0]);assertEquals("toucannon",result[0].enemy);assertEquals("swampert",result[0].own);assertEquals(4,result[0].moves.size());reader.close();frame.recycle();
    }
    @Test public void pokemonSwitchRefreshesNamesAndAllMoves() throws Exception {
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();BattleReader reader=new BattleReader(new Dex(app));
        String[][] states={{"HARIYAMA","SWAMPERT","MUD BOMB","MUDDY WATER","MEGA PUNCH","ROCK SMASH"},{"TOUCANNON","LICKITUNG","ZEN HEADBUTT","BULLDOZE","CHIP AWAY","DISABLE"}};
        try{for(String[] state:states){
            Bitmap frame=Bitmap.createBitmap(900,1600,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(frame);c.drawColor(Color.WHITE);Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);ink.setColor(Color.BLACK);ink.setTextSize(38);ink.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText(state[0],60,270,ink);c.drawText(state[1],510,480,ink);c.drawText(state[2],40,800,ink);c.drawText(state[3],40,850,ink);c.drawText(state[4],500,800,ink);c.drawText(state[5],500,850,ink);
            CountDownLatch latch=new CountDownLatch(1);BattleReader.Result[] result=new BattleReader.Result[1];Exception[] errors=new Exception[1];
            reader.scan(frame,new Profile("switch"),new BattleReader.Callback(){public void done(BattleReader.Result r){result[0]=r;latch.countDown();}public void error(Exception e){errors[0]=e;latch.countDown();}});
            assertTrue(latch.await(90,TimeUnit.SECONDS));assertNull(errors[0]);assertNotNull(result[0]);
            assertEquals(state[0].toLowerCase(java.util.Locale.ROOT),result[0].enemy);assertEquals(state[1].toLowerCase(java.util.Locale.ROOT),result[0].own);
            java.util.Set<String> expected=new java.util.HashSet<>();for(int i=2;i<6;i++)expected.add(state[i].toLowerCase(java.util.Locale.ROOT).replace(' ','-'));
            assertEquals(expected,new java.util.HashSet<>(result[0].moves));frame.recycle();
            assertEquals(4,result[0].positions.size());
            for(MovePosition p:result[0].positions){
                int index=-1;for(int i=2;i<6;i++)if(state[i].toLowerCase(java.util.Locale.ROOT).replace(' ','-').equals(p.name))index=i;
                assertTrue("Every OCR anchor must match its current move",index>=2);
                assertEquals(index<4?40f/900:500f/900,p.left,.025f);
                float baseline=(index==2||index==4)?800:850;
                assertTrue("Anchor must follow the current row",p.top>(baseline-60)/1600&&p.bottom<(baseline+15)/1600);
                assertTrue(p.left<p.right&&p.top<p.bottom);
            }
        }}finally{reader.close();}
    }
    @Test public void compactDarkRowsKeepMovesAndScreenAnchors() throws Exception {
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();Bitmap frame=Bitmap.createBitmap(900,1600,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(frame);canvas.drawColor(Color.rgb(16,32,48));Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);ink.setColor(Color.WHITE);ink.setTextSize(38);ink.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("CHARIZARD",60,270,ink);canvas.drawText("SQUIRTLE",510,480,ink);canvas.drawText("ROCK SLIDE",40,800,ink);canvas.drawText("WATER GUN",500,800,ink);canvas.drawText("TACKLE",40,850,ink);canvas.drawText("TAIL WHIP",500,850,ink);
        CountDownLatch done=new CountDownLatch(1);BattleReader.Result[] result={null};Exception[] error={null};BattleReader reader=new BattleReader(new Dex(app));
        try{reader.scan(frame,new Profile("compact dark"),new BattleReader.Callback(){public void done(BattleReader.Result r){result[0]=r;done.countDown();}public void error(Exception e){error[0]=e;done.countDown();}});assertTrue(done.await(30,TimeUnit.SECONDS));assertNull(error[0]);assertEquals("charizard",result[0].enemy);assertEquals("squirtle",result[0].own);assertTrue(result[0].battleVisible);assertEquals(new HashSet<>(Arrays.asList("rock-slide","water-gun","tackle","tail-whip")),new HashSet<>(result[0].moves));assertEquals(4,result[0].positions.size());
            assertTrue("Remove blank margins instead of shrinking the entire screen",result[0].inputPixels<200000);
            for(MovePosition move:result[0].positions){boolean left=move.name.equals("rock-slide")||move.name.equals("tackle"),top=move.name.equals("rock-slide")||move.name.equals("water-gun");assertEquals(left?40f/900:500f/900,move.left,.025f);float baseline=top?800:850;assertTrue(move.top>(baseline-60)/1600&&move.bottom<(baseline+15)/1600);}
        }finally{reader.close();frame.recycle();}
    }
    @Test public void routeScreenIsNotBattleDespiteManualOverrides() throws Exception {
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();Bitmap frame=Bitmap.createBitmap(900,1600,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(frame);c.drawColor(Color.WHITE);Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);ink.setColor(Color.BLACK);ink.setTextSize(38);ink.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText("CHOOSE A PATH",60,270,ink);c.drawText("PIKACHU",510,480,ink);c.drawText("LEFT",40,800,ink);c.drawText("RIGHT",500,800,ink);c.drawText("CONTINUE",40,850,ink);
        Profile p=new Profile("route");p.manualEnemy="ditto";p.manualOwn="pikachu";p.manualMoves="charm,nasty-plot,play-nice,nuzzle";
        CountDownLatch done=new CountDownLatch(1);BattleReader.Result[] result={null};Exception[] error={null};BattleReader reader=new BattleReader(new Dex(app));
        try{reader.scan(frame,p,new BattleReader.Callback(){public void done(BattleReader.Result r){result[0]=r;done.countDown();}public void error(Exception e){error[0]=e;done.countDown();}});assertTrue(done.await(30,TimeUnit.SECONDS));assertNull(error[0]);assertNotNull(result[0]);assertEquals("ditto",result[0].enemy);assertEquals(4,result[0].moves.size());assertFalse("Manual values must not make route navigation a battle",result[0].battleVisible);}finally{reader.close();frame.recycle();}
    }
}
