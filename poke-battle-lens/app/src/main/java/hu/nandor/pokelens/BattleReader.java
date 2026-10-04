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
        public String raw = "";
    }

    /** One OCR request reads all three regions together; it owns its input bitmap. */
    public void scan(Bitmap frame, Profile profile, Callback callback) {
        String[] names = {"enemy", "own", "moves"};
        Rect[] sources = new Rect[3];
        Rect[] targets = new Rect[3];
        int sheetWidth = 1, y = 24;
        for (int i = 0; i < 3; i++) {
            sources[i] = profile.crop(names[i], frame.getWidth(), frame.getHeight());
            float scale = Math.min(3f, Math.min(1600f / sources[i].width(), 420f / sources[i].height()));
            int w = Math.max(1, Math.round(sources[i].width() * scale));
            int h = Math.max(1, Math.round(sources[i].height() * scale));
            targets[i] = new Rect(24, y, 24 + w, y + h);
            sheetWidth = Math.max(sheetWidth, w + 48);
            y += h + 48;
        }
        final Bitmap sheet = Bitmap.createBitmap(sheetWidth, y, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(); // nearest-neighbour scaling keeps pixel fonts crisp
        for (int i = 0; i < 3; i++) canvas.drawBitmap(frame, sources[i], targets[i], paint);

        recognizer.process(InputImage.fromBitmap(sheet, 0)).addOnSuccessListener(text -> {
            try {
                List<List<String>> lines = Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
                for (Text.TextBlock block : text.getTextBlocks()) {
                    for (Text.Line line : block.getLines()) {
                        // Work at element level: OCR can join two columns, or even different regions.
                        for (int region = 0; region < 3; region++) {
                            List<Text.Element> elements = new ArrayList<>();
                            for (Text.Element e : line.getElements()) {
                                Rect box = e.getBoundingBox();
                                if (box != null && targets[region].contains(box.centerX(), box.centerY())) elements.add(e);
                            }
                            if (elements.isEmpty()) continue;
                            elements.sort(Comparator.comparingInt(e -> e.getBoundingBox().left));
                            List<String> segment = new ArrayList<>();
                            int right = -1;
                            for (Text.Element e : elements) {
                                Rect box = e.getBoundingBox();
                                if (region == 2 && right >= 0 && box.left - right > Math.max(20, box.height() * 2)) {
                                    lines.get(region).add(String.join(" ", segment));
                                    segment.clear();
                                }
                                segment.add(e.getText());
                                right = box.right;
                            }
                            if (!segment.isEmpty()) lines.get(region).add(String.join(" ", segment));
                        }
                    }
                }
                Result out = new Result();
                out.enemy = dex.matchPokemon(profile.manualEnemy);
                if (out.enemy == null) out.enemy = findMon(lines.get(0));
                out.own = dex.matchPokemon(profile.manualOwn);
                if (out.own == null) out.own = findMon(lines.get(1));
                Collection<String> candidates = profile.manualMoves.trim().isEmpty()
                        ? lines.get(2) : Arrays.asList(profile.manualMoves.split("[,;\\n]"));
                LinkedHashSet<String> detected = new LinkedHashSet<>();
                for (String line : candidates) {
                    String match = dex.matchMove(line);
                    if (match != null) detected.add(match);
                }
                out.moves = new ArrayList<>(detected);
                if (out.moves.size() > 4) out.moves = new ArrayList<>(out.moves.subList(0, 4));
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
