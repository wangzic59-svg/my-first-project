package com.example.cardpool;

/**
 * Aggregates observations about ONE selected unit across the eight player slots.
 * This is a manual observation model: it does not introspect a game process.
 */
public final class CardPoolTracker {
    public static final int PLAYER_COUNT = 8;

    private CardPoolTracker() { }

    public static final class Snapshot {
        public final int ownHeld;
        public final int opponentsObserved;
        public final int unattributedObserved;
        public final int totalObserved;
        public final Calculator.Result probability;

        private Snapshot(int ownHeld, int opponentsObserved, int unattributedObserved,
                         int totalObserved, Calculator.Result probability) {
            this.ownHeld = ownHeld;
            this.opponentsObserved = opponentsObserved;
            this.unattributedObserved = unattributedObserved;
            this.totalObserved = totalObserved;
            this.probability = probability;
        }
    }

    public static Snapshot calculate(int initialCopies, int[] heldByPlayer,
                                     int unattributedObserved, int tierTotalRemaining,
                                     double tierOddsPercent, int shopSlots) {
        if (heldByPlayer == null || heldByPlayer.length != PLAYER_COUNT) {
            throw new IllegalArgumentException("需要恰好 8 位玩家的数据。");
        }
        if (unattributedObserved < 0) {
            throw new IllegalArgumentException("未归属记录不能为负数。");
        }
        long opponents = 0;
        long total = unattributedObserved;
        for (int i = 0; i < PLAYER_COUNT; i++) {
            if (heldByPlayer[i] < 0) {
                throw new IllegalArgumentException("玩家的持有量不能为负数。");
            }
            total += heldByPlayer[i];
            if (i > 0) opponents += heldByPlayer[i];
        }
        if (total > Integer.MAX_VALUE || opponents + unattributedObserved > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("记录数量超出范围。");
        }
        Calculator.Result probability = Calculator.calculate(
                initialCopies, heldByPlayer[0], (int)(opponents + unattributedObserved),
                tierTotalRemaining, tierOddsPercent, shopSlots);
        return new Snapshot(heldByPlayer[0], (int) opponents, unattributedObserved,
                (int) total, probability);
    }
}
