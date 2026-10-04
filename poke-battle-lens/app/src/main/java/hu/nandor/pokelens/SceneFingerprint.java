package hu.nandor.pokelens;
import java.nio.ByteBuffer;

/** Samples only name and move rectangles, ignoring unrelated battle animations. */
public final class SceneFingerprint {
    private SceneFingerprint() {}
    public static long rgba(ByteBuffer pixels, int rowStride, int pixelStride, int[][] regions) {
        long hash = 0xcbf29ce484222325L;
        for (int[] r : regions) {
            for (int y = r[1]; y < r[3]; y += 4) {
                for (int x = r[0]; x < r[2]; x += 4) {
                    int i = y * rowStride + x * pixelStride;
                    // Small colour differences from rendering/compression do not cause extra OCR.
                    int colour = ((pixels.get(i) & 0xf8) << 16)
                            | ((pixels.get(i + 1) & 0xf8) << 8) | (pixels.get(i + 2) & 0xf8);
                    hash = (hash ^ colour) * 0x100000001b3L;
                }
            }
        }
        return hash;
    }
}
