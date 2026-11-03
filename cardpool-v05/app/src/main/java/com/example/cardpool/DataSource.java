package com.example.cardpool;
/** Future authorized recognition must deliver validated observations with provenance.
 * It must not silently infer unseen shop contents. No recognition is implemented. */
public interface DataSource {
    String label();
    void apply(GameState state,long tick);
    final class Manual implements DataSource {
        public String label(){return "手动输入";}
        public void apply(GameState s,long tick){s.source=s.legacyImported?"手动维护 / 含旧版未核实记录":label();}
    }
    final class Simulated implements DataSource {
        public String label(){return "模拟实时数据（非实际对局）";}
        public void apply(GameState s,long tick){
            s.source=label();
            for(int i=0;i<8;i++){GameState.Player p=s.players[i];p.level=7;p.gold=(int)((tick+i*7)%70);p.hp=100-(int)((tick+i*3)%90);}
            // Deterministic sample observations, not generated real shop results.
            if(!s.players[1].units.isEmpty())s.players[1].units.get(0).count=(int)(tick%3);
        }
        public static GameState initial(){
            GameState s=new GameState();s.season="模拟规则 · 三英雄示例（非官方赛季）";
            s.heroes.put("sample_a",new GameState.Hero("sample_a","模拟英雄甲",4,10));
            s.heroes.put("sample_b",new GameState.Hero("sample_b","模拟英雄乙",4,10));
            s.heroes.put("sample_c",new GameState.Hero("sample_c","模拟英雄丙",1,30));
            s.target="sample_a";s.rosterComplete=true;s.odds[7]=new double[]{.75,0,0,.25,0};
            s.players[0].units.add(new GameState.Holding("sample_a",2,1,"模拟装备"));
            s.players[1].units.add(new GameState.Holding("sample_b",1,1,""));new Simulated().apply(s,0);return s;
        }
    }
    interface AuthorizedRecognition extends DataSource { boolean hasUserAuthorization(); }
}
