package hu.nandor.pokelens;
import android.graphics.Bitmap;
import android.graphics.Rect;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.*;

public final class BattleReader implements AutoCloseable {
    private final TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private final Dex dex;
    public BattleReader(Dex dex){this.dex=dex;}
    public interface Callback { void done(Result result);void error(Exception e); }
    public static final class Result {public String enemy,own;public List<String> moves=new ArrayList<>();public String raw="";}
    public void scan(Bitmap frame,Profile profile,Callback callback){
        Result out=new Result();
        region(frame,profile,"enemy",(text,lines)->{
            out.enemy=dex.matchPokemon(profile.manualEnemy);if(out.enemy==null)out.enemy=findMon(lines);
            out.raw="Ellenfél: "+text;
            region(frame,profile,"own",(own,ownLines)->{
                out.own=dex.matchPokemon(profile.manualOwn);if(out.own==null)out.own=findMon(ownLines);out.raw+="\nSaját: "+own;
                region(frame,profile,"moves",(moves,moveLines)->{
                    Collection<String> candidates=profile.manualMoves.trim().isEmpty()?moveLines:Arrays.asList(profile.manualMoves.split("[,;\\n]"));
                    LinkedHashSet<String> detected=new LinkedHashSet<>();
                    for(String line:candidates){String match=NameMatcher.match(line,dex.moves.keySet());if(match!=null)detected.add(match);}
                    out.moves=new ArrayList<>(detected);if(out.moves.size()>4)out.moves=new ArrayList<>(out.moves.subList(0,4));out.raw+="\nTámadások: "+moves;callback.done(out);
                },callback);
            },callback);
        },callback);
    }
    private String manual(String s,Collection<String> names){return s.trim().isEmpty()?null:NameMatcher.match(s,names);}
    private String findMon(List<String> lines){for(String line:lines){String match=dex.matchPokemon(line);if(match!=null)return match;}return null;}
    private interface RegionCallback{void done(String text,List<String> lines);}
    private void region(Bitmap frame,Profile profile,String name,RegionCallback done,Callback failure){
        Rect r=profile.crop(name,frame.getWidth(),frame.getHeight());Bitmap regionBitmap=Bitmap.createBitmap(frame,r.left,r.top,r.width(),r.height());
        final Bitmap crop=regionBitmap==frame?regionBitmap.copy(Bitmap.Config.ARGB_8888,false):regionBitmap;
        // Pixel fonts benefit from upscaling. Cap work to avoid large allocations.
        float scale=Math.min(3f,Math.min(1800f/crop.getWidth(),600f/crop.getHeight()));
        Bitmap input=scale>1.05f?Bitmap.createScaledBitmap(crop,Math.round(crop.getWidth()*scale),Math.round(crop.getHeight()*scale),false):crop;
        recognizer.process(InputImage.fromBitmap(input,0)).addOnSuccessListener(text->{
            List<String> lines=new ArrayList<>();
            for(Text.TextBlock block:text.getTextBlocks())for(Text.Line line:block.getLines()){
                lines.add(line.getText());
                // Two move columns may be joined into one OCR line.
                if(name.equals("moves")) {
                    List<Text.Element> elements=line.getElements();List<String> segment=new ArrayList<>();int right=-1;
                    for(Text.Element element:elements){Rect box=element.getBoundingBox();if(box!=null&&right>=0&&box.left-right>Math.max(20,box.height()*2)){lines.add(String.join(" ",segment));segment.clear();}segment.add(element.getText());if(box!=null)right=box.right;}
                    if(!segment.isEmpty())lines.add(String.join(" ",segment));
                }
            }
            done.done(text.getText(),lines);
        }).addOnFailureListener(failure::error).addOnCompleteListener(task->{if(input!=crop)input.recycle();crop.recycle();});
    }
    @Override public void close(){recognizer.close();}
}
