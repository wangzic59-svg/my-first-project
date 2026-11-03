import com.example.cardpool.*;
import android.content.*;
import java.util.*;
public final class V05Test {
 static int checks=0;static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static void invalid(Runnable r){boolean failed=false;try{r.run();}catch(IllegalArgumentException e){failed=true;}ok(failed,"invalid input rejected");}
 static class Prefs implements SharedPreferences {
   Map<String,Object> data=new HashMap<>();boolean fail=false;
   public String getString(String k,String f){return (String)data.getOrDefault(k,f);}public long getLong(String k,long f){return (Long)data.getOrDefault(k,f);}public Map<String,?> getAll(){return new HashMap<>(data);}
   public Editor edit(){return new Editor(){Map<String,Object> pending=new HashMap<>();public Editor putString(String k,String v){pending.put(k,v);return this;}public Editor putLong(String k,long v){pending.put(k,v);return this;}public void apply(){if(!fail)data.putAll(pending);}public boolean commit(){if(fail)return false;apply();return true;}};}
 }
 public static void main(String[] args){
   Map<String,Object> old=new HashMap<>();old.put("champion","旧英雄");old.put("initial","10");old.put("self","3");old.put("other","2");old.put("p1_lineup","旧阵容");old.put("p1_equipment","旧装备");old.put("p0_level","7");
   GameState migrated=GameState.migrate(old);ok(migrated.observed("legacy")==5,"V0.2 migration no double count");ok(migrated.source.contains("待核实"),"legacy provenance never guessed");ok(migrated.players[1].equipment.equals("旧装备"),"preserve notes");ok(migrated.heroes.get("legacy").cost==0&&!migrated.rosterComplete,"do not invent old cost or full season");
   old.put("p0_copies","4");old.put("p1_copies","1");old.put("unknown","0");migrated=GameState.migrate(old);ok(migrated.observed("legacy")==5,"V0.4 migration");
   old.put("p0_copies","9999");ok(GameState.migrate(old).players[0].units.stream().mapToInt(GameState.Holding::copies).sum()==9999,"large legacy count retained");old.put("p0_copies","4");
   GameState s=DataSource.Simulated.initial();ok(s.observed("sample_a")==3,"2-star counts three copies");s.players[7].units.add(new GameState.Holding("sample_a",3,1,"装备"));ok(s.observed("sample_a")==12,"all eight players + three-star = nine copies");
   GameState copy=s.copy();copy.players[7].units.clear();ok(s.observed("sample_a")==12,"defensive deep snapshot");ok(copy.players[0].units.get(0).equipment.equals("模拟装备"),"serialization keeps equipment");
   invalid(()->GameState.decode("broken"));invalid(()->{GameState x=new GameState();x.players[0].gold=-1;x.validate();});
   invalid(()->{GameState x=new GameState();x.odds[1]=new double[]{Double.NaN,0,0,0,0};x.validate();});
   invalid(()->{GameState x=new GameState();x.players[0].units.add(new GameState.Holding("missing",1,1,""));x.validate();});
   ok(Prediction.explain(new GameState()).contains("完整"),"missing roster disables prediction");ok(Prediction.explain(s).contains("预测不可用"),"overdraw disables prediction, no stale result");
   GameState demo=DataSource.Simulated.initial();String prediction=Prediction.explain(demo);ok(prediction.contains("非真实商店结果")&&prediction.contains("单格"),"prediction clearly labeled");
   demo.odds[7]=new double[5];ok(Prediction.explain(demo).contains("未配置"),"missing level rules not guessed");
   double[] p=NativeEngine.predict(1,10,1,5,false);ok(Math.abs(p[1]-.5)<1e-12,"real JNI roundtrip");invalid(()->NativeEngine.remaining(10,11));invalid(()->NativeEngine.predict(1,10,Double.NaN,5,false));
   Prefs prefs=new Prefs();prefs.data.putAll(old);Context ctx=new Context(){public SharedPreferences getSharedPreferences(String n,int mode){return prefs;}};
   DataManager m=DataManager.get(ctx);ok(prefs.data.get("p0_copies").equals("4"),"legacy preferences untouched");ok(prefs.data.containsKey("state_v05_manual"),"versioned migration persisted");
   final int[] a={0},b={0};m.add(()->a[0]++);m.add(()->b[0]++);
   m.adjust(0,"legacy",1);ok(m.snapshot().observed("legacy")==6,"latest manual transaction");ok(a[0]==1&&b[0]==1,"both surfaces notified");
   m.adjust(1,"legacy",1);ok(m.snapshot().revision==2&&m.snapshot().observed("legacy")==7,"reverse update retains earlier change");
   String saved=GameState.encode(m.snapshot());invalid(()->m.mutate(x->x.players[0].level=20));ok(GameState.encode(m.snapshot()).equals(saved),"failed transaction rolls back all state");
   prefs.fail=true;try{m.adjust(0,"legacy",1);throw new AssertionError("failure expected");}catch(IllegalStateException expected){}prefs.fail=false;ok(GameState.encode(m.snapshot()).equals(saved),"storage failure doesn't publish unpersisted state");
   m.simulation(true);ok(m.isSimulating()&&m.snapshot().source.contains("模拟"),"simulation provenance");m.mutate(x->new DataSource.Simulated().apply(x,10));m.simulation(false);ok(GameState.encode(m.snapshot()).equals(saved),"simulation never overwrites manual state");
   ok(GameState.decode(prefs.getString("state_v05_manual","")).observed("legacy")==7,"reload persistence");
   GameState snap=m.snapshot();snap.players[0].gold=999;ok(m.snapshot().players[0].gold!=999,"UI cannot mutate manager snapshot");
   System.out.println("PASS Java/JNI: "+checks+" checks covering migration, stars, sources, persistence, atomic updates, synchronization, invalid-state predictions");
 }
}
