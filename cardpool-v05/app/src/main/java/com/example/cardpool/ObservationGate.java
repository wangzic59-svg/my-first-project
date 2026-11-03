package com.example.cardpool;
import java.util.*;
/** Pure-Java confidence and temporal gate. Does not infer invisible or next-shop data. */
public final class ObservationGate {
 public static final double MIN=.90, IDENTITY_MIN=.92;
 public static final class Token {
  public final String text;public final double confidence;
  public Token(String t,double c){text=t;confidence=Double.isFinite(c)?Math.max(0,Math.min(1,c)):0;}
 }
 public static final class Frame {
  public int player=-1;public double identityConfidence=0;public long time;
  public final Map<String,Token> fields=new LinkedHashMap<>();
 }
 private String signature="";private int streak=0;private long last=0;
 public void reset(){signature="";streak=0;last=0;}
 public Map<String,Token> accepted(Frame f){
  Map<String,Token> out=new LinkedHashMap<>();
  if(f.time<=last)return out;if(f.time-last>10000){signature="";streak=0;previous.clear();}last=f.time;
  if(f.player<0||f.player>=8||f.identityConfidence<IDENTITY_MIN){signature="";streak=0;return out;}
  // Identity must be stable, while each field independently needs agreement.
  String identity=String.valueOf(f.player);
  if(!identity.equals(signature)){signature=identity;streak=1;previous.clear();}else streak++;
  for(Map.Entry<String,Token> e:f.fields.entrySet()){
   Token t=e.getValue();String key=e.getKey();
   if(t.confidence>=MIN&&!t.text.trim().isEmpty()&&t.text.equals(previous.get(key))&&streak>=2)out.put(key,t);
  }
  previous.clear();for(Map.Entry<String,Token> e:f.fields.entrySet())if(e.getValue().confidence>=MIN)previous.put(e.getKey(),e.getValue().text);
  return out;
 }
 private final Map<String,String> previous=new HashMap<>();
 public static Integer scalar(String key,String text){
  if(!text.matches("[0-9]{1,5}"))return null;
  int n=Integer.parseInt(text);int max=key.equals("level")?11:key.equals("hp")?1000:10000;
  return n<0||n>max||(key.equals("level")&&n==0)?null:n;
 }
 public static String exact(String raw,Map<String,String> aliases){
  String n=raw.replaceAll("\\s+","");String match=null;
  for(Map.Entry<String,String> e:aliases.entrySet())if(e.getKey().replaceAll("\\s+","").equals(n)){
   if(match!=null&&!match.equals(e.getValue()))return null;match=e.getValue();
  }return match;
 }
}
