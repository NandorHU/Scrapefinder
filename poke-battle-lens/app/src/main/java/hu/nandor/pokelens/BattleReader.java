package hu.nandor.pokelens;
import android.graphics.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.*;

public final class BattleReader implements AutoCloseable {
    private final TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private final Dex dex;
    public BattleReader(Dex dex) { this.dex = dex; }
    public interface Callback { void done(Result result); void error(Exception e); }
    public static final class Result {
        public String enemy, own;
        public List<String> moves = new ArrayList<>();
        public List<MovePosition> positions = new ArrayList<>();
        public String raw = "";
        public boolean battleVisible;
        public int inputPixels;
    }
    private static final class Tile {final int region;final Rect source;final float scale;Rect target;Tile(int region,Rect source,float scale){this.region=region;this.source=source;this.scale=scale;}}
    private static final class Segment {final String text;final Rect box;final Tile tile;Segment(String text,Rect box,Tile tile){this.text=text;this.box=box;this.tile=tile;}}
    private static void segment(Tile tile,List<String> words,Rect box,List<List<String>> lines,List<Segment> moves){
        int region=tile.region;
        if(words.isEmpty())return;String text=String.join(" ",words);lines.get(region).add(text);if(region==2)moves.add(new Segment(text,new Rect(box),tile));
    }

    /** One OCR request reads all three regions together; it owns its input bitmap. */
    public void scan(Bitmap frame, Profile profile, Callback callback) {
        final int frameW=frame.getWidth(),frameH=frame.getHeight();
        String[] names = {"enemy", "own", "moves"};
        List<Tile> tiles=new ArrayList<>();
        for(int region=0;region<3;region++){
            Rect source=profile.crop(names[region],frameW,frameH);int w=source.width(),h=source.height();int[] pixels=new int[w*h];frame.getPixels(pixels,0,w,source.left,source.top,w,h);
            List<OcrBands.Band> bands=OcrBands.find(pixels,w,h);
            if(bands.isEmpty())tiles.add(new Tile(region,source,Math.min(3f,Math.min(960f/w,240f/h))));
            else for(OcrBands.Band band:bands){Rect crop=new Rect(source.left+band.left,source.top+band.top,source.left+band.right,source.top+band.bottom);tiles.add(new Tile(region,crop,Math.min(3f,Math.min(960f/crop.width(),24f/band.inkHeight))));}
        }
        int sheetWidth=1,y=16;
        for(Tile tile:tiles){int w=Math.max(1,Math.round(tile.source.width()*tile.scale)),h=Math.max(1,Math.round(tile.source.height()*tile.scale));tile.target=new Rect(16,y,16+w,y+h);sheetWidth=Math.max(sheetWidth,w+32);y+=h+16;}
        final Bitmap sheet=Bitmap.createBitmap(sheetWidth,y,Bitmap.Config.ARGB_8888);final int inputPixels=sheetWidth*y;
        Canvas canvas=new Canvas(sheet);canvas.drawColor(Color.WHITE);
        Paint paint=new Paint(); // Preserve pixel-font edges while packing only unambiguous text rows.
        for(Tile tile:tiles)canvas.drawBitmap(frame,tile.source,tile.target,paint);

        recognizer.process(InputImage.fromBitmap(sheet, 0)).addOnSuccessListener(text -> {
            try {
                List<List<String>> lines = Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
                List<Segment> moveSegments=new ArrayList<>();
                for (Text.TextBlock block : text.getTextBlocks()) {
                    for (Text.Line line : block.getLines()) {
                        // Work at element level: OCR can join two columns, or even different regions.
                        for (Tile tile : tiles) {
                            int region=tile.region;
                            List<Text.Element> elements = new ArrayList<>();
                            for (Text.Element e : line.getElements()) {
                                Rect box = e.getBoundingBox();
                                if (box != null && tile.target.contains(box.centerX(), box.centerY())) elements.add(e);
                            }
                            if (elements.isEmpty()) continue;
                            elements.sort(Comparator.comparingInt(e -> e.getBoundingBox().left));
                            List<String> segment = new ArrayList<>();
                            Rect segmentBox=null;
                            int right = -1;
                            for (Text.Element e : elements) {
                                Rect box = e.getBoundingBox();
                                if (region == 2 && right >= 0 && box.left - right > Math.max(20, box.height() * 2)) {
                                    segment(tile,segment,segmentBox,lines,moveSegments);
                                    segment.clear();
                                    segmentBox=null;
                                }
                                segment.add(e.getText());
                                if(segmentBox==null)segmentBox=new Rect(box);else segmentBox.union(box);
                                right = box.right;
                            }
                            segment(tile,segment,segmentBox,lines,moveSegments);
                        }
                    }
                }
                Result out = new Result();out.inputPixels=inputPixels;
                String observedEnemy=findMon(lines.get(0)),observedOwn=findMon(lines.get(1));
                boolean observedMove=false;for(Segment seg:moveSegments)if(dex.matchMove(seg.text)!=null){observedMove=true;break;}
                // Manual overrides must not turn a route/menu screen into a battle.
                out.battleVisible=observedMove&&(observedEnemy!=null||observedOwn!=null);
                out.enemy = dex.matchPokemon(profile.manualEnemy);
                if (out.enemy == null) out.enemy = observedEnemy;
                out.own = dex.matchPokemon(profile.manualOwn);
                if (out.own == null) out.own = observedOwn;
                Collection<String> candidates = profile.manualMoves.trim().isEmpty()
                        ? lines.get(2) : Arrays.asList(profile.manualMoves.split("[,;\\n]"));
                LinkedHashSet<String> detected = new LinkedHashSet<>();
                for (String line : candidates) {
                    String match = dex.matchMove(line);
                    if (match != null) detected.add(match);
                }
                out.moves = new ArrayList<>(detected);
                if (out.moves.size() > 4) out.moves = new ArrayList<>(out.moves.subList(0, 4));
                Set<String> positioned=new HashSet<>();
                moveSegments.sort(Comparator.comparingInt((Segment s)->s.box.top).thenComparingInt(s->s.box.left));
                // Known names first; unmatched plausible menu text gets a question mark.
                for(int pass=0;pass<2;pass++)for(Segment seg:moveSegments){
                    String name=dex.matchMove(seg.text);if(name!=null&&!out.moves.contains(name))name=null;
                    if((pass==0)!=(name!=null)||out.positions.size()>=4)continue;
                    String norm=NameMatcher.normalize(seg.text);
                    if(name==null&&(norm.length()<3||norm.matches("(bag|pokemon|fight|run|pp|type|power)[0-9]*")))continue;
                    if(!positioned.add(name==null?norm:name))continue;
                    Rect b=seg.box,s=seg.tile.source,t=seg.tile.target;
                    out.positions.add(MovePosition.map(name,b.left,b.top,b.right,b.bottom,s.left,s.top,s.width(),s.height(),t.left,t.top,t.width(),t.height(),frameW,frameH));
                }
                out.raw = "Ellenfél: " + String.join(" | ", lines.get(0))
                        + "\nSaját: " + String.join(" | ", lines.get(1))
                        + "\nTámadások: " + String.join(" | ", lines.get(2));
                callback.done(out);
            } catch (Exception e) { callback.error(e); }
        }).addOnFailureListener(callback::error).addOnCompleteListener(task -> sheet.recycle());
    }

    private String findMon(List<String> lines) {
        for (String line : lines) { String match = dex.matchPokemon(line); if (match != null) return match; }
        return null;
    }
    @Override public void close() { recognizer.close(); }
}
