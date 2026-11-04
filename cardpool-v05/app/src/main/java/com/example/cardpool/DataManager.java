package com.example.cardpool;
import android.content.*;
import android.os.*;
import java.util.*;
/** One process-wide authority. Mutations always use the latest state, never a UI snapshot. */
public final class DataManager {
    public interface Listener{void changed();}
    public interface Mutation{void apply(GameState s);}
    private static DataManager instance;
    public static synchronized DataManager get(Context c){if(instance==null)instance=new DataManager(c.getApplicationContext());return instance;}
    private final SharedPreferences prefs;
    private final Set<Listener> listeners=new HashSet<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private GameState manual,simulation;
    private boolean simulating=false;private long tick=0;
    private final Runnable pulse=new Runnable(){public void run(){if(!simulating)return;mutate(s->new DataSource.Simulated().apply(s,++tick));handler.postDelayed(this,2000);}};
    private DataManager(Context c){
        prefs=c.getSharedPreferences("card_pool_form",Context.MODE_PRIVATE);
        String text=prefs.getString("state_v05_manual",null);
        manual=text==null?GameState.migrate(prefs.getAll()):GameState.decode(text);
        if(!prefs.getString("v06_locks_initialized", "").equals("yes")){
            for(int i=0;i<8;i++){GameState.Player p=manual.players[i];if(p.level>0)manual.capture.players[i].manualLocks.add("level");if(p.gold>0)manual.capture.players[i].manualLocks.add("gold");if(p.hp>0)manual.capture.players[i].manualLocks.add("hp");if(!p.units.isEmpty())manual.capture.players[i].manualLocks.add("units");}
            if(!prefs.edit().putString("state_v05_manual",GameState.encode(manual)).putString("v06_locks_initialized","yes").commit())throw new IllegalStateException("旧记录保护初始化失败");
        }
        if(text==null&&!prefs.edit().putString("state_v05_manual",GameState.encode(manual)).commit())throw new IllegalStateException("迁移保存失败，旧数据未修改");
    }
    public synchronized GameState snapshot(){return (simulating?simulation:manual).copy();}
    public synchronized boolean isSimulating(){return simulating;}
    public synchronized void add(Listener l){listeners.add(l);}
    public synchronized void remove(Listener l){listeners.remove(l);}
    private void notifyChanged(){for(Listener l:new ArrayList<>(listeners))l.changed();}
    public synchronized void mutate(Mutation mutation){
        GameState next=(simulating?simulation:manual).copy();mutation.apply(next);next.validate();markManualLocks(simulating?simulation:manual,next);next.manualRevision++;next.revision++;next.updatedAt=System.currentTimeMillis();
        if(simulating)simulation=next;
        else {new DataSource.Manual().apply(next,next.revision);if(!prefs.edit().putString("state_v05_manual",GameState.encode(next)).commit())throw new IllegalStateException("保存失败，请重试");manual=next;}
        notifyChanged();
    }
    private static String unitsKey(GameState.Player p){StringBuilder b=new StringBuilder();for(GameState.Holding h:p.units)b.append(h.hero).append(':').append(h.star).append(':').append(h.count).append(':').append(h.equipment).append(';');return b.toString();}
    private void markManualLocks(GameState before,GameState after){
        for(int i=0;i<8;i++){
            GameState.Player a=before.players[i],b=after.players[i];Set<String> locks=after.capture.players[i].manualLocks;
            if(!unitsKey(a).equals(unitsKey(b)))locks.add("units");
            if(a.level!=b.level)locks.add("level");if(a.gold!=b.gold)locks.add("gold");if(a.hp!=b.hp)locks.add("hp");
        }
    }
    public synchronized void captureStatus(String status){
        GameState next=manual.copy();next.capture.status=status;
        if(!prefs.edit().putString("state_v05_manual",GameState.encode(next)).commit())return;
        manual=next;if(!simulating)notifyChanged();
    }
    public synchronized void observe(ObservationGate.Frame frame,Map<String,ObservationGate.Token> accepted){
        if(simulating)return;
        GameState next=manual.copy();CaptureData data=next.capture;
        if(frame.player<0||frame.player>7||frame.identityConfidence<ObservationGate.IDENTITY_MIN){data.lastReview="身份不明确：未关联任何玩家，未覆盖记录";}
        else {
            CaptureData.Player p=data.players[frame.player];if(frame.time<=p.latest)return;p.latest=frame.time;
            int good=0;
            for(Map.Entry<String,ObservationGate.Token> e:accepted.entrySet()){
                String key=e.getKey();ObservationGate.Token t=e.getValue();
                if(t.confidence<ObservationGate.MIN)continue;
                if(key.equals("level")||key.equals("gold")||key.equals("hp")){
                    Integer n=ObservationGate.scalar(key,t.text);if(n==null)continue;
                    if(data.autoMerge&&!p.manualLocks.contains(key)){
                        GameState.Player player=next.players[frame.player];if(key.equals("level"))player.level=n;else if(key.equals("gold"))player.gold=n;else player.hp=n;
                    }
                }
                p.values.put(key,new CaptureData.Value(t.text,t.confidence,frame.time));good++;
            }
            // Only a complete five-slot shop, accepted in the same high-confidence frame,
            // contributes one observed shop state. Partial/uncertain OCR never logs a change.
            boolean shopChanged=ShopTimeline.record(next,frame,accepted);
            java.util.List<GameState.Holding> visible=new java.util.ArrayList<>();
            for(Map.Entry<String,ObservationGate.Token> e:accepted.entrySet())if(e.getKey().matches("board\\.[0-9]+\\.hero")&&e.getValue().confidence>=ObservationGate.MIN&&next.heroes.containsKey(e.getValue().text)){
                String prefix=e.getKey().substring(0,e.getKey().length()-4);ObservationGate.Token star=accepted.get(prefix+"star");
                if(star!=null&&star.confidence>=ObservationGate.MIN&&star.text.matches("[123]")){ObservationGate.Token equipment=accepted.get(prefix+"equipment");visible.add(new GameState.Holding(e.getValue().text,Integer.parseInt(star.text),1,equipment==null?"":equipment.text));}
            }
            if(!visible.isEmpty()){p.board.clear();p.board.addAll(visible);p.boardSeen=frame.time;}
            data.lastReview="玩家"+(frame.player+1)+" · 本帧字段 "+frame.fields.size()+" · 双帧高置信确认 "+good+(shopChanged?"；记录一组新五格商店":"")+"；低分/未稳定项未覆盖";
            // Raw uncertain candidates are review-only, never effective player statistics.
            for(Map.Entry<String,ObservationGate.Token> e:frame.fields.entrySet())if(!accepted.containsKey(e.getKey()))data.lastReview+="\n待确认 "+e.getKey()+"="+e.getValue().text+String.format(java.util.Locale.ROOT," (%.1f%%)",e.getValue().confidence*100);
        }
        next.source="授权截图观察 + 手动记录（概率为估算）";next.revision++;next.updatedAt=System.currentTimeMillis();next.validate();
        if(!prefs.edit().putString("state_v05_manual",GameState.encode(next)).commit())return;manual=next;notifyChanged();
    }
    public synchronized void simulation(boolean enable){
        handler.removeCallbacks(pulse);simulating=enable;
        if(enable){simulation=DataSource.Simulated.initial();tick=0;handler.postDelayed(pulse,2000);}
        notifyChanged();
    }
    public void adjust(int player,String hero,int delta){mutate(s->{
        if(player<0||player>=8||!s.heroes.containsKey(hero))throw new IllegalArgumentException("请先选择英雄");
        GameState.Player p=s.players[player];GameState.Holding found=null;
        for(GameState.Holding h:p.units)if(h.hero.equals(hero)&&h.star==1&&h.equipment.isEmpty()){found=h;break;}
        if(found==null){if(delta<0)return;found=new GameState.Holding(hero,1,0,"");p.units.add(found);}
        found.count=Math.max(0,Math.min(1000,found.count+delta));if(found.count==0)p.units.remove(found);
    });}
}
