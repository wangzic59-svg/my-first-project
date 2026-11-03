package com.example.cardpool;

import java.io.*;
import java.util.*;

/** Versioned, UI-independent state. Counts are observed copies, never server data. */
public final class GameState implements Serializable {
    private static final long serialVersionUID = 5L;
    public int schema = 5;
    public long revision = 0, updatedAt = 0;
    public String source = "手动输入", season = "自定义赛季（请配置规则）", target = "";
    public boolean rosterComplete = false, independentSlots = false, legacyImported = false;
    public int slots = 5, refreshCost = 2, selectedPlayer = 0;
    public final Player[] players = new Player[8];
    public final LinkedHashMap<String, Hero> heroes = new LinkedHashMap<>();
    public final Map<String,Integer> unattributed = new HashMap<>();
    public double[][] odds = new double[12][5];
    public GameState() { for(int i=0;i<8;i++) players[i]=new Player(); }
    public static final class Player implements Serializable {
        private static final long serialVersionUID=5L;
        public int level=0, gold=0, hp=0;
        public String lineup="", equipment="";
        public final List<Holding> units=new ArrayList<>();
    }
    public static final class Hero implements Serializable {
        private static final long serialVersionUID=5L;
        public String id,name; public int cost,total;
        public Hero(String id,String name,int cost,int total){this.id=id;this.name=name;this.cost=cost;this.total=total;}
    }
    public static final class Holding implements Serializable {
        private static final long serialVersionUID=5L;
        public String hero,equipment; public int star,count;
        public Holding(String hero,int star,int count,String equipment){this.hero=hero;this.star=star;this.count=count;this.equipment=equipment;}
        public int copies(){return count*(star==1?1:star==2?3:9);}
    }
    public int observed(String id){
        long n=unattributed.getOrDefault(id,0);
        for(Player p:players)for(Holding h:p.units)if(h.hero.equals(id))n+=h.copies();
        if(n>Integer.MAX_VALUE)throw new IllegalArgumentException("观察数量过大");
        return (int)n;
    }
    public void validate(){
        if(schema!=5||players.length!=8)throw new IllegalArgumentException("不支持的数据版本");
        if(selectedPlayer<0||selectedPlayer>7||slots<1||slots>20||refreshCost<1||refreshCost>100)throw new IllegalArgumentException("刷新参数超出范围");
        if(heroes.size()>500)throw new IllegalArgumentException("英雄数量过多");
        for(Map.Entry<String,Hero> e:heroes.entrySet()){
            Hero h=e.getValue();
            if(!e.getKey().equals(h.id)||h.id.isEmpty()||h.name.trim().isEmpty()||h.cost<0||h.cost>5||h.total<0||h.total>10000)throw new IllegalArgumentException("英雄规则无效");
        }
        for(Player p:players){
            if(p.level<0||p.level>11||p.gold<0||p.gold>10000||p.hp<0||p.hp>1000)throw new IllegalArgumentException("等级 0–11、金币 0–10000、血量 0–1000；0 级表示未知");
            for(Holding h:p.units)if(!heroes.containsKey(h.hero)||h.star<1||h.star>3||h.count<0||h.count>1000)throw new IllegalArgumentException("英雄、星级或数量无效");
        }
        for(Map.Entry<String,Integer> e:unattributed.entrySet())if(!heroes.containsKey(e.getKey())||e.getValue()<0||e.getValue()>10000)throw new IllegalArgumentException("未归属数量无效");
        if(!target.isEmpty()&&!heroes.containsKey(target))throw new IllegalArgumentException("目标英雄不存在");
        if(odds.length!=12)throw new IllegalArgumentException("需要 1–11 级的概率表");
        for(double[] row:odds){
            if(row.length!=5)throw new IllegalArgumentException("每级需要五个费用概率");
            double sum=0;for(double x:row){if(!Double.isFinite(x)||x<0||x>1)throw new IllegalArgumentException("概率须为 0–100%");sum+=x;}
            if(sum!=0&&Math.abs(sum-1)>1e-8)throw new IllegalArgumentException("每级概率之和须为 100%，未知等级可全为 0");
        }
    }
    public GameState copy(){return decode(encode(this));}
    public static String encode(GameState s){try{
        ByteArrayOutputStream b=new ByteArrayOutputStream();try(ObjectOutputStream o=new ObjectOutputStream(b)){o.writeObject(s);}
        return Base64.getEncoder().encodeToString(b.toByteArray());
    }catch(IOException e){throw new IllegalStateException(e);}}
    public static GameState decode(String text){try{
        byte[] b=Base64.getDecoder().decode(text);if(b.length>2000000)throw new IOException("数据过大");
        try(ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(b))){GameState s=(GameState)in.readObject();s.validate();return s;}
    }catch(Exception e){throw new IllegalArgumentException("保存的数据无法读取，原数据已保留",e);}}
    static int number(Object x,int fallback){try{return Integer.parseInt(String.valueOf(x).trim());}catch(Exception e){return fallback;}}
    /** Copy old values without modifying or removing the original preferences. */
    public static GameState migrate(Map<String,?> old){
        GameState s=new GameState();String name=String.valueOf(old.containsKey("champion")?old.get("champion"):"旧版目标英雄");
        if(name.trim().isEmpty())name="旧版目标英雄";
        if(!old.isEmpty()){
            s.legacyImported=true;s.source="旧版导入（来源待核实）";
            s.heroes.put("legacy",new Hero("legacy",name,0,Math.max(0,Math.min(10000,number(old.get("initial"),0)))));s.target="legacy";
            s.unattributed.put("legacy",Math.max(0,Math.min(10000,number(old.containsKey("unknown")?old.get("unknown"):old.get("other"),0))));
        }
        for(int i=0;i<8;i++){
            Player p=s.players[i];String k="p"+i+"_";
            p.level=Math.max(0,Math.min(11,number(old.get(k+"level"),0)));p.hp=Math.max(0,Math.min(1000,number(old.get(k+"hp"),0)));p.gold=Math.max(0,Math.min(10000,number(old.get(k+"gold"),0)));
            p.lineup=String.valueOf(old.containsKey(k+"lineup")?old.get(k+"lineup"):"");p.equipment=String.valueOf(old.containsKey(k+"equipment")?old.get(k+"equipment"):"");
            int n=number(old.containsKey(k+"copies")?old.get(k+"copies"):(i==0?old.get("self"):null),0);
            // Split into rows so original V0.4 counts up to 9999 are retained.
            n=Math.max(0,Math.min(9999,n));while(n>0){int take=Math.min(1000,n);p.units.add(new Holding("legacy",1,take,""));n-=take;}
        }
        s.validate();return s;
    }
    public String playersText(){
        StringBuilder b=new StringBuilder();for(int i=0;i<8;i++){
            Player p=players[i];b.append(i==0?"我方":"玩家"+(i+1)).append(" · 等级 ").append(p.level==0?"未知":p.level).append(" · 金币 ").append(p.gold).append(" · 血量 ").append(p.hp).append("\n");
            for(Holding h:p.units){Hero hero=heroes.get(h.hero);b.append("  ").append(hero.name).append(" ").append(h.star).append("★ ×").append(h.count).append("（").append(h.copies()).append("张） ").append(h.equipment).append("\n");}
            if(!p.lineup.isEmpty())b.append("  阵容备注：").append(p.lineup).append("\n");if(!p.equipment.isEmpty())b.append("  装备备注：").append(p.equipment).append("\n");
        }return b.toString();
    }
    public String poolText(){StringBuilder b=new StringBuilder(season+"\n仅按观察估算；未观察棋子未扣除\n");
        for(Hero h:heroes.values()){int n=observed(h.id);b.append(h.name).append(" · ").append(h.cost==0?"费用待配置":h.cost+"费").append("\n已观察 ").append(n).append(" / 总量 ").append(h.total).append(" · 估算剩余 ").append(Math.max(0,h.total-n));if(n>h.total)b.append(" ⚠ 观察超过卡池，预测不可用");b.append("\n");}return b.toString();}
}
