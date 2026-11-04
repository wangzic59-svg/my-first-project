package com.example.cardpool;
import java.util.Locale;
/**
 * Explain what is verified and what is missing before any numerical shop estimate.
 * Importing a third-party roster does not verify a live season or special mode.
 */
public final class SeasonAudit {
 private SeasonAudit(){}
 public static boolean totalsKnown(GameState s){
  if(s.heroes.isEmpty())return false;
  for(GameState.Hero h:s.heroes.values())if(h.cost<1||h.cost>7||h.total<=0||s.observed(h.id)>h.total)return false;
  return true;
 }
 public static int missingTotals(GameState s){
  int n=0;for(GameState.Hero h:s.heroes.values())if(h.total<=0)n++;return n;
 }
 public static String report(GameState s){
  StringBuilder b=new StringBuilder("赛季规则核验（仅用户填写/第三方静态资料）\n");
  b.append("当前标签：").append(s.season).append("\n");
  if(s.heroes.isEmpty())return b.append("还没有导入英雄名单。\n不能计算商店概率。").toString();
  int[] kinds=new int[8],missing=new int[8],excess=new int[8];
  for(GameState.Hero h:s.heroes.values()){
   if(h.cost<1||h.cost>7)continue;kinds[h.cost]++;
   if(h.total==0)missing[h.cost]++;
   if(h.total>0&&s.observed(h.id)>h.total)excess[h.cost]++;
  }
  b.append("英雄条目：").append(s.heroes.size()).append("（可能含特殊英雄、重复变体，需核对）\n");
  for(int tier=1;tier<=7;tier++){
   b.append(tier).append("费：").append(kinds[tier]).append("种，未知总张数 ")
    .append(missing[tier]).append("，观察超过上限 ").append(excess[tier]).append("\n");
  }
  int valid=0;StringBuilder unknown=new StringBuilder();
  for(int level=1;level<=11;level++){
   double sum=0;for(double rate:s.odds[level])sum+=rate;
   if(Math.abs(sum-1)<1e-8)valid++;
   else{if(unknown.length()>0)unknown.append("、");unknown.append(level);}
  }
  b.append("已填写刷新概率等级：").append(valid).append("/11\n");
  b.append("费用概率来源：").append(s.oddsSource==null?"未知":s.oddsSource).append("\n");
  if(unknown.length()>0)b.append("尚无完整概率：").append(unknown).append("级\n");
  if(s.season.contains("英雄联盟传奇"))b.append("特殊机制：解锁弈子共享卡池、锁定商店右侧栏位与7费英雄未接入模型。即使填满普通概率表也不能证明估算有效。\n");
  b.append("名单及同费分母确认：").append(s.rosterComplete?"用户已勾选":"尚未勾选").append("\n");
  b.append("总张数核验：").append(totalsKnown(s)?"通过基础检查":"不完整，不能用未知总量推算").append("\n");
  b.append("注意：商店事件、特殊奇遇、固定价格/三星商店等会改变规则；本模型只估算普通商店，不预测下一轮真实五张。");
  return b.toString();
 }
}
