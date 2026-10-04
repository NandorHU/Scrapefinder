package hu.nandor.pokelens;
import android.content.Context;
import android.graphics.*;
import android.hardware.input.InputManager;
import android.os.*;
import android.view.*;
import java.util.*;

/** One translucent, non-touchable window; only its small labels are drawn. */
final class BadgeLayer extends View {
    private static final class Badge {final BadgePlacement.Box box;final BadgeValue value;Badge(BadgePlacement.Box box,BadgeValue value){this.box=box;this.value=value;}}
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG),background=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Badge> badges=new ArrayList<>();private final RevealWindow reveal=new RevealWindow();
    private final Runnable expire=()->invalidate();boolean unplaced;
    int visibleCount(long now){int count=0;for(Badge b:badges)if(reveal.showing(now)||b.value.exceptional())count++;return count;}
    BadgeLayer(Context c){super(c);ink.setTypeface(Typeface.DEFAULT_BOLD);ink.setTextSize(12*c.getResources().getDisplayMetrics().scaledDensity);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void clear(boolean resetReveal){badges.clear();if(resetReveal){reveal.reset();removeCallbacks(expire);}invalidate();}
    void revealAll(){reveal.reveal(SystemClock.elapsedRealtime());removeCallbacks(expire);postDelayed(expire,RevealWindow.DURATION_MS);invalidate();}
    List<int[]> prepare(Dex dex,BattleReader.Result result,Profile profile,int width,int height){
        badges.clear();unplaced=false;List<int[]> masks=new ArrayList<>();List<BadgePlacement.Box> occupied=new ArrayList<>();
        Paint.FontMetrics font=ink.getFontMetrics();float h=Math.max(Ui.dp(getContext(),21),font.descent-font.ascent+Ui.dp(getContext(),6)),gap=Ui.dp(getContext(),4);
        for(MovePosition move:result.positions){
            BadgeValue value=BadgeValue.of(dex,move.name,result.enemy,profile.generation);float w=Math.max(Ui.dp(getContext(),28),ink.measureText(value.label)+Ui.dp(getContext(),10));
            BadgePlacement.Box box=BadgePlacement.place(move,w,h,width,height,gap,result.positions,occupied);if(box==null){unplaced=true;continue;}
            occupied.add(box);badges.add(new Badge(box,value));int pad=Ui.dp(getContext(),2);
            masks.add(new int[]{Math.max(0,(int)Math.floor(box.left)-pad),Math.max(0,(int)Math.floor(box.top)-pad),Math.min(width,(int)Math.ceil(box.right)+pad),Math.min(height,(int)Math.ceil(box.bottom)+pad)});
        }
        invalidate();return masks;
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);boolean all=reveal.showing(SystemClock.elapsedRealtime());Paint.FontMetrics font=ink.getFontMetrics();
        for(Badge badge:badges){BadgeValue v=badge.value;if(!all&&!v.exceptional())continue;BadgePlacement.Box b=badge.box;
            background.setColor(v.unknown?Color.rgb(89,63,131):v.status||v.factor==1?Ui.CARD:v.factor==0?Color.rgb(151,35,49):v.factor<1?Color.rgb(137,83,19):Color.rgb(20,112,67));
            canvas.drawRoundRect(b.left,b.top,b.right,b.bottom,Ui.dp(getContext(),5),Ui.dp(getContext(),5),background);
            ink.setColor(Color.WHITE);canvas.drawText(v.label,(b.left+b.right-ink.measureText(v.label))/2,(b.top+b.bottom-font.ascent-font.descent)/2,ink);
        }
    }
    @Override protected void onDetachedFromWindow(){removeCallbacks(expire);super.onDetachedFromWindow();}
    static WindowManager.LayoutParams windowParams(Context context,int width,int height){
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;p.alpha=.75f;
        if(Build.VERSION.SDK_INT>=31)p.alpha=Math.min(p.alpha,context.getSystemService(InputManager.class).getMaximumObscuringOpacityForTouch());
        if(Build.VERSION.SDK_INT>=30)p.setFitInsetsTypes(0);
        if(Build.VERSION.SDK_INT>=28)p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        return p;
    }
}
