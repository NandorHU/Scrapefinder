package hu.nandor.pokelens;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.ArrayDeque;

/** Full-size image canvas: view transforms never alter the saved normalized regions. */
final class RegionEditor extends View {
    private final Bitmap image;
    private final Profile profile;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final ScaleGestureDetector scaler;
    private final ArrayDeque<Edit> history=new ArrayDeque<>();
    private float scale,left,top,lastX,lastY,anchorX,anchorY;
    private float[] draft;
    private int selection;
    private boolean panMode,multiple,drawing;
    private Runnable listener;
    private static final int[] COLORS={Color.rgb(255,163,102),Color.rgb(93,228,199),Color.rgb(133,164,255)};
    private static final String[] LABELS={"ELLENFÉL","SAJÁT","TÁMADÁSOK"};
    private static final class Edit {final int region;final float[] before;Edit(int r,float[] b){region=r;before=b;}}

    RegionEditor(Context c,Bitmap bitmap,Profile p){
        super(c);image=bitmap;profile=p;setBackgroundColor(Color.rgb(8,13,22));
        setContentDescription("Nagyítható kép. Egy ujjal kijelölés, két ujjal nagyítás és mozgatás.");
        scaler=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector detector){zoom(detector.getScaleFactor(),detector.getFocusX(),detector.getFocusY());return true;}
        });
        scaler.setQuickScaleEnabled(false);
    }
    void setListener(Runnable value){listener=value;}
    void select(int value){selection=value;draft=null;drawing=false;focusRegion();}
    void setPanMode(boolean value){panMode=value;draft=null;drawing=false;changed();}
    int selectedRegion(){return selection;}
    boolean canUndo(){return !history.isEmpty();}
    float zoomLevel(){return scale/(getWidth()/(float)image.getWidth());}
    void zoomBy(float factor){zoom(factor,getWidth()/2f,getHeight()/2f);}
    void fitImage(){
        if(getWidth()==0||getHeight()==0)return;
        scale=minScale();left=(getWidth()-image.getWidth()*scale)/2;top=(getHeight()-image.getHeight()*scale)/2;changed();
    }
    void undo(){
        if(history.isEmpty())return;
        Edit edit=history.removeLast();System.arraycopy(edit.before,0,region(edit.region),0,4);draft=null;drawing=false;
        selection=edit.region;focusRegion();
    }
    private float[] region(int index){return index==0?profile.enemy:index==1?profile.own:profile.moves;}
    private float minScale(){return Math.min(getWidth()/(float)image.getWidth(),getHeight()/(float)image.getHeight());}
    private void focusRegion(){
        if(getWidth()==0||getHeight()==0)return;
        float[] r=region(selection);float pad=Ui.dp(getContext(),36);
        scale=Math.max(minScale(),Math.min(getWidth()*8f/image.getWidth(),Math.min((getWidth()-pad*2)/((r[2]-r[0])*image.getWidth()),(getHeight()-pad*2)/((r[3]-r[1])*image.getHeight()))));
        left=getWidth()/2f-(r[0]+r[2])*image.getWidth()*scale/2;top=getHeight()/2f-(r[1]+r[3])*image.getHeight()*scale/2;
        constrain();changed();
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);focusRegion();}
    private void zoom(float factor,float focusX,float focusY){
        if(scale<=0)return;
        float next=Math.max(minScale(),Math.min(getWidth()*8f/image.getWidth(),scale*factor));
        float ratio=next/scale;left=focusX-(focusX-left)*ratio;top=focusY-(focusY-top)*ratio;scale=next;
        constrain();changed();
    }
    private void pan(float dx,float dy){left+=dx;top+=dy;constrain();changed();}
    private void constrain(){
        float w=image.getWidth()*scale,h=image.getHeight()*scale;
        left=w<=getWidth()?(getWidth()-w)/2:Math.max(getWidth()-w,Math.min(0,left));
        top=h<=getHeight()?(getHeight()-h)/2:Math.max(getHeight()-h,Math.min(0,top));
    }
    private void changed(){invalidate();if(listener!=null)listener.run();}
    private float x(float px){return Math.max(0,Math.min(1,(px-left)/(image.getWidth()*scale)));}
    private float y(float py){return Math.max(0,Math.min(1,(py-top)/(image.getHeight()*scale)));}
    private RectF screenRect(float[] r){return new RectF(left+r[0]*image.getWidth()*scale,top+r[1]*image.getHeight()*scale,left+r[2]*image.getWidth()*scale,top+r[3]*image.getHeight()*scale);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(scale<=0)return;
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);
        canvas.drawBitmap(image,null,new RectF(left,top,left+image.getWidth()*scale,top+image.getHeight()*scale),paint);
        for(int i=0;i<3;i++){
            RectF box=screenRect(i==selection&&draft!=null?draft:region(i));
            if(!RectF.intersects(box,new RectF(0,0,getWidth(),getHeight())))continue;
            paint.setColor(COLORS[i]);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(getContext(),i==selection?2.5f:1));canvas.drawRect(box,paint);
            paint.setStyle(Paint.Style.FILL);
            if(i==selection){float radius=Ui.dp(getContext(),6);canvas.drawCircle(box.left,box.top,radius,paint);canvas.drawCircle(box.right,box.top,radius,paint);canvas.drawCircle(box.left,box.bottom,radius,paint);canvas.drawCircle(box.right,box.bottom,radius,paint);}
            paint.setTextSize(Ui.dp(getContext(),11));String label=LABELS[i];float labelX=Math.max(Ui.dp(getContext(),4),Math.min(getWidth()-paint.measureText(label)-8,box.left+4));float labelY=Math.max(Ui.dp(getContext(),16),Math.min(getHeight()-4,box.top-Ui.dp(getContext(),7)));
            paint.setColor(Ui.BG);canvas.drawRect(labelX-3,labelY-Ui.dp(getContext(),13),labelX+paint.measureText(label)+3,labelY+3,paint);
            paint.setColor(COLORS[i]);canvas.drawText(label,labelX,labelY,paint);
        }
    }
    private void startSelection(float px,float py){
        if(px<left||px>left+image.getWidth()*scale||py<top||py>top+image.getHeight()*scale)return;
        float[] r=region(selection);RectF box=screenRect(r);float radius=Ui.dp(getContext(),24);
        float[][] corners={{box.left,box.top,r[2],r[3]},{box.right,box.top,r[0],r[3]},{box.left,box.bottom,r[2],r[1]},{box.right,box.bottom,r[0],r[1]}};
        float nearest=radius*radius;float[] handle=null;
        for(float[] corner:corners){float dx=px-corner[0],dy=py-corner[1],distance=dx*dx+dy*dy;if(distance<nearest){nearest=distance;handle=corner;}}
        anchorX=handle==null?x(px):handle[2];anchorY=handle==null?y(py):handle[3];drawing=true;
    }
    private void updateSelection(float px,float py){float endX=x(px),endY=y(py);draft=new float[]{Math.min(anchorX,endX),Math.min(anchorY,endY),Math.max(anchorX,endX),Math.max(anchorY,endY)};invalidate();}
    @Override public boolean onTouchEvent(MotionEvent e){
        // Feed every event, including UP/CANCEL, to the scale detector.
        scaler.onTouchEvent(e);if(scale<=0)return true;
        int action=e.getActionMasked();float fx=0,fy=0;int count=0;
        for(int i=0;i<e.getPointerCount();i++){if(action==MotionEvent.ACTION_POINTER_UP&&i==e.getActionIndex())continue;fx+=e.getX(i);fy+=e.getY(i);count++;}
        fx/=Math.max(1,count);fy/=Math.max(1,count);
        if(action==MotionEvent.ACTION_DOWN){
            multiple=false;draft=null;drawing=false;lastX=fx;lastY=fy;
            if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
            if(!panMode)startSelection(e.getX(),e.getY());return true;
        }
        if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_POINTER_UP){multiple=true;drawing=false;draft=null;lastX=fx;lastY=fy;invalidate();return true;}
        if(action==MotionEvent.ACTION_MOVE){
            if(e.getPointerCount()>1||panMode){pan(fx-lastX,fy-lastY);}
            else if(!multiple&&drawing)updateSelection(e.getX(),e.getY());
            lastX=fx;lastY=fy;return true;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            if(action==MotionEvent.ACTION_UP&&!multiple&&drawing){
                updateSelection(e.getX(),e.getY());
                if(draft!=null&&(draft[2]-draft[0])*image.getWidth()*scale>=Ui.dp(getContext(),6)&&(draft[3]-draft[1])*image.getHeight()*scale>=Ui.dp(getContext(),6)){
                    history.addLast(new Edit(selection,region(selection).clone()));if(history.size()>30)history.removeFirst();System.arraycopy(draft,0,region(selection),0,4);
                }
                performClick();
            }
            drawing=false;draft=null;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);changed();return true;
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
