package hu.nandor.pokelens;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;

/** Small, content-sized panel shared by the live service and UI tests. */
final class OverlayPanel extends LinearLayout {
    static final int WIDTH_DP=236;
    final TextView status,body;
    final Button pause,forceLoad;
    private boolean collapsed;

    OverlayPanel(Context c,Runnable onPause,Runnable onReload,Runnable onSettings,Runnable onClose){
        super(c);setOrientation(VERTICAL);setPadding(Ui.dp(c,8),Ui.dp(c,4),Ui.dp(c,8),Ui.dp(c,6));
        setBackground(Ui.rounded(Ui.CARD,c));setElevation(Ui.dp(c,12));
        status=Ui.text(c,"Felismerés…",11,Ui.ACCENT);status.setSingleLine(true);status.setEllipsize(TextUtils.TruncateAt.END);status.setPadding(0,0,0,Ui.dp(c,2));addView(status);
        body=Ui.text(c,"Nyisd meg a támadásmenüt.",13,Ui.FG);body.setPadding(0,Ui.dp(c,3),0,0);
        LinearLayout tools=new LinearLayout(c);
        pause=button(c,"Ⅱ","Figyelés szüneteltetése / folytatása",onPause);tools.addView(pause,new LayoutParams(0,Ui.dp(c,36),1));
        tools.addView(button(c,"▾","Eredmények összecsukása / kinyitása",()->{collapsed=!collapsed;body.setVisibility(collapsed?View.GONE:View.VISIBLE);}),new LayoutParams(0,Ui.dp(c,36),1));
        forceLoad=button(c,"Force load","Mentett profil újratöltése és új felismerés",onReload);forceLoad.setTag("force-load");tools.addView(forceLoad,new LayoutParams(0,Ui.dp(c,36),2.8f));
        tools.addView(button(c,"⚙","Profilok és beállítások",onSettings),new LayoutParams(0,Ui.dp(c,36),1));
        tools.addView(button(c,"×","Figyelés leállítása",onClose),new LayoutParams(0,Ui.dp(c,36),1));addView(tools);
        addView(body,new LayoutParams(-1,-2));
    }
    private static Button button(Context c,String label,String description,Runnable action){
        Button b=Ui.button(c,label,action);b.setTextSize(11);b.setPadding(0,0,0,0);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setSingleLine(true);b.setTextColor(Ui.ACCENT);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.BG));b.setContentDescription(description);return b;
    }
}
