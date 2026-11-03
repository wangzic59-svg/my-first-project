package com.example.cardpool;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class ScreenRecognitionTest {
 private Context target,tests;
 @Before public void setup(){target=InstrumentationRegistry.getInstrumentation().getTargetContext();tests=InstrumentationRegistry.getInstrumentation().getContext();}
 private String asset(String name)throws Exception{try(InputStream in=tests.getAssets().open("fixtures/"+name)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
 @Test public void ocrAccuracyOnSyntheticScreenshots()throws Exception{
  CaptureProfile profile=new CaptureProfile(asset("profile.json"));JSONArray expected=new JSONArray(asset("expected.json"));GameState game=new GameState();game.heroes.put("ahri",new GameState.Hero("ahri","Ahri",4,10));
  int correct=0,total=0,identity=0,semantic=0,highConfidence=0;
  try(ScreenRecognizer engine=new ScreenRecognizer(target)){
   for(int i=0;i<expected.length();i++){
    JSONObject row=expected.getJSONObject(i);Bitmap image;try(InputStream in=tests.getAssets().open("fixtures/"+row.getString("file"))){image=BitmapFactory.decodeStream(in);}
    ObservationGate.Frame f=engine.read(image,profile,game,1000+i);image.recycle();if(f.player==row.getInt("player"))identity++;
    ObservationGate gate=new ObservationGate();gate.accepted(f);f.time++;Map<String,ObservationGate.Token> confirmed=gate.accepted(f);
    for(String key:new String[]{"gold","level","hp"}){if(confirmed.containsKey(key))highConfidence++;total++;if(f.fields.containsKey(key)&&f.fields.get(key).text.equals(row.getString(key)))correct++;}
    for(String key:new String[]{"shop.0","board.0.hero","board.0.star","board.0.equipment"})if(f.fields.containsKey(key))semantic++;
   }
  }
  String report="Synthetic numeric exact accuracy="+correct+"/"+total+"; confidence-gated stable scalar fields="+highConfidence+"/"+total+"; identity="+identity+"/"+expected.length()+"; semantic fields="+semantic+"/"+(expected.length()*4)+". No real-game accuracy claim.";
  android.util.Log.i("OCR_ACCURACY",report);try(FileOutputStream out=new FileOutputStream(new File(target.getExternalFilesDir(null),"ocr-accuracy.txt"))){out.write(report.getBytes(StandardCharsets.UTF_8));}
  assertTrue(report,correct>=total*.9);assertTrue(report,highConfidence>=total*.8);assertTrue(report,identity>=expected.length()*.9);assertTrue(report,semantic>=expected.length()*4*.8);
 }
 @Test public void eightPlayerUpdatesReachActualOverlay()throws Exception{
  Instrumentation ins=InstrumentationRegistry.getInstrumentation();Activity activity=ins.startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  ins.getUiAutomation().executeShellCommand("appops set com.example.cardpool SYSTEM_ALERT_WINDOW allow").close();
  DataManager m=DataManager.get(target);ins.runOnMainSync(()->{m.simulation(false);m.mutate(s->{s.capture.autoMerge=true;for(int i=0;i<8;i++)s.capture.players[i].manualLocks.remove("gold");});target.startForegroundService(new Intent(target,OverlayService.class));});
  try{
   for(int attempt=0;attempt<100&&OverlayService.renderedRevision<0;attempt++)Thread.sleep(100);assertTrue("overlay created",OverlayService.renderedRevision>=0);
   for(int player=0;player<8;player++){
    final int p=player;ins.runOnMainSync(()->{ObservationGate.Frame f=new ObservationGate.Frame();f.player=p;f.identityConfidence=.99;f.time=System.currentTimeMillis()+p;f.fields.put("gold",new ObservationGate.Token(String.valueOf(50+p),.99));m.observe(f,f.fields);});
    ins.waitForIdleSync();assertEquals(50+player,m.snapshot().players[player].gold);assertEquals("overlay renders latest revision",m.snapshot().revision,OverlayService.renderedRevision);
   }
  }finally{ins.runOnMainSync(()->{target.stopService(new Intent(target,OverlayService.class));activity.finish();});}
 }
 @Test public void deniedConsentCannotStartProjection()throws Exception{
  Instrumentation ins=InstrumentationRegistry.getInstrumentation();target.getSharedPreferences("capture_profile",0).edit().putBoolean("configured",true).putString("json",asset("profile.json")).commit();
  Activity activity=ins.startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  ins.runOnMainSync(()->target.startForegroundService(new Intent(target,CaptureService.class).setAction(CaptureService.START).putExtra("result",Activity.RESULT_CANCELED)));
  Thread.sleep(700);ins.waitForIdleSync();assertFalse(CaptureService.projectionActive);ins.runOnMainSync(activity::finish);
 }
}
