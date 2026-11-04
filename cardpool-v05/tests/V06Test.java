import com.example.cardpool.*;
import java.util.*;import java.nio.file.*;
public class V06Test {
 static int checks=0;static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static ObservationGate.Frame f(int player,long time,String key,String value,double confidence){ObservationGate.Frame f=new ObservationGate.Frame();f.player=player;f.time=time;f.identityConfidence=.99;f.fields.put(key,new ObservationGate.Token(value,confidence));return f;}
 public static void main(String[] args)throws Exception{
  int[] large=CaptureDimensions.fit(3840,2160);ok(large[0]==1920&&large[1]==1080,"4K capture bounded");int[] portrait=CaptureDimensions.fit(1080,2400);ok(portrait[0]==864&&portrait[1]==1920,"portrait preserves aspect");ok(CaptureDimensions.fit(640,360)[0]==640,"small capture not upscaled");boolean invalidDimensions=false;try{CaptureDimensions.fit(0,100);}catch(IllegalArgumentException e){invalidDimensions=true;}ok(invalidDimensions,"invalid capture dimensions rejected");
  ObservationGate gate=new ObservationGate();ok(gate.accepted(f(0,1,"gold","50",.99)).isEmpty(),"single frame waits");ok(gate.accepted(f(0,2,"gold","50",.99)).size()==1,"stable two frames");
  ok(gate.accepted(f(1,3,"gold","50",.99)).isEmpty(),"switch identity waits");ok(gate.accepted(f(1,4,"gold","50",.5)).isEmpty(),"low confidence rejected");ok(gate.accepted(f(1,5,"gold","50",.99)).isEmpty(),"low confidence breaks agreement");ok(gate.accepted(f(1,6,"gold","50",.99)).size()==1,"reconfirmed");
  ObservationGate shopGate=new ObservationGate();
  ok(shopGate.accepted(f(2,10,"shop.0","ahri",.99)).isEmpty(),"shop needs two consecutive frames");
  ok(shopGate.accepted(f(2,11,"shop.0","ahri",.99)).containsKey("shop.0"),"stable shop candidate confirmed");
  ok(shopGate.accepted(f(2,12,"shop.0","未匹配：阿狸",0)).isEmpty(),"unmatched shop candidate never confirmed");
  SeasonRosterCsv.Parsed csv=SeasonRosterCsv.parse("# season=测试赛季\nid,name,cost,total\nahri,阿狸,4,0\nshen,慎,1,29\n");
  String helperRoster=Files.readString(Path.of("app/src/main/assets/season_profiles/jcc_helper_set16_unverified.csv"));
  SeasonRosterCsv.Parsed candidate=SeasonRosterCsv.parse(helperRoster);
  int[] costCounts=new int[6];for(GameState.Hero hero:candidate.heroes.values())costCounts[hero.cost]++;
  ok(candidate.heroes.size()==114,"helper Set16 roster has 114 deduplicated entries");
  ok(Arrays.equals(costCounts,new int[]{0,14,20,23,28,29}),"helper Set16 roster cost buckets");
  ok(candidate.heroes.containsKey("11402")&&candidate.heroes.get("11402").name.equals("慎"),"original helper hero ids retained");
  ok(candidate.missingTotals==114,"helper roster has no invented pool totals");
  String legendRoster=Files.readString(Path.of("app/src/main/assets/season_profiles/jcc_legend_20261104_normal_candidate.csv"));
  SeasonRosterCsv.Parsed legend=SeasonRosterCsv.parse(legendRoster);
  ok(legend.heroes.size()==119&&legend.missingTotals==119,"legend 2026-11-04 candidate adds five with unknown totals");
  ok(legend.heroes.containsKey("legend20261104_smolder")&&legend.heroes.get("legend20261104_smolder").cost==7,"new 7-cost units accepted, not mislabeled 5 cost");
  ok(legend.heroes.containsKey("11402")&&legend.heroes.get("11402").cost==1,"helper native IDs preserved in legend candidate");
  GameState legendState=new GameState();SeasonRosterCsv.apply(legendState,legend);legendState.rosterComplete=true;
  ok(Prediction.explain(legendState).contains("解锁"),"legend model must refuse unsupported unlock odds");
  ok(SeasonAudit.report(legendState).contains("7费"),"legend audit reports special hero tier");


  ok(csv.heroes.size()==2&&csv.missingTotals==1,"CSV roster parses heroes and flags unknown totals");
  GameState imported=new GameState();SeasonRosterCsv.apply(imported,csv);
  ok(!SeasonAudit.totalsKnown(imported)&&SeasonAudit.missingTotals(imported)==1,"unknown pool totals fail audit");
  ok(imported.poolText().contains("总量未核实"),"unknown pool never rendered as zero remaining");
  imported.rosterComplete=true;
  ok(Prediction.explain(imported).contains("未核实的英雄总张数"),"prediction blocked despite user-complete tick when totals missing");
  imported.rosterComplete=false;
  ok(SeasonAudit.report(imported).contains("不能用未知总量推算")||SeasonAudit.report(imported).contains("不完整"),"audit explains incomplete season data");

  ok(imported.heroes.size()==2&&!imported.rosterComplete&&imported.target.equals("ahri"),"CSV import does not claim complete verified rules");
  imported.players[0].units.add(new GameState.Holding("ahri",1,1,""));
  boolean preserved=false;try{SeasonRosterCsv.apply(imported,SeasonRosterCsv.parse("shen,慎,1,29"));}catch(IllegalArgumentException ignored){preserved=true;}
  ok(preserved&&imported.heroes.containsKey("ahri"),"CSV missing held hero cannot delete existing data");

  GameState timeline=new GameState();timeline.season="英雄联盟传奇普通模式";
  for(String id:new String[]{"s_a","s_b","s_c"})timeline.heroes.put(id,new GameState.Hero(id,id,1,0));
  ObservationGate.Frame firstShop=new ObservationGate.Frame();firstShop.time=100;firstShop.player=0;firstShop.identityConfidence=.99;
  Map<String,ObservationGate.Token> complete=new LinkedHashMap<>();
  for(int slot=0;slot<5;slot++)complete.put("shop."+slot,new ObservationGate.Token("s_a",.99));
  Map<String,ObservationGate.Token> partial=new LinkedHashMap<>(complete);partial.remove("shop.4");
  ok(!ShopTimeline.record(timeline,firstShop,partial),"partial shops must not enter timeline");
  ok(ShopTimeline.record(timeline,firstShop,complete),"full confirmed shop logged");
  firstShop.time=101;ok(!ShopTimeline.record(timeline,firstShop,complete),"unchanged shop not double counted");
  complete.put("shop.4",new ObservationGate.Token("s_b",.99));
  firstShop.time=102;ok(ShopTimeline.record(timeline,firstShop,complete),"changed visible shop logged");
  ok(ShopTimeline.current(timeline).size()==2,"two confirmed distinct shop snapshots retained");
  ok(ShopTimeline.report(timeline).contains("不是实际刷新次数"),"report never invents paid refreshes");
  ok(ShopTimeline.csv(timeline).contains("slot5_name"),"CSV export contains per-slot observations");
  firstShop.player=1;firstShop.time=103;firstShop.identityConfidence=.5;
  ok(!ShopTimeline.record(timeline,firstShop,complete),"untrusted identity not logged");
  firstShop.identityConfidence=.99;
  ok(ShopTimeline.record(timeline,firstShop,complete),"independent player shop timeline");
  for(int i=0;i<170;i++){
   ObservationGate.Frame next=new ObservationGate.Frame();next.player=0;next.time=1000+i;next.identityConfidence=.99;
   int value=i;
   for(int slot=0;slot<5;slot++){String id=new String[]{"s_a","s_b","s_c"}[value%3];value/=3;complete.put("shop."+slot,new ObservationGate.Token(id,.99));}
   ShopTimeline.record(timeline,next,complete);
  }
  ok(timeline.capture.shopHistory.size()==ShopTimeline.MAX_EVENTS,"history bound prevents unbounded on-device storage");
  GameState copyWithHistory=timeline.copy();ok(copyWithHistory.capture.shopHistory.size()==ShopTimeline.MAX_EVENTS,"history survives serialization");
  ObservationGate.Frame ambiguous=f(1,7,"gold","99",.99);ambiguous.identityConfidence=.5;ok(gate.accepted(ambiguous).isEmpty(),"ambiguous identity never overwrites");ok(gate.accepted(f(1,6,"gold","50",.99)).isEmpty(),"out of order rejected");
  ok(ObservationGate.scalar("level","12")==null,"level range");ok(ObservationGate.scalar("gold","5O")==null,"no digit hallucination");ok(ObservationGate.scalar("hp","100")==100,"valid numeric");ok(ObservationGate.scalar("level","0")==null,"unknown level not observed");
  Map<String,String> aliases=new LinkedHashMap<>();aliases.put("Alice","0");aliases.put("A lice","1");ok(ObservationGate.exact("Alice",aliases)==null,"ambiguous normalized alias rejected");
  GameState old=GameState.decode(Files.readString(Path.of("tests/fixtures/v05-state.txt")).trim());ok(old.capture!=null&&old.players[3].gold==42&&old.observed("old")==3,"genuine V0.5 deserialization keeps data");
  V05Test.Prefs prefs=new V05Test.Prefs(); // V05 tests run in another JVM; singleton is fresh.
  android.content.Context context=new android.content.Context(){public android.content.SharedPreferences getSharedPreferences(String n,int mode){return prefs;}};
  DataManager m=DataManager.get(context);m.mutate(s->{s.heroes.put("a",new GameState.Hero("a","Ahri",4,10));s.target="a";s.capture.autoMerge=true;s.capture.autoBoard=true;});
  int[] main={0},overlay={0};m.add(()->main[0]++);m.add(()->overlay[0]++);
  for(int player=0;player<8;player++){
   ObservationGate.Frame frame=f(player,100+player,"gold",String.valueOf(20+player),.99);Map<String,ObservationGate.Token> accepted=new LinkedHashMap<>(frame.fields);accepted.put("board.0.hero",new ObservationGate.Token("a",.99));accepted.put("board.0.star",new ObservationGate.Token("1",.99));accepted.put("shop.0",new ObservationGate.Token("a",.99));m.observe(frame,accepted);
  }
  ok(main[0]==8&&overlay[0]==8,"eight player observations notify both surfaces");for(int i=0;i<8;i++)ok(m.snapshot().players[i].gold==20+i,"correct eight-way association");ok(m.snapshot().observed("a")==8,"board lower bounds count once, shop not held");
  long manualRevision=m.snapshot().manualRevision;m.observe(f(7,999,"gold","80",.99),Collections.singletonMap("gold",new ObservationGate.Token("80",.99)));ok(m.snapshot().manualRevision==manualRevision,"live observations do not invalidate a manual edit dialog");
  m.mutate(s->s.players[0].gold=88);m.observe(f(0,1000,"gold","40",.99),Collections.singletonMap("gold",new ObservationGate.Token("40",.99)));ok(m.snapshot().players[0].gold==88,"manual correction locked");
  m.observe(f(1,1001,"gold","44",.4),Collections.singletonMap("gold",new ObservationGate.Token("44",.4)));ok(m.snapshot().players[1].gold==21,"low confidence cannot overwrite correct record");
  ObservationGate.Frame unknown=f(-1,1002,"gold","99",.99);m.observe(unknown,unknown.fields);ok(m.snapshot().players[0].gold==88,"unobserved player not invented");
  CaptureData.Value history=new CaptureData.Value("Ahri",.99,0);ok(history.describe(40000).contains("历史"),"old observations labeled historical");
  int[] a=new int[1024],b=new int[1024];for(int i=0;i<a.length;i++)a[i]=b[i]=(i%2==0?0xffffff:0xff0000);ok(TemplateMatcher.score(a,b)==1,"exact screenshot template");Arrays.fill(b,0);ok(TemplateMatcher.score(a,b)==0,"blank template rejected");
  System.out.println("PASS V0.6: "+checks+" confidence/identity, V0.5 migration, eight-player sync, manual locks, board/shop separation and template checks");
 }
}
