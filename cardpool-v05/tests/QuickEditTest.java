import android.content.SharedPreferences;
import com.example.cardpool.CardPoolRepository;

import java.util.HashMap;
import java.util.Map;

public class QuickEditTest {
    static class FakePreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();
        @Override public String getString(String key, String fallback) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : fallback;
        }
        @Override public long getLong(String key, long fallback) {
            Object value = values.get(key);
            return value instanceof Long ? (Long) value : fallback;
        }
        @Override public Editor edit() { return new Editor() {
            private final Map<String,Object> pending = new HashMap<>();
            @Override public Editor putString(String k, String v) { pending.put(k, v); return this; }
            @Override public Editor putLong(String k, long v) { pending.put(k, v); return this; }
            @Override public void apply() { values.putAll(pending); }
        }; }
    }
    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
    private static FakePreferences setup() {
        FakePreferences p = new FakePreferences();
        SharedPreferences.Editor e = p.edit();
        e.putString("champion", "测试棋子").putString("initial", "10")
                .putString("unknown", "0").putString("tierTotal", "120")
                .putString("odds", "25").putString("slots", "5");
        int[] start = {3, 2, 1, 1, 0, 0, 0, 0};
        for (int i = 0; i < 8; i++) e.putString("p" + i + "_copies", String.valueOf(start[i]));
        e.putString("p1_lineup", "阵容备注必须保留");
        e.apply();
        return p;
    }

    public static void main(String[] args) {
        FakePreferences p = setup();
        check(CardPoolRepository.summary(p, -1, -1).contains("预计卡池剩余：3 张"), "initial count");
        check(CardPoolRepository.adjustPlayer(p, 1, 1), "increase opponent 1");
        check("3".equals(p.getString("p1_copies", "?")), "correct storage key");
        check(p.getString("result", "").contains("预计卡池剩余：2 张"), "result recalculated");
        check(p.getString("players_summary", "").contains("阵容备注必须保留"), "notes maintained");
        check(p.getLong("revision", -1) == 1, "result revision");
        check(p.getLong("overlay_revision", -1) == 1, "MainActivity reverse sync revision");
        check(CardPoolRepository.adjustPlayer(p, 1, -1), "decrease opponent 1");
        check(p.getString("result", "").contains("预计卡池剩余：3 张"), "revert counts");
        check(p.getLong("overlay_revision", -1) == 2, "revision increases again");
        check(!CardPoolRepository.adjustPlayer(p, -1, 1), "invalid player rejected");
        check(!CardPoolRepository.adjustPlayer(p, 8, 1), "invalid player rejected 2");
        check(CardPoolRepository.changeCount(0, -1) == 0, "lower bound");
        check(CardPoolRepository.changeCount(9999, 1) == 9999, "upper bound");
        check(CardPoolRepository.changeCount(Integer.MAX_VALUE, 1) == 9999, "no overflow");
        check(CardPoolRepository.parseCount("invalid") == 0, "bad input handling");
        check(CardPoolRepository.adjustPlayer(p, 0, 99), "invalid probability state updated");
        check(p.getString("result", "").startsWith("请检查输入"), "old probability cleared on invalid data");
        System.out.println("PASSED: overlay quick editing, reverse-sync revision, boundary validation and current results");
    }
}
