package com.example.cardpool;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Same manager and editors as Activity; no cached derived preference strings. */
public class OverlayService extends Service implements DataManager.Listener {
    static volatile long renderedRevision=-1;
    private DataManager manager;private WindowManager wm;private LinearLayout widget,body;private WindowManager.LayoutParams params;private TextView status;private int mode=0;private boolean collapsed=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    @Override public IBinder onBind(Intent i){return null;}
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public int onStartCommand(Intent i,int flags,int startId){try{
        NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("cardpool_overlay","卡池分析悬浮窗",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this,"cardpool_overlay").setSmallIcon(android.R.drawable.ic_menu_info_details).setContentTitle("卡池助手 V0.6").setContentText("八人观察 · 卡池统计 · 概率估算").setContentIntent(open).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(203,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(203,n);
        if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}
        if(widget==null){manager=DataManager.get(this);manager.add(this);create();}render();
    }catch(Exception e){stopSelf();}return START_NOT_STICKY;}
    TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setPadding(dp(4),dp(5),dp(4),dp(5));return t;}
    void button(LinearLayout row,String title,Runnable run){Button b=new Button(this);b.setText(title);b.setTextSize(11);b.setMinWidth(0);b.setPadding(0,0,0,0);row.addView(b,new LinearLayout.LayoutParams(0,dp(44),1));b.setOnClickListener(v->{try{run.run();}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}});}
    void create(){
        wm=getSystemService(WindowManager.class);params=new WindowManager.LayoutParams(dp(310),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);params.gravity=Gravity.TOP|Gravity.START;params.x=dp(8);params.y=dp(90);
        widget=new LinearLayout(this);widget.setOrientation(LinearLayout.VERTICAL);widget.setPadding(dp(6),dp(6),dp(6),dp(6));widget.setBackgroundColor(Color.argb(245,15,32,49));
        LinearLayout bar=new LinearLayout(this);widget.addView(bar);TextView drag=text("⠿ V0.6",14);bar.addView(drag,new LinearLayout.LayoutParams(0,dp(44),1));
        drag.setOnTouchListener(new View.OnTouchListener(){float x,y;int px,py;public boolean onTouch(View v,MotionEvent e){if(e.getAction()==0){x=e.getRawX();y=e.getRawY();px=params.x;py=params.y;return true;}if(e.getAction()==2){params.x=Math.max(0,px+(int)(e.getRawX()-x));params.y=Math.max(0,py+(int)(e.getRawY()-y));wm.updateViewLayout(widget,params);return true;}if(e.getAction()==MotionEvent.ACTION_UP)v.performClick();return true;}});
        button(bar,"收展",()->{collapsed=!collapsed;body.setVisibility(collapsed?View.GONE:View.VISIBLE);});button(bar,"×",this::stopSelf);
        status=text("",11);widget.addView(status);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);widget.addView(body);wm.addView(widget,params);handler.postDelayed(ageTick,2000);
    }
    private final Runnable ageTick=new Runnable(){public void run(){if(widget==null)return;if(mode==4)render();handler.postDelayed(this,2000);}};
    @Override public void changed(){handler.post(this::render);}
    void render(){if(body==null)return;GameState s=manager.snapshot();renderedRevision=s.revision;status.setText(s.source+" · 修订 "+s.revision);body.removeAllViews();
        LinearLayout tabs=new LinearLayout(this);body.addView(tabs);String[] labels={"八人","卡池","预测","快编","观察"};for(int i=0;i<5;i++){final int n=i;button(tabs,labels[i],()->{mode=n;render();});}
        LinearLayout selectors=new LinearLayout(this);body.addView(selectors);button(selectors,"目标英雄",()->Editors.chooseHero(this,manager));button(selectors,"预测玩家"+(s.selectedPlayer+1),()->Editors.choosePlayer(this,manager));
        String target=s.heroes.containsKey(s.target)?s.heroes.get(s.target).name:"未选择";body.addView(text("当前目标："+target,12));
        ScrollView scroll=new ScrollView(this);body.addView(scroll,new LinearLayout.LayoutParams(-1,dp(280)));LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);
        if(mode==0)content.addView(text(s.playersText(),12));else if(mode==1)content.addView(text(s.poolText(),13));else if(mode==2)content.addView(text(CaptureControls.predictions(s),13));
        else if(mode==4)content.addView(text(s.capture.text(System.currentTimeMillis()),12));
        else {for(int i=0;i<8;i++){final int player=i;LinearLayout row=new LinearLayout(this);content.addView(row);int count=0;for(GameState.Holding h:s.players[i].units)if(h.hero.equals(s.target))count+=h.copies();
            button(row,"P"+(i+1)+" · "+count+"张",()->Editors.player(this,manager,player));button(row,"−1★",()->{if(Editors.writable(this,manager))manager.adjust(player,s.target,-1);});button(row,"+1★",()->{if(Editors.writable(this,manager))manager.adjust(player,s.target,1);});}
            content.addView(text("±只修改目标英雄的一星无装备记录；点玩家按钮可编辑等级、金币、血量、各英雄星级及装备。",11));}
        content.addView(text("观察数据与概率预测；非真实商店结果",11));
    }
    @Override public void onDestroy(){renderedRevision=-1;handler.removeCallbacksAndMessages(null);if(manager!=null)manager.remove(this);if(widget!=null){try{wm.removeView(widget);}catch(Exception ignored){}}super.onDestroy();}
}
