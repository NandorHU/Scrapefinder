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
    private List<Profile> profiles;private int active;private Dex dex;private BattleReader tester;private Bitmap preview;private boolean editing;
    @Override public void onCreate(Bundle b){super.onCreate(b);profiles=Profile.load(this);active=Math.min(getSharedPreferences("lens",0).getInt("active",0),profiles.size()-1);try{dex=new Dex(this);}catch(Exception e){new AlertDialog.Builder(this).setMessage("Nem tölthető be az adatbázis: "+e.getMessage()).setPositiveButton("Bezárás",(d,w)->finish()).show();return;}home();}
    private Profile profile(){return profiles.get(Math.max(0,active));}
    private void save(){Profile.save(this,profiles);getSharedPreferences("lens",0).edit().putInt("active",active).apply();}
    private LinearLayout screen(){LinearLayout root=Ui.column(this);ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);if(Build.VERSION.SDK_INT>=30)scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});return root;}
    private void home(){
        editing=false;
        LinearLayout root=screen();root.addView(Ui.text(this,"KÉPERNYŐFIGYELŐ • OFFLINE",12,Ui.ACCENT));root.addView(Ui.title(this,"Poké Battle Lens"));root.addView(Ui.text(this,"Támadások értékelése játék közben",16,Ui.MUTED));
        root.addView(Ui.text(this,"1. Válassz profilt",17,Ui.FG));Spinner picker=new Spinner(this);List<String> names=new ArrayList<>();for(Profile p:profiles)names.add(p.name);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names);picker.setAdapter(adapter);picker.setSelection(active);picker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){active=pos;save();}public void onNothingSelected(android.widget.AdapterView<?> p){}});root.addView(picker);
        LinearLayout profileActions=new LinearLayout(this);
        profileActions.addView(editorButton("Profil mentése",()->{save();toast("Profil mentve: "+profile().name);}),new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        profileActions.addView(editorButton("Mentés más néven",this::saveAs),new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));root.addView(profileActions);
        root.addView(Ui.button(this,"Mentett profil újratöltése",()->{profiles=Profile.load(this);active=Math.max(0,Math.min(getSharedPreferences("lens",0).getInt("active",0),profiles.size()-1));Profile.requestReload(this);home();toast("Mentett profil újratöltve: "+profile().name);}));
        root.addView(Ui.button(this,"Profil beállítása / kép tesztelése",this::chooseImage));root.addView(Ui.button(this,"Új játékprofil",()->{EditText e=Ui.edit(this,"Profil neve","");new AlertDialog.Builder(this).setTitle("Új profil").setView(e).setPositiveButton("Létrehozás",(d,w)->{String n=e.getText().toString().trim();if(!n.isEmpty()){profiles.add(new Profile(n));active=profiles.size()-1;save();home();}}).setNegativeButton("Mégse",null).show();}));
        root.addView(Ui.text(this,"2. Indítsd el a figyelést",17,Ui.FG));root.addView(Ui.button(this,"▶ Képernyőfigyelés indítása",this::start));root.addView(Ui.button(this,"■ Figyelés leállítása",()->{stopService(new Intent(this,ScanService.class));toast("Figyelés leállítva");}));root.addView(Ui.button(this,"Felismerés kézi javítása",this::manual));
        root.addView(Ui.text(this,"Első használat",17,Ui.FG));root.addView(Ui.text(this,"Készíts képernyőképet a nyitott támadásmenüről. A profilban jelöld ki az ellenfél nevét, a saját Pokémon nevét és a négy támadást. Ezt elrendezésenként egyszer kell beállítani. Utána indítsd el a figyelést, és válts vissza a játékra. Pokémonváltáskor automatikusan újraolvassa a neveket és támadásokat.\n\nÁlló és fekvő elrendezéshez külön profil ajánlott. A böngésző görgetése vagy címsávjának eltűnése után a területeket újra kell állítani.",14,Ui.MUTED));
        root.addView(Ui.text(this,"Mit mutat?",17,Ui.FG));root.addView(Ui.text(this,"A szorzók és az alap-pontosság százaléka a játék támadásai mellett jelennek meg. A ★ a bázisadatokból becsült legerősebb sebző támadást jelöli. 10%-on belül több támadás kap csillagot. Nem pontos sebzés vagy taktikai ajánlás. Hiányzó adatoknál vagy különleges sebző támadásnál nincs csillag. A százalék minden felismert támadásnál látszik; — esetén nincs megadott alap-pontosság. A csata közbeni pontosság/kitérés, képesség és tárgy módosításait nem követi. Alapból az 1× és az állapottámadások rejtve maradnak. Csatán kívül ○ jelzés látszik, szorzók nélkül. A kis gombbal minden jelzés 5 másodpercre előhívható; hosszan nyomva profilválasztás, Force load és leállítás.\n\n0×: hatástalan • ½×: ellenállás • 1×: semleges • 2×/4×: szuperhatékony. A STAB a saját Pokémon típusbónusza.\n\nAz erőmutató nem sebzésszámítás: a csillaghoz a faj bázis Attack/Sp. Attack és az ellenfél bázis Defense/Sp. Defense értékeit használja, nem a tényleges statisztikákat. Szint, IV/EV, nature és csata közbeni statváltozás nincs beleszámítva. Képességek és tárgyak módosíthatják a tényleges hatást. A felismerés angol nevekkel működik; becenevet vagy módosított játékadatot kézzel kell korrigálni.",14,Ui.MUTED));
        root.addView(Ui.text(this,"v0.3.2 • Nincs felhő, reklám vagy képfeltöltés",12,Ui.ACCENT));
    }
    private void saveAs(){
        EditText name=Ui.edit(this,"Új profil neve",profile().name+" – másolat");
        new AlertDialog.Builder(this).setTitle("Profil mentése más néven").setView(name).setPositiveButton("Mentés",(d,w)->{
            String n=name.getText().toString().trim();if(n.isEmpty()){toast("Adj nevet a profilnak.");return;}
            for(Profile p:profiles)if(p.name.equalsIgnoreCase(n)){toast("Ilyen nevű profil már létezik. Válassz másik nevet.");return;}
            Profile copy=Profile.from(profile().json());copy.name=n;profiles.add(copy);active=profiles.size()-1;save();home();toast("Profil mentve: "+n);
        }).setNegativeButton("Mégse",null).show();
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
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==CAPTURE&&result==RESULT_OK&&data!=null){Intent service=new Intent(this,ScanService.class).putExtra("result",result).putExtra("data",data);startForegroundService(service);toast("Válts vissza a játékra. Koppintás a kis gombra: minden szorzó. Hosszan nyomva: menü.");}else if(request==IMAGE&&result==RESULT_OK&&data!=null){try{if(preview!=null)preview.recycle();Uri uri=data.getData();if(Build.VERSION.SDK_INT>=28){preview=ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(),uri),(decoder,info,source)->{decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);int w=info.getSize().getWidth(),h=info.getSize().getHeight();if(Math.max(w,h)>2200){float scale=2200f/Math.max(w,h);decoder.setTargetSize(Math.round(w*scale),Math.round(h*scale));}});}else preview=MediaStore.Images.Media.getBitmap(getContentResolver(),uri);editor();}catch(Exception e){toast("A kép nem nyitható meg: "+e.getMessage());}}}
    private void editor(){
        editing=true;
        // A weighted canvas gets the remaining window, without a ScrollView shrinking the image.
        LinearLayout root=Ui.column(this);root.setPadding(Ui.dp(this,8),Ui.dp(this,4),Ui.dp(this,8),Ui.dp(this,4));setContentView(root);
        if(Build.VERSION.SDK_INT>=30)root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(i.left+Ui.dp(this,8),i.top+Ui.dp(this,4),i.right+Ui.dp(this,8),i.bottom+Ui.dp(this,4));return insets;});
        root.addView(Ui.text(this,"Területek kijelölése",20,Ui.FG));
        RegionEditor regions=new RegionEditor(this,preview,profile());regions.setTag("region-editor");
        LinearLayout choices=new LinearLayout(this);Spinner area=new Spinner(this);area.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"🟠 Ellenfél neve","🟢 Saját Pokémon neve","🔵 Négy támadás"}));
        area.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){regions.select(pos);}public void onNothingSelected(android.widget.AdapterView<?> p){}});
        choices.addView(area,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));Button generation=editorButton("Gen "+profile().generation,()->{});generation.setOnClickListener(v->{String[] gens=new String[9];for(int i=0;i<9;i++)gens[i]="Generáció "+(i+1);new AlertDialog.Builder(this).setTitle("A játék generációja").setSingleChoiceItems(gens,profile().generation-1,(d,pos)->{profile().generation=pos+1;generation.setText("Gen "+(pos+1));d.dismiss();}).setNegativeButton("Vissza",null).show();});choices.addView(generation,new LinearLayout.LayoutParams(Ui.dp(this,72),Ui.dp(this,48)));root.addView(choices);
        LinearLayout tools=new LinearLayout(this);final boolean[] moving={false};Button mode=editorButton("✎ Kijelölés",()->{});mode.setOnClickListener(v->{moving[0]=!moving[0];mode.setText(moving[0]?"✋ Mozgatás":"✎ Kijelölés");regions.setPanMode(moving[0]);});tools.addView(mode,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1.6f));
        tools.addView(editorButton("−",()->regions.zoomBy(1/1.4f)),new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));tools.addView(editorButton("+",()->regions.zoomBy(1.4f)),new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));tools.addView(editorButton("Teljes kép",regions::fitImage),new LinearLayout.LayoutParams(0,Ui.dp(this,48),1.3f));root.addView(tools);
        TextView hint=Ui.text(this,"",12,Ui.MUTED);root.addView(hint);
        root.addView(regions,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=new LinearLayout(this);Button undo=editorButton("Visszavonás",()->{regions.undo();area.setSelection(regions.selectedRegion());}),test=editorButton("Kép tesztelése",()->{}),done=editorButton("Profil mentése",()->{save();home();toast("Profil mentve: "+profile().name);});
        actions.addView(undo,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));actions.addView(test,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));actions.addView(done,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));root.addView(actions);
        regions.setListener(()->{undo.setEnabled(regions.canUndo());hint.setText((moving[0]?"Egy ujjal mozgatás":"Egy ujjal kijelölés • fogható sarkok")+"\nKét ujjal nagyítás / mozgatás • "+String.format(java.util.Locale.ROOT,"%.1f×",regions.zoomLevel()));});undo.setEnabled(false);
        test.setOnClickListener(v->{save();test.setEnabled(false);test.setText("Olvasás…");if(tester==null)tester=new BattleReader(dex);tester.scan(preview,profile(),new BattleReader.Callback(){public void done(BattleReader.Result r){test.setEnabled(true);test.setText("Kép tesztelése");if(!isFinishing()&&!isDestroyed()){TextView output=Ui.text(MainActivity.this,BattleSummary.describe(dex,r,profile())+"\n\nBeolvasott szöveg:\n"+r.raw,14,Ui.FG);ScrollView scroll=new ScrollView(MainActivity.this);scroll.setPadding(Ui.dp(MainActivity.this,16),0,Ui.dp(MainActivity.this,16),0);scroll.addView(output);new AlertDialog.Builder(MainActivity.this).setTitle("Felismerés eredménye").setView(scroll).setPositiveButton("Vissza a kijelöléshez",null).show();}}public void error(Exception e){test.setEnabled(true);test.setText("Kép tesztelése");toast("OCR-hiba: "+e.getMessage());}});});
    }
    private Button editorButton(String label,Runnable action){Button b=Ui.button(this,label,action);b.setTextSize(12);b.setPadding(Ui.dp(this,2),0,Ui.dp(this,2),0);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);return b;}
    @Override public void onBackPressed(){if(editing){save();home();}else super.onBackPressed();}
    private void manual(){
        LinearLayout box=Ui.column(this);EditText enemy=Ui.edit(this,"Ellenfél neve (üres = automatikus)",profile().manualEnemy),own=Ui.edit(this,"Saját Pokémon neve",profile().manualOwn),moves=Ui.edit(this,"Támadások vesszővel elválasztva",profile().manualMoves);box.addView(enemy);box.addView(own);box.addView(moves);box.addView(Ui.text(this,"Angol neveket használj. A javítás fixen megmarad, amíg vissza nem kapcsolsz automatikus módra.",13,Ui.MUTED));new AlertDialog.Builder(this).setTitle("Kézi javítás").setView(box).setPositiveButton("Mentés",(d,w)->{profile().manualEnemy=enemy.getText().toString().trim();profile().manualOwn=own.getText().toString().trim();profile().manualMoves=moves.getText().toString().trim();save();}).setNeutralButton("Automatikus mód",(d,w)->{profile().manualEnemy="";profile().manualOwn="";profile().manualMoves="";save();}).setNegativeButton("Mégse",null).show();
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){super.onDestroy();if(tester!=null)tester.close();/* bitmap lives until any outstanding OCR task finishes */}
}
