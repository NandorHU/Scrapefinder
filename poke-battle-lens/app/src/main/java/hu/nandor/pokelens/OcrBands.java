package hu.nandor.pokelens;
import java.util.*;

/** Conservative whitespace removal. Ambiguous backgrounds retain the whole region. */
final class OcrBands {
    static final class Band {
        final int left,top,right,bottom,inkHeight;
        Band(int l,int t,int r,int b,int ink){left=l;top=t;right=r;bottom=b;inkHeight=ink;}
    }
    static List<Band> find(int[] pixels,int w,int h){
        if(w<1||h<1||pixels.length<w*h)return Collections.emptyList();
        int[] histogram=new int[4096];int samples=0,dominant=0;
        int step=Math.max(1,Math.max(w,h)/256);
        for(int y=0;y<h;y+=step)for(int x=0;x<w;x+=step){int c=pixels[y*w+x];int key=((c>>12)&0xf00)|((c>>8)&0xf0)|((c>>4)&15);histogram[key]++;samples++;if(histogram[key]>histogram[dominant])dominant=key;}
        if(histogram[dominant]<samples*.85f)return Collections.emptyList();
        long rr=0,gg=0,bb=0;int count=0;
        for(int y=0;y<h;y+=step)for(int x=0;x<w;x+=step){int c=pixels[y*w+x];int key=((c>>12)&0xf00)|((c>>8)&0xf0)|((c>>4)&15);if(key==dominant){rr+=(c>>16)&255;gg+=(c>>8)&255;bb+=c&255;count++;}}
        int r=(int)(rr/count),g=(int)(gg/count),b=(int)(bb/count);
        int[] left=new int[h],right=new int[h];Arrays.fill(left,w);Arrays.fill(right,-1);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int c=pixels[y*w+x];int delta=Math.max(Math.abs(((c>>16)&255)-r),Math.max(Math.abs(((c>>8)&255)-g),Math.abs((c&255)-b)));if(delta>40){left[y]=Math.min(left[y],x);right[y]=x;}}
        List<Band> bands=new ArrayList<>();int start=-1,end=-1,l=w,rt=-1;
        for(int y=0;y<h;y++){
            if(right[y]>=0){
                if(start>=0&&y-end>7){if(!append(bands,start,end,l,rt,w,h))return Collections.emptyList();start=-1;l=w;rt=-1;}
                if(start<0)start=y;end=y;l=Math.min(l,left[y]);rt=Math.max(rt,right[y]);
            }
        }
        if(start>=0&&!append(bands,start,end,l,rt,w,h))return Collections.emptyList();
        return bands.size()<=8?bands:Collections.emptyList();
    }
    private static boolean append(List<Band> out,int top,int bottom,int left,int right,int w,int h){
        int inkHeight=bottom-top+1;
        // Tiny lines may be borders; tall objects may be sprites. Never shrink their text.
        if(inkHeight<8||inkHeight>80)return false;
        int pad=Math.max(4,inkHeight/6);
        out.add(new Band(Math.max(0,left-pad),Math.max(0,top-pad),Math.min(w,right+1+pad),Math.min(h,bottom+1+pad),inkHeight));return true;
    }
}
