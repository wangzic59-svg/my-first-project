package com.example.cardpool;
import org.json.*;
import android.content.Context;
import android.graphics.*;
import java.util.*;
/** Explicit normalized ROIs; no assumed official layout. Profile includes unique 8-player aliases. */
public final class CaptureProfile {
 public double aspect; public int shopOwner=-1; public final LinkedHashMap<String,RectF> regions=new LinkedHashMap<>();
 public final LinkedHashMap<String,String> players=new LinkedHashMap<>(),equipment=new LinkedHashMap<>();
 public CaptureProfile(String json)throws JSONException {
  JSONObject root=new JSONObject(json);aspect=root.getDouble("aspect");shopOwner=root.optInt("shopOwner",-1);if(shopOwner<-1||shopOwner>7)throw new JSONException("商店绑定玩家必须是 -1 或 0–7");if(aspect<.3||aspect>4)throw new JSONException("aspect 无效");
  JSONObject aliases=root.getJSONObject("players");Set<String> identities=new HashSet<>();Iterator<String> names=aliases.keys();while(names.hasNext()){String name=names.next();int index=aliases.getInt(name);if(name.trim().isEmpty()||index<0||index>7||!identities.add(name.replaceAll("\\s+","")))throw new JSONException("玩家别名为空、重复或编号无效");players.put(name,String.valueOf(index));}
  JSONObject boxes=root.getJSONObject("regions");Iterator<String> keys=boxes.keys();while(keys.hasNext()){
   String key=keys.next();if(!key.matches("player|gold|level|hp|shop\\.[0-9]+|board\\.[0-9]+\\.(hero|star|equipment)"))throw new JSONException("未知区域 "+key);
   JSONArray a=boxes.getJSONArray(key);if(a.length()!=4)throw new JSONException("区域需要四个归一化坐标");float l=(float)a.getDouble(0),t=(float)a.getDouble(1),r=(float)a.getDouble(2),b=(float)a.getDouble(3);
   if(!Float.isFinite(l)||!Float.isFinite(t)||!Float.isFinite(r)||!Float.isFinite(b)||l<0||t<0||r>1||b>1||l>=r||t>=b)throw new JSONException("区域超出画面");regions.put(key,new RectF(l,t,r,b));
  }
  if(!regions.containsKey("player")||regions.size()>100)throw new JSONException("必须配置玩家身份区域，最多100个区域");
  JSONObject eq=root.optJSONObject("equipment");if(eq!=null){Iterator<String> it=eq.keys();while(it.hasNext()){String k=it.next();equipment.put(k,eq.getString(k));}}
 }
 public static CaptureProfile load(Context c)throws JSONException{return new CaptureProfile(c.getSharedPreferences("capture_profile",0).getString("json",EXAMPLE));}
 public static final String EXAMPLE="{\"aspect\":1.777778,\"players\":{\"请填写玩家名\":0},\"equipment\":{},\"regions\":{\"player\":[0.02,0.02,0.28,0.1],\"gold\":[0.40,0.02,0.48,0.10],\"level\":[0.52,0.02,0.60,0.10],\"hp\":[0.65,0.02,0.73,0.10]}}";
 /** Preview-only preset derived from two user-supplied 1536 x 695 shop screenshots. Not a universal game layout. */
 public static final String SHOP_PRESET_1536 = "{\"aspect\":2.210071942446043,\"shopOwner\":-1,\"players\":{},\"equipment\":{},\"regions\":{\"player\":[0.190755,0.526619,0.246094,0.564029],\"gold\":[0.927734,0.843165,0.957682,0.905036],\"level\":[0.114583,0.926619,0.130859,0.978417],\"shop.0\":[0.177083,0.405755,0.272786,0.453237],\"shop.1\":[0.339844,0.405755,0.435547,0.453237],\"shop.2\":[0.502604,0.405755,0.599609,0.453237],\"shop.3\":[0.665365,0.405755,0.76237,0.453237],\"shop.4\":[0.826823,0.405755,0.923828,0.453237]}}";
 public static Bitmap crop(Bitmap frame,RectF box){int x=Math.max(0,(int)(box.left*frame.getWidth())),y=Math.max(0,(int)(box.top*frame.getHeight()));int w=Math.max(1,Math.min(frame.getWidth()-x,(int)((box.right-box.left)*frame.getWidth()))),h=Math.max(1,Math.min(frame.getHeight()-y,(int)((box.bottom-box.top)*frame.getHeight())));return Bitmap.createBitmap(frame,x,y,w,h);}
}
