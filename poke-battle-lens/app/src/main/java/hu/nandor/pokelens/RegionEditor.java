package hu.nandor.pokelens;
import android.content.Context;
import android.graphics.*;
import android.view.*;

final class RegionEditor extends View {
    private final Bitmap image;private final Profile profile;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF fit=new RectF();private int selection=0;private float startX,startY;
    private static final int[] COLORS={Color.rgb(255,163,102),Color.rgb(93,228,199),Color.rgb(133,164,255)};
    RegionEditor(Context c,Bitmap bitmap,Profile p){super(c);image=bitmap;profile=p;setMinimumHeight(Ui.dp(c,360));}
    void select(int value){selection=value;invalidate();}
    private float[] active(){return selection==0?profile.enemy:selection==1?profile.own:profile.moves;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float scale=Math.min(getWidth()/(float)image.getWidth(),getHeight()/(float)image.getHeight());float w=image.getWidth()*scale,h=image.getHeight()*scale;
        fit.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);canvas.drawBitmap(image,null,fit,paint);
        float[][] all={profile.enemy,profile.own,profile.moves};String[] labels={"ELLENFÉL","SAJÁT","TÁMADÁSOK"};
        for(int i=0;i<3;i++){float[] r=all[i];RectF box=new RectF(fit.left+r[0]*w,fit.top+r[1]*h,fit.left+r[2]*w,fit.top+r[3]*h);paint.setColor(COLORS[i]);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(i==selection?6:3);canvas.drawRect(box,paint);paint.setStyle(Paint.Style.FILL);paint.setTextSize(Ui.dp(getContext(),12));canvas.drawText(labels[i],box.left+4,Math.max(fit.top+15,box.top-5),paint);}
    }
    private float x(float px){return Math.max(0,Math.min(1,(px-fit.left)/fit.width()));}
    private float y(float py){return Math.max(0,Math.min(1,(py-fit.top)/fit.height()));}
    @Override public boolean onTouchEvent(MotionEvent e){
        if(fit.width()==0)return false;
        if(e.getAction()==MotionEvent.ACTION_DOWN){startX=x(e.getX());startY=y(e.getY());getParent().requestDisallowInterceptTouchEvent(true);return true;}
        if(e.getAction()==MotionEvent.ACTION_MOVE||e.getAction()==MotionEvent.ACTION_UP){float endX=x(e.getX()),endY=y(e.getY());if(Math.abs(endX-startX)>.015&&Math.abs(endY-startY)>.01){float[] r=active();r[0]=Math.min(startX,endX);r[1]=Math.min(startY,endY);r[2]=Math.max(startX,endX);r[3]=Math.max(startY,endY);invalidate();}if(e.getAction()==MotionEvent.ACTION_UP){performClick();getParent().requestDisallowInterceptTouchEvent(false);}return true;}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
