package hu.nandor.pokelens;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;

public class BadgeOverlayTest {
    private Context app(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static final String[] IDS={"rock-slide","water-gun","tackle","tail-whip"},LABELS={"Rock Slide","Water Gun","Tackle","Tail Whip"};
    private static final class BattleScene extends View {
        final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);BattleScene(Context c){super(c);}
        Paint textPaint(){ink.setColor(Color.BLACK);ink.setTypeface(Typeface.DEFAULT_BOLD);ink.setTextSize(getWidth()*.038f);return ink;}
        BattleReader.Result result(){BattleReader.Result r=new BattleReader.Result();r.enemy="charizard";r.own="squirtle";r.moves=Arrays.asList(IDS);Paint p=textPaint();Paint.FontMetrics f=p.getFontMetrics();for(int i=0;i<4;i++){float x=(i%2==0?.08f:.57f)*getWidth(),y=(i<2?.48f:.57f)*getHeight();r.positions.add(new MovePosition(IDS[i],x/getWidth(),(y+f.ascent)/getHeight(),(x+p.measureText(LABELS[i]))/getWidth(),(y+f.descent)/getHeight()));}return r;}
        @Override protected void onDraw(Canvas canvas){canvas.drawColor(Color.rgb(239,233,212));ink.setColor(Color.rgb(35,65,75));ink.setTypeface(Typeface.DEFAULT_BOLD);ink.setTextSize(getWidth()*.06f);canvas.drawText("CHARIZARD",getWidth()*.08f,getHeight()*.22f,ink);ink.setTextSize(getWidth()*.03f);canvas.drawText("Synthetic battle preview",getWidth()*.08f,getHeight()*.28f,ink);ink.setColor(Color.WHITE);canvas.drawRect(getWidth()*.04f,getHeight()*.42f,getWidth()*.96f,getHeight()*.62f,ink);Paint p=textPaint();for(int i=0;i<4;i++)canvas.drawText(LABELS[i],(i%2==0?.08f:.57f)*getWidth(),(i<2?.48f:.57f)*getHeight(),p);}
    }
    private Bitmap render(View v){Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));return b;}
    private String shell(String cmd) throws IOException {try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(cmd))){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return out.toString("UTF-8");}}
    @Test public void annotationsFollowMovesNeutralIsHiddenAndSmallButtonRevealsAll() throws Exception{
        Dex dex=new Dex(app());BadgeLayer[] layer={null};BattleScene[] scene={null};FloatingControls[] controls={null};FrameLayout[] holder={null};List<int[]>[] masks=new List[]{null};int[] reloads={0};
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{holder[0]=new FrameLayout(a);scene[0]=new BattleScene(a);layer[0]=new BadgeLayer(a);holder[0].addView(scene[0],new FrameLayout.LayoutParams(-1,-1));holder[0].addView(layer[0],new FrameLayout.LayoutParams(-1,-1));
                controls[0]=new FloatingControls(a,()->{},()->{reloads[0]++;controls[0].collapse();},()->{},()->{},()->layer[0].revealAll(),()->controls[0].collapse(),pos->{});
                controls[0].bubble.setText("◎");controls[0].bubble.setOnLongClickListener(v->{controls[0].expand(Arrays.asList(new Profile("Game A"),new Profile("Game B")),0);return true;});
                FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);p.setMargins(0,0,Ui.dp(a,12),Ui.dp(a,24));holder[0].addView(controls[0],p);a.setContentView(holder[0]);});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{BattleReader.Result result=scene[0].result();masks[0]=layer[0].prepare(dex,result,new Profile("test"),scene[0].getWidth(),scene[0].getHeight());assertEquals(4,masks[0].size());assertEquals(2,layer[0].visibleCount(SystemClock.elapsedRealtime()));assertEquals(Ui.dp(a,44),controls[0].getWidth());assertEquals(Ui.dp(a,44),controls[0].getHeight());
                Bitmap normal=render(holder[0]);controls[0].bubble.performClick();assertEquals(4,layer[0].visibleCount(SystemClock.elapsedRealtime()));Bitmap revealed=render(holder[0]);int[] neutral=masks[0].get(2);int changed=0;for(int y=neutral[1];y<neutral[3];y++)for(int x=neutral[0];x<neutral[2];x++)if(normal.getPixel(x,y)!=revealed.getPixel(x,y))changed++;assertTrue("Neutral move must visibly appear on demand",changed>50);normal.recycle();
                File output=new File(app().getExternalFilesDir(null),"badges-preview.png");try(FileOutputStream out=new FileOutputStream(output)){revealed.compress(Bitmap.CompressFormat.PNG,100,out);}catch(IOException e){throw new RuntimeException(e);}finally{revealed.recycle();}
                try{shell("cp "+output.getAbsolutePath()+" /data/local/tmp/badges-preview.png");}catch(IOException e){throw new RuntimeException(e);}
                controls[0].bubble.performLongClick();assertTrue(controls[0].expanded());
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{assertEquals(2,controls[0].profiles.getCount());assertTrue(controls[0].menu.forceLoad.isShown());controls[0].menu.forceLoad.performClick();assertEquals(1,reloads[0]);assertFalse(controls[0].expanded());layer[0].clear(true);assertEquals(0,layer[0].visibleCount(SystemClock.elapsedRealtime()));});
        }
    }
    private AccessibilityNodeInfo findDescription(AccessibilityNodeInfo root,String contains){
        if(root==null)return null;CharSequence description=root.getContentDescription();if(description!=null&&description.toString().contains(contains))return root;
        for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo found=findDescription(root.getChild(i),contains);if(found!=null)return found;}return null;
    }
    private AccessibilityNodeInfo waitDescription(String text,long timeout){
        long end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){for(android.view.accessibility.AccessibilityWindowInfo window:InstrumentationRegistry.getInstrumentation().getUiAutomation().getWindows()){AccessibilityNodeInfo found=findDescription(window.getRoot(),text);if(found!=null)return found;}SystemClock.sleep(100);}return null;
    }
    private boolean clickConsent(AccessibilityNodeInfo root){
        if(root==null)return false;String owner=String.valueOf(root.getPackageName()),id=String.valueOf(root.getViewIdResourceName());CharSequence raw=root.getText();String text=raw==null?"":raw.toString().trim().toLowerCase(java.util.Locale.ROOT);
        boolean positive=owner.equals("com.android.systemui")&&(id.endsWith(":id/button1")||id.endsWith(":id/start_button")||java.util.Arrays.asList("start now","start recording","share screen","share","start","continue").contains(text));
        if(positive){AccessibilityNodeInfo button=root;while(button!=null&&!button.isClickable())button=button.getParent();if(button!=null&&button.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;}
        for(int i=0;i<root.getChildCount();i++)if(clickConsent(root.getChild(i)))return true;return false;
    }
    private String windowState(){StringBuilder state=new StringBuilder();for(android.view.accessibility.AccessibilityWindowInfo window:InstrumentationRegistry.getInstrumentation().getUiAutomation().getWindows())appendDescriptions(window.getRoot(),state);return state.toString();}
    private void appendDescriptions(AccessibilityNodeInfo node,StringBuilder state){if(node==null)return;CharSequence description=node.getContentDescription();if(description!=null)state.append("[").append(node.getPackageName()).append(": ").append(description).append("]");for(int i=0;i<node.getChildCount();i++)appendDescriptions(node.getChild(i),state);}
    private String describeConsent(AccessibilityNodeInfo node,int depth){if(node==null||depth>5)return "";StringBuilder s=new StringBuilder();s.append("[").append(node.getViewIdResourceName()).append(" ").append(node.getText()).append(" click=").append(node.isClickable()).append("]");for(int i=0;i<node.getChildCount();i++)s.append(describeConsent(node.getChild(i),depth+1));return s.toString();}
    @Test public void liveProjectionKeepsStableBadgesAndFollowsPokemonSwitch() throws Exception{
        SharedPreferences prefs=app().getSharedPreferences("lens",0);String old=prefs.getString("profiles",null);int oldIndex=prefs.getInt("active",0);long oldReload=prefs.getLong("reload",0);
        UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();android.accessibilityservice.AccessibilityServiceInfo original=automation.getServiceInfo();android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.setServiceInfo(info);
        shell("appops set "+app().getPackageName()+" SYSTEM_ALERT_WINDOW allow");if(Build.VERSION.SDK_INT>=33)shell("pm grant "+app().getPackageName()+" android.permission.POST_NOTIFICATIONS");
        Profile profile=new Profile("Live capture fixture");profile.enemy=new float[]{.02f,.08f,.72f,.33f};profile.own=new float[]{.45f,.30f,.98f,.52f};profile.moves=new float[]{.03f,.56f,.98f,.84f};Profile.save(app(),Arrays.asList(profile));prefs.edit().putInt("active",0).commit();Profile.requestReload(app());
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{android.media.projection.MediaProjectionManager manager=a.getSystemService(android.media.projection.MediaProjectionManager.class);Intent consent=Build.VERSION.SDK_INT>=34?manager.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay()):manager.createScreenCaptureIntent();a.startActivityForResult(consent,10);});
            long end=SystemClock.uptimeMillis()+10000;boolean accepted=false;while(SystemClock.uptimeMillis()<end){if(clickConsent(automation.getRootInActiveWindow())){accepted=true;break;}SystemClock.sleep(100);}assertTrue("Real screen-sharing consent must be accepted: "+describeConsent(automation.getRootInActiveWindow(),0),accepted);
            // Wait until the service window exists before putting the synthetic game in front.
            assertNotNull("Capture service must start",waitDescription("Hosszan nyomva",10000));
            String fixture="hu.nandor.pokelens.fixture";scenario.onActivity(a->a.startActivity(new Intent().setComponent(new ComponentName(fixture,fixture+".BattleActivity"))));
            AccessibilityNodeInfo recognized=waitDescription("Charizard",15000);if(recognized==null)fail("Real projection must read the game: "+windowState()+" LOG: "+shell("logcat -d -t 250 -s PokeLens:D AndroidRuntime:E ActivityTaskManager:W"));
            int stable=0;for(int i=0;i<20;i++){if(waitDescription("Charizard",300)!=null)stable++;SystemClock.sleep(150);}assertTrue("Own overlays must not create a recognition loop",stable>=12);
            AccessibilityNodeInfo bubble=waitDescription("Charizard",5000);assertNotNull(bubble);assertTrue(bubble.performAction(AccessibilityNodeInfo.ACTION_CLICK));SystemClock.sleep(300);assertNotNull("Showing neutral badges must not pollute OCR",waitDescription("Tackle · 1×",5000));
            android.util.DisplayMetrics metrics=app().getResources().getDisplayMetrics();float x=metrics.widthPixels*.2f,y=metrics.heightPixels*.7f;long down=SystemClock.uptimeMillis();MotionEvent press=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x,y,0),release=MotionEvent.obtain(down,down+50,MotionEvent.ACTION_UP,x,y,0);press.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);release.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);automation.injectInputEvent(press,true);automation.injectInputEvent(release,true);press.recycle();release.recycle();
            AccessibilityNodeInfo changed=waitDescription("Swampert",15000);assertNotNull("Changed enemy and moves must replace old annotations: "+windowState(),changed);assertNotNull(waitDescription("Energy Ball · 4×",5000));assertNotNull(waitDescription("Thunderbolt · 0×",5000));
        }finally{app().stopService(new Intent(app(),ScanService.class));shell("am force-stop hu.nandor.pokelens.fixture");shell("appops set "+app().getPackageName()+" SYSTEM_ALERT_WINDOW default");automation.setServiceInfo(original);SharedPreferences.Editor edit=prefs.edit().putInt("active",oldIndex).putLong("reload",oldReload);if(old==null)edit.remove("profiles");else edit.putString("profiles",old);edit.commit();}
    }
    @Test public void translucentAnnotationLetsTapReachAnotherAppUid() throws Exception{
        String fixture="hu.nandor.pokelens.fixture";BadgeLayer[] layer={null};WindowManager manager=app().getSystemService(WindowManager.class);
        shell("appops set "+app().getPackageName()+" SYSTEM_ALERT_WINDOW allow");
        try{
            app().startActivity(new Intent().setComponent(new ComponentName(fixture,fixture+".TouchActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long end=SystemClock.uptimeMillis()+5000;boolean ready=false;while(SystemClock.uptimeMillis()<end){AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root!=null&&!root.findAccessibilityNodeInfosByText("Fixture taps: 0").isEmpty()){ready=true;break;}SystemClock.sleep(100);}assertTrue("Separate test app must be visible",ready);
            String packages=shell("cmd package list packages -U "+fixture);assertTrue(packages.contains(fixture));java.util.regex.Matcher uid=java.util.regex.Pattern.compile("uid:([0-9]+)").matcher(packages);assertTrue(uid.find());assertNotEquals("Fixture must have another UID",app().getApplicationInfo().uid,Integer.parseInt(uid.group(1)));
            android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();manager.getDefaultDisplay().getRealMetrics(metrics);int w=metrics.widthPixels,h=metrics.heightPixels;
            Dex dex=new Dex(app());int[][] hit={null};
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{layer[0]=new BadgeLayer(app());BattleReader.Result result=new BattleReader.Result();result.enemy="swampert";result.moves=Arrays.asList("thunderbolt");result.positions=Arrays.asList(new MovePosition("thunderbolt",.35f,.49f,.49f,.515f));hit[0]=layer[0].prepare(dex,result,new Profile("touch"),w,h).get(0);WindowManager.LayoutParams p=BadgeLayer.windowParams(app(),w,h);assertTrue((p.flags&WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)!=0);assertTrue(p.alpha<=.8f);manager.addView(layer[0],p);});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();SystemClock.sleep(200);
            float x=(hit[0][0]+hit[0][2])/2f,y=(hit[0][1]+hit[0][3])/2f;long down=SystemClock.uptimeMillis();MotionEvent press=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x,y,0),release=MotionEvent.obtain(down,down+50,MotionEvent.ACTION_UP,x,y,0);press.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);release.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            assertTrue(InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(press,true));assertTrue(InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(release,true));press.recycle();release.recycle();
            end=SystemClock.uptimeMillis()+3000;boolean clicked=false;while(SystemClock.uptimeMillis()<end){AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root!=null&&!root.findAccessibilityNodeInfosByText("Fixture taps: 1").isEmpty()){clicked=true;break;}SystemClock.sleep(100);}assertTrue("Tap must reach the game below the annotation window",clicked);
        }finally{if(layer[0]!=null)InstrumentationRegistry.getInstrumentation().runOnMainSync(()->manager.removeView(layer[0]));shell("am force-stop "+fixture);shell("appops set "+app().getPackageName()+" SYSTEM_ALERT_WINDOW default");}
    }
}
