package com.example.cardpool;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends Activity implements DataManager.Listener {
    private DataManager manager;private LinearLayout body;private TextView status;private int tab=0;private boolean awaiting=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle b){super.onCreate(b);try{manager=DataManager.get(this);}catch(Exception e){TextView t=new TextView(this);t.setText(e.getMessage()+"\n未覆盖原始数据，请先备份应用数据后排查。");setContentView(t);return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setFitsSystemWindows(true);root.setPadding(dp(16),dp(20),dp(16),dp(8));root.setBackgroundColor(Color.rgb(13,25,40));setContentView(root);
        TextView title=text("金铲铲卡池助手 V0.5",23);root.addView(title);status=text("",13);root.addView(status);
        LinearLayout tabs=new LinearLayout(this);root.addView(tabs);String[] labels={"八人数据","卡池统计","商店预测"};for(int i=0;i<3;i++){final int n=i;button(tabs,labels[i],()->{tab=n;render();});}
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);
        LinearLayout actions=new LinearLayout(this);root.addView(actions);button(actions,"开启悬浮窗",this::startOverlay);button(actions,"关闭",()->stopService(new Intent(this,OverlayService.class)));
        Button source=new Button(this);source.setText("切换手动 / 模拟实时数据");root.addView(source);source.setOnClickListener(v->manager.simulation(!manager.isSimulating()));manager.add(this);render();
    }
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(225,238,245));t.setPadding(8,12,8,12);return t;}
    void button(LinearLayout row,String s,Runnable run){Button b=new Button(this);b.setText(s);b.setTextSize(12);row.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->{try{run.run();}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}});}
    void action(String label,Runnable r){LinearLayout row=new LinearLayout(this);body.addView(row);button(row,label,r);}
    @Override public void changed(){handler.post(this::render);}
    private void render(){if(body==null)return;GameState s=manager.snapshot();body.removeAllViews();
        status.setText(s.source+" · 修订 "+s.revision+(s.updatedAt==0?"":" · "+DateFormat.getTimeInstance().format(new Date(s.updatedAt)))+"\n所有结果来自记录与规则估算，不是游戏实际商店。");
        action("选择目标英雄（当前："+(s.heroes.containsKey(s.target)?s.heroes.get(s.target).name:"未选择")+"）",()->Editors.chooseHero(this,manager));
        if(tab==0){
            body.addView(text("全场八人 · 星级折算 1★=1 / 2★=3 / 3★=9 张\n下方 ± 只增减目标英雄的一星无装备记录；完整编辑可修改其他星级及装备。",13));
            for(int i=0;i<8;i++){final int player=i;GameState.Player p=s.players[i];LinearLayout row=new LinearLayout(this);body.addView(row);
                button(row,"玩家"+(i+1)+" 编辑",()->Editors.player(this,manager,player));button(row,"−1",()->{if(Editors.writable(this,manager))manager.adjust(player,s.target,-1);});button(row,"+1",()->{if(Editors.writable(this,manager))manager.adjust(player,s.target,1);});
                StringBuilder detail=new StringBuilder("Lv "+(p.level==0?"未知":p.level)+" · 金币 "+p.gold+" · HP "+p.hp+"\n");
                for(GameState.Holding h:p.units)detail.append(s.heroes.get(h.hero).name).append(" ").append(h.star).append("★ ×").append(h.count).append(" · ").append(h.equipment).append("\n");
                detail.append("阵容：").append(p.lineup).append("\n装备：").append(p.equipment);body.addView(text(detail.toString(),14));
            }
        }else if(tab==1){action("配置赛季完整卡池与刷新规则",()->Editors.rules(this,manager));action("编辑目标英雄未归属观察",()->Editors.unknown(this,manager));body.addView(text(s.poolText(),16));}
        else {action("选择预测玩家（当前玩家"+(s.selectedPlayer+1)+"）",()->Editors.choosePlayer(this,manager));action("配置刷新规则",()->Editors.rules(this,manager));body.addView(text(Prediction.explain(s),17));body.addView(text("数据来源接口：手动输入 / 隔离的模拟实时数据。已预留授权画面识别接口；尚未接入识别。无需 ROOT，不访问游戏进程。",13));}
    }
    private void startOverlay(){if(!Settings.canDrawOverlays(this)){awaiting=true;startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
        startForegroundService(new Intent(this,OverlayService.class));}
    @Override protected void onResume(){super.onResume();if(manager!=null)render();if(awaiting){awaiting=false;if(Settings.canDrawOverlays(this))startOverlay();}}
    @Override protected void onDestroy(){if(manager!=null)manager.remove(this);handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
