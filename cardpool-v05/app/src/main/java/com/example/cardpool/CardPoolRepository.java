package com.example.cardpool;

import android.content.SharedPreferences;

import java.util.Locale;

/**
 * Shared preference-backed calculation used by overlay quick edit. All values come
 * from manual input; this class neither reads a game process nor a screen.
 */
public final class CardPoolRepository {
    private static final int MAX_COPIES = 9999;
    public static final String KEY_REVISION = "revision";
    public static final String KEY_OVERLAY_REVISION = "overlay_revision";
    private static final String[] LABELS = {
            "我方（玩家1）", "对手1（玩家2）", "对手2（玩家3）", "对手3（玩家4）",
            "对手4（玩家5）", "对手5（玩家6）", "对手6（玩家7）", "对手7（玩家8）"
    };

    private CardPoolRepository() { }

    public static int parseCount(String value) {
        try { return Math.max(0, Math.min(MAX_COPIES, Integer.parseInt(value.trim()))); }
        catch (Exception ignored) { return 0; }
    }

    /** Numeric saturation prevents accidental wraparound or negative counts. */
    public static int changeCount(int count, int delta) {
        long sum = (long) count + delta;
        return (int) Math.max(0L, Math.min(MAX_COPIES, sum));
    }

    private static String get(SharedPreferences p, String key) {
        return p.getString(key, "").trim();
    }

    public static String summary(SharedPreferences p, int changingPlayer, int newCount) {
        try {
            int[] held = new int[CardPoolTracker.PLAYER_COUNT];
            for (int i = 0; i < held.length; i++) {
                held[i] = i == changingPlayer ? newCount : Integer.parseInt(get(p, "p" + i + "_copies"));
            }
            CardPoolTracker.Snapshot s = CardPoolTracker.calculate(
                    Integer.parseInt(get(p, "initial")), held,
                    Integer.parseInt(get(p, "unknown")),
                    Integer.parseInt(get(p, "tierTotal")),
                    Double.parseDouble(get(p, "odds")),
                    Integer.parseInt(get(p, "slots")));
            String name = get(p, "champion").isEmpty() ? "目标棋子" : get(p, "champion");
            return String.format(Locale.CHINA,
                    "%s\n我方持有：%d 张 · 对手观察：%d 张\n未归属：%d 张 · 已知合计：%d 张\n预计卡池剩余：%d 张\n单格概率：%.3f%%\n刷新出现 ≥1 张：%.2f%%",
                    name, s.ownHeld, s.opponentsObserved, s.unattributedObserved, s.totalObserved,
                    s.probability.remaining, s.probability.slotProbability * 100,
                    s.probability.fiveSlotProbability * 100);
        } catch (NumberFormatException e) {
            return "输入尚未完成：请填写所有数量、卡池和概率参数。";
        } catch (IllegalArgumentException e) {
            return "请检查输入：" + e.getMessage();
        }
    }

    public static String playersSummary(SharedPreferences p, int changingPlayer, int newCount) {
        StringBuilder b = new StringBuilder("八位玩家 · 人工记录\n");
        for (int i = 0; i < CardPoolTracker.PLAYER_COUNT; i++) {
            String copies = i == changingPlayer ? String.valueOf(newCount) : get(p, "p" + i + "_copies");
            b.append(LABELS[i]).append("  ").append(copies.isEmpty() ? "?" : copies).append(" 张");
            String level = get(p, "p" + i + "_level");
            String hp = get(p, "p" + i + "_hp");
            String lineup = get(p, "p" + i + "_lineup");
            String equipment = get(p, "p" + i + "_equipment");
            if (!level.isEmpty()) b.append("  Lv").append(level);
            if (!hp.isEmpty()) b.append("  HP").append(hp);
            b.append("\n");
            if (!lineup.isEmpty()) b.append("阵容：").append(lineup).append("\n");
            if (!equipment.isEmpty()) b.append("装备：").append(equipment).append("\n");
        }
        return b.toString().trim();
    }

    /**
     * Overlay writes the changed count and its derived snapshots in one apply(),
     * then changes a dedicated key so MainActivity can refresh its form.
     */
    public static boolean adjustPlayer(SharedPreferences p, int index, int delta) {
        if (index < 0 || index >= CardPoolTracker.PLAYER_COUNT) return false;
        String key = "p" + index + "_copies";
        int old = parseCount(p.getString(key, "0"));
        int next = changeCount(old, delta);
        if (old == next && String.valueOf(next).equals(p.getString(key, "0"))) return false;

        p.edit().putString(key, String.valueOf(next))
                .putString("result", summary(p, index, next))
                .putString("players_summary", playersSummary(p, index, next))
                .putLong(KEY_REVISION, p.getLong(KEY_REVISION, 0L) + 1L)
                .putLong(KEY_OVERLAY_REVISION, p.getLong(KEY_OVERLAY_REVISION, 0L) + 1L)
                .apply();
        return true;
    }
}
