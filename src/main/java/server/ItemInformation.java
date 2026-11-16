package server;

import client.inventory.Equip;
import java.util.List;
import java.util.Map;

public class ItemInformation {

    public List<Integer> scrollReqs = null, questItems = null, incSkill = null;
    public short slotMax, itemMakeLevel;
    public Equip eq = null;
    public Map<String, Integer> equipStats;
    public double price = 0.0;
    public int itemId,
            wholePrice,
            monsterBook,
            stateChange,
            meso,
            questId,
            totalprob,
            replaceItem,
            mob,
            cardSet,
            create,
            flag,
            npc;
    public String name, desc, msg, replaceMsg, afterImage, script;
    public byte karmaEnabled;
    public List<StructRewardItem> rewardItems = null;
    public Map<Integer, Map<String, Integer>> equipIncs = null;

    public record EquipAddition(String key, String subKey, String value) {}

    public List<EquipAddition> equipAdditions = null;
}
