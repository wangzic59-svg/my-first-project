package com.example.cardpool;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Both surfaces use identical validated transactions and reject stale dialogs. */
public final class Editors {
    static void show(Context c,AlertDialog d){
        if(!(c instanceof Activity))d.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        d.show();d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    static LinearLayout form(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(24,12,24,12);return l;}
    static EditText field(Context c,LinearLayout l,String title,String value,boolean multiline){
        TextView t=new TextView(c);t.setText(title);l.addView(t);EditText e=new EditText(c);e.setText(value);e.setTextSize(14);
        e.setInputType(InputType.TYPE_CLASS_TEXT|(multiline?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));if(multiline)e.setMinLines(3);else e.setSingleLine(true);l.addView(e);return e;
    }
    static String value(EditText e){return e.getText().toString().trim();}
    static int num(EditText e){return Integer.parseInt(value(e));}
    static boolean writable(Context c,DataManager m){if(m.isSimulating()){Toast.makeText(c,"模拟模式仅供演示，请先返回手动数据",Toast.LENGTH_LONG).show();return false;}return true;}
    static void edit(Context c,DataManager m,GameState start,String title,LinearLayout fields,DataManager.Mutation mutation){
        ScrollView scroll=new ScrollView(c);scroll.addView(fields);
        AlertDialog d=new AlertDialog.Builder(c).setTitle(title).setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存并实时同步",null).create();show(c,d);
        d.getButton(-1).setOnClickListener(v->{try{
            m.mutate(current->{if(m.isSimulating()||current.manualRevision!=start.manualRevision)throw new IllegalArgumentException("数据已更新，请取消后重新打开编辑，避免覆盖新记录");mutation.apply(current);});d.dismiss();
        }catch(Exception ex){Toast.makeText(c,ex instanceof NumberFormatException?"请填写有效数字":ex.getMessage(),Toast.LENGTH_LONG).show();}});
    }
    public static void choosePlayer(Context c,DataManager m){String[] names=new String[8];for(int i=0;i<8;i++)names[i]="玩家 "+(i+1);AlertDialog d=new AlertDialog.Builder(c).setTitle("分析哪位玩家的商店？").setItems(names,(a,n)->m.mutate(s->s.selectedPlayer=n)).create();show(c,d);}
    public static void chooseHero(Context c,DataManager m){GameState s=m.snapshot();String[] ids=s.heroes.keySet().toArray(new String[0]);String[] labels=new String[ids.length];for(int i=0;i<ids.length;i++)labels[i]=s.heroes.get(ids[i]).name+" ["+ids[i]+"]";
        if(ids.length==0){Toast.makeText(c,"请先配置英雄卡池",Toast.LENGTH_LONG).show();return;}
        AlertDialog d=new AlertDialog.Builder(c).setTitle("目标英雄 / 快捷编辑对象").setItems(labels,(a,n)->m.mutate(x->x.target=ids[n])).create();show(c,d);
    }
    public static void player(Context c,DataManager m,int index){if(!writable(c,m))return;GameState s=m.snapshot();GameState.Player p=s.players[index];LinearLayout f=form(c);
        EditText level=field(c,f,"等级（0 表示未知，1–11）",String.valueOf(p.level),false),gold=field(c,f,"金币",String.valueOf(p.gold),false),hp=field(c,f,"血量",String.valueOf(p.hp),false);
        StringBuilder units=new StringBuilder();for(GameState.Holding h:p.units)units.append(h.hero).append(",").append(h.star).append(",").append(h.count).append(",").append(h.equipment).append("\n");
        StringBuilder legend=new StringBuilder("英雄 ID：");for(GameState.Hero h:s.heroes.values())legend.append(h.id).append("=").append(h.name).append("； ");
        EditText rows=field(c,f,"每行：英雄ID,星级1–3,个数,装备（可空）\n"+legend,units.toString(),true);
        EditText lineup=field(c,f,"阵容备注（保留旧版文字）",p.lineup,false),equipment=field(c,f,"装备备注",p.equipment,false);
        edit(c,m,s,"玩家 "+(index+1),f,x->{GameState.Player q=x.players[index];if(num(level)!=p.level)q.level=num(level);if(num(gold)!=p.gold)q.gold=num(gold);if(num(hp)!=p.hp)q.hp=num(hp);q.lineup=value(lineup);q.equipment=value(equipment);q.units.clear();
            if(!value(rows).isEmpty())for(String line:value(rows).split("\\n")){String[] a=line.split(",",4);if(a.length<3)throw new IllegalArgumentException("持牌格式：英雄ID,星级,个数,装备");q.units.add(new GameState.Holding(a[0].trim(),Integer.parseInt(a[1].trim()),Integer.parseInt(a[2].trim()),a.length==4?a[3].trim():""));}
        });
    }
    public static void rules(Context c,DataManager m){if(!writable(c,m))return;GameState s=m.snapshot();LinearLayout f=form(c);
        EditText name=field(c,f,"赛季 / 规则版本（请依据当前游戏配置）",s.season,false);
        StringBuilder heroes=new StringBuilder();for(GameState.Hero h:s.heroes.values())heroes.append(h.id).append(",").append(h.name).append(",").append(h.cost).append(",").append(h.total).append("\n");
        EditText roster=field(c,f,"完整英雄名单，每行：稳定ID,名称,费用1–5,该英雄总张数\n迁移英雄 legacy 请保留 ID，并补全费用；不可删除仍有持牌记录的英雄。",heroes.toString(),true);
        StringBuilder odds=new StringBuilder();for(int i=1;i<=11;i++){odds.append(i);for(double n:s.odds[i])odds.append(",").append(n*100);odds.append("\n");}
        EditText rates=field(c,f,"每行：等级,1费%,2费%,3费%,4费%,5费%\n每级合计100；未知等级可全0。不内置未经核实的官方赛季表。",odds.toString(),true);
        CheckBox complete=new CheckBox(c);complete.setText("已核对完整名单，同费剩余分母包含所有英雄");complete.setChecked(s.rosterComplete);f.addView(complete);
        CheckBox independent=new CheckBox(c);independent.setText("独立格有放回近似（不勾选为同费无放回）");independent.setChecked(s.independentSlots);f.addView(independent);
        EditText slots=field(c,f,"商店格数（1–20）",String.valueOf(s.slots),false),cost=field(c,f,"每次刷新金币（1–100）",String.valueOf(s.refreshCost),false);
        edit(c,m,s,"赛季卡池与刷新规则",f,x->{
            LinkedHashMap<String,GameState.Hero> next=new LinkedHashMap<>();
            for(String line:value(roster).split("\\n")){if(line.trim().isEmpty())continue;String[] a=line.split(",",-1);if(a.length!=4)throw new IllegalArgumentException("英雄行需要四列");String id=a[0].trim();if(next.containsKey(id))throw new IllegalArgumentException("英雄ID重复");next.put(id,new GameState.Hero(id,a[1].trim(),Integer.parseInt(a[2].trim()),Integer.parseInt(a[3].trim())));}
            x.heroes.clear();x.heroes.putAll(next);x.odds=new double[12][5];Set<Integer> seen=new HashSet<>();
            for(String line:value(rates).split("\\n")){if(line.trim().isEmpty())continue;String[] a=line.split(",",-1);if(a.length!=6)throw new IllegalArgumentException("概率行需要等级及五个费用");int level=Integer.parseInt(a[0].trim());if(level<1||level>11||!seen.add(level))throw new IllegalArgumentException("等级重复或超出1–11");for(int i=0;i<5;i++)x.odds[level][i]=Double.parseDouble(a[i+1].trim())/100;}
            x.rosterComplete=complete.isChecked();x.independentSlots=independent.isChecked();x.slots=num(slots);x.refreshCost=num(cost);x.season=value(name);
            if(!x.heroes.containsKey(x.target))x.target=x.heroes.isEmpty()?"":x.heroes.keySet().iterator().next();
        });
    }
    public static void unknown(Context c,DataManager m){if(!writable(c,m))return;GameState s=m.snapshot();if(s.target.isEmpty())return;LinearLayout f=form(c);EditText n=field(c,f,"目标英雄未归属基础卡张数；转记到玩家时须扣除，避免重复",String.valueOf(s.unattributed.getOrDefault(s.target,0)),false);edit(c,m,s,"未归属观察",f,x->x.unattributed.put(s.target,num(n)));}
}
