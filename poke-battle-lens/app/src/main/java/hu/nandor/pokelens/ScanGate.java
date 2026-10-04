package hu.nandor.pokelens;

/** Tracks the latest visible scene; a completed OCR request cannot revive an older scene. */
public final class ScanGate {
    public static final long MIN_SCAN_INTERVAL_MS = 300;
    public static final long FALLBACK_REFRESH_MS = 2000;
    private long revision, fingerprint, completedRevision = -1, lastStart = -FALLBACK_REFRESH_MS;
    private boolean hasFrame, busy;

    public boolean observe(long nextFingerprint) {
        if (hasFrame && fingerprint == nextFingerprint) return false;
        fingerprint = nextFingerprint;
        hasFrame = true;
        revision++;
        return true;
    }

    public long begin(long now) {
        if (!canBegin(now)) return -1;
        busy = true;
        lastStart = now;
        return revision;
    }

    public boolean canBegin(long now){return hasFrame&&!busy&&now-lastStart>=MIN_SCAN_INTERVAL_MS&&(completedRevision!=revision||now-lastStart>=FALLBACK_REFRESH_MS);}
    /** A changed exclusion mask must not be mistaken for a changed game scene. */
    public void rebase(long cleanFingerprint){if(hasFrame)fingerprint=cleanFingerprint;}

    public boolean finish(long requestRevision) {
        busy = false;
        if (!hasFrame || requestRevision != revision) return false;
        completedRevision = revision;
        return true;
    }

    public void refresh() { completedRevision = -1; revision++; }
    public void failed() { busy = false; }
    public boolean isBusy() { return busy; }
    public void invalidate() { hasFrame = false; completedRevision = -1; revision++; }
}
