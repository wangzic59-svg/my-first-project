package com.example.cardpool;
import java.util.Locale;
public final class Prediction {
    public static String explain(GameState s){try{
        s.validate();
        if(s.season.contains("英雄联盟传奇"))
            return (ShopOddsPreset.configured(s,s.players[s.selectedPlayer].level)
                ?"已配置当前玩家等级的1–5费参考刷新比例，但它不是指定英雄的出现概率。"
                :"当前玩家等级的基础费用刷新概率尚未配置或玩家等级未知。")
                +"\\n指定英雄概率尚不能可靠计算：解锁棋子、固定右侧栏位、7费传说英雄、卡池完整性和当前版本差异都可能改变实际概率。"
                +"\\n仍可正常使用非ROOT屏幕识别、商店观察记录和八人持牌统计。";
        for(GameState.Hero hero:s.heroes.values())if(hero.cost>5)
            return "预测不可用：当前普通商店计算器暂不支持6费或7费弈子，不能将其错误算作5费。";

        if(!s.rosterComplete) return "预测不可用：请在赛季规则中配置完整英雄名单，并确认同费分母完整。";
        if(!SeasonAudit.totalsKnown(s)) return "预测不可用：存在未核实的英雄总张数、费用或观察超额。请先核实全套卡池数据。";
        GameState.Hero target=s.heroes.get(s.target);if(target==null||target.cost==0)return "预测不可用：请选择目标英雄并配置费用。";
        int level=s.players[s.selectedPlayer].level;if(level==0)return "预测不可用：该玩家等级未知。";
        double sum=0;for(double x:s.odds[level])sum+=x;if(Math.abs(sum-1)>1e-8)return "预测不可用：该等级的刷新概率未配置。";
        int[] pools=new int[6];
        for(GameState.Hero h:s.heroes.values()){
            if(h.cost==0)throw new IllegalArgumentException("名单中仍有费用待配置的英雄");
            pools[h.cost]+=NativeEngine.remaining(h.total,s.observed(h.id));
        }
        int n=NativeEngine.remaining(target.total,s.observed(target.id));
        for(int c=1;c<=5;c++)if(s.odds[level][c-1]>0&&pools[c]==0)throw new IllegalArgumentException("配置的费用卡池已空，当前模型不支持空费用重新分配");
        if(!s.independentSlots)for(int c=1;c<=5;c++)if(s.odds[level][c-1]>0&&pools[c]<s.slots)throw new IllegalArgumentException("同费卡池不足一店格数；当前模型不支持空费用重分配，请核实规则");
        double[] p=NativeEngine.predict(n,pools[target.cost],s.odds[level][target.cost-1],s.slots,s.independentSlots);
        return String.format(Locale.CHINA,"概率预测 · 非真实商店结果\n玩家%d · 等级%d · %s（%d费）\n目标剩余 %d / 同费剩余 %d\n单格 %.4f%%\n一店至少一张 %.4f%%\n一店期望 %.4f 张\n金币可付费刷新 %d 次（每次 %d 金币；不扣购买及升级支出）\n模型：%s\n未观察持牌、特殊机制和规则误差会影响结果。无法读取或预知下一店五张牌。",s.selectedPlayer+1,level,target.name,target.cost,n,pools[target.cost],100*p[0],100*p[1],p[2],s.players[s.selectedPlayer].gold/s.refreshCost,s.refreshCost,s.independentSlots?"独立格有放回近似":"先抽费用、同费用无放回；费用概率固定");
    }catch(IllegalArgumentException e){return "预测不可用："+e.getMessage();}}
}
