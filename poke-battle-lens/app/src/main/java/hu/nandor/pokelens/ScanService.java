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
    private WindowManager windows;private LinearLayout overlay;private TextView body,status;private ScrollView scroll;private WindowManager.LayoutParams params;
    private boolean paused=false,collapsed=false,busy=false,stopped=false;private long lastScan=0;private int width,height;private String lastProfile="";
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
            dex=new Dex(this);reader=new BattleReader(dex);windows=getSystemService(WindowManager.class);makeOverlay();
            MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);Intent consent=Build.VERSION.SDK_INT>=33?intent.getParcelableExtra("data",Intent.class):intent.getParcelableExtra("data");
            if(consent==null)throw new IllegalArgumentException("Új képernyőengedély szükséges.");
            projection=manager.getMediaProjection(intent.getIntExtra("result",Activity.RESULT_CANCELED),consent);
            projection.registerCallback(new MediaProjection.Callback(){
                @Override public void onStop(){stopSelf();}
                @Override public void onCapturedContentResize(int w,int h){if(w>0&&h>0&&(w!=width||h!=height))resize(w,h);}
            },main);
            android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(metrics);
            width=metrics.widthPixels;height=metrics.heightPixels;createReader();
            display=projection.createVirtualDisplay("Poké Battle Lens",width,height,getResources().getDisplayMetrics().densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,images.getSurface(),null,main);
        }catch(Exception e){Toast.makeText(this,"A figyelés nem indult el: "+e.getMessage(),Toast.LENGTH_LONG).show();stopSelf();}
        return START_NOT_STICKY;
    }
    private void createReader(){images=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2);images.setOnImageAvailableListener(this::frame,main);}
    private void resize(int w,int h){
        width=w;height=h;if(display==null)return;
        ImageReader old=images;createReader();display.resize(w,h,getResources().getDisplayMetrics().densityDpi);display.setSurface(images.getSurface());old.close();
        if(body!=null)body.setText("A képernyő mérete változott. Ellenőrizd, hogy az aktuális profil területei illeszkednek.");
        if(params!=null&&overlay!=null){params.x=0;params.y=Ui.dp(this,48);windows.updateViewLayout(overlay,params);}
    }
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);if(Build.VERSION.SDK_INT<34&&windows!=null&&projection!=null){android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(metrics);if(metrics.widthPixels!=width||metrics.heightPixels!=height)resize(metrics.widthPixels,metrics.heightPixels);}}
    private void frame(ImageReader source){
        Image image=null;
        try {
            image=source.acquireLatestImage();if(image==null)return;
            if(stopped||paused||busy||SystemClock.elapsedRealtime()-lastScan<1500)return;
            busy=true;lastScan=SystemClock.elapsedRealtime();
            Image.Plane plane=image.getPlanes()[0];int pixel=plane.getPixelStride(),stride=plane.getRowStride(),w=image.getWidth(),h=image.getHeight();
            ByteBuffer buffer=plane.getBuffer();Bitmap padded=Bitmap.createBitmap(stride/pixel,h,Bitmap.Config.ARGB_8888);padded.copyPixelsFromBuffer(buffer);Bitmap bitmap=Bitmap.createBitmap(padded,0,0,w,h);if(bitmap!=padded)padded.recycle();
            Profile profile=Profile.active(this);lastProfile=profile.name;
            reader.scan(bitmap,profile,new BattleReader.Callback(){
                public void done(BattleReader.Result result){
                    try{if(!stopped&&!paused&&profile.json().toString().equals(Profile.active(ScanService.this).json().toString())){body.setText(BattleSummary.describe(dex,result,profile));status.setText((paused?"Szünet":"OCR • ")+profile.name+(profile.manualEnemy.trim().isEmpty()&&profile.manualMoves.trim().isEmpty()&&profile.manualOwn.trim().isEmpty()?"":" • KÉZI"));}}finally{bitmap.recycle();busy=false;if(stopped&&reader!=null)reader.close();}
                }
                public void error(Exception e){try{if(!stopped){status.setText("OCR-hiba");body.setText("Nem sikerült olvasni a képernyőt. "+e.getMessage());}}finally{bitmap.recycle();busy=false;if(stopped&&reader!=null)reader.close();}}
            });
        }catch(Exception e){busy=false;if(!stopped&&body!=null)body.setText("Képernyőolvasási hiba: "+e.getMessage());}
        finally{if(image!=null)image.close();}
    }
    private void makeOverlay(){
        overlay=new LinearLayout(this);overlay.setOrientation(LinearLayout.VERTICAL);overlay.setPadding(Ui.dp(this,10),Ui.dp(this,8),Ui.dp(this,10),Ui.dp(this,8));overlay.setBackground(Ui.rounded(Ui.CARD,this));overlay.setElevation(Ui.dp(this,12));
        status=Ui.text(this,"Poké Battle Lens • húzható",12,Ui.ACCENT);overlay.addView(status);LinearLayout row=new LinearLayout(this);
        Button pause=small("Ⅱ",()->{paused=!paused;status.setText(paused?"Szünet – az eredmény nem frissül":"OCR • "+lastProfile);if(paused&&body!=null)body.setText("Figyelés szünetel. Folytatáshoz nyomd meg újra a Ⅱ gombot.");});
        Button fold=small("▾",()->{collapsed=!collapsed;scroll.setVisibility(collapsed?View.GONE:View.VISIBLE);windows.updateViewLayout(overlay,params);});
        Button settings=small("⚙",()->{Intent i=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);});Button close=small("×",this::stopSelf);
        row.addView(pause);row.addView(fold);row.addView(settings);row.addView(close);overlay.addView(row);
        body=Ui.text(this,"Nyisd meg a támadásmenüt.\nHa nem ismeri fel, állítsd be a profil területeit a ⚙ gombbal.",13,Ui.FG);scroll=new ScrollView(this);scroll.addView(body);overlay.addView(scroll,new LinearLayout.LayoutParams(-1,Ui.dp(this,235)));
        params=new WindowManager.LayoutParams(Ui.dp(this,300),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);params.gravity=Gravity.TOP|Gravity.LEFT;params.x=0;params.y=Ui.dp(this,48);
        status.setOnTouchListener(new View.OnTouchListener(){int x,y;float dx,dy;public boolean onTouch(View v,android.view.MotionEvent event){switch(event.getAction()){case MotionEvent.ACTION_DOWN:x=params.x;y=params.y;dx=event.getRawX();dy=event.getRawY();return true;case MotionEvent.ACTION_MOVE:params.x=Math.max(0,Math.min(width-overlay.getWidth(),x+(int)(event.getRawX()-dx)));params.y=Math.max(0,Math.min(height-overlay.getHeight(),y+(int)(event.getRawY()-dy)));windows.updateViewLayout(overlay,params);return true;case MotionEvent.ACTION_UP:v.performClick();return true;}return false;}});
        windows.addView(overlay,params);
    }
    private Button small(String text,Runnable action){Button b=Ui.button(this,text,action);b.setTextSize(16);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setLayoutParams(new LinearLayout.LayoutParams(0,Ui.dp(this,40),1));return b;}
    @Override public void onDestroy(){
        stopped=true;if(display!=null){display.release();display=null;}if(images!=null){images.close();images=null;}if(projection!=null){projection.stop();projection=null;}if(reader!=null&&!busy)reader.close();if(overlay!=null&&windows!=null){try{windows.removeView(overlay);}catch(Exception ignored){}overlay=null;}stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
}
