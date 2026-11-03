package android.os;
// Deterministic host scheduler: periodic ticks are tested directly via DataSource.
public class Handler { public Handler(Looper l){} public boolean postDelayed(Runnable r,long n){return true;}public void removeCallbacks(Runnable r){} }
