package hu.nandor.pokelens;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.nio.ByteBuffer;
import java.util.*;

public final class ScanService extends Service {
    private static final String CHANNEL="battle_scan";
    private static final String TAG="PokeLens";
    private final Handler main=new Handler(Looper.getMainLooper());
    private MediaProjection projection;private VirtualDisplay display;private ImageReader images;private BattleReader reader;private Dex dex;
    private WindowManager windows;private BadgeLayer badges;private FloatingControls controls;private WindowManager.LayoutParams badgeParams,controlParams;
    private boolean paused,menuOpen,stopped;private long reloadToken,lastTimestamp;private int width,height;private String profileKey="",lastProfile="";
    private final ScanGate gate=new ScanGate();private final CleanCapture capture=new CleanCapture();private List<int[]> badgeMasks=new ArrayList<>();private BattleReader.Result lastResult;
    private final BattlePresence presence=new BattlePresence();
    private long startedAt,scanStartedAt,scanSerial,lastBusyFrameAt;private boolean noFrameReported,revealWhenReady,readerReady,warmingReported;
    private ByteBuffer cachedFrame;private int cachedStride,cachedPixel,cachedWidth,cachedHeight;private boolean cachedDirty;
    private String statusTitle="Olvasás",statusText="Pokémonok és támadások felismerése…";
    private final Runnable poll=new Runnable(){public void run(){if(stopped)return;frame();if(!stopped)main.postDelayed(this,100);}};
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||"STOP".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(projection!=null)return START_NOT_STICKY;
        try{
            NotificationManager notifications=getSystemService(NotificationManager.class);notifications.createNotificationChannel(new NotificationChannel(CHANNEL,"Pokémon képernyőfigyelés",NotificationManager.IMPORTANCE_LOW));
            PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,ScanService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_lens).setContentTitle("Poké Battle Lens figyel").setContentText("Szorzók a támadások mellett • hosszan nyomva: menü").setContentIntent(open).addAction(new Notification.Action.Builder(null,"Leállítás",stop).build()).setOngoing(true).build();
            if(Build.VERSION.SDK_INT>=29)startForeground(100,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);else startForeground(100,n);
            if(!Settings.canDrawOverlays(this))throw new IllegalStateException("Engedélyezd a lebegő ablakot.");
            dex=new Dex(this);reader=new BattleReader(dex);windows=getSystemService(WindowManager.class);
            MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);Intent consent=Build.VERSION.SDK_INT>=33?intent.getParcelableExtra("data",Intent.class):intent.getParcelableExtra("data");
            if(consent==null)throw new IllegalArgumentException("Új képernyőengedély szükséges.");
            projection=manager.getMediaProjection(intent.getIntExtra("result",Activity.RESULT_CANCELED),consent);
            projection.registerCallback(new MediaProjection.Callback(){public void onStop(){stopSelf();}public void onCapturedContentResize(int w,int h){if(w>0&&h>0&&(w!=width||h!=height))resize(w,h);}},main);
            android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(metrics);width=metrics.widthPixels;height=metrics.heightPixels;
            makeOverlay();createReader();display=projection.createVirtualDisplay("Poké Battle Lens",width,height,getResources().getDisplayMetrics().densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,images.getSurface(),null,main);startedAt=SystemClock.elapsedRealtime();main.post(poll);
        }catch(Exception e){android.util.Log.e(TAG,"Capture startup failed",e);Toast.makeText(this,"A figyelés nem indult el: "+e.getMessage(),Toast.LENGTH_LONG).show();stopSelf();}
        return START_NOT_STICKY;
    }
    private void createReader(){images=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2);}
    private void cancelCapture(){capture.cancel();if(badges!=null)badges.setVisibility(View.VISIBLE);if(controls!=null)controls.setVisibility(View.VISIBLE);}
    private void clearResult(boolean resetReveal){lastResult=null;if(badges!=null)badges.clear(resetReveal);}
    private void resize(int w,int h){
        width=w;height=h;gate.invalidate();cancelCapture();clearResult(true);presence.reset();cachedFrame=null;revealWhenReady=false;badgeMasks.clear();lastTimestamp=0;startedAt=SystemClock.elapsedRealtime();noFrameReported=false;if(display==null)return;
        ImageReader old=images;createReader();display.resize(w,h,getResources().getDisplayMetrics().densityDpi);display.setSurface(images.getSurface());old.close();
        badgeParams.width=w;badgeParams.height=h;windows.updateViewLayout(badges,badgeParams);controlParams.x=Math.max(0,w-Ui.dp(this,48));controlParams.y=Math.max(0,h-Ui.dp(this,100));windows.updateViewLayout(controls,controlParams);
        message("Új képernyőméret","Ellenőrizd a profil kijelöléseit.","?");
    }
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);if(Build.VERSION.SDK_INT<34&&windows!=null&&projection!=null){android.util.DisplayMetrics m=new android.util.DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(m);if(m.widthPixels!=width||m.heightPixels!=height)resize(m.widthPixels,m.heightPixels);}}
    private void message(String title,String text,String icon){statusTitle=title;statusText=text;if(controls==null)return;controls.menu.status.setText(title+" · "+lastProfile);controls.menu.body.setText(text);controls.bubble.setText(icon);controls.bubble.setContentDescription(title+". "+text+" Koppintás: minden szorzó 5 másodpercre. Hosszan nyomva: menü.");}
    private void changing(){clearResult(false);presence.reset();if(!paused&&!menuOpen)message("Olvasás","Pokémonok és támadások felismerése…","…");}
    private void reloadProfile(){
        Profile p=Profile.active(this);lastProfile=p.name;profileKey="";reloadToken=getSharedPreferences("lens",0).getLong("reload",0);gate.invalidate();cancelCapture();clearResult(true);presence.reset();cachedFrame=null;revealWhenReady=false;startedAt=SystemClock.elapsedRealtime();noFrameReported=false;paused=false;controls.menu.pause.setText("Ⅱ");
        if(menuOpen){menuOpen=false;controls.collapse();}message("Újratöltve","Mentett profil betöltve. Friss képernyő olvasása…","…");
    }
    private int[][] regions(Profile p,int w,int h){int[][] out=new int[3][];String[] keys={"enemy","own","moves"};for(int i=0;i<3;i++){Rect r=p.crop(keys[i],w,h);out[i]=new int[]{r.left,r.top,r.right,r.bottom};}return out;}
    private int[] controlBounds(){int[] pos=new int[2];controls.getLocationOnScreen(pos);int pad=Ui.dp(this,8);return new int[]{Math.max(0,pos[0]-pad),Math.max(0,pos[1]-pad),Math.min(width,pos[0]+controls.getWidth()+pad),Math.min(height,pos[1]+controls.getHeight()+pad)};}
    private int[][] masks(){List<int[]> out=new ArrayList<>(badgeMasks);if(controls!=null)out.add(controlBounds());return out.toArray(new int[0][]);}
    private boolean controlOverlaps(int[][] areas){int[] c=controlBounds();for(int[] r:areas)if(c[0]<r[2]&&c[2]>r[0]&&c[1]<r[3]&&c[3]>r[1])return true;return false;}
    private void retainFrame(Image image,boolean dirty){
        Image.Plane plane=image.getPlanes()[0];cachedStride=plane.getRowStride();cachedPixel=plane.getPixelStride();cachedWidth=image.getWidth();cachedHeight=image.getHeight();
        int size=cachedStride*cachedHeight;if(cachedFrame==null||cachedFrame.capacity()!=size)cachedFrame=ByteBuffer.allocate(size);
        ByteBuffer source=plane.getBuffer();source.rewind();cachedFrame.clear();cachedFrame.put(source);cachedFrame.rewind();cachedDirty=dirty;
    }
    private void ocrTimeout(){
        scanSerial++;scanStartedAt=0;gate.failed();gate.invalidate();cancelCapture();clearResult(true);presence.reset();cachedFrame=null;startedAt=SystemClock.elapsedRealtime();noFrameReported=false;
        reader.close();reader=new BattleReader(dex);readerReady=false;warmingReported=false;
        message("Felismerési időtúllépés","A szövegfelismerés nem válaszolt a megengedett időn belül. Új olvasóval újrapróbálás; ha ismétlődik, indítsd újra a figyelést.","?");
    }
    private void frame(){
        Image image=null;boolean starting=false;
        try{
            long requested=getSharedPreferences("lens",0).getLong("reload",0);if(requested!=reloadToken)reloadProfile();
            Profile profile=Profile.active(this);String key=profile.json().toString();
            if(!key.equals(profileKey)){profileKey=key;lastProfile=profile.name;gate.invalidate();cancelCapture();cachedFrame=null;startedAt=SystemClock.elapsedRealtime();noFrameReported=false;changing();}
            if(paused||menuOpen)return;
            long now=SystemClock.elapsedRealtime();
            // The bundled recognizer may need considerably longer for its first model load.
            // Recreating it at 7 seconds repeatedly would prevent that load from finishing.
            // Busy devices also get that grace period for later reads, with a visible slow state.
            if(gate.isBusy()&&scanStartedAt>0){
                long elapsed=now-scanStartedAt;
                if(elapsed>30000){ocrTimeout();return;}
                if(!warmingReported&&elapsed>7000){warmingReported=true;clearResult(true);message(readerReady?"Lassú felismerés":"Felismerő indítása","A szövegfelismerés még folyamatban van. Legfeljebb 30 másodpercig várok a válaszra.","…");}
            }
            // OCR and animated capture compete for CPU on slower devices. Keep only
            // the newest frame twice a second while the recognizer is occupied.
            if(gate.isBusy()){if(now-lastBusyFrameAt<500)return;lastBusyFrameAt=now;}
            if(images!=null)image=images.acquireLatestImage();
            int[][] areas=regions(profile,width,height);
            if(image!=null){Image.Plane plane=image.getPlanes()[0];lastTimestamp=image.getTimestamp();noFrameReported=false;gate.observe(SceneFingerprint.rgba(plane.getBuffer(),plane.getRowStride(),plane.getPixelStride(),areas,masks()));retainFrame(image,capture.waiting()||badges.visibleCount(now)>0||controlOverlaps(areas));}
            else if(cachedFrame!=null&&!gate.hasFrame()){gate.observe(SceneFingerprint.rgba(cachedFrame,cachedStride,cachedPixel,areas,masks()));}
            else if(cachedFrame==null&&!noFrameReported&&now-startedAt>3500){noFrameReported=true;clearResult(true);presence.reset();message("Nincs képkocka","A megosztásból nem érkezik kép. Indítsd újra a figyelést, és válaszd a teljes képernyő megosztását.","?");}
            if(capture.timedOut(now)){android.util.Log.w(TAG,"Fresh capture timeout; timestamp="+lastTimestamp+", size="+width+"x"+height);cancelCapture();gate.refresh();clearResult(true);presence.reset();cachedFrame=null;message("Nincs friss képkocka","A jelzések elrejtése után nem érkezett új kép. A korábbi jelzéseket töröltem; újrapróbálás.","?");return;}
            if(!capture.waiting()){
                if(!gate.canBegin(now)||cachedFrame==null)return;
                // An initial/static frame is already clean when no glyphs or control cover
                // the reading regions. Do not discard it waiting for another composition.
                if(cachedDirty){
                    capture.begin(now,lastTimestamp);badges.setVisibility(View.INVISIBLE);controls.setVisibility(View.INVISIBLE);return;
                }
            }
            if(capture.waiting()){if(!capture.ready(now,lastTimestamp))return;cachedDirty=false;}
            final long revision=gate.begin(now);if(revision<0){cancelCapture();return;}
            starting=true;android.util.Log.d(TAG,"Starting scan; time="+now);
            final int stride=cachedStride,pixel=cachedPixel,w=cachedWidth,h=cachedHeight;final ByteBuffer clean=ByteBuffer.allocate(stride*h);ByteBuffer snapshot=cachedFrame.duplicate();snapshot.rewind();clean.put(snapshot);clean.rewind();
            Bitmap padded=Bitmap.createBitmap(stride/pixel,h,Bitmap.Config.ARGB_8888),bitmap=null;
            try{padded.copyPixelsFromBuffer(clean);bitmap=Bitmap.createBitmap(padded,0,0,w,h);}finally{if(padded!=bitmap)padded.recycle();}
            final Profile scannedProfile=profile;final String scannedKey=key;final long scannedReload=reloadToken,serial=++scanSerial;final BattleReader scanner=reader;scanStartedAt=now;warmingReported=false;
            try{scanner.scan(bitmap,profile,new BattleReader.Callback(){
                public void done(BattleReader.Result result){
                    if(serial!=scanSerial)return;android.util.Log.d(TAG,"OCR duration="+(SystemClock.elapsedRealtime()-scanStartedAt));readerReady=true;warmingReported=false;scanStartedAt=0;boolean current=gate.finish(revision);if(stopped){scanner.close();return;}
                    android.util.Log.d(TAG,"OCR complete; current="+current+", enemy="+(result.enemy!=null)+", positions="+result.positions.size());
                    if(current&&!paused&&!menuOpen&&scannedReload==getSharedPreferences("lens",0).getLong("reload",0)&&scannedKey.equals(Profile.active(ScanService.this).json().toString())){
                        BattlePresence.Mode mode=presence.observe(result.battleVisible,result.enemy,result.own,result.moves);
                        if(mode==BattlePresence.Mode.WAITING){clearResult(true);revealWhenReady=false;message("Várakozás csatára","Nem látható felismerhető csata és támadásmenü. Útválasztás vagy más képernyő alatt nincsenek csatajelzések. Ha csata látható, ellenőrizd a profil területeit.","○");return;}
                        if(mode==BattlePresence.Mode.VERIFYING){clearResult(false);message("Csata ellenőrzése","Új Pokémon vagy támadáslista. Második olvasással megerősítés…","…");gate.refresh();return;}
                        if(mode==BattlePresence.Mode.UNCERTAIN){clearResult(false);message("Ellenőrizd a felismerést",BattleSummary.compact(dex,result,scannedProfile)+"\n"+result.raw,"?");return;}
                        lastResult=result;badgeMasks=badges.prepare(dex,result,scannedProfile,w,h);
                        if(revealWhenReady){badges.revealAll();revealWhenReady=false;}
                        gate.rebase(SceneFingerprint.rgba(clean,stride,pixel,regions(scannedProfile,w,h),masks()));
                        boolean uncertain=result.enemy==null||result.positions.isEmpty()||badges.unplaced;for(MovePosition p:result.positions)if(p.name==null)uncertain=true;
                        String summary=BattleSummary.compact(dex,result,scannedProfile);if(!uncertain&&badges.visibleCount(SystemClock.elapsedRealtime())==0)summary+="\nAlapnézetben nincs eltérő típusszorzó. Koppintásra az 1× és az állapottámadások is megjelennek.";
                        message(uncertain?"Ellenőrizd a felismerést":"Élő",summary,uncertain?"?":"◎");
                    }
                }
                public void error(Exception e){if(serial!=scanSerial)return;scanStartedAt=0;android.util.Log.e(TAG,"OCR failed",e);boolean current=gate.finish(revision);gate.refresh();if(stopped){scanner.close();return;}if(current&&!paused&&!menuOpen){clearResult(false);presence.reset();message("OCR-hiba","Olvasás nem sikerült. Újrapróbálás…","?");}}
            });}finally{bitmap.recycle();}
            starting=false;cancelCapture();
        }catch(Exception e){android.util.Log.e(TAG,"Frame processing failed",e);if(starting)gate.failed();gate.invalidate();cancelCapture();clearResult(false);presence.reset();cachedFrame=null;if(!stopped&&!paused)message("Olvasási hiba",e.getMessage()==null?"Próbáld a Force load gombot.":e.getMessage(),"?");}
        finally{if(image!=null)image.close();}
    }
    private void showAll(){boolean wasBattle=lastResult!=null||presence.mode()!=BattlePresence.Mode.WAITING;if(menuOpen)closeMenu();if(lastResult!=null){badges.revealAll();return;}if(!wasBattle){Toast.makeText(this,"Csata és látható támadásmenü szükséges a szorzókhoz.",Toast.LENGTH_SHORT).show();return;}revealWhenReady=true;Toast.makeText(this,"A megerősített felismerés után minden jelzés 5 másodpercre megjelenik.",Toast.LENGTH_SHORT).show();}
    private void openMenu(){
        if(stopped||menuOpen)return;menuOpen=true;gate.invalidate();cancelCapture();badges.clear(false);
        List<Profile> saved=Profile.load(this);int active=Math.max(0,Math.min(saved.size()-1,getSharedPreferences("lens",0).getInt("active",0)));controls.expand(saved,active);
        controls.menu.status.setText(statusTitle+" · "+lastProfile);controls.menu.body.setText(statusText+"\n\náll. = állapottámadás • ? = bizonytalan\nA figyelés a menüben szünetel.");
    }
    private void closeMenu(){menuOpen=false;controls.collapse();gate.invalidate();cancelCapture();changing();if(paused)message("Szünet","A figyelés folytatható a menüben.","Ⅱ");}
    private void pause(){paused=!paused;controls.menu.pause.setText(paused?"▶":"Ⅱ");gate.invalidate();cancelCapture();clearResult(true);presence.reset();revealWhenReady=false;if(menuOpen){menuOpen=false;controls.collapse();}message(paused?"Szünet":"Olvasás",paused?"A figyelés folytatható a menüben.":"Friss képernyő olvasása…",paused?"Ⅱ":"…");}
    private void makeOverlay(){
        badges=new BadgeLayer(this);badgeParams=BadgeLayer.windowParams(this,width,height);windows.addView(badges,badgeParams);
        controls=new FloatingControls(this,this::pause,this::reloadProfile,()->{closeMenu();startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));},this::stopSelf,this::showAll,this::closeMenu,pos->{if(!menuOpen||pos==getSharedPreferences("lens",0).getInt("active",0))return;getSharedPreferences("lens",0).edit().putInt("active",pos).apply();Profile.requestReload(this);reloadProfile();});
        controlParams=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);controlParams.gravity=Gravity.TOP|Gravity.LEFT;if(Build.VERSION.SDK_INT>=30)controlParams.setFitInsetsTypes(0);controlParams.x=Math.max(0,width-Ui.dp(this,48));controlParams.y=Math.max(0,height-Ui.dp(this,100));
        View.OnTouchListener drag=new View.OnTouchListener(){int x,y;float downX,downY;boolean moved,longClick;final Runnable hold=()->{longClick=true;openMenu();};
            public boolean onTouch(View v,MotionEvent event){switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:x=controlParams.x;y=controlParams.y;downX=event.getRawX();downY=event.getRawY();moved=false;longClick=false;if(v==controls.bubble)main.postDelayed(hold,ViewConfiguration.getLongPressTimeout());return true;
                case MotionEvent.ACTION_MOVE:float dx=event.getRawX()-downX,dy=event.getRawY()-downY;if(Math.hypot(dx,dy)>Ui.dp(ScanService.this,6)){moved=true;main.removeCallbacks(hold);}if(moved&&!longClick){controlParams.x=Math.max(0,Math.min(Math.max(0,width-controls.getWidth()),x+(int)dx));controlParams.y=Math.max(0,Math.min(Math.max(0,height-controls.getHeight()),y+(int)dy));windows.updateViewLayout(controls,controlParams);}return true;
                case MotionEvent.ACTION_UP:main.removeCallbacks(hold);if(!moved&&!longClick)v.performClick();return true;
                case MotionEvent.ACTION_CANCEL:main.removeCallbacks(hold);return true;
            }return false;}
        };
        controls.bubble.setOnTouchListener(drag);controls.bubble.setOnLongClickListener(v->{openMenu();return true;});controls.menu.status.setOnTouchListener(drag);
        controls.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(stopped)return;int x=Math.max(0,Math.min(Math.max(0,width-controls.getWidth()),controlParams.x)),y=Math.max(0,Math.min(Math.max(0,height-controls.getHeight()),controlParams.y));if(x!=controlParams.x||y!=controlParams.y){controlParams.x=x;controlParams.y=y;windows.updateViewLayout(controls,controlParams);}});
        windows.addView(controls,controlParams);
    }
    @Override public void onDestroy(){
        stopped=true;cachedFrame=null;main.removeCallbacksAndMessages(null);gate.invalidate();if(display!=null)display.release();if(images!=null)images.close();if(projection!=null)projection.stop();if(reader!=null&&!gate.isBusy())reader.close();
        if(windows!=null){if(controls!=null)try{windows.removeView(controls);}catch(Exception ignored){}if(badges!=null)try{windows.removeView(badges);}catch(Exception ignored){}}
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
}
