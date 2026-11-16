/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tools;

import client.ISkill;
import client.MapleJob;
import client.SkillFactory;
import client.inventory.MapleInventoryType;
import constants.GameConstants;
import handling.RecvPacketOpcode;
import handling.SendPacketOpcode;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import provider.MapleData;
import provider.MapleDataProviderFactory;
import provider.MapleDataTool;
import server.ItemInformation;
import server.MapleItemInformationProvider;
import server.ServerProperties;
import server.life.MapleLifeFactory;
import server.life.MapleMonsterInformationProvider;
import server.quest.MapleQuest;

/**
 * @author Pungin
 */
public class SearchGenerator {

    public enum SearchType {
        Item(1),
        NPC(2),
        Map(3),
        Mob(4),
        Quest(5),
        Skill(6),
        Job(7),
        Srv_Header(8),
        Cli_Header(9),
        Hair(10),
        Face(11),
        Unknown;

        private int value;

        SearchType() {
            this.value = 0;
        }

        SearchType(int value) {
            this.value = value;
        }

        public final int getValue() {
            return value;
        }

        public static String nameOf(int value) {
            for (SearchType type : SearchType.values()) {
                if (type.getValue() == value) {
                    return type.name();
                }
            }
            return "未知";
        }
    }

    public static final int Item = SearchType.Item.getValue();
    public static final int NPC = SearchType.NPC.getValue();
    public static final int Map = SearchType.Map.getValue();
    public static final int Mob = SearchType.Mob.getValue();
    public static final int Quest = SearchType.Quest.getValue();
    public static final int Skill = SearchType.Skill.getValue();
    public static final int Job = SearchType.Job.getValue();
    public static final int Srv_Header = SearchType.Srv_Header.getValue();
    public static final int Cli_Header = SearchType.Cli_Header.getValue();
    public static final int Hair = SearchType.Hair.getValue();
    public static final int Face = SearchType.Face.getValue();
    private static final Map<SearchType, Map<Integer, String>> searchs = new HashMap();

    public static Map<Integer, String> getSearchs(int type) {
        return getSearchs(SearchType.valueOf(SearchType.nameOf(type)));
    }

    public static Map<Integer, String> getSearchs(SearchType type) {
        Map<Integer, String> value = null;
        if (searchs.containsKey(type)) {
            return searchs.get(type);
        } else if ((type == SearchType.Face || type == SearchType.Hair)
                && searchs.containsKey(SearchType.Item)) {
            value = searchs.get(SearchType.Item);
        }

        Map<Integer, String> values = new TreeMap();

        switch (type) {
            case Item:
                for (ItemInformation itemInfo :
                        MapleItemInformationProvider.getInstance().getAllItems()) {
                    values.put(itemInfo.itemId, itemInfo.name);
                }
                break;
            case NPC:
                values = MapleLifeFactory.getNPCNames();
                break;
            case Map:
                MapleData data =
                        MapleDataProviderFactory.getDataProvider(
                                        new File(
                                                ServerProperties.getProperty("tms.Wzpath")
                                                        + "/String.wz"))
                                .getData("Map.img");
                for (MapleData mapAreaData : data.getChildren()) {
                    for (MapleData mapIdData : mapAreaData.getChildren()) {
                        values.put(
                                Integer.parseInt(mapIdData.getName()),
                                "'"
                                        + MapleDataTool.getString(
                                                mapIdData.getChildByPath("streetName"), "無名稱")
                                        + " : "
                                        + MapleDataTool.getString(
                                                mapIdData.getChildByPath("mapName"), "無名稱")
                                        + "'");
                    }
                }
                break;
            case Mob:
                for (Map.Entry<Integer, String> mob :
                        MapleMonsterInformationProvider.getInstance().getAllMonsters().entrySet()) {
                    values.put(mob.getKey(), mob.getValue());
                }
                break;
            case Quest:
                for (MapleQuest quest : MapleQuest.getAllInstances()) {
                    values.put(quest.getId(), quest.getName());
                }
                break;
            case Skill:
                {
                    for (ISkill skill : SkillFactory.getAllSkills()) {
                        values.put(skill.getId(), skill.getName());
                    }
                    break;
                }
            case Job:
                for (MapleJob job : MapleJob.values()) {
                    values.put(job.getId(), job.name());
                }
                break;
            case Srv_Header:
                for (SendPacketOpcode send : SendPacketOpcode.values()) {
                    values.put((int) send.getValue(), send.name());
                }
                break;
            case Cli_Header:
                for (RecvPacketOpcode recv : RecvPacketOpcode.values()) {
                    values.put((int) recv.getValue(), recv.name());
                }
                break;
            case Hair:
            case Face:
                if (value == null) {
                    value = new TreeMap<>((v1, v2) -> v1.compareTo(v2));
                    for (ItemInformation itemInfo :
                            MapleItemInformationProvider.getInstance().getAllItems()) {
                        value.put(itemInfo.itemId, itemInfo.name);
                    }
                    searchs.put(SearchType.Item, value);
                }
                MapleInventoryType iType =
                        type == SearchType.Hair ? MapleInventoryType.HAIR : MapleInventoryType.FACE;
                for (Map.Entry<Integer, String> entry : value.entrySet()) {
                    if (GameConstants.getInventoryType(entry.getKey()) == iType) {
                        values.put(entry.getKey(), entry.getValue());
                    }
                }
                break;
        }

        searchs.put(type, values);
        return values;
    }

