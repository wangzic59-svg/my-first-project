package com.example.cardpool;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Logs only five complete, double-frame-confirmed *visible* shop changes.
 * A change may be a refresh, automatic stage reset or other game effect:
 * no game-process injection, no inferred refresh button presses and no RNG prediction.
 */
public final class ShopTimeline {
    private ShopTimeline(){}
    public static final int MAX_EVENTS=160;
    public static boolean record(GameState game,ObservationGate.Frame frame,Map<String,ObservationGate.Token> accepted){
        if(frame.player<0||frame.player>=8||frame.identityConfidence<ObservationGate.IDENTITY_MIN)return false;
        String[] ids=new String[5];
        for(int k=0;k<5;k++){
            ObservationGate.Token token=accepted.get("shop."+k);
            if(token==null||token.confidence<ObservationGate.MIN||!game.heroes.containsKey(token.text))return false;
            ids[k]=token.text;
        }
        game.capture.ensureHistory();
        List<CaptureData.ShopEvent> history=game.capture.shopHistory;
        for(int i=history.size()-1;i>=0;i--){
            CaptureData.ShopEvent previous=history.get(i);
            if(previous.player!=frame.player||!previous.season.equals(game.season))continue;
            if(frame.time<=previous.time||Arrays.equals(previous.ids,ids))return false;
            break;
        }
        history.add(new CaptureData.ShopEvent(frame.time,frame.player,game.season,ids));
        if(history.size()>MAX_EVENTS)history.remove(0);
        return true;
    }
    public static List<CaptureData.ShopEvent> current(GameState game){
        game.capture.ensureHistory();
        List<CaptureData.ShopEvent> out=new ArrayList<>();
        for(CaptureData.ShopEvent e:game.capture.shopHistory)
            if(e.season.equals(game.season)&&valid(e,game))out.add(e);
        return out;
    }
    private static boolean valid(CaptureData.ShopEvent e,GameState s){
        if(e==null||e.ids==null||e.ids.length!=5||e.player<0||e.player>7)return false;
        for(String id:e.ids)if(!s.heroes.containsKey(id))return false;
        return true;
    }
    private static String timestamp(long millis){
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.ROOT).format(new Date(millis));
    }
    public static String report(GameState game){
        List<CaptureData.ShopEvent> all=current(game);
        if(all.isEmpty())return "本赛季暂无完整的五格商店观察。需授权屏幕捕获，配置五个商店识别区域和当前商店归属玩家；连续两帧识别一致后才计入。";
        int[] byPlayer=new int[8],byCost=new int[8];Map<String,Integer> appearances=new HashMap<>();
        for(CaptureData.ShopEvent e:all){
            byPlayer[e.player]++;
            for(String id:e.ids){GameState.Hero h=game.heroes.get(id);if(h.cost>=1&&h.cost<=7)byCost[h.cost]++;appearances.put(id,appearances.getOrDefault(id,0)+1);}
        }
        StringBuilder out=new StringBuilder("实时商店观察统计（只记录完整且变化的可见五格）\n");
        out.append("已观察不同商店：").append(all.size()).append("组，共 ").append(5*all.size()).append("个可见格（不是实际刷新次数）\n");
        for(int player=0;player<8;player++)if(byPlayer[player]>0)out.append("玩家").append(player+1).append("：").append(byPlayer[player]).append("组  ");
        out.append("\n费用分布：");
        for(int tier=1;tier<=7;tier++)if(byCost[tier]>0)out.append(tier).append("费 ").append(byCost[tier]).append(" (")
            .append(String.format(Locale.ROOT,"%.1f%%",byCost[tier]*100.0/(all.size()*5))).append(")  ");
        List<Map.Entry<String,Integer>> ranked=new ArrayList<>(appearances.entrySet());
        ranked.sort((a,b)->{int n=Integer.compare(b.getValue(),a.getValue());return n!=0?n:game.heroes.get(a.getKey()).name.compareTo(game.heroes.get(b.getKey()).name);});
        out.append("\n出现次数较多：");
        for(int i=0;i<Math.min(8,ranked.size());i++){
            Map.Entry<String,Integer> x=ranked.get(i);
            out.append(game.heroes.get(x.getKey()).name).append("×").append(x.getValue()).append(" ");
        }
        out.append("\n最近五组：\n");
        for(int i=all.size()-1;i>=Math.max(0,all.size()-5);i--){
            CaptureData.ShopEvent e=all.get(i);out.append(timestamp(e.time)).append(" P").append(e.player+1).append("：");
            for(int j=0;j<5;j++)out.append(j==0?"":" / ").append(game.heroes.get(e.ids[j]).name);
            out.append("\n");
        }
        out.append("提示：更换商店不一定是付费刷新；相同五格可能再次出现但不会重复记数。样本受识别率、观看时间、英雄解锁和商店机制影响；不能据此读取隐藏卡池、推断官方出卡概率或预测下次结果。");
        return out.toString();
    }
    private static String cell(String value){
        return "\"" + (value==null?"":value).replace("\"","\"\"") + "\"";
    }
    public static String csv(GameState game){
        StringBuilder out=new StringBuilder("timestamp_local,player,season");
        for(int i=1;i<=5;i++)out.append(",slot").append(i).append("_id,slot").append(i).append("_name");
        out.append("\n");
        for(CaptureData.ShopEvent event:current(game)){
            out.append(cell(timestamp(event.time))).append(",").append(event.player+1).append(",").append(cell(event.season));
            for(String id:event.ids){
                out.append(",").append(cell(id)).append(",").append(cell(game.heroes.get(id).name));
            }
            out.append("\n");
        }
        return out.toString();
    }
}
