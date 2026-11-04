package com.example.cardpool;
import android.content.Context;
import android.graphics.*;
import com.googlecode.tesseract.android.TessBaseAPI;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;
/** Synchronous, offline Tesseract recognition with actual engine confidence values. */
public final class ScreenRecognizer implements AutoCloseable {
 private final Context context;private final File data;private TessBaseAPI latin,chinese;
 private static final String[] LANGS={"eng","chi_sim"};
 private static final String[] HASHES={"7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2","a5fcb6f0db1e1d6d8522f39db4e848f05984669172e584e8d76b6b3141e1f730"};
 public ScreenRecognizer(Context c)throws Exception{
  context=c.getApplicationContext();data=new File(context.getFilesDir(),"ocr-v06");File dir=new File(data,"tessdata");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("OCR模型目录创建失败");
  synchronized(ScreenRecognizer.class){for(int i=0;i<LANGS.length;i++){
   File model=new File(dir,LANGS[i]+".traineddata");if(model.exists()&&HASHES[i].equals(hash(model)))continue;
   File temp=new File(dir,LANGS[i]+".tmp");try(InputStream in=context.getAssets().open("tessdata/"+LANGS[i]+".traineddata");FileOutputStream out=new FileOutputStream(temp)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
   if(!HASHES[i].equals(hash(temp)))throw new IOException("OCR模型校验失败");if(model.exists()&&!model.delete())throw new IOException("无法替换OCR模型");if(!temp.renameTo(model))throw new IOException("无法保存OCR模型");
  }}
 }
 private static String hash(File file)throws Exception{MessageDigest h=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)h.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:h.digest())s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
 private TessBaseAPI engine(boolean han){
  TessBaseAPI api=han?chinese:latin;if(api!=null)return api;api=new TessBaseAPI();if(!api.init(data.getPath(),han?"chi_sim+eng":"eng",TessBaseAPI.OEM_LSTM_ONLY)){api.recycle();throw new IllegalStateException("本地OCR模型初始化失败");}
  api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_LINE);if(han)chinese=api;else latin=api;return api;
 }
 private static boolean hasHan(Map<String,String> aliases){for(String text:aliases.keySet())for(int i=0;i<text.length();i++)if(Character.UnicodeScript.of(text.charAt(i))==Character.UnicodeScript.HAN)return true;return false;}
 private ObservationGate.Token recognize(Bitmap crop,boolean han,boolean numeric){
  TessBaseAPI api=engine(han);api.setVariable("tessedit_char_whitelist",numeric?"0123456789":"");
  int[] pixels=new int[crop.getWidth()*crop.getHeight()];crop.getPixels(pixels,0,crop.getWidth(),0,0,crop.getWidth(),crop.getHeight());long sum=0;
  for(int p:pixels)sum+=(((p>>16)&255)*77+((p>>8)&255)*150+(p&255)*29)>>8;boolean invert=sum/(double)pixels.length<128;
  for(int i=0;i<pixels.length;i++){int p=pixels[i],v=((((p>>16)&255)*77+((p>>8)&255)*150+(p&255)*29)>>8);if(invert)v=255-v;pixels[i]=0xff000000|(v<<16)|(v<<8)|v;}
  Bitmap prepared=Bitmap.createBitmap(pixels,crop.getWidth(),crop.getHeight(),Bitmap.Config.ARGB_8888);
  // Tiny single-line shop labels need more pixels; upscale the prepared crop without inventing recognition confidence.
  Bitmap enlarged=prepared;
  if(prepared.getHeight()<56){int scale=Math.min(3,Math.max(2,(int)Math.ceil(56.0/prepared.getHeight())));enlarged=Bitmap.createScaledBitmap(prepared,prepared.getWidth()*scale,prepared.getHeight()*scale,true);}
  try{api.setImage(enlarged);String text=api.getUTF8Text();double confidence=api.meanConfidence()/100.;api.clear();ObservationGate.Token best=new ObservationGate.Token(text==null?"":text.replaceAll("\\s+",""),confidence);
   // Retry the original pixels only for weak/empty results; confidence remains the engine's measured score.
   if(best.text.isEmpty()||best.confidence<ObservationGate.MIN){api.setImage(crop);String second=api.getUTF8Text();double c=api.meanConfidence()/100.;api.clear();String t=second==null?"":second.replaceAll("\\s+","");
    if(!best.text.isEmpty()&&!t.isEmpty()&&!best.text.equals(t))return new ObservationGate.Token(best.text,0);
    if(c>best.confidence)best=new ObservationGate.Token(t,c);
   }return best;
  }finally{if(enlarged!=prepared)enlarged.recycle();prepared.recycle();}
 }
 public ObservationGate.Frame read(Bitmap image,CaptureProfile profile,GameState game,long timestamp)throws Exception{return read(image,profile,game,timestamp,()->true);}
 public ObservationGate.Frame read(Bitmap image,CaptureProfile profile,GameState game,long timestamp,BooleanSupplier active)throws Exception{
  if(Math.abs((double)image.getWidth()/image.getHeight()-profile.aspect)>profile.aspect*.04)throw new IllegalArgumentException("画面比例与校准不符，请重新校准或重授权");
  ObservationGate.Frame f=new ObservationGate.Frame();f.time=timestamp;Map<String,String> heroes=new LinkedHashMap<>();Set<String> ambiguous=new HashSet<>();
  for(GameState.Hero h:game.heroes.values()){String name=h.name.replaceAll("\\s+","");if(heroes.containsKey(name)){heroes.remove(name);ambiguous.add(name);}else if(!ambiguous.contains(name))heroes.put(name,h.id);}
  for(Map.Entry<String,RectF> entry:profile.regions.entrySet()){
   if(!active.getAsBoolean())throw new InterruptedException("采集会话已暂停、停止或改变");String key=entry.getKey();Bitmap crop=CaptureProfile.crop(image,entry.getValue());
   try{
    ObservationGate.Token matched=TemplateStore.match(context,key,crop);
    if(matched!=null&&!key.equals("player")){
     boolean valid=key.startsWith("shop.")||key.endsWith(".hero")?game.heroes.containsKey(matched.text):key.endsWith(".star")?matched.text.matches("[123]"):key.endsWith(".equipment");
     if(valid){f.fields.put(key,matched);continue;}
    }
    boolean numeric=key.equals("gold")||key.equals("level")||key.equals("hp")||key.endsWith(".star");Map<String,String> vocabulary=key.equals("player")?profile.players:key.endsWith(".equipment")?profile.equipment:heroes;
    ObservationGate.Token token=recognize(crop,!numeric&&(key.startsWith("shop.")||hasHan(vocabulary)),numeric);String raw=token.text,value=raw;
    if(key.equals("player")){String id=ObservationGate.exact(raw,profile.players);if(id!=null){f.player=Integer.parseInt(id);f.identityConfidence=token.confidence;}continue;}
    if(key.equals("gold")||key.equals("level")||key.equals("hp")){if(ObservationGate.scalar(key,raw)==null)continue;}
    else if(key.startsWith("shop.")||key.endsWith(".hero")){value=ShopDiagnostic.canonicalExact(raw,heroes);if(value==null){
      // Keep rejected candidates visible to the screenshot-review UI, but never auto-accept them.
      if(key.startsWith("shop.")&&!raw.isEmpty())f.fields.put(key,new ObservationGate.Token("未匹配："+raw,0));
      continue;
    }}
    else if(key.endsWith(".star")){if(!raw.matches("[123]"))continue;}
    else if(key.endsWith(".equipment")){value=ObservationGate.exact(raw,profile.equipment);if(value==null)continue;}
    f.fields.put(key,new ObservationGate.Token(value,token.confidence));
   }finally{if(crop!=image)crop.recycle();}
  }
  if(f.player<0&&profile.shopOwner>=0){
    // A manually selected owner is valid ONLY for current visible shop entries.
    // It must never authorize guessed board, health, gold or level updates.
    f.fields.entrySet().removeIf(e->!e.getKey().matches("shop\\.[0-4]"));
    f.player=profile.shopOwner;f.identityConfidence=1.0;
  }
  return f;
 }
 public void close(){if(latin!=null){latin.recycle();latin=null;}if(chinese!=null){chinese.recycle();chinese=null;}}
}
