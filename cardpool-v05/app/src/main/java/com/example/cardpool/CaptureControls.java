package com.example.cardpool;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.util.*;
public final class CaptureControls {
 public static void profile(Activity a){LinearLayout form=Editors.form(a);EditText json=Editors.field(a,form,"坐标为相对画面的 left,top,right,bottom（0–1）。先填唯一玩家名 → 0–7 编号。商店:shop.0…4；棋盘:board.0.hero / .star / .equipment。只填实际可见区域。",a.getSharedPreferences("capture_profile",0).getString("json",CaptureProfile.EXAMPLE),true);
  ScrollView scroll=new ScrollView(a);scroll.addView(form);AlertDialog d=new AlertDialog.Builder(a).setTitle("校准截图布局（没有通用官方模板）").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create();d.show();d.getButton(-1).setOnClickListener(v->{try{new CaptureProfile(Editors.value(json));
   a.stopService(new Intent(a,CaptureService.class));if(!a.getSharedPreferences("capture_profile",0).edit().putString("json",Editors.value(json)).putBoolean("configured",true).commit())throw new IOException("保存失败");d.dismiss();Toast.makeText(a,"布局已保存，需重新授权开始识别",Toast.LENGTH_LONG).show();
  }catch(Exception e){Toast.makeText(a,e.getMessage(),Toast.LENGTH_LONG).show();}});
 }
 public static void template(Activity a,Uri uri){
  try{
   Bitmap image=read(a,uri);CaptureProfile profile=CaptureProfile.load(a);LinearLayout f=Editors.form(a);
   EditText key=Editors.field(a,f,"区域键，例如 board.0.hero / board.0.star / board.0.equipment","board.0.hero",false);
   EditText id=Editors.field(a,f,"英雄填现有英雄ID；星级填1–3；装备填名称","",false);
   AlertDialog d=new AlertDialog.Builder(a).setTitle("从选定截图裁剪本地匹配模板").setView(f).setNegativeButton("取消",(q,n)->image.recycle()).setPositiveButton("保存",null).create();d.setOnCancelListener(x->image.recycle());d.show();d.getButton(-1).setOnClickListener(v->{try{
    String k=Editors.value(key),label=Editors.value(id);if(!profile.regions.containsKey(k)||!(k.endsWith(".hero")||k.endsWith(".star")||k.endsWith(".equipment")))throw new IllegalArgumentException("请先配置对应棋盘区域");
    if(k.endsWith(".hero")&&!DataManager.get(a).snapshot().heroes.containsKey(label))throw new IllegalArgumentException("英雄ID不存在");if(k.endsWith(".star")&&!label.matches("[123]"))throw new IllegalArgumentException("星级为1–3");if(label.isEmpty())throw new IllegalArgumentException("标签不能为空");
    if(Math.abs((double)image.getWidth()/image.getHeight()-profile.aspect)>profile.aspect*.04)throw new IllegalArgumentException("截图比例不符");Bitmap crop=CaptureProfile.crop(image,profile.regions.get(k));TemplateStore.add(a,k,label,crop);if(crop!=image)crop.recycle();image.recycle();d.dismiss();
   }catch(Exception e){Toast.makeText(a,e.getMessage(),Toast.LENGTH_LONG).show();}});
  }catch(Exception e){Toast.makeText(a,"截图读取失败："+e.getMessage(),Toast.LENGTH_LONG).show();}
 }
 static Bitmap read(Activity a,Uri uri)throws IOException{
  BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;try(InputStream in=a.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
  if(options.outWidth<=0||options.outHeight<=0||(long)options.outWidth*options.outHeight>20000000)throw new IOException("图片无效或超过2000万像素");
  options.inJustDecodeBounds=false;options.inSampleSize=1;while((long)(options.outWidth/options.inSampleSize)*(options.outHeight/options.inSampleSize)>4000000)options.inSampleSize*=2;
  try(InputStream in=a.getContentResolver().openInputStream(uri)){Bitmap b=BitmapFactory.decodeStream(in,null,options);if(b==null)throw new IOException("不支持图片格式");return b;}
 }
 public static void auto(Activity a,DataManager m){GameState s=m.snapshot();new AlertDialog.Builder(a).setTitle("自动合并高置信数值？").setMessage("仅金币/等级/血量在身份明确、连续两帧且置信分≥90%时自动更新。已有手动修正默认锁定；解锁后允许高置信识别更新。棋盘与装备只写观察层，不覆盖可能包含备战席的完整持牌。")
  .setNegativeButton("关闭自动合并",(d,n)->m.mutate(x->x.capture.autoMerge=false))
  .setNeutralButton("解锁当前玩家数值",(d,n)->m.mutate(x->x.capture.players[x.selectedPlayer].manualLocks.removeAll(java.util.Arrays.asList("gold","level","hp"))))
  .setPositiveButton("开启",(d,n)->m.mutate(x->x.capture.autoMerge=true)).show();
 }
 public static void board(Activity a,DataManager m){new AlertDialog.Builder(a).setTitle("可见棋盘参与卡池估算").setMessage("只用连续两帧确认的英雄+星级作为可见持牌下界，与完整手动记录取较大值，不重复相加。未识别装备不猜测。已手动修正的玩家持牌仍受锁定保护；历史观察可能过期。")
 .setNegativeButton("关闭",(d,n)->m.mutate(x->x.capture.autoBoard=false))
 .setNeutralButton("解锁所选玩家持牌",(d,n)->m.mutate(x->x.capture.players[x.selectedPlayer].manualLocks.remove("units")))
 .setPositiveButton("启用",(d,n)->m.mutate(x->x.capture.autoBoard=true)).show();}
 public static void targets(Activity a,DataManager m){GameState s=m.snapshot();String[] ids=s.heroes.keySet().toArray(new String[0]),names=new String[ids.length];boolean[] selected=new boolean[ids.length];Set<String> next=new LinkedHashSet<>(s.capture.targets);for(int i=0;i<ids.length;i++){names[i]=s.heroes.get(ids[i]).name;selected[i]=next.contains(ids[i]);}
  new AlertDialog.Builder(a).setTitle("多目标英雄").setMultiChoiceItems(names,selected,(d,i,on)->{if(on)next.add(ids[i]);else next.remove(ids[i]);}).setPositiveButton("保存",(d,n)->m.mutate(x->{x.capture.targets.clear();for(String id:next)if(x.heroes.containsKey(id))x.capture.targets.add(id);})).setNegativeButton("取消",null).show();
 }
 public static String predictions(GameState s){if(s.capture.targets.isEmpty())return Prediction.explain(s);StringBuilder b=new StringBuilder();for(String target:s.capture.targets)if(s.heroes.containsKey(target)){GameState copy=s.copy();copy.target=target;b.append(Prediction.explain(copy)).append("\n\n");}return b.toString();}
}
