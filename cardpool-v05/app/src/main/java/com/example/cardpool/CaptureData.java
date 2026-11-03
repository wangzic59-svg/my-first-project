package com.example.cardpool;
import java.io.Serializable;
import java.util.*;
/** Persisted observations are separate from manual holdings and probability estimates. */
public final class CaptureData implements Serializable {
 private static final long serialVersionUID=6L;
 public final Player[] players=new Player[8];
 public final Set<String> targets=new LinkedHashSet<>();
 public boolean autoMerge=false, autoBoard=false;
 public String status="未采集",lastReview="";
 public CaptureData(){for(int i=0;i<8;i++)players[i]=new Player();}
 public static final class Value implements Serializable {
  private static final long serialVersionUID=6L;
  public String text;public double confidence;public long seen;
  public Value(String t,double c,long when){text=t;confidence=c;seen=when;}
  public String describe(long now){return text+String.format(java.util.Locale.ROOT," · %.1f%% · ",confidence*100)+(now-seen>30000?"历史观察":"已观察")+" · "+Math.max(0,(now-seen)/1000)+"秒前";}
 }
 public static final class Player implements Serializable {
  private static final long serialVersionUID=6L;
  public final Map<String,Value> values=new LinkedHashMap<>();
  public final Set<String> manualLocks=new HashSet<>();
  public final List<GameState.Holding> board=new ArrayList<>();
  public long boardSeen=0;
  public long latest=0;
 }
 public String text(long now){StringBuilder b=new StringBuilder(status+"\n"+lastReview+"\n");for(int i=0;i<8;i++){
  Player p=players[i];b.append("玩家").append(i+1).append(" · 手动锁定 ").append(p.manualLocks).append("\n");
  for(Map.Entry<String,Value> e:p.values.entrySet())b.append(e.getKey()).append(": ").append(e.getValue().describe(now)).append("\n");
 }return b.toString();}
}
