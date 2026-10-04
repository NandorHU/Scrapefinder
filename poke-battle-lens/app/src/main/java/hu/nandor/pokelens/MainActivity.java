package hu.nandor.pokelens;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.media.projection.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int CAPTURE=10,IMAGE=11;
    private List<Profile> profiles;private int active;private Dex dex;private BattleReader tester;private Bitmap preview;
    @Override public void onCreate(Bundle b){super.onCreate(b);profiles=Profile.load(this);active=Math.min(getSharedPreferences("lens",0).getInt("active",0),profiles.size()-1);try{dex=new Dex(this);}catch(Exception e){new AlertDialog.Builder(this).setMessage("Nem tölthető be az adatbázis: "+e.getMessage()).setPositiveButton("Bezárás",(d,w)->finish()).show();return;}home();}
    private Profile profile(){return profiles.get(Math.max(0,active));}
    private void save(){Profile.save(this,profiles);getSharedPreferences("lens",0).edit().putInt("active",active).apply();}
    private LinearLayout screen(){LinearLayout root=Ui.column(this);ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);if(Build.VERSION.SDK_INT>=30)scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});return root;}
    private void home(){
        LinearLayout root=screen();root.addView(Ui.text(this,"KÉPERNYŐFIGYELŐ • OFFLINE",12,Ui.ACCENT));root.addView(Ui.title(this,"Poké Battle Lens"));root.addView(Ui.text(this,"Támadások értékelése játék közben",16,Ui.MUTED));
        root.addView(Ui.text(this,"1. Válassz profilt",17,Ui.FG));Spinner picker=new Spinner(this);List<String> names=new ArrayList<>();for(Profile p:profiles)names.add(p.name);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names);picker.setAdapter(adapter);picker.setSelection(active);picker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){active=pos;save();}public void onNothingSelected(android.widget.AdapterView<?> p){}});root.addView(picker);
        root.addView(Ui.button(this,"Profil beállítása / kép tesztelése",this::chooseImage));root.addView(Ui.button(this,"Új játékprofil",()->{EditText e=Ui.edit(this,"Profil neve","");new AlertDialog.Builder(this).setTitle("Új profil").setView(e).setPositiveButton("Létrehozás",(d,w)->{String n=e.getText().toString().trim();if(!n.isEmpty()){profiles.add(new Profile(n));active=profiles.size()-1;save();home();}}).setNegativeButton("Mégse",null).show();}));
        root.addView(Ui.text(this,"2. Indítsd el a figyelést",17,Ui.FG));root.addView(Ui.button(this,"▶ Képernyőfigyelés indítása",this::start));root.addView(Ui.button(this,"■ Figyelés leállítása",()->{stopService(new Intent(this,ScanService.class));toast("Figyelés leállítva");}));root.addView(Ui.button(this,"Felismerés kézi javítása",this::manual));
        root.addView(Ui.text(this,"Első használat",17,Ui.FG));root.addView(Ui.text(this,"Készíts képernyőképet a nyitott támadásmenüről. A profilban jelöld ki az ellenfél nevét, a saját Pokémon nevét és a négy támadást. Utána indítsd el a figyelést, és válts vissza a játékra.\n\nÁlló és fekvő elrendezéshez külön profil ajánlott. A böngésző görgetése vagy címsávjának eltűnése után a területeket újra kell állítani.",14,Ui.MUTED));
        root.addView(Ui.text(this,"Mit mutat?",17,Ui.FG));root.addView(Ui.text(this,"0×: hatástalan • ½×: ellenállás • 1×: semleges • 2×/4×: szuperhatékony. A STAB a saját Pokémon típusbónusza.\n\nAz erőmutató nem sebzésszámítás: a fizikai és speciális támadásokat a saját Attack/Sp. Attack nélkül nem rangsorolja. Képességek és tárgyak módosíthatják a tényleges hatást. A felismerés angol nevekkel működik; becenevet vagy módosított játékadatot kézzel kell korrigálni.",14,Ui.MUTED));
        root.addView(Ui.text(this,"v0.1.0 • Nincs felhő, reklám vagy képfeltöltés",12,Ui.ACCENT));
    }
    private void start(){
        save();if(!Settings.canDrawOverlays(this)){new AlertDialog.Builder(this).setTitle("Lebegő ablak engedélyezése").setMessage("Engedélyezd a más alkalmazások feletti megjelenítést, majd nyomd meg újra az Indítás gombot.").setPositiveButton("Beállítás megnyitása",(d,w)->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())))).setNegativeButton("Mégse",null).show();return;}
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},20);return;}
        capture();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==20)capture();}
    private void capture(){
        MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent consent=Build.VERSION.SDK_INT>=34?manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()):manager.createScreenCaptureIntent();startActivityForResult(consent,CAPTURE);
    }
    private void chooseImage(){save();Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMAGE);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==CAPTURE&&result==RESULT_OK&&data!=null){Intent service=new Intent(this,ScanService.class).putExtra("result",result).putExtra("data",data);startForegroundService(service);toast("Válts vissza a játékra. A lebegő ablak húzható.");}else if(request==IMAGE&&result==RESULT_OK&&data!=null){try{if(preview!=null)preview.recycle();Uri uri=data.getData();if(Build.VERSION.SDK_INT>=28){preview=ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(),uri),(decoder,info,source)->{decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);int w=info.getSize().getWidth(),h=info.getSize().getHeight();if(Math.max(w,h)>2200){float scale=2200f/Math.max(w,h);decoder.setTargetSize(Math.round(w*scale),Math.round(h*scale));}});}else preview=MediaStore.Images.Media.getBitmap(getContentResolver(),uri);editor();}catch(Exception e){toast("A kép nem nyitható meg: "+e.getMessage());}}}
    private void editor(){
        LinearLayout root=screen();root.addView(Ui.title(this,"Olvasási területek"));root.addView(Ui.text(this,"Válassz területet, majd húzz köré téglalapot a képen. Csak a neveket jelöld ki, HP és szint nélkül.",14,Ui.MUTED));
        Spinner gen=new Spinner(this);String[] gens=new String[9];for(int i=0;i<9;i++)gens[i]="Generáció "+(i+1);gen.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,gens));gen.setSelection(profile().generation-1);gen.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){profile().generation=pos+1;}public void onNothingSelected(android.widget.AdapterView<?> p){}});root.addView(gen);
        RegionEditor regions=new RegionEditor(this,preview,profile());Spinner area=new Spinner(this);area.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"🟠 Ellenfél neve","🟢 Saját Pokémon neve","🔵 Négy támadás"}));area.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){regions.select(pos);}public void onNothingSelected(android.widget.AdapterView<?> p){}});root.addView(area);
        int height=Math.min(Ui.dp(this,520),Math.max(Ui.dp(this,320),getResources().getDisplayMetrics().heightPixels/2));root.addView(regions,new LinearLayout.LayoutParams(-1,height));
        root.addView(Ui.button(this,"Mentés és vissza",()->{save();home();}));TextView output=Ui.text(this,"A teszt eredménye itt jelenik meg.",14,Ui.MUTED);Button test=Ui.button(this,"Kép felismerésének tesztelése",()->{});test.setOnClickListener(v->{save();test.setEnabled(false);output.setText("Felismerés…");if(tester==null)tester=new BattleReader(dex);tester.scan(preview,profile(),new BattleReader.Callback(){public void done(BattleReader.Result r){test.setEnabled(true);output.setText(BattleSummary.describe(dex,r,profile())+"\n\nBeolvasott szöveg:\n"+r.raw);}public void error(Exception e){test.setEnabled(true);output.setText("OCR-hiba: "+e.getMessage());}});});root.addView(test);root.addView(output);
    }
    private void manual(){
        LinearLayout box=Ui.column(this);EditText enemy=Ui.edit(this,"Ellenfél neve (üres = automatikus)",profile().manualEnemy),own=Ui.edit(this,"Saját Pokémon neve",profile().manualOwn),moves=Ui.edit(this,"Támadások vesszővel elválasztva",profile().manualMoves);box.addView(enemy);box.addView(own);box.addView(moves);box.addView(Ui.text(this,"Angol neveket használj. A javítás fixen megmarad, amíg vissza nem kapcsolsz automatikus módra.",13,Ui.MUTED));new AlertDialog.Builder(this).setTitle("Kézi javítás").setView(box).setPositiveButton("Mentés",(d,w)->{profile().manualEnemy=enemy.getText().toString().trim();profile().manualOwn=own.getText().toString().trim();profile().manualMoves=moves.getText().toString().trim();save();}).setNeutralButton("Automatikus mód",(d,w)->{profile().manualEnemy="";profile().manualOwn="";profile().manualMoves="";save();}).setNegativeButton("Mégse",null).show();
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){super.onDestroy();if(tester!=null)tester.close();/* bitmap lives until any outstanding OCR task finishes */}
}
