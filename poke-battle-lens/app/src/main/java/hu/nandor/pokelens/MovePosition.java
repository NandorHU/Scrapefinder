package hu.nandor.pokelens;

/** OCR name rectangle in normalized coordinates of the captured full screen. */
public final class MovePosition {
    public final String name;
    public final float left,top,right,bottom;
    public MovePosition(String name,float left,float top,float right,float bottom){this.name=name;this.left=left;this.top=top;this.right=right;this.bottom=bottom;}
    static MovePosition map(String name,int l,int t,int r,int b,int sourceL,int sourceT,int sourceW,int sourceH,int targetL,int targetT,int targetW,int targetH,int frameW,int frameH){
        return new MovePosition(name,(sourceL+(l-targetL)*(float)sourceW/targetW)/frameW,(sourceT+(t-targetT)*(float)sourceH/targetH)/frameH,(sourceL+(r-targetL)*(float)sourceW/targetW)/frameW,(sourceT+(b-targetT)*(float)sourceH/targetH)/frameH);
    }
}
