package com.example.cardpool;
/** JNI has no access to the game, ROOT, sockets, or random results. */
public final class NativeEngine {
    static { System.loadLibrary("cardpool"); }
    private NativeEngine(){}
    public static native int remaining(int total,int observed);
    /** Returns [per slot, >=1 in shop, expected copies]; invalid inputs throw. */
    public static native double[] predict(int targetRemaining,int tierRemaining,double tierOdds,int slots,boolean independent);
}
