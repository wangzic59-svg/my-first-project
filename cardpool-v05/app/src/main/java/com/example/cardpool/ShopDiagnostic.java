package com.example.cardpool;

import java.text.Normalizer;
import java.util.*;

/**
 * Screenshot-only diagnostics. Suggestions never enter the recorded shop or pool.
 * Report contains text, hero IDs and OCR scores, not screenshots or private game data.
 */
public final class ShopDiagnostic {
    private ShopDiagnostic() {}

    private static String clean(String text) {
        if(text==null)return "";
        String compact=Normalizer.normalize(text,Normalizer.Form.NFKC)
            .replaceAll("[\\s·•・.．\\-—_（）()]+","");
        return compact.length()>64?compact.substring(0,64):compact;
    }

    // One code point wrong/omitted/added: only a review hint, NEVER an auto-match.
    static int distanceAtMostOne(String x,String y) {
        int[] a=x.codePoints().toArray(),b=y.codePoints().toArray();
        if(Math.abs(a.length-b.length)>1)return 2;
        int i=0,j=0,edits=0;
        while(i<a.length&&j<b.length) {
            if(a[i]==b[j]){i++;j++;continue;}
            if(++edits>1)return 2;
            if(a.length>b.length)i++;
            else if(a.length<b.length)j++;
            else{i++;j++;}
        }
        edits+=(a.length-i)+(b.length-j);
        return edits;
    }

    public static List<String> suggestions(String raw,GameState game) {
        String key=clean(raw);
        if(key.codePointCount(0,key.length())<2)return Collections.emptyList();
        ArrayList<String> candidates=new ArrayList<>();
        for(GameState.Hero hero:game.heroes.values()){
            String name=clean(hero.name);
            if(distanceAtMostOne(key,name)==1&&!candidates.contains(hero.name))
                candidates.add(hero.name);
        }
        if(candidates.size()>3)return Collections.emptyList();
        Collections.sort(candidates);
        return candidates;
    }

    public static String report(GameState game,ObservationGate.Frame frame,Set<String> configuredRegions) {
        StringBuilder out=new StringBuilder("非ROOT · 五格商店截图诊断（未写入对局）\n");
        out.append("当前候选字典：").append(game.heroes.size()).append("个英雄，")
           .append(game.season).append("\n");
        if(frame.player<0)out.append("商店归属玩家：未识别。可在「绑定当前商店归属玩家」中手动指定。\n");
        else out.append("商店归属玩家：").append(frame.player+1)
            .append(" · 身份置信 ").append((int)Math.round(frame.identityConfidence*100)).append("%\n");
        int good=0;
        for(int slot=0;slot<5;slot++){
            String key="shop."+slot;
            out.append(slot+1).append("号格：");
            if(configuredRegions==null||!configuredRegions.contains(key)){
                out.append("未配置识别区域\n");continue;
            }
            ObservationGate.Token token=frame.fields.get(key);
            if(token==null||token.text.isEmpty()){
                out.append("没有有效的OCR结果（检查位置、遮挡、字体和截图方向）\n");continue;
            }
            GameState.Hero hero=game.heroes.get(token.text);
            if(hero!=null){
                out.append(hero.name).append("（").append(hero.cost).append("费，ID ").append(hero.id).append("）")
                   .append(" · OCR ").append((int)Math.round(token.confidence*100)).append("%");
                if(token.confidence>=ObservationGate.MIN){out.append(" · 候选合格");good++;}
                else out.append(" · 置信度过低，不会自动记录");
                out.append("\n");continue;
            }
            String raw=token.text.startsWith("未匹配：")?token.text.substring("未匹配：".length()):token.text;
            out.append("未匹配：").append(raw.length()>64?raw.substring(0,64):raw)
               .append("（不会自动识别为其他英雄）\n");
            List<String> suggestions=suggestions(raw,game);
            if(!suggestions.isEmpty())out.append("   仅供人工核对的相近名称：")
                .append(String.join("、",suggestions)).append("\n");
        }
        out.append("五格可识别：").append(good).append("/5。");
        if(good==5)out.append(" 单张截图检查合格；正式记录仍须连续两帧稳定一致。");
        else out.append(" 请优先检查未匹配格的区域和英雄名单。");
        out.append("\n备注：OCR百分比分值不是实际识别准确率；相近名称只是提示，永不自动填牌。");
        return out.toString();
    }
}
