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
