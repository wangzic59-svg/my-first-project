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
        TextView title=text("金铲铲卡池助手 V0.6",23);root.addView(title);status=text("",13);root.addView(status);
        LinearLayout tabs=new LinearLayout(this);root.addView(tabs);String[] labels={"八人数据","卡池统计","商店预测","采集"};for(int i=0;i<4;i++){final int n=i;button(tabs,labels[i],()->{tab=n;render();});}
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);
        LinearLayout actions=new LinearLayout(this);root.addView(actions);button(actions,"开启悬浮窗",this::startOverlay);button(actions,"关闭",()->stopService(new Intent(this,OverlayService.class)));
        Button source=new Button(this);source.setText("切换手动 / 模拟实时数据");root.addView(source);source.setOnClickListener(v->{stopService(new Intent(this,CaptureService.class));manager.simulation(!manager.isSimulating());});manager.add(this);render();
    }
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(225,238,245));t.setPadding(8,12,8,12);return t;}
    void button(LinearLayout row,String s,Runnable run){Button b=new Button(this);b.setText(s);b.setTextSize(12);row.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->{try{run.run();}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}});}
    void action(String label,Runnable r){LinearLayout row=new LinearLayout(this);body.addView(row);button(row,label,r);}
    private final Runnable ageTick=new Runnable(){public void run(){if(manager!=null&&tab==3)render();handler.postDelayed(this,2000);}};
    @Override protected void onPause(){handler.removeCallbacks(ageTick);super.onPause();}
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
        else if(tab==2){action("多目标英雄",()->CaptureControls.targets(this,manager));action("选择预测玩家（当前玩家"+(s.selectedPlayer+1)+"）",()->Editors.choosePlayer(this,manager));action("配置刷新规则",()->Editors.rules(this,manager));body.addView(text(CaptureControls.predictions(s),17));body.addView(text("数据来源：手动 / 模拟 / 用户授权截图。实际可见商店与概率预测分开；没有下一轮真实商店接口。无需 ROOT，不访问游戏进程。",13));}
        else {
            action("校准识别区域与8名玩家别名",()->CaptureControls.profile(this));
            action("从截图录入英雄/星级/装备图像模板",()->pickImage(68));
            action("验证一张截图（不写入对局）",()->pickImage(67));
            action("开始：请求系统屏幕捕获授权",this::startCapture);
            action("暂停识别（断开采集表面）",()->captureAction(CaptureService.PAUSE));
            action("恢复识别（仅当前授权会话）",()->captureAction(CaptureService.RESUME));
            action("停止采集（释放授权）",()->stopService(new Intent(this,CaptureService.class)));
            action("自动合并数值 / 手动修正锁定",()->CaptureControls.auto(this,manager));
            action("可见棋盘参与卡池估算 / 持牌锁定",()->CaptureControls.board(this,manager));
            body.addView(text("本地OCR置信分与图像相似度分不是准确率保证。须配置适合当前分辨率/布局的区域、唯一玩家名和模板。低置信/未知身份/画面遮挡不覆盖记录。棋盘通常不能看见备战席，观察层只表示可见子集。",13));
            body.addView(text(s.capture.text(System.currentTimeMillis()),14));
        }
    }
    private void pickImage(int request){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,request);}
    private void captureAction(String action){startService(new Intent(this,CaptureService.class).setAction(action));}
    private void startCapture(){
        if(manager.isSimulating()){Toast.makeText(this,"请先退出模拟模式",Toast.LENGTH_LONG).show();return;}
        if(!getSharedPreferences("capture_profile",0).getBoolean("configured",false)){Toast.makeText(this,"请先校准画面布局与玩家别名",Toast.LENGTH_LONG).show();return;}
        stopService(new Intent(this,CaptureService.class));
        android.media.projection.MediaProjectionManager pm=getSystemService(android.media.projection.MediaProjectionManager.class);
        startActivityForResult(pm.createScreenCaptureIntent(),66);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==66){if(result==RESULT_OK&&data!=null)startForegroundService(new Intent(this,CaptureService.class).setAction(CaptureService.START).putExtra("result",result).putExtra("consent",data));else Toast.makeText(this,"未授权，不进行屏幕捕获",Toast.LENGTH_LONG).show();}
        else if((request==67||request==68)&&result==RESULT_OK&&data!=null){
            if(request==68)CaptureControls.template(this,data.getData());
            else {final Uri uri=data.getData();Toast.makeText(this,"正在本地验证截图…",Toast.LENGTH_SHORT).show();new Thread(()->{
                android.graphics.Bitmap image=null;
                try{image=CaptureControls.read(this,uri);try(ScreenRecognizer recognizer=new ScreenRecognizer(this)){
                    ObservationGate.Frame f=recognizer.read(image,CaptureProfile.load(this),manager.snapshot(),System.currentTimeMillis());StringBuilder b=new StringBuilder("仅导入验证，未写入对局。\n玩家编号 "+f.player+"（0–7） · 身份置信分 "+f.identityConfidence+"\n");
                    for(java.util.Map.Entry<String,ObservationGate.Token> e:f.fields.entrySet())b.append(e.getKey()).append("=").append(e.getValue().text).append(" · ").append(e.getValue().confidence).append("\n");
                    handler.post(()->{if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("截图识别结果").setMessage(b.toString()).setPositiveButton("关闭",null).show();});
                }}catch(Exception e){handler.post(()->Toast.makeText(this,"验证失败："+e.getMessage(),Toast.LENGTH_LONG).show());}finally{if(image!=null)image.recycle();}
            },"screenshot-validation").start();}
        }
    }
    private void startOverlay(){if(!Settings.canDrawOverlays(this)){awaiting=true;startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
        startForegroundService(new Intent(this,OverlayService.class));}
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(ageTick);handler.postDelayed(ageTick,2000);if(manager!=null)render();if(awaiting){awaiting=false;if(Settings.canDrawOverlays(this))startOverlay();}}
    @Override protected void onDestroy(){if(manager!=null)manager.remove(this);handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
