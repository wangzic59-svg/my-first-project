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
        if(text==null&&!prefs.edit().putString("state_v05_manual",GameState.encode(manual)).commit())throw new IllegalStateException("迁移保存失败，旧数据未修改");
    }
    public synchronized GameState snapshot(){return (simulating?simulation:manual).copy();}
    public synchronized boolean isSimulating(){return simulating;}
    public synchronized void add(Listener l){listeners.add(l);}
    public synchronized void remove(Listener l){listeners.remove(l);}
    private void notifyChanged(){for(Listener l:new ArrayList<>(listeners))l.changed();}
    public synchronized void mutate(Mutation mutation){
        GameState next=(simulating?simulation:manual).copy();mutation.apply(next);next.validate();next.revision++;next.updatedAt=System.currentTimeMillis();
        if(simulating)simulation=next;
        else {new DataSource.Manual().apply(next,next.revision);if(!prefs.edit().putString("state_v05_manual",GameState.encode(next)).commit())throw new IllegalStateException("保存失败，请重试");manual=next;}
        notifyChanged();
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
