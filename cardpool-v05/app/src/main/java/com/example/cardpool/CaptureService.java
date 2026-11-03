package com.example.cardpool;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.nio.ByteBuffer;
import java.util.concurrent.*;

/** Consent token lives only in this non-exported, non-sticky service. No silent restart. */
public final class CaptureService extends Service {
 static volatile boolean projectionActive=false;
 public static final String START="capture.START",PAUSE="capture.PAUSE",RESUME="capture.RESUME",STOP="capture.STOP";
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private MediaProjection projection;private VirtualDisplay display;private ImageReader reader;
 private DataManager manager;private CaptureProfile profile;private ScreenRecognizer recognizer;
 private final ObservationGate gate=new ObservationGate();
 private volatile boolean paused=true,busy=false,ended=false;private volatile int generation=0;private long lastFrame=0;private int density;
 @Override public IBinder onBind(Intent i){return null;}
 @Override public void onCreate(){super.onCreate();manager=DataManager.get(this);}
 private PendingIntent action(String label){return PendingIntent.getService(this,label.hashCode(),new Intent(this,CaptureService.class).setAction(label),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
 private Notification notification(){NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("screen_capture","授权屏幕识别",NotificationManager.IMPORTANCE_LOW));return new Notification.Builder(this,"screen_capture").setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("V0.6 · "+(paused?"识别已暂停":"正在识别授权画面")).setContentText("仅识别可见信息；可随时停止屏幕捕获").setOngoing(true).addAction(new Notification.Action.Builder(null,paused?"恢复":"暂停",action(paused?RESUME:PAUSE)).build()).addAction(new Notification.Action.Builder(null,"停止",action(STOP)).build()).build();}
 private void foreground(){if(Build.VERSION.SDK_INT>=29)startForeground(206,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);else startForeground(206,notification());}
 @Override public int onStartCommand(Intent intent,int flags,int id){
  if(intent==null){stopSelf();return START_NOT_STICKY;}
  String action=intent.getAction();
  if(STOP.equals(action)){stopSelf();return START_NOT_STICKY;}
  if(PAUSE.equals(action)||RESUME.equals(action)){
   if(projection==null){stopSelf();return START_NOT_STICKY;}
   paused=PAUSE.equals(action);generation++;gate.reset();if(display!=null)display.setSurface(paused?null:reader.getSurface());
   manager.captureStatus(paused?"已暂停（采集表面已断开）":"已恢复授权屏幕识别");foreground();return START_NOT_STICKY;
  }
  if(!START.equals(action)||projection!=null)return START_NOT_STICKY;
  try{
   if(!getSharedPreferences("capture_profile",0).getBoolean("configured",false))throw new IllegalStateException("请先校准画面区域和玩家别名");
   int result=intent.getIntExtra("result",Activity.RESULT_CANCELED);
   Intent consent=Build.VERSION.SDK_INT>=33?intent.getParcelableExtra("consent",Intent.class):intent.getParcelableExtra("consent");
   if(result!=Activity.RESULT_OK||consent==null)throw new SecurityException("未获得用户屏幕捕获授权");
   profile=CaptureProfile.load(this);paused=false;foreground();
   projection=getSystemService(MediaProjectionManager.class).getMediaProjection(result,consent);
   projectionActive=projection!=null;
   if(projection==null)throw new SecurityException("授权已失效，请重新授权");
   projection.registerCallback(new MediaProjection.Callback(){
    @Override public void onStop(){manager.captureStatus("系统已撤销屏幕捕获，需重新授权");stopSelf();}
    @Override public void onCapturedContentResize(int w,int h){int[] size=CaptureDimensions.fit(w,h);if(display!=null&&reader!=null&&(reader.getWidth()!=size[0]||reader.getHeight()!=size[1]))resize(size[0],size[1]);}
   },main);
   DisplayMetrics dm=new DisplayMetrics();getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(dm);density=dm.densityDpi;
   int[] size=CaptureDimensions.fit(dm.widthPixels,dm.heightPixels);createReader(size[0],size[1]);
   display=projection.createVirtualDisplay("AuthorizedCardPoolCapture",size[0],size[1],density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,main);
   manager.captureStatus("正在识别授权画面 · OCR本地处理 · 连续两帧确认");
  }catch(Exception e){manager.captureStatus("识别未启动："+e.getMessage());stopSelf();}
  return START_NOT_STICKY;
 }
 private void resize(int w,int h){generation++;display.setSurface(null);reader.close();createReader(w,h);display.resize(w,h,density);if(!paused)display.setSurface(reader.getSurface());}
 private void createReader(int w,int h){reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);reader.setOnImageAvailableListener(source->{
  Image image=null;
  try{image=source.acquireLatestImage();if(image==null||paused||ended||busy||SystemClock.elapsedRealtime()-lastFrame<1500)return;
   Image.Plane plane=image.getPlanes()[0];ByteBuffer buffer=plane.getBuffer();int stride=plane.getPixelStride(),padding=plane.getRowStride()-stride*image.getWidth();
   Bitmap padded=Bitmap.createBitmap(image.getWidth()+padding/stride,image.getHeight(),Bitmap.Config.ARGB_8888);padded.copyPixelsFromBuffer(buffer);Bitmap frame=Bitmap.createBitmap(padded,0,0,image.getWidth(),image.getHeight());if(frame!=padded)padded.recycle();
   busy=true;lastFrame=SystemClock.elapsedRealtime();final int session=generation;final long timestamp=System.currentTimeMillis();
   worker.execute(()->{try{
    if(recognizer==null)recognizer=new ScreenRecognizer(this);ObservationGate.Frame f=recognizer.read(frame,profile,manager.snapshot(),timestamp,()->!ended&&!paused&&generation==session);
    main.post(()->{if(!ended&&!paused&&generation==session&&!manager.isSimulating())manager.observe(f,gate.accepted(f));});
   }catch(Exception e){main.post(()->{if(!ended&&generation==session)manager.captureStatus("识别失败（未覆盖数据）："+e.getMessage());});}finally{frame.recycle();busy=false;}});
  }catch(Exception e){manager.captureStatus("截图不可用："+e.getMessage());}finally{if(image!=null)image.close();}
 },main);}
 @Override public void onDestroy(){projectionActive=false;ended=true;paused=true;generation++;main.removeCallbacksAndMessages(null);
  if(display!=null){display.release();display=null;}if(reader!=null){reader.close();reader=null;}if(projection!=null){MediaProjection p=projection;projection=null;p.stop();}
  worker.execute(()->{if(recognizer!=null)recognizer.close();});worker.shutdown();manager.captureStatus("已停止采集；历史观察保留，新采集需要重新授权");stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
 }
}
