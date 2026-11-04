package com.example.cardpool;
import java.util.Locale;

/**
 * Opt-in, date-labeled reference shop cost-tier odds (not hero appearance
 * probabilities). The China mobile game can differ from TFT and special shops
 * override normal odds. Never fills hero totals or marks roster verified.
 */
public final class ShopOddsPreset {
    private ShopOddsPreset(){}
    public static final String VERSION_164="参考表：金铲铲16.4（2026-02）7级调整后；当前版本需核验";
    public static final String VERSION_161="参考表：英雄联盟传奇16.1／16.4前（7级旧概率）；当前版本需核验";
    private static final int[][] BASE={
        {0,0,0,0,0},
        {100,0,0,0,0}, // 1
        {100,0,0,0,0}, // 2
        {75,25,0,0,0}, // 3
        {55,30,15,0,0}, // 4
        {45,33,20,2,0}, // 5
        {30,40,25,5,0}, // 6
        {16,30,43,10,1}, // 7: 16.4 patch
        {15,20,32,30,3}, // 8
        {10,17,25,33,15}, // 9
        {5,10,20,40,25}, // 10
        {1,2,12,50,35} // 11: reference only; confirm level validity in chosen game mode
    };
    public static void apply(GameState state,boolean patch164){
        double[][] odds=new double[12][5];
        for(int level=1;level<=11;level++)for(int tier=0;tier<5;tier++)
            odds[level][tier]=(level==7&&!patch164
                ?new int[]{19,30,40,10,1}[tier]:BASE[level][tier])/100.;
        state.odds=odds;
        state.oddsSource=patch164?VERSION_164:VERSION_161;
        // This is only a cost-tier reference table, not verified pool data.
        // Leave all hero totals, ownership, unlocks and rosterComplete untouched.
    }
    public static boolean configured(GameState state,int level){
        if(level<1||level>11)return false;
        double sum=0;
        for(double p:state.odds[level])sum+=p;
        return Math.abs(sum-1)<1e-8;
    }
    public static String summary(GameState state){
        StringBuilder b=new StringBuilder("各等级商店费用概率（参考配置，不是指定英雄概率）\n");
        b.append("来源：").append(state.oddsSource==null?"未设置":state.oddsSource).append("\n");
        b.append("等级   1费   2费   3费   4费   5费\n");
        for(int level=1;level<=11;level++){
            b.append(level).append("级  ");
            if(!configured(state,level))b.append("待填写");
            else for(int tier=0;tier<5;tier++)
                b.append(String.format(Locale.ROOT,"%.0f%%",state.odds[level][tier]*100)).append(tier==4?"":" / ");
            b.append("\n");
        }
        b.append("注意：来源是特定历史版本的公开数据，须与手机当前版本核对。7费属于传说级但其个人解锁、权重和固定右侧栏位并非普通概率，不能用本表直接推算。");
        return b.toString();
    }
    public static String selectedLevel(GameState s){
        int level=s.players[s.selectedPlayer].level;
        if(!configured(s,level))
            return "当前玩家"+(s.selectedPlayer+1)+"等级"+(level==0?"未知":level)+"：基础商店费用概率尚未配置。";
        StringBuilder b=new StringBuilder("玩家"+(s.selectedPlayer+1)+" · "+level+"级参考费用概率：");
        for(int tier=0;tier<5;tier++)b.append(tier+1).append("费 ")
            .append(String.format(Locale.ROOT,"%.0f%%",s.odds[level][tier]*100)).append("  ");
        b.append("\n").append(s.oddsSource==null?"未标注来源":s.oddsSource);
        return b.toString();
    }
}
