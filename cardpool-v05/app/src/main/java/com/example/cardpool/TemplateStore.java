package com.example.cardpool;
import android.content.*;
import android.graphics.*;
import org.json.*;
import java.io.*;
import java.util.*;
public final class TemplateStore {
 public static String index(Context c){return c.getSharedPreferences("capture_profile",0).getString("templates","[]");}
 public static void add(Context c,String key,String id,Bitmap crop)throws Exception{
  String file="template_"+UUID.randomUUID()+".png";File target=new File(c.getFilesDir(),file);
  try(FileOutputStream out=new FileOutputStream(target)){crop.compress(Bitmap.CompressFormat.PNG,100,out);}
  JSONArray all=new JSONArray(index(c));all.put(new JSONObject().put("key",key).put("id",id).put("file",file));
  if(!c.getSharedPreferences("capture_profile",0).edit().putString("templates",all.toString()).commit()){target.delete();throw new IOException("模板保存失败");}
 }
 public static int[] pixels(Bitmap b){Bitmap s=Bitmap.createScaledBitmap(b,32,32,true);int[] p=new int[1024];s.getPixels(p,0,32,0,0,32,32);if(s!=b)s.recycle();return p;}
 public static ObservationGate.Token match(Context c,String key,Bitmap crop)throws Exception{
  JSONArray all=new JSONArray(index(c));double best=0,second=0;String id=null;Map<String,Double> scores=new HashMap<>();int[] input=pixels(crop);
  for(int i=0;i<all.length();i++){JSONObject row=all.getJSONObject(i);if(!key.equals(row.getString("key")))continue;String file=row.getString("file");if(!file.matches("template_[a-zA-Z0-9-]+\\.png"))continue;
   Bitmap template=BitmapFactory.decodeFile(new File(c.getFilesDir(),file).getPath());if(template==null)continue;double score=TemplateMatcher.score(input,pixels(template));template.recycle();scores.put(row.getString("id"),Math.max(score,scores.getOrDefault(row.getString("id"),0.)));
  }
  for(Map.Entry<String,Double> e:scores.entrySet())if(e.getValue()>best){second=best;best=e.getValue();id=e.getKey();}else second=Math.max(second,e.getValue());
  return best>=.97&&best-second>=.04?new ObservationGate.Token(id,best):null;
 }
}
