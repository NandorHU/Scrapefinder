package hu.nandor.pokelens;
final class RevealWindow {
    static final long DURATION_MS=5000;
    private long until;
    void reveal(long now){until=now+DURATION_MS;}
    boolean showing(long now){return now<until;}
    void reset(){until=0;}
}
