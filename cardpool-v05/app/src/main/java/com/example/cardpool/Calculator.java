package com.example.cardpool;

/**
 * Demonstration model, not verified live game mechanics. Assumes each slot is
 * independently cost-tier rolled, then one remaining copy is sampled uniformly.
 */
public final class Calculator {
    private Calculator() { }

    public static final class Result {
        public final int remaining;
        public final double slotProbability;
        public final double fiveSlotProbability;

        private Result(int remaining, double slotProbability, double anyProbability) {
            this.remaining = remaining;
            this.slotProbability = slotProbability;
            this.fiveSlotProbability = anyProbability;
        }
    }

    public static Result calculate(int initialCopies,
                                   int selfHeld,
                                   int otherObserved,
                                   int tierTotalRemaining,
                                   double tierOddsPercent,
                                   int shopSlots) {
        if (initialCopies < 0 || selfHeld < 0 || otherObserved < 0) {
            throw new IllegalArgumentException("初始数量和持有数量不能是负数。");
        }
        if ((long)selfHeld + otherObserved > initialCopies) {
            throw new IllegalArgumentException("已知持有量不能超过目标英雄初始总量。");
        }
        if (tierTotalRemaining <= 0) {
            throw new IllegalArgumentException("同费用卡池剩余总量必须大于 0。");
        }
        if (!Double.isFinite(tierOddsPercent) || tierOddsPercent < 0 || tierOddsPercent > 100) {
            throw new IllegalArgumentException("该费用出卡概率必须在 0%～100% 之间。");
        }
        if (shopSlots < 1 || shopSlots > 10) {
            throw new IllegalArgumentException("商店栏位数必须为 1～10。");
        }
        int remaining = initialCopies - selfHeld - otherObserved;
        if (remaining > tierTotalRemaining) {
            throw new IllegalArgumentException("目标剩余张数不能超过同费用卡池剩余总量。");
        }
        double oneSlot = (tierOddsPercent / 100.0) * remaining / tierTotalRemaining;
        double anySlots = 1.0 - Math.pow(1.0 - oneSlot, shopSlots);
        return new Result(remaining, oneSlot, anySlots);
    }
}
