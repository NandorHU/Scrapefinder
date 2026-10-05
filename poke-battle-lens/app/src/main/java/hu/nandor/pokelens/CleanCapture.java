package hu.nandor.pokelens;
/** Wait for a newly composed image after the labels have been hidden. */
final class CleanCapture {
    private long afterTimestamp,readyAt;private boolean waiting;
    void begin(long now,long previousTimestamp){waiting=true;readyAt=now+100;afterTimestamp=previousTimestamp;}
    boolean ready(long now,long timestamp){return waiting&&now>=readyAt&&timestamp>afterTimestamp;}
    boolean timedOut(long now){return waiting&&now>readyAt+1500;}
    boolean waiting(){return waiting;}
    void cancel(){waiting=false;}
}
