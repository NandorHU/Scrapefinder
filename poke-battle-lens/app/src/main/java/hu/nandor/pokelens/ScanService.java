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

public final class ScanService extends Service {
    private static final String CHANNEL="battle_scan";
    private final Handler main=new Handler(Looper.getMainLooper());
    private MediaProjection projection;private VirtualDisplay display;private ImageReader images;private BattleReader reader;private Dex dex;
    private WindowManager windows;private OverlayPanel overlay;private TextView body,status;private WindowManager.LayoutParams params;
    private boolean paused=false,stopped=false;private long reloadToken;private int width,height;private String lastProfile="",profileKey="";
    private final ScanGate gate=new ScanGate();private Bitmap latestFrame;private Profile latestProfile;
    private final Runnable poll=new Runnable(){public void run(){if(stopped)return;frame();if(!stopped)main.postDelayed(this,100);}};
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||"STOP".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(projection!=null){return START_NOT_STICKY;}
        try{
            NotificationManager notifications=getSystemService(NotificationManager.class);notifications.createNotificationChannel(new NotificationChannel(CHANNEL,"Pokémon képernyőfigyelés",NotificationManager.IMPORTANCE_LOW));
            PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,ScanService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_lens).setContentTitle("Poké Battle Lens figyel").setContentText("A képernyő helyi feldolgozása aktív").setContentIntent(open).addAction(new Notification.Action.Builder(null,"Leállítás",stop).build()).setOngoing(true).build();
            if(Build.VERSION.SDK_INT>=29)startForeground(100,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);else startForeground(100,n);
            if(!Settings.canDrawOverlays(this))throw new IllegalStateException("Engedélyezd a lebegő ablakot.");
            dex=new Dex(this);reader=new BattleReader(dex);windows=getSystemService(WindowManager.class);
            MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);Intent consent=Build.VERSION.SDK_INT>=33?intent.getParcelableExtra("data",Intent.class):intent.getParcelableExtra("data");
            if(consent==null)throw new IllegalArgumentException("Új képernyőengedély szükséges.");
            projection=manager.getMediaProjection(intent.getIntExtra("result",Activity.RESULT_CANCELED),consent);
            projection.registerCallback(new MediaProjection.Callback(){
                @Override public void onStop(){stopSelf();}
                @Override public void onCapturedContentResize(int w,int h){if(w>0&&h>0&&(w!=width||h!=height))resize(w,h);}
            },main);
            android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(metrics);
            width=metrics.widthPixels;height=metrics.heightPixels;makeOverlay();createReader();
            display=projection.createVirtualDisplay("Poké Battle Lens",width,height,getResources().getDisplayMetrics().densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,images.getSurface(),null,main);
            main.post(poll);
        }catch(Exception e){Toast.makeText(this,"A figyelés nem indult el: "+e.getMessage(),Toast.LENGTH_LONG).show();stopSelf();}
        return START_NOT_STICKY;
    }
    private void createReader(){images=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2);}
    private void resize(int w,int h){
        width=w;height=h;gate.invalidate();discardFrame();if(display==null)return;
        ImageReader old=images;createReader();display.resize(w,h,getResources().getDisplayMetrics().densityDpi);display.setSurface(images.getSurface());old.close();
        if(body!=null)body.setText("A képernyő mérete változott. Ellenőrizd, hogy az aktuális profil területei illeszkednek.");
        if(params!=null&&overlay!=null){params.width=Math.min(Ui.dp(this,OverlayPanel.WIDTH_DP),width);params.x=Math.max(0,width-params.width);params.y=Math.max(0,height-Ui.dp(this,220));windows.updateViewLayout(overlay,params);}
    }
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);if(Build.VERSION.SDK_INT<34&&windows!=null&&projection!=null){android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(metrics);if(metrics.widthPixels!=width||metrics.heightPixels!=height)resize(metrics.widthPixels,metrics.heightPixels);}}
    private void discardFrame(){if(latestFrame!=null){latestFrame.recycle();latestFrame=null;}latestProfile=null;}
    private void changing(){if(!paused&&body!=null){status.setText("Olvasás · "+lastProfile);body.setText("Pokémonok / támadások olvasása…");}}
    private void reloadProfile(){
        // Drop both the bitmap and the pending OCR revision, even if the profile is unchanged.
        Profile p=Profile.active(this);lastProfile=p.name;profileKey="";gate.invalidate();discardFrame();
        paused=false;overlay.pause.setText("Ⅱ");reloadToken=getSharedPreferences("lens",0).getLong("reload",0);
        status.setText("Újratöltve · "+p.name);body.setText("Mentett profil betöltve. Új olvasás…");
    }
    /** Inspect current regions even while OCR is busy, so old callbacks are invalidated. */
    private void frame(){
        Image image=null;boolean starting=false;
        try{
            long requested=getSharedPreferences("lens",0).getLong("reload",0);if(requested!=reloadToken)reloadProfile();
            Profile profile=Profile.active(this);String key=profile.json().toString();
            if(!key.equals(profileKey)){profileKey=key;lastProfile=profile.name;gate.invalidate();discardFrame();changing();}
            if(images!=null)image=images.acquireLatestImage();
            if(image!=null){
                Image.Plane plane=image.getPlanes()[0];int pixel=plane.getPixelStride(),stride=plane.getRowStride(),w=image.getWidth(),h=image.getHeight();
                ByteBuffer buffer=plane.getBuffer();int[][] regions=new int[3][];String[] names={"enemy","own","moves"};
                for(int i=0;i<3;i++){Rect r=profile.crop(names[i],w,h);regions[i]=new int[]{r.left,r.top,r.right,r.bottom};}
                long fingerprint=SceneFingerprint.rgba(buffer,stride,pixel,regions);
                if(gate.observe(fingerprint)){
                    discardFrame();changing();
                    Bitmap padded=Bitmap.createBitmap(stride/pixel,h,Bitmap.Config.ARGB_8888);
                    try{buffer.rewind();padded.copyPixelsFromBuffer(buffer);latestFrame=Bitmap.createBitmap(padded,0,0,w,h);}
                    finally{if(padded!=latestFrame)padded.recycle();}
                    latestProfile=profile;
                }
            }
            if(!paused&&latestFrame!=null){
                final long revision=gate.begin(SystemClock.elapsedRealtime());
                if(revision>=0){
                    final Profile scannedProfile=latestProfile;final String scannedKey=profileKey;
                    starting=true;reader.scan(latestFrame,scannedProfile,new BattleReader.Callback(){
                        public void done(BattleReader.Result result){
                            boolean current=gate.finish(revision);
                            if(stopped){reader.close();return;}
                            if(current&&!paused&&scannedKey.equals(Profile.active(ScanService.this).json().toString())){
                                body.setText(BattleSummary.compact(dex,result,scannedProfile));
                                status.setText((scannedProfile.manualEnemy.trim().isEmpty()&&scannedProfile.manualMoves.trim().isEmpty()&&scannedProfile.manualOwn.trim().isEmpty()?"Élő · ":"Kézi · ")+scannedProfile.name);
                            }
                        }
                        public void error(Exception e){
                            boolean current=gate.finish(revision);gate.failed();gate.refresh();
                            if(stopped){reader.close();return;}
                            if(current&&!paused&&scannedKey.equals(Profile.active(ScanService.this).json().toString())){status.setText("OCR-hiba – újrapróbálás");body.setText("Nem sikerült olvasni a képernyőt. "+e.getMessage());}
                        }
                    });starting=false;
                }
            }
        }catch(Exception e){if(starting)gate.failed();gate.invalidate();discardFrame();if(!stopped&&!paused&&body!=null)body.setText("Képernyőolvasási hiba: "+e.getMessage());}
        finally{if(image!=null)image.close();}
    }
    private void makeOverlay(){
        overlay=new OverlayPanel(this,()->{
            paused=!paused;gate.refresh();overlay.pause.setText(paused?"▶":"Ⅱ");
            status.setText((paused?"Szünet · ":"Olvasás · ")+lastProfile);
            body.setText(paused?"Figyelés szünetel.":"Pokémonok / támadások olvasása…");
        },this::reloadProfile,()->startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)),this::stopSelf);
        body=overlay.body;status=overlay.status;
        params=new WindowManager.LayoutParams(Math.min(Ui.dp(this,OverlayPanel.WIDTH_DP),width),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT;params.x=Math.max(0,width-params.width);params.y=Math.max(0,height-Ui.dp(this,220));
        status.setOnTouchListener(new View.OnTouchListener(){int x,y;float dx,dy;public boolean onTouch(View v,android.view.MotionEvent event){switch(event.getAction()){case MotionEvent.ACTION_DOWN:x=params.x;y=params.y;dx=event.getRawX();dy=event.getRawY();return true;case MotionEvent.ACTION_MOVE:params.x=Math.max(0,Math.min(width-overlay.getWidth(),x+(int)(event.getRawX()-dx)));params.y=Math.max(0,Math.min(height-overlay.getHeight(),y+(int)(event.getRawY()-dy)));windows.updateViewLayout(overlay,params);return true;case MotionEvent.ACTION_UP:v.performClick();return true;}return false;}});
        overlay.addOnLayoutChangeListener((v,l,t,r,b,oldL,oldT,oldR,oldB)->{
            if(stopped)return;
            int x=Math.max(0,Math.min(Math.max(0,width-overlay.getWidth()),params.x));
            int y=Math.max(0,Math.min(Math.max(0,height-overlay.getHeight()),params.y));
            if(x!=params.x||y!=params.y){params.x=x;params.y=y;windows.updateViewLayout(overlay,params);}
        });
        windows.addView(overlay,params);
    }
    @Override public void onDestroy(){
        stopped=true;main.removeCallbacks(poll);gate.invalidate();discardFrame();if(display!=null){display.release();display=null;}if(images!=null){images.close();images=null;}if(projection!=null){projection.stop();projection=null;}if(reader!=null&&!gate.isBusy())reader.close();if(overlay!=null&&windows!=null){try{windows.removeView(overlay);}catch(Exception ignored){}overlay=null;}stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
}
