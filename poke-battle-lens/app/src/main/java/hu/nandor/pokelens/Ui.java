package hu.nandor.pokelens;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;

final class Ui {
    static final int BG=Color.rgb(16,23,37),CARD=Color.rgb(27,39,58),FG=Color.rgb(237,243,255),MUTED=Color.rgb(166,185,211),ACCENT=Color.rgb(93,228,199);
    static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static TextView text(Context c,String text,int size,int color){TextView v=new TextView(c);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(c,5),0,dp(c,5));return v;}
    static TextView title(Context c,String s){TextView v=text(c,s,24,FG);v.setTypeface(null,Typeface.BOLD);return v;}
    static Button button(Context c,String s,Runnable click){Button b=new Button(c);b.setText(s);b.setTextColor(FG);b.setAllCaps(false);b.setOnClickListener(v->click.run());return b;}
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(c,18),dp(c,16),dp(c,18),dp(c,16));l.setBackgroundColor(BG);return l;}
    static GradientDrawable rounded(int color,Context c){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,16));return d;}
    static EditText edit(Context c,String hint,String value){EditText e=new EditText(c);e.setTextColor(FG);e.setHintTextColor(MUTED);e.setTextSize(15);e.setHint(hint);e.setText(value);e.setSingleLine(true);return e;}
}
