package hu.nandor.pokelens;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.IntConsumer;

/** The only touchable window: one small button, or an explicitly opened menu. */
final class FloatingControls extends LinearLayout {
    final Button bubble;final OverlayPanel menu;final Spinner profiles;private boolean expanded;
    FloatingControls(Context c,Runnable pause,Runnable reload,Runnable settings,Runnable stop,Runnable reveal,Runnable back,IntConsumer select){
        super(c);setOrientation(VERTICAL);
        bubble=Ui.button(c,"…",reveal);bubble.setTextSize(21);bubble.setPadding(0,0,0,0);bubble.setMinWidth(0);bubble.setMinimumWidth(0);bubble.setMinHeight(0);bubble.setMinimumHeight(0);bubble.setTextColor(Ui.ACCENT);bubble.setBackgroundTintList(ColorStateList.valueOf(Ui.CARD));bubble.setContentDescription("Szorzók mutatása 5 másodpercre. Hosszan nyomva: profil és vezérlés.");addView(bubble,new LayoutParams(Ui.dp(c,44),Ui.dp(c,44)));
        menu=new OverlayPanel(c,pause,reload,settings,stop);menu.setVisibility(GONE);profiles=new Spinner(c);profiles.setContentDescription("Mentett játékprofil");menu.addView(profiles,2,new LayoutParams(-1,Ui.dp(c,44)));
        profiles.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View view,int pos,long id){select.accept(pos);}public void onNothingSelected(AdapterView<?> parent){}});
        LinearLayout actions=new LinearLayout(c);actions.addView(action(c,"Minden szorzó · 5 mp",reveal),new LayoutParams(0,Ui.dp(c,44),1.6f));actions.addView(action(c,"Vissza",back),new LayoutParams(0,Ui.dp(c,44),1));menu.addView(actions);addView(menu,new LayoutParams(Ui.dp(c,OverlayPanel.WIDTH_DP),-2));
    }
    private static Button action(Context c,String text,Runnable action){Button b=Ui.button(c,text,action);b.setTextSize(11);b.setPadding(0,0,0,0);b.setMinWidth(0);b.setMinimumWidth(0);b.setTextColor(Ui.ACCENT);b.setBackgroundTintList(ColorStateList.valueOf(Ui.BG));return b;}
    void expand(List<Profile> saved,int active){List<String> names=new ArrayList<>();for(Profile p:saved)names.add(p.name);profiles.setAdapter(new ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,names));profiles.setSelection(active);expanded=true;bubble.setVisibility(GONE);menu.setVisibility(VISIBLE);}
    void collapse(){expanded=false;menu.setVisibility(GONE);bubble.setVisibility(VISIBLE);}
    boolean expanded(){return expanded;}
}
