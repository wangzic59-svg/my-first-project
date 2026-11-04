package com.example.cardpool;
import java.util.*;

/**
 * Offline CSV roster import. The app never assumes that an APK version implies a season.
 * Format: id,name,cost,total; zero total means unverified, not an assumed pool size.
 */
public final class SeasonRosterCsv {
 private SeasonRosterCsv(){}
 public static final class Parsed {
  public final LinkedHashMap<String,GameState.Hero> heroes=new LinkedHashMap<>();
  public final String season;
  public final int missingTotals;
  private Parsed(String name,int missing){season=name;missingTotals=missing;}
 }
 private static List<String> columns(String line,int number){
  List<String> parts=new ArrayList<>();StringBuilder field=new StringBuilder();boolean quoted=false;
  char delimiter=line.indexOf('\t')>=0?'\t':',';
  for(int i=0;i<line.length();i++){
   char c=line.charAt(i);
   if(c=='"'){
    if(quoted&&i+1<line.length()&&line.charAt(i+1)=='"'){field.append('"');i++;}
    else quoted=!quoted;
   }else if(c==delimiter&&!quoted){parts.add(field.toString().trim());field.setLength(0);}
   else field.append(c);
  }
  if(quoted)throw new IllegalArgumentException("第"+number+"行CSV引号不完整");
  parts.add(field.toString().trim());return parts;
 }
 public static Parsed parse(String csv){
  if(csv==null||csv.isEmpty()||csv.length()>512000)throw new IllegalArgumentException("CSV为空或超过500KB");
  String[] lines=csv.replace("\uFEFF","").split("\\r?\\n");
  String name="";LinkedHashMap<String,GameState.Hero> heroes=new LinkedHashMap<>();
  Set<String> normalizedNames=new HashSet<>();int missing=0;
  for(int n=0;n<lines.length;n++){
   String row=lines[n].trim();if(row.isEmpty())continue;
   if(row.startsWith("#")){
    if(row.startsWith("# season="))name=row.substring("# season=".length()).trim();
    continue;
   }
   List<String> fields=columns(row,n+1);
   if(fields.size()==4&&(fields.get(0).equalsIgnoreCase("id")||fields.get(0).equals("英雄ID")))continue;
   if(fields.size()!=4)throw new IllegalArgumentException("第"+(n+1)+"行需要4列：英雄ID,名称,费用,总张数");
   String id=fields.get(0),title=fields.get(1);
   if(!id.matches("[A-Za-z0-9_.-]{1,64}")||title.isEmpty()||title.length()>64)throw new IllegalArgumentException("第"+(n+1)+"行英雄ID或名称不合法");
   int cost,total;try{cost=Integer.parseInt(fields.get(2));total=Integer.parseInt(fields.get(3));}catch(NumberFormatException e){throw new IllegalArgumentException("第"+(n+1)+"行费用或总张数不是整数");}
   if(cost<1||cost>7||total<0||total>10000)throw new IllegalArgumentException("第"+(n+1)+"行费用应为1–7，总张数应为0–10000");
   if(heroes.containsKey(id))throw new IllegalArgumentException("第"+(n+1)+"行英雄ID重复："+id);
   if(!normalizedNames.add(title.replaceAll("\\s+","")))throw new IllegalArgumentException("第"+(n+1)+"行英雄名称重复："+title);
   heroes.put(id,new GameState.Hero(id,title,cost,total));if(total==0)missing++;
   if(heroes.size()>500)throw new IllegalArgumentException("英雄数量超过500");
  }
  if(heroes.isEmpty())throw new IllegalArgumentException("CSV没有有效英雄");
  Parsed result=new Parsed(name,missing);result.heroes.putAll(heroes);return result;
 }
 /** Preserve holdings and any referenced heroes. Never silently delete recorded data. */
 public static void apply(GameState state,Parsed csv){
  Set<String> used=new HashSet<>(state.unattributed.keySet());
  for(GameState.Player player:state.players)for(GameState.Holding h:player.units)used.add(h.hero);
  for(CaptureData.Player player:state.capture.players)for(GameState.Holding h:player.board)used.add(h.hero);
  for(String id:used)if(!csv.heroes.containsKey(id))throw new IllegalArgumentException("已有记录引用英雄 "+id+"，新CSV未包含；为防丢失已取消导入");
  state.heroes.clear();state.heroes.putAll(csv.heroes);
  if(!state.heroes.containsKey(state.target))state.target=state.heroes.keySet().iterator().next();
  state.capture.targets.retainAll(state.heroes.keySet());
  if(!csv.season.isEmpty())state.season=csv.season;
  state.rosterComplete=false; // User must explicitly verify all heroes, pool totals, and mode-specific odds.
 }
}
