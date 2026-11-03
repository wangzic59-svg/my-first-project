import com.example.cardpool.CardPoolTracker;
import com.example.cardpool.Calculator;

public class TrackerTest {
    private static void expectFailure(Runnable f, String label) {
        try { f.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected failure: " + label);
    }

    private static void eq(int actual, int expected, String label) {
        if (actual != expected) throw new AssertionError(label + ": " + actual + " != " + expected);
    }

    public static void main(String[] args) {
        int[] players = {3, 2, 1, 1, 0, 0, 0, 0};
        CardPoolTracker.Snapshot state = CardPoolTracker.calculate(10, players, 0, 120, 25, 5);
        eq(state.ownHeld, 3, "own");
        eq(state.opponentsObserved, 4, "opponents");
        eq(state.totalObserved, 7, "total");
        eq(state.probability.remaining, 3, "remaining");
        if (Math.abs(state.probability.slotProbability - 0.00625) > 1e-12)
            throw new AssertionError("Single slot probability");

        // Old v0.2 'other=4' migrates to unattributed, not individual opponent slots.
        int[] oldPlayers = {3, 0, 0, 0, 0, 0, 0, 0};
        CardPoolTracker.Snapshot imported = CardPoolTracker.calculate(10, oldPlayers, 4, 120, 25, 5);
        eq(imported.totalObserved, 7, "legacy total");
        eq(imported.probability.remaining, 3, "legacy remaining");

        expectFailure(() -> CardPoolTracker.calculate(10, players, 4, 120, 25, 5), "double counting");
        expectFailure(() -> CardPoolTracker.calculate(10, new int[]{3, 2}, 0, 120, 25, 5), "8 rows");
        expectFailure(() -> CardPoolTracker.calculate(10, new int[]{3, -1, 1, 0, 0, 0, 0, 0}, 0, 120, 25, 5), "negative");
        expectFailure(() -> CardPoolTracker.calculate(10, players, -1, 120, 25, 5), "unknown negative");
        int[] zeros = new int[8];
        CardPoolTracker.Snapshot zero = CardPoolTracker.calculate(10, zeros, 0, 120, 0, 5);
        eq(zero.probability.remaining, 10, "zero observed");
        if (zero.probability.fiveSlotProbability != 0) throw new AssertionError("Zero odds");
        System.out.println("PASSED: eight-player model, legacy import invariant and input validation");
    }
}
