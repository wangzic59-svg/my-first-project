package com.example.cardpool;
import android.graphics.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.android.gms.tasks.Tasks;
import java.util.*;
import java.util.concurrent.TimeUnit;
/** Runs only on capture worker, all OCR stays on device with the bundled model. */
public final class ScreenRecognizer implements AutoCloseable {
 private final android.content.Context context;
 public ScreenRecognizer(android.content.Context c){context=c.getApplicationContext();}
 private final TextRecognizer ocr=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
 public ObservationGate.Frame read(Bitmap image,CaptureProfile profile,GameState game,long timestamp)throws Exception {
  if(Math.abs((double)image.getWidth()/image.getHeight()-profile.aspect)>profile.aspect*.04)throw new IllegalArgumentException("画面比例与校准不符，请重新校准或重授权");
  ObservationGate.Frame f=new ObservationGate.Frame();f.time=timestamp;
  Map<String,String> heroes=new LinkedHashMap<>();for(GameState.Hero h:game.heroes.values())heroes.put(h.name,h.id);
  for(Map.Entry<String,RectF> entry:profile.regions.entrySet()){
   Bitmap crop=CaptureProfile.crop(image,entry.getValue());
   try{
    String region=entry.getKey();
    ObservationGate.Token template=TemplateStore.match(context,region,crop);
    if(template!=null&&!region.equals("player")){
     boolean valid=region.startsWith("shop.")||region.endsWith(".hero")?game.heroes.containsKey(template.text):region.endsWith(".star")?template.text.matches("[123]"):region.endsWith(".equipment");
     if(valid){f.fields.put(region,template);continue;}
    }
    Text result=Tasks.await(ocr.process(InputImage.fromBitmap(crop,0)),10,TimeUnit.SECONDS);
    String raw=result.getText().replaceAll("\\s+","");double confidence=1;int elements=0;
    for(Text.TextBlock block:result.getTextBlocks())for(Text.Line line:block.getLines())for(Text.Element element:line.getElements()){confidence=Math.min(confidence,element.getConfidence());elements++;}
    if(elements==0)confidence=0;
    String key=entry.getKey(),value=raw;
    if(key.equals("player")){
     String id=ObservationGate.exact(raw,profile.players);if(id!=null){f.player=Integer.parseInt(id);f.identityConfidence=confidence;}continue;
    }
    if(key.equals("gold")||key.equals("level")||key.equals("hp")){if(ObservationGate.scalar(key,raw)==null)continue;}
    else if(key.startsWith("shop.")||key.endsWith(".hero")){value=ObservationGate.exact(raw,heroes);if(value==null)continue;}
    else if(key.endsWith(".star")){if(raw.matches("[★*]{1,3}"))value=String.valueOf(raw.length());else if(!raw.matches("[123]"))continue;}
    else if(key.endsWith(".equipment")){value=ObservationGate.exact(raw,profile.equipment);if(value==null)continue;}
    f.fields.put(key,new ObservationGate.Token(value,confidence));
   }finally{if(crop!=image)crop.recycle();}
  }
  return f;
 }
 public void close(){ocr.close();}
}