    public static Map<Integer, String> getSearchData(int type, String search) {
        return getSearchData(SearchType.valueOf(SearchType.nameOf(type)), search);
    }

    public static Map<Integer, String> getSearchData(SearchType type, String search) {
        Map<Integer, String> values = new TreeMap();
        Map<Integer, String> ss = getSearchs(type);

        for (int i : ss.keySet()) {
            if (String.valueOf(i).toLowerCase().contains(search.toLowerCase())
                    || ss.get(i).toLowerCase().contains(search.toLowerCase())) {
                values.put(i, ss.get(i));
            }
        }

        return values;
    }

    public static String searchData(int type, String search) {
        return searchData(SearchType.valueOf(SearchType.nameOf(type)), search);
    }

    public static String searchData(SearchType type, String search) {
        Map<Integer, String> ss = getSearchData(type, search);
        List<String> ret = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        switch (type) {
            case Item:
                if (!search.isEmpty()) {
                    for (Integer i : ss.keySet()) {
                        if (MapleItemInformationProvider.getInstance().itemExists(i)) {
                            ret.add("\r\n#L" + i + "##z" + i + "# (" + i + ")#l");
                            // ret.add("\r\n#L" + i + "##i" + i + ":# #z" + i + "#(" + i + ")#l");
                        }
                    }
                } else {
                    sb.append("\r\n請輸入Item名稱....");
                }
                break;
            case NPC:
                for (Map.Entry<Integer, String> i : ss.entrySet()) {
                    ret.add("\r\n#L" + i.getKey() + "##p" + i.getKey() + "#(" + i.getKey() + ")#l");
                }
                break;
            case Map:
                for (Integer i : ss.keySet()) {
                    ret.add("\r\n#L" + i + "##m" + i + "#(" + i + ")#l");
                }
                break;
            case Mob:
                for (Integer i : ss.keySet()) {
                    ret.add("\r\n#L" + i + "##o" + i + "#(" + i + ")#l");
                }
                break;
            case Quest:
                for (Map.Entry<Integer, String> i : ss.entrySet()) {
                    ret.add("\r\n#L" + i.getKey() + "#" + i.getValue() + "(" + i.getKey() + ")#l");
                }
                break;
            case Skill:
                for (Map.Entry<Integer, String> i : ss.entrySet()) {
                    ret.add(
                            "\r\n#L"
                                    + i.getKey()
                                    + "##s"
                                    + i.getKey()
                                    + "#"
                                    + i.getValue()
                                    + "("
                                    + i.getKey()
                                    + ")#l");
                }
                break;
            case Job:
                for (Map.Entry<Integer, String> i : ss.entrySet()) {
                    ret.add("\r\n#L" + i.getKey() + "#" + i.getValue() + "(" + i.getKey() + ")#l");
                }
                break;
            case Srv_Header:
            case Cli_Header:
                for (Map.Entry<Integer, String> i : ss.entrySet()) {
                    ret.add(
                            "\r\n"
                                    + i.getValue()
                                    + " 值: "
                                    + i.getKey()
                                    + " 16進制: "
                                    + HexTool.getOpcodeToString(i.getKey()));
                }
                break;
            default:
                sb.append("對不起, 這個檢索類型不被支援");
        }

        if (ret.size() > 0) {
            for (String singleRetItem : ret) {
                if (sb.length() > 3500) {
                    sb.append("\r\n後面還有很多搜尋結果, 但已經無法顯示更多");
                    break;
                }
                sb.append(singleRetItem);
            }
        }

        StringBuilder sbs = new StringBuilder();
        if (!sb.toString().isEmpty() && !sb.toString().equalsIgnoreCase("對不起, 這個檢索指令不被支援")) {
            sbs.append("<<類型: ")
                    .append(type.name())
                    .append(" | 搜尋訊息: ")
                    .append(search)
                    .append(">>");
        }
        sbs.append(sb);
        if (sbs.toString().isEmpty()) {
            sbs.append("搜尋不到此").append(type.name());
        }
        return sbs.toString();
    }

    public static boolean foundData(int type, String search) {
        return !getSearchData(type, search).isEmpty();
    }
}
