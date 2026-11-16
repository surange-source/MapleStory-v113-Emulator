package server;

import client.MapleCharacter;
import client.MapleClient;
import client.inventory.Equip;
import client.inventory.IItem;
import client.inventory.ItemFlag;
import client.inventory.MapleInventoryType;
import constants.GameConstants;
import constants.ItemConstants;
import database.DatabaseConnection;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import provider.MapleData;
import provider.MapleDataDirectoryEntry;
import provider.MapleDataFileEntry;
import provider.MapleDataProvider;
import provider.MapleDataProviderFactory;
import provider.MapleDataTool;
import provider.WzXML.MapleDataType;
import server.ItemInformation.EquipAddition;
import server.StructSetItem.SetItem;
import tools.Pair;
import tools.StringUtil;

public class MapleItemInformationProvider {
    private static final Logger LOG = LoggerFactory.getLogger(MapleItemInformationProvider.class);

    private static final class Holder {
        private static final MapleItemInformationProvider INSTANCE =
                new MapleItemInformationProvider();
    }

    protected final MapleDataProvider etcData =
            MapleDataProviderFactory.getDataProvider(
                    new File(ServerProperties.getProperty("tms.Wzpath") + "/Etc.wz"));
    protected final MapleDataProvider itemData =
            MapleDataProviderFactory.getDataProvider(
                    new File(ServerProperties.getProperty("tms.Wzpath") + "/Item.wz"));
    protected final MapleDataProvider equipData =
            MapleDataProviderFactory.getDataProvider(
                    new File(ServerProperties.getProperty("tms.Wzpath") + "/Character.wz"));
    protected final MapleDataProvider stringData =
            MapleDataProviderFactory.getDataProvider(
                    new File(ServerProperties.getProperty("tms.Wzpath") + "/String.wz"));
    protected final Map<Integer, ItemInformation> dataCache =
            new TreeMap<>((v1, v2) -> v1.compareTo(v2));
    protected final Map<Integer, Integer> mobIds = new TreeMap<>();

    // 透過屬性名稱，直接賦予這三個欄位真正的業務語意
    public record MonsterBookSetDetail(
            int setId, // 原本的 left
            List<Integer> cardItemIds, // 原本的 mid (從 .mid.contains 看得出來這是卡片/物品清單)
            List<Integer> rewardIds // 原本的 right (推測是獎勵或其它清單)
            ) {}

    protected final Map<Integer, MonsterBookSetDetail> monsterBookSets = new TreeMap<>();
    protected final MapleData cashStringData = stringData.getData("Cash.img");
    protected final MapleData consumeStringData = stringData.getData("Consume.img");
    protected final MapleData eqpStringData = stringData.getData("Eqp.img");
    protected final MapleData etcStringData = stringData.getData("Etc.img");
    protected final MapleData insStringData = stringData.getData("Ins.img");
    protected final MapleData petStringData = stringData.getData("Pet.img");
    protected final Map<Integer, List<Integer>> scrollReqCache = new HashMap<>();
    protected final Map<Integer, Short> slotMaxCache = new HashMap<>();
    protected final Map<Integer, List<StructPotentialItem>> potentialCache = new HashMap<>();
    protected final Map<Integer, MapleStatEffect> itemEffects = new HashMap<>();
    protected final Map<Integer, Map<String, Integer>> equipStatsCache = new HashMap<>();
    protected final Map<Integer, Map<String, Byte>> itemMakeStatsCache = new HashMap<>();
    protected final Map<Integer, Short> itemMakeLevel = new HashMap<>();
    protected final Map<Integer, Equip> equipCache = new HashMap<>();
    protected final Map<Integer, Double> priceCache = new HashMap<>();
    protected final Map<Integer, Integer> wholePriceCache = new HashMap<>();
    protected final Map<Integer, Integer> projectileWatkCache = new HashMap<>();
    protected final Map<Integer, Integer> monsterBookID = new HashMap<>();
    protected final Map<Integer, String> nameCache = new HashMap<>();
    protected final Map<Integer, String> descCache = new HashMap<>();
    protected final Map<Integer, String> msgCache = new HashMap<>();
    protected final Map<Integer, MapleInventoryType> inventoryTypeCache = new HashMap<>();
    protected final Map<Integer, Short> petFlagInfo = new HashMap<>();
    protected final Map<Integer, Integer> petLimitLifeInfo = new HashMap<>();
    protected final Map<Integer, Integer> petLifeInfo = new HashMap<>();

    protected final Map<Integer, Map<String, Integer>> SkillStatsCache = new HashMap<>();
    protected final Map<Integer, Byte> consumeOnPickupCache = new HashMap<>();
    protected final Map<Integer, Boolean> dropRestrictionCache = new HashMap<>();
    protected final Map<Integer, Boolean> accCache = new HashMap<>();
    protected final Map<Integer, Boolean> pickupRestrictionCache = new HashMap<>();
    protected final Map<Integer, Integer> stateChangeCache = new HashMap<>();
    protected final Map<Integer, Integer> mesoCache = new HashMap<>();
    protected final Map<Integer, Boolean> notSaleCache = new HashMap<>();
    protected final Map<Integer, Integer> karmaEnabledCache = new HashMap<>();
    protected final Map<Integer, Boolean> isQuestItemCache = new HashMap<>();
    protected final Map<Integer, Boolean> blockPickupCache = new HashMap<>();
    protected final Map<Integer, List<Integer>> petsCanConsumeCache = new HashMap<>();
    protected final Map<Integer, Boolean> logoutExpireCache = new HashMap<>();
    protected final Map<Integer, List<Pair<Integer, Integer>>> summonMobCache = new HashMap<>();
    //    protected final List<Pair<Integer, String>> itemNameCache = new ArrayList<>();

    protected final Map<Integer, Map<Integer, Map<String, Integer>>> equipIncsCache =
            new HashMap<>();
    protected final Map<Integer, Map<Integer, List<Integer>>> equipSkillsCache = new HashMap<>();
    protected final Map<Integer, Pair<Integer, List<StructRewardItem>>> RewardItem =
            new HashMap<>();
    protected final Map<Byte, StructSetItem> setItems = new HashMap<>();
    protected final Map<Integer, Pair<Integer, List<Integer>>> questItems = new HashMap<>();
    protected final Map<Integer, String> faceList = new HashMap();
    protected final Map<Integer, String> hairList = new HashMap();

    private MapleItemInformationProvider() {
        // IO.print("Loading MapleItemInformationProvider :::");
    }

    public final void loadEtc() {
        LOG.info("Loading MapleItemInformationProvider.loadEtc() :::");
        if (!setItems.isEmpty() || !potentialCache.isEmpty()) {
            return;
        }

        final MapleData setsData = etcData.getData("SetItemInfo.img");
        StructSetItem itemz;
        SetItem itez;
        for (MapleData dat : setsData) {
            itemz = new StructSetItem();
            itemz.setItemID = (byte) Integer.parseInt(dat.getName());
            itemz.completeCount = (byte) MapleDataTool.getIntConvert("completeCount", dat, 0);
            for (MapleData level : dat.getChildByPath("ItemID")) {
                if (level.getType() != MapleDataType.INT) {
                    for (MapleData leve : level) {
                        if (!leve.getName().equals("representName")
                                && !leve.getName().equals("typeName")) {
                            itemz.itemIDs.add(MapleDataTool.getString(level));
                        }
                    }
                } else {
                    itemz.itemIDs.add(Integer.toString(MapleDataTool.getInt(level)));
                }
            }
            for (MapleData level : dat.getChildByPath("Effect")) {
                itez = new SetItem();
                itez.incPDD = MapleDataTool.getIntConvert("incPDD", level, 0);
                itez.incMDD = MapleDataTool.getIntConvert("incMDD", level, 0);
                itez.incSTR = MapleDataTool.getIntConvert("incSTR", level, 0);
                itez.incDEX = MapleDataTool.getIntConvert("incDEX", level, 0);
                itez.incINT = MapleDataTool.getIntConvert("incINT", level, 0);
                itez.incLUK = MapleDataTool.getIntConvert("incLUK", level, 0);
                itez.incACC = MapleDataTool.getIntConvert("incACC", level, 0);
                itez.incPAD = MapleDataTool.getIntConvert("incPAD", level, 0);
                itez.incMAD = MapleDataTool.getIntConvert("incMAD", level, 0);
                itez.incSpeed = MapleDataTool.getIntConvert("incSpeed", level, 0);
                itez.incMHP = MapleDataTool.getIntConvert("incMHP", level, 0);
                itez.incMMP = MapleDataTool.getIntConvert("incMMP", level, 0);
                itemz.items.put(Integer.parseInt(level.getName()), itez);
            }
            setItems.put(itemz.setItemID, itemz);
        }
        LOG.info(" Loaded");
    }

    public void loadItems() {
        LOG.info("Loading MapleItemInformationProvider.loadItems() :::");
        if (!dataCache.isEmpty()) {
            return;
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                LOG.warn(" Failed (no connection)");
                return;
            }

            // Load Item Data
            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM wz_itemdata");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    initItemInformation(rs);
                }
            }

            // Load Item Equipment Data
            try (PreparedStatement ps =
                            con.prepareStatement("SELECT * FROM wz_itemequipdata ORDER BY itemid");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    initItemEquipData(rs);
                }
            }

            // Load Item Addition Data
            try (PreparedStatement ps =
                            con.prepareStatement("SELECT * FROM wz_itemadddata ORDER BY itemid");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    initItemAddData(rs);
                }
            }

            // Load Item Reward Data
            try (PreparedStatement ps =
                            con.prepareStatement(
                                    "SELECT * FROM wz_itemrewarddata ORDER BY itemid");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    initItemRewardData(rs);
                }
            }

            // Finalize all Equipments
            for (Entry<Integer, ItemInformation> entry : dataCache.entrySet()) {
                if (GameConstants.getInventoryType(entry.getKey()) == MapleInventoryType.EQUIP) {
                    finalizeEquipData(entry.getValue());
                }
            }
        } catch (SQLException ex) {
            // 保持原有行為：忽略 SQL 例外
        }
        // System.out.println(dataCache.size() + " items loaded.");
        LOG.info(" Loaded");
    }

    public final List<StructPotentialItem> getPotentialInfo(final int potId) {
        return potentialCache.get(potId);
    }

    public final Map<Integer, List<StructPotentialItem>> getAllPotentialInfo() {
        return potentialCache;
    }

    public static final MapleItemInformationProvider getInstance() {
        return Holder.INSTANCE;
    }

    public final Collection<ItemInformation> getAllItems() {
        return dataCache.values();
    }

    //    public final List<Pair<Integer, String>> getAllItems() {
    //        if (itemNameCache.size() != 0) {
    //            return itemNameCache;
    //        }
    //        final List<Pair<Integer, String>> itemPairs = new ArrayList<>();
    //        MapleData itemsData;
    //
    //        itemsData = stringData.getData("Cash.img");
    //        for (final MapleData itemFolder : itemsData.getChildren()) {
    //            itemPairs.add(new Pair<>(Integer.parseInt(itemFolder.getName()),
    // MapleDataTool.getString("name", itemFolder, "NO-NAME")));
    //        }
    //
    //        itemsData = stringData.getData("Consume.img");
    //        for (final MapleData itemFolder : itemsData.getChildren()) {
    //            itemPairs.add(new Pair<>(Integer.parseInt(itemFolder.getName()),
    // MapleDataTool.getString("name", itemFolder, "NO-NAME")));
    //        }
    //
    //        itemsData = stringData.getData("Eqp.img").getChildByPath("Eqp");
    //        for (final MapleData eqpType : itemsData.getChildren()) {
    //            for (final MapleData itemFolder : eqpType.getChildren()) {
    //                itemPairs.add(new Pair<>(Integer.parseInt(itemFolder.getName()),
    // MapleDataTool.getString("name", itemFolder, "NO-NAME")));
    //            }
    //        }
    //
    //        itemsData = stringData.getData("Etc.img").getChildByPath("Etc");
    //        for (final MapleData itemFolder : itemsData.getChildren()) {
    //            itemPairs.add(new Pair<>(Integer.parseInt(itemFolder.getName()),
    // MapleDataTool.getString("name", itemFolder, "NO-NAME")));
    //        }
    //
    //        itemsData = stringData.getData("Ins.img");
    //        for (final MapleData itemFolder : itemsData.getChildren()) {
    //            itemPairs.add(new Pair<>(Integer.parseInt(itemFolder.getName()),
    // MapleDataTool.getString("name", itemFolder, "NO-NAME")));
    //        }
    //
    //        itemsData = stringData.getData("Pet.img");
    //        for (final MapleData itemFolder : itemsData.getChildren()) {
    //            itemPairs.add(new Pair<>(Integer.parseInt(itemFolder.getName()),
    // MapleDataTool.getString("name", itemFolder, "NO-NAME")));
    //        }
    //        return itemPairs;
    //    }
    protected final MapleData getStringData(final int itemId) {
        String cat = null;
        MapleData data;

        if (itemId >= 5010000) {
            data = cashStringData;
        } else if (itemId >= 2000000 && itemId < 3000000) {
            data = consumeStringData;
        } else if ((itemId >= 1142000 && itemId < 1143000)
                || (itemId >= 1010000 && itemId < 1040000)
                || (itemId >= 1122000 && itemId < 1123000)) {
            data = eqpStringData;
            cat = "Accessory";
        } else if (itemId >= 1000000 && itemId < 1010000) {
            data = eqpStringData;
            cat = "Cap";
        } else if (itemId >= 1102000 && itemId < 1103000) {
            data = eqpStringData;
            cat = "Cape";
        } else if (itemId >= 1040000 && itemId < 1050000) {
            data = eqpStringData;
            cat = "Coat";
        } else if (itemId >= 20000 && itemId < 22000) {
            data = eqpStringData;
            cat = "Face";
        } else if (itemId >= 1080000 && itemId < 1090000) {
            data = eqpStringData;
            cat = "Glove";
        } else if (itemId >= 30000 && itemId < 32000) {
            data = eqpStringData;
            cat = "Hair";
        } else if (itemId >= 1050000 && itemId < 1060000) {
            data = eqpStringData;
            cat = "Longcoat";
        } else if (itemId >= 1060000 && itemId < 1070000) {
            data = eqpStringData;
            cat = "Pants";
        } else if (itemId >= 1610000 && itemId < 1660000) {
            data = eqpStringData;
            cat = "Mechanic";
        } else if (itemId >= 1802000 && itemId < 1810000) {
            data = eqpStringData;
            cat = "PetEquip";
        } else if (itemId >= 1920000 && itemId < 2000000) {
            data = eqpStringData;
            cat = "Dragon";
        } else if (itemId >= 1112000 && itemId < 1120000) {
            data = eqpStringData;
            cat = "Ring";
        } else if (itemId >= 1092000 && itemId < 1100000) {
            data = eqpStringData;
            cat = "Shield";
        } else if (itemId >= 1070000 && itemId < 1080000) {
            data = eqpStringData;
            cat = "Shoes";
        } else if (itemId >= 1900000 && itemId < 1920000) {
            data = eqpStringData;
            cat = "Taming";
        } else if (itemId >= 1300000 && itemId < 1800000) {
            data = eqpStringData;
            cat = "Weapon";
        } else if (itemId >= 4000000 && itemId < 5000000) {
            data = etcStringData;
        } else if (itemId >= 3000000 && itemId < 4000000) {
            data = insStringData;
        } else if (itemId >= 5000000 && itemId < 5010000) {
            data = petStringData;
        } else {
            return null;
        }
        if (cat == null) {
            return data.getChildByPath(String.valueOf(itemId));
        } else {
            return data.getChildByPath("Eqp/" + cat + "/" + itemId);
        }
    }

    protected final MapleData getItemData(final int itemId) {
        MapleData ret = null;
        final String idStr = "0" + String.valueOf(itemId);
        MapleDataDirectoryEntry root = itemData.getRoot();
        for (final MapleDataDirectoryEntry topDir : root.getSubdirectories()) {
            // we should have .img files here beginning with the first 4 IID
            for (final MapleDataFileEntry iFile : topDir.getFiles()) {
                if (iFile.getName().equals(idStr.substring(0, 4) + ".img")) {
                    ret = itemData.getData(topDir.getName() + "/" + iFile.getName());
                    if (ret == null) {
                        return null;
                    }
                    ret = ret.getChildByPath(idStr);
                    return ret;
                } else if (iFile.getName().equals(idStr.substring(1) + ".img")) {
                    return itemData.getData(topDir.getName() + "/" + iFile.getName());
                }
            }
        }
        root = equipData.getRoot();
        for (final MapleDataDirectoryEntry topDir : root.getSubdirectories()) {
            for (final MapleDataFileEntry iFile : topDir.getFiles()) {
                if (iFile.getName().equals(idStr + ".img")) {
                    return equipData.getData(topDir.getName() + "/" + iFile.getName());
                }
            }
        }
        return ret;
    }

    /** returns the maximum of items in one slot */
    public final short getSlotMax(final MapleClient c, final int itemId) {
        if (slotMaxCache.containsKey(itemId)) {
            return slotMaxCache.get(itemId);
        }
        short ret = 0;
        final MapleData item = getItemData(itemId);
        if (item != null) {
            final MapleData smEntry = item.getChildByPath("info/slotMax");
            if (smEntry == null) {
                if (getInventoryType(itemId) == MapleInventoryType.EQUIP) {
                    ret = 1;
                } else {
                    ret = 100;
                }
            } else {
                ret = (short) MapleDataTool.getInt(smEntry);
            }
        }
        slotMaxCache.put(itemId, ret);
        return ret;
    }

    public final int getWholePrice(final int itemId) {
        if (wholePriceCache.containsKey(itemId)) {
            return wholePriceCache.get(itemId);
        }
        final MapleData item = getItemData(itemId);
        if (item == null) {
            return -1;
        }
        int pEntry = 0;
        final MapleData pData = item.getChildByPath("info/price");
        if (pData == null) {
            return -1;
        }
        pEntry = MapleDataTool.getInt(pData);

        wholePriceCache.put(itemId, pEntry);
        return pEntry;
    }

    public final double getPrice(final int itemId) {
        if (priceCache.containsKey(itemId)) {
            return priceCache.get(itemId);
        }
        final MapleData item = getItemData(itemId);
        if (item == null) {
            return -1;
        }
        double pEntry = 0.0;
        MapleData pData = item.getChildByPath("info/unitPrice");
        if (pData != null) {
            try {
                pEntry = MapleDataTool.getDouble(pData);
            } catch (Exception e) {
                pEntry = (double) MapleDataTool.getIntConvert(pData);
            }
        } else {
            pData = item.getChildByPath("info/price");
            if (pData == null) {
                return -1;
            }
            pEntry = (double) MapleDataTool.getIntConvert(pData);
        }
        if (itemId == 2070019 || itemId == 2330007) {
            pEntry = 1.0;
        }
        priceCache.put(itemId, pEntry);
        return pEntry;
    }

    public final Map<String, Byte> getItemMakeStats(final int itemId) {
        if (itemMakeStatsCache.containsKey(itemId)) {
            return itemMakeStatsCache.get(itemId);
        }
        if (itemId / 10000 != 425) {
            return null;
        }
        final Map<String, Byte> ret = new LinkedHashMap<>();
        final MapleData item = getItemData(itemId);
        if (item == null) {
            return null;
        }
        final MapleData info = item.getChildByPath("info");
        if (info == null) {
            return null;
        }
        ret.put("incPAD", (byte) MapleDataTool.getInt("incPAD", info, 0)); // WATK
        ret.put("incMAD", (byte) MapleDataTool.getInt("incMAD", info, 0)); // MATK
        ret.put("incACC", (byte) MapleDataTool.getInt("incACC", info, 0)); // ACC
        ret.put("incEVA", (byte) MapleDataTool.getInt("incEVA", info, 0)); // AVOID
        ret.put("incSpeed", (byte) MapleDataTool.getInt("incSpeed", info, 0)); // SPEED
        ret.put("incJump", (byte) MapleDataTool.getInt("incJump", info, 0)); // JUMP
        ret.put("incMaxHP", (byte) MapleDataTool.getInt("incMaxHP", info, 0)); // HP
        ret.put("incMaxMP", (byte) MapleDataTool.getInt("incMaxMP", info, 0)); // MP
        ret.put("incSTR", (byte) MapleDataTool.getInt("incSTR", info, 0)); // STR
        ret.put("incINT", (byte) MapleDataTool.getInt("incINT", info, 0)); // INT
        ret.put("incLUK", (byte) MapleDataTool.getInt("incLUK", info, 0)); // LUK
        ret.put("incDEX", (byte) MapleDataTool.getInt("incDEX", info, 0)); // DEX
        //	ret.put("incReqLevel", MapleDataTool.getInt("incReqLevel", info, 0)); // IDK!
        ret.put(
                "randOption",
                (byte) MapleDataTool.getInt("randOption", info, 0)); // Black Crystal Wa/MA
        ret.put(
                "randStat",
                (byte) MapleDataTool.getInt("randStat", info, 0)); // Dark Crystal - Str/Dex/int/Luk

        itemMakeStatsCache.put(itemId, ret);
        return ret;
    }

    private int rand(int min, int max) {
        return Math.abs((int) Randomizer.rand(min, max));
    }

    public Equip levelUpEquip(Equip equip, Map<String, Integer> sta) {
        Equip nEquip = (Equip) equip.copy();
        // is this all the stats?
        try {
            for (Entry<String, Integer> stat : sta.entrySet()) {
                if (stat.getKey().equals("STRMin")) {
                    nEquip.setStr(
                            (short)
                                    (nEquip.getStr()
                                            + rand(
                                                    stat.getValue().intValue(),
                                                    sta.get("STRMax").intValue())));
                } else if (stat.getKey().equals("DEXMin")) {
                    nEquip.setDex(
                            (short)
                                    (nEquip.getDex()
                                            + rand(
                                                    stat.getValue().intValue(),
                                                    sta.get("DEXMax").intValue())));
                } else if (stat.getKey().equals("INTMin")) {
                    nEquip.setInt(
                            (short)
                                    (nEquip.getInt()
                                            + rand(
                                                    stat.getValue().intValue(),
                                                    sta.get("INTMax").intValue())));
                } else if (stat.getKey().equals("LUKMin")) {
                    nEquip.setLuk(
                            (short)
                                    (nEquip.getLuk()
                                            + rand(
                                                    stat.getValue().intValue(),
                                                    sta.get("LUKMax").intValue())));
                } else if (stat.getKey().equals("PADMin")) {
                    nEquip.setWatk(
                            (short)
                                    (nEquip.getWatk()
                                            + rand(
                                                    stat.getValue().intValue(),
                                                    sta.get("PADMax").intValue())));
                } else if (stat.getKey().equals("PDDMin")) {
                    nEquip.setWdef(
                            (short)
                                    (nEquip.getWdef()
                                            + rand(
                                                    stat.getValue().intValue(),
                                                    sta.get("PDDMax").intValue())));
                } else if (stat.getKey().equals("MADMin")) {
                    nEquip.setMatk(
                            (short)
                                    (nEquip.getMatk()
                                            + rand(
                                                    stat.getValue().intValue(),
                                                    sta.get("MADMax").intValue())));
                } else if (stat.getKey().equals("MDDMin")) {
                    nEquip.setMdef(
                            (short)
                                    (nEquip.getMdef()
                                            + rand(
                                                    stat.getValue().intValue(),
                                                    sta.get("MDDMax").intValue())));
                } else if (stat.getKey().equals("ACCMin")) {
                    nEquip.setAcc(
                            (short)
                                    (nEquip.getAcc()
                                            + rand(
                                                    stat.getValue().intValue(),
                                                    sta.get("ACCMax").intValue())));
                } else if (stat.getKey().equals("EVAMin")) {
                    nEquip.setAvoid(
                            (short)
                                    (nEquip.getAvoid()
                                            + rand(
                                                    stat.getValue().intValue(),
                                                    sta.get("EVAMax").intValue())));
                } else if (stat.getKey().equals("SpeedMin")) {
                    nEquip.setSpeed(
                            (short)
                                    (nEquip.getSpeed()
                                            + rand(
                                                    stat.getValue().intValue(),
                                                    sta.get("SpeedMax").intValue())));
                } else if (stat.getKey().equals("JumpMin")) {
                    nEquip.setJump(
                            (short)
                                    (nEquip.getJump()
                                            + rand(
                                                    stat.getValue().intValue(),
                                                    sta.get("JumpMax").intValue())));
                } else if (stat.getKey().equals("MHPMin")) {
                    nEquip.setHp(
                            (short)
                                    (nEquip.getHp()
                                            + rand(
                                                    stat.getValue().intValue(),
                                                    sta.get("MHPMax").intValue())));
                } else if (stat.getKey().equals("MMPMin")) {
                    nEquip.setMp(
                            (short)
                                    (nEquip.getMp()
                                            + rand(
                                                    stat.getValue().intValue(),
                                                    sta.get("MMPMax").intValue())));
                } else if (stat.getKey().equals("MaxHPMin")) {
                    nEquip.setHp(
                            (short)
                                    (nEquip.getHp()
                                            + rand(
                                                    stat.getValue().intValue(),
                                                    sta.get("MaxHPMax").intValue())));
                } else if (stat.getKey().equals("MaxMPMin")) {
                    nEquip.setMp(
                            (short)
                                    (nEquip.getMp()
                                            + rand(
                                                    stat.getValue().intValue(),
                                                    sta.get("MaxMPMax").intValue())));
                }
            }
        } catch (NullPointerException e) {
            // catch npe because obviously the wz have some error XD
            LOG.error("MapleItemInformationProvider.levelUpEquip 發生例外", e);
        }
        return nEquip;
    }

    public final Map<Integer, Map<String, Integer>> getEquipIncrements(final int itemId) {
        if (equipIncsCache.containsKey(itemId)) {
            return equipIncsCache.get(itemId);
        }
        final Map<Integer, Map<String, Integer>> ret = new LinkedHashMap<>();
        final MapleData item = getItemData(itemId);
        if (item == null) {
            return null;
        }
        final MapleData info = item.getChildByPath("info/level/info");
        if (info == null) {
            return null;
        }
        for (MapleData dat : info.getChildren()) {
            Map<String, Integer> incs = new HashMap<>();
            for (MapleData data :
                    dat.getChildren()) { // why we have to do this? check if number has skills
                // or not
                if (data.getName().length() > 3) {
                    incs.put(
                            data.getName().substring(3),
                            MapleDataTool.getIntConvert(data.getName(), dat, 0));
                }
            }
            ret.put(Integer.parseInt(dat.getName()), incs);
        }
        equipIncsCache.put(itemId, ret);
        return ret;
    }

    public final Map<Integer, List<Integer>> getEquipSkills(final int itemId) {
        if (equipSkillsCache.containsKey(itemId)) {
            return equipSkillsCache.get(itemId);
        }
        final Map<Integer, List<Integer>> ret = new LinkedHashMap<>();
        final MapleData item = getItemData(itemId);
        if (item == null) {
            return null;
        }
        final MapleData info = item.getChildByPath("info/level/case");
        if (info == null) {
            return null;
        }
        for (MapleData dat : info.getChildren()) {
            for (MapleData data :
                    dat.getChildren()) { // why we have to do this? check if number has skills
                // or not
                if (data.getName().length()
                        == 1) { // the numbers all them are one digit. everything else isnt so we're
                    // lucky
                    // here..
                    List<Integer> adds = new ArrayList<>();
                    for (MapleData skil : data.getChildByPath("Skill").getChildren()) {
                        adds.add(MapleDataTool.getIntConvert("id", skil, 0));
                    }
                    ret.put(Integer.parseInt(data.getName()), adds);
                }
            }
        }
        equipSkillsCache.put(itemId, ret);
        return ret;
    }

    public final Map<String, Integer> getEquipStats(final int itemId) {
        if (equipStatsCache.containsKey(itemId)) {
            return equipStatsCache.get(itemId);
        }
        final Map<String, Integer> ret = new LinkedHashMap<>();
        final MapleData item = getItemData(itemId);
        if (item == null) {
            return null;
        }
        final MapleData info = item.getChildByPath("info");
        if (info == null) {
            return null;
        }
        for (final MapleData data : info.getChildren()) {
            if (data.getName().startsWith("inc")) {
                ret.put(data.getName().substring(3), MapleDataTool.getIntConvert(data));
            }
        }
        ret.put("tuc", MapleDataTool.getInt("tuc", info, 0));
        ret.put("reqLevel", MapleDataTool.getInt("reqLevel", info, 0));
        ret.put("reqJob", MapleDataTool.getInt("reqJob", info, 0));
        ret.put("reqSTR", MapleDataTool.getInt("reqSTR", info, 0));
        ret.put("reqDEX", MapleDataTool.getInt("reqDEX", info, 0));
        ret.put("reqINT", MapleDataTool.getInt("reqINT", info, 0));
        ret.put("reqLUK", MapleDataTool.getInt("reqLUK", info, 0));
        ret.put("reqPOP", MapleDataTool.getInt("reqPOP", info, 0));
        ret.put("cash", MapleDataTool.getInt("cash", info, 0));
        ret.put("canLevel", info.getChildByPath("level") == null ? 0 : 1);
        ret.put("cursed", MapleDataTool.getInt("cursed", info, 0));
        ret.put("success", MapleDataTool.getInt("success", info, 0));
        ret.put("setItemID", MapleDataTool.getInt("setItemID", info, 0));
        ret.put("equipTradeBlock", MapleDataTool.getInt("equipTradeBlock", info, 0));
        ret.put("durability", MapleDataTool.getInt("durability", info, -1));

        if (ItemConstants.ItemTypes.isAPWeapon(itemId)) {
            ret.put("elemDefault", MapleDataTool.getInt("elemDefault", info, 100));
            ret.put("incRMAS", MapleDataTool.getInt("incRMAS", info, 100)); // Poison
            ret.put("incRMAF", MapleDataTool.getInt("incRMAF", info, 100)); // Fire
            ret.put("incRMAL", MapleDataTool.getInt("incRMAL", info, 100)); // Lightning
            ret.put("incRMAI", MapleDataTool.getInt("incRMAI", info, 100)); // Ice
        }

        equipStatsCache.put(itemId, ret);
        return ret;
    }

    public final boolean canEquip(
            final Map<String, Integer> stats,
            final int itemid,
            final int level,
            final int job,
            final int fame,
            final int str,
            final int dex,
            final int luk,
            final int int_,
            final int supremacy) {
        if ((level + supremacy) >= stats.get("reqLevel")
                && str >= stats.get("reqSTR")
                && dex >= stats.get("reqDEX")
                && luk >= stats.get("reqLUK")
                && int_ >= stats.get("reqINT")) {
            final int fameReq = stats.get("reqPOP");
            if (fameReq != 0 && fame < fameReq) {
                return false;
            }
            return true;
        }
        return false;
    }

    public final int getReqLevel(final int itemId) {
        if (getEquipStats(itemId) == null) {
            return 0;
        }
        return getEquipStats(itemId).get("reqLevel");
    }

    public final int getSlots(final int itemId) {
        if (getEquipStats(itemId) == null) {
            return 0;
        }
        return getEquipStats(itemId).get("tuc");
    }

    public final int getSetItemID(final int itemId) {
        if (getEquipStats(itemId) == null) {
            return 0;
        }
        return getEquipStats(itemId).get("setItemID");
    }

    public final StructSetItem getSetItem(final int setItemId) {
        return setItems.get((byte) setItemId);
    }

    public final List<Integer> getScrollReqs(final int itemId) {
        if (scrollReqCache.containsKey(itemId)) {
            return scrollReqCache.get(itemId);
        }
        final List<Integer> ret = new ArrayList<>();
        final MapleData data = getItemData(itemId).getChildByPath("req");

        if (data == null) {
            return ret;
        }
        for (final MapleData req : data.getChildren()) {
            ret.add(MapleDataTool.getInt(req));
        }
        scrollReqCache.put(itemId, ret);
        return ret;
    }

    public final IItem scrollEquipWithId(
            final IItem equip,
            final IItem scrollId,
            final boolean ws,
            final MapleCharacter chr,
            final int vegas) {
        if (equip.getType() == 1) { // See IItem.java
            final Equip nEquip = (Equip) equip;
            final Map<String, Integer> stats = getEquipStats(scrollId.getItemId());
            final Map<String, Integer> eqstats = getEquipStats(equip.getItemId());
            final int succ =
                    (ItemConstants.isTablet(scrollId.getItemId())
                            ? ItemConstants.getSuccessTablet(
                                    scrollId.getItemId(), nEquip.getLevel())
                            : ((ItemConstants.ItemTypes.isEquipScroll(scrollId.getItemId())
                                            || ItemConstants.ItemTypes.isPotentialScroll(
                                                    scrollId.getItemId())
                                    ? 0
                                    : stats.get("success"))));
            final int curse =
                    (ItemConstants.isTablet(scrollId.getItemId())
                            ? ItemConstants.getCurseTablet(scrollId.getItemId(), nEquip.getLevel())
                            : ((ItemConstants.ItemTypes.isEquipScroll(scrollId.getItemId())
                                            || ItemConstants.ItemTypes.isPotentialScroll(
                                                    scrollId.getItemId())
                                    ? 0
                                    : stats.get("cursed"))));
            int success =
                    succ
                            + (vegas == 5610000 && succ == 10
                                    ? 20
                                    : (vegas == 5610001 && succ == 60 ? 30 : 0));
            if (ItemConstants.ItemTypes.isPotentialScroll(scrollId.getItemId())
                    || ItemConstants.ItemTypes.isEquipScroll(scrollId.getItemId())
                    || Randomizer.nextInt(100) <= success) {
                switch (scrollId.getItemId()) {
                    case 2049000:
                    case 2049001:
                    case 2049002:
                    case 2049003:
                    case 2049004:
                    case 2049005:
                        {
                            if (nEquip.getLevel() + nEquip.getUpgradeSlots() < eqstats.get("tuc")) {
                                nEquip.setUpgradeSlots((byte) (nEquip.getUpgradeSlots() + 1));
                            }
                            break;
                        }
                    case 2049006:
                    case 2049007:
                    case 2049008:
                        {
                            if (nEquip.getLevel() + nEquip.getUpgradeSlots() < eqstats.get("tuc")) {
                                nEquip.setUpgradeSlots((byte) (nEquip.getUpgradeSlots() + 2));
                            }
                            break;
                        }
                    case 2040727: // Spikes on shoe, prevents slip
                        {
                            byte flag = nEquip.getFlag();
                            flag |= ItemFlag.SPIKES.getValue();
                            nEquip.setFlag(flag);
                            break;
                        }
                    case 2041058: // Cape for Cold protection
                        {
                            byte flag = nEquip.getFlag();
                            flag |= ItemFlag.COLD.getValue();
                            nEquip.setFlag(flag);
                            break;
                        }
                    default:
                        {
                            if (ItemConstants.ItemTypes.isChaosScroll(scrollId.getItemId())) {
                                final int z = ItemConstants.getChaosNumber(scrollId.getItemId());
                                if (nEquip.getStr() > 0) {
                                    nEquip.setStr(
                                            (short)
                                                    (nEquip.getStr()
                                                            + Randomizer.nextInt(z)
                                                                    * (Randomizer.nextBoolean()
                                                                            ? 1
                                                                            : -1)));
                                }
                                if (nEquip.getDex() > 0) {
                                    nEquip.setDex(
                                            (short)
                                                    (nEquip.getDex()
                                                            + Randomizer.nextInt(z)
                                                                    * (Randomizer.nextBoolean()
                                                                            ? 1
                                                                            : -1)));
                                }
                                if (nEquip.getInt() > 0) {
                                    nEquip.setInt(
                                            (short)
                                                    (nEquip.getInt()
                                                            + Randomizer.nextInt(z)
                                                                    * (Randomizer.nextBoolean()
                                                                            ? 1
                                                                            : -1)));
                                }
                                if (nEquip.getLuk() > 0) {
                                    nEquip.setLuk(
                                            (short)
                                                    (nEquip.getLuk()
                                                            + Randomizer.nextInt(z)
                                                                    * (Randomizer.nextBoolean()
                                                                            ? 1
                                                                            : -1)));
                                }
                                if (nEquip.getWatk() > 0) {
                                    nEquip.setWatk(
                                            (short)
                                                    (nEquip.getWatk()
                                                            + Randomizer.nextInt(z)
                                                                    * (Randomizer.nextBoolean()
                                                                            ? 1
                                                                            : -1)));
                                }
                                if (nEquip.getWdef() > 0) {
                                    nEquip.setWdef(
                                            (short)
                                                    (nEquip.getWdef()
                                                            + Randomizer.nextInt(z)
                                                                    * (Randomizer.nextBoolean()
                                                                            ? 1
                                                                            : -1)));
                                }
                                if (nEquip.getMatk() > 0) {
                                    nEquip.setMatk(
                                            (short)
                                                    (nEquip.getMatk()
                                                            + Randomizer.nextInt(z)
                                                                    * (Randomizer.nextBoolean()
                                                                            ? 1
                                                                            : -1)));
                                }
                                if (nEquip.getMdef() > 0) {
                                    nEquip.setMdef(
                                            (short)
                                                    (nEquip.getMdef()
                                                            + Randomizer.nextInt(z)
                                                                    * (Randomizer.nextBoolean()
                                                                            ? 1
                                                                            : -1)));
                                }
                                if (nEquip.getAcc() > 0) {
                                    nEquip.setAcc(
                                            (short)
                                                    (nEquip.getAcc()
                                                            + Randomizer.nextInt(z)
                                                                    * (Randomizer.nextBoolean()
                                                                            ? 1
                                                                            : -1)));
                                }
                                if (nEquip.getAvoid() > 0) {
                                    nEquip.setAvoid(
                                            (short)
                                                    (nEquip.getAvoid()
                                                            + Randomizer.nextInt(z)
                                                                    * (Randomizer.nextBoolean()
                                                                            ? 1
                                                                            : -1)));
                                }
                                if (nEquip.getSpeed() > 0) {
                                    nEquip.setSpeed(
                                            (short)
                                                    (nEquip.getSpeed()
                                                            + Randomizer.nextInt(z)
                                                                    * (Randomizer.nextBoolean()
                                                                            ? 1
                                                                            : -1)));
                                }
                                if (nEquip.getJump() > 0) {
                                    nEquip.setJump(
                                            (short)
                                                    (nEquip.getJump()
                                                            + Randomizer.nextInt(z)
                                                                    * (Randomizer.nextBoolean()
                                                                            ? 1
                                                                            : -1)));
                                }
                                if (nEquip.getHp() > 0) {
                                    nEquip.setHp(
                                            (short)
                                                    (nEquip.getHp()
                                                            + Randomizer.nextInt(z)
                                                                    * (Randomizer.nextBoolean()
                                                                            ? 1
                                                                            : -1)));
                                }
                                if (nEquip.getMp() > 0) {
                                    nEquip.setMp(
                                            (short)
                                                    (nEquip.getMp()
                                                            + Randomizer.nextInt(z)
                                                                    * (Randomizer.nextBoolean()
                                                                            ? 1
                                                                            : -1)));
                                }
                                break;
                            } else if (ItemConstants.ItemTypes.isEquipScroll(
                                    scrollId.getItemId())) {
                                final int chanc =
                                        Math.max(
                                                (scrollId.getItemId() == 2049300 ? 100 : 80)
                                                        - (nEquip.getEnhance() * 10),
                                                10);
                                if (Randomizer.nextInt(100) > chanc) {
                                    return null; // destroyed, nib
                                }
                                if (nEquip.getStr() > 0 || Randomizer.nextInt(50) == 1) { // 1/50
                                    nEquip.setStr(
                                            (short) (nEquip.getStr() + Randomizer.nextInt(5)));
                                }
                                if (nEquip.getDex() > 0 || Randomizer.nextInt(50) == 1) { // 1/50
                                    nEquip.setDex(
                                            (short) (nEquip.getDex() + Randomizer.nextInt(5)));
                                }
                                if (nEquip.getInt() > 0 || Randomizer.nextInt(50) == 1) { // 1/50
                                    nEquip.setInt(
                                            (short) (nEquip.getInt() + Randomizer.nextInt(5)));
                                }
                                if (nEquip.getLuk() > 0 || Randomizer.nextInt(50) == 1) { // 1/50
                                    nEquip.setLuk(
                                            (short) (nEquip.getLuk() + Randomizer.nextInt(5)));
                                }
                                if (nEquip.getWatk() > 0
                                        && ItemConstants.ItemTypes.isWeapon(nEquip.getItemId())) {
                                    nEquip.setWatk(
                                            (short) (nEquip.getWatk() + Randomizer.nextInt(5)));
                                }
                                if (nEquip.getWdef() > 0 || Randomizer.nextInt(40) == 1) { // 1/40
                                    nEquip.setWdef(
                                            (short) (nEquip.getWdef() + Randomizer.nextInt(5)));
                                }
                                if (nEquip.getMatk() > 0
                                        && ItemConstants.ItemTypes.isWeapon(nEquip.getItemId())) {
                                    nEquip.setMatk(
                                            (short) (nEquip.getMatk() + Randomizer.nextInt(5)));
                                }
                                if (nEquip.getMdef() > 0 || Randomizer.nextInt(40) == 1) { // 1/40
                                    nEquip.setMdef(
                                            (short) (nEquip.getMdef() + Randomizer.nextInt(5)));
                                }
                                if (nEquip.getAcc() > 0 || Randomizer.nextInt(20) == 1) { // 1/20
                                    nEquip.setAcc(
                                            (short) (nEquip.getAcc() + Randomizer.nextInt(5)));
                                }
                                if (nEquip.getAvoid() > 0 || Randomizer.nextInt(20) == 1) { // 1/20
                                    nEquip.setAvoid(
                                            (short) (nEquip.getAvoid() + Randomizer.nextInt(5)));
                                }
                                if (nEquip.getSpeed() > 0 || Randomizer.nextInt(10) == 1) { // 1/10
                                    nEquip.setSpeed(
                                            (short) (nEquip.getSpeed() + Randomizer.nextInt(5)));
                                }
                                if (nEquip.getJump() > 0 || Randomizer.nextInt(10) == 1) { // 1/10
                                    nEquip.setJump(
                                            (short) (nEquip.getJump() + Randomizer.nextInt(5)));
                                }
                                if (nEquip.getHp() > 0 || Randomizer.nextInt(5) == 1) { // 1/5
                                    nEquip.setHp((short) (nEquip.getHp() + Randomizer.nextInt(5)));
                                }
                                if (nEquip.getMp() > 0 || Randomizer.nextInt(5) == 1) { // 1/5
                                    nEquip.setMp((short) (nEquip.getMp() + Randomizer.nextInt(5)));
                                }
                                nEquip.setEnhance((byte) (nEquip.getEnhance() + 1));
                                break;
                            } else if (ItemConstants.ItemTypes.isPotentialScroll(
                                    scrollId.getItemId())) {
                                if (nEquip.getState() == 0) {
                                    final int chanc = scrollId.getItemId() == 2049400 ? 90 : 70;
                                    if (Randomizer.nextInt(100) > chanc) {
                                        return null; // destroyed, nib
                                    }
                                    nEquip.resetPotential();
                                }
                                break;
                            } else {
                                for (Entry<String, Integer> stat : stats.entrySet()) {
                                    final String key = stat.getKey();

                                    if (key.equals("STR")) {
                                        nEquip.setStr(
                                                (short)
                                                        (nEquip.getStr()
                                                                + stat.getValue().intValue()));
                                    } else if (key.equals("DEX")) {
                                        nEquip.setDex(
                                                (short)
                                                        (nEquip.getDex()
                                                                + stat.getValue().intValue()));
                                    } else if (key.equals("INT")) {
                                        nEquip.setInt(
                                                (short)
                                                        (nEquip.getInt()
                                                                + stat.getValue().intValue()));
                                    } else if (key.equals("LUK")) {
                                        nEquip.setLuk(
                                                (short)
                                                        (nEquip.getLuk()
                                                                + stat.getValue().intValue()));
                                    } else if (key.equals("PAD")) {
                                        nEquip.setWatk(
                                                (short)
                                                        (nEquip.getWatk()
                                                                + stat.getValue().intValue()));
                                    } else if (key.equals("PDD")) {
                                        nEquip.setWdef(
                                                (short)
                                                        (nEquip.getWdef()
                                                                + stat.getValue().intValue()));
                                    } else if (key.equals("MAD")) {
                                        nEquip.setMatk(
                                                (short)
                                                        (nEquip.getMatk()
                                                                + stat.getValue().intValue()));
                                    } else if (key.equals("MDD")) {
                                        nEquip.setMdef(
                                                (short)
                                                        (nEquip.getMdef()
                                                                + stat.getValue().intValue()));
                                    } else if (key.equals("ACC")) {
                                        nEquip.setAcc(
                                                (short)
                                                        (nEquip.getAcc()
                                                                + stat.getValue().intValue()));
                                    } else if (key.equals("EVA")) {
                                        nEquip.setAvoid(
                                                (short)
                                                        (nEquip.getAvoid()
                                                                + stat.getValue().intValue()));
                                    } else if (key.equals("Speed")) {
                                        nEquip.setSpeed(
                                                (short)
                                                        (nEquip.getSpeed()
                                                                + stat.getValue().intValue()));
                                    } else if (key.equals("Jump")) {
                                        nEquip.setJump(
                                                (short)
                                                        (nEquip.getJump()
                                                                + stat.getValue().intValue()));
                                    } else if (key.equals("MHP")) {
                                        nEquip.setHp(
                                                (short)
                                                        (nEquip.getHp()
                                                                + stat.getValue().intValue()));
                                    } else if (key.equals("MMP")) {
                                        nEquip.setMp(
                                                (short)
                                                        (nEquip.getMp()
                                                                + stat.getValue().intValue()));
                                    } else if (key.equals("MHPr")) {
                                        nEquip.setHpR(
                                                (short)
                                                        (nEquip.getHpR()
                                                                + stat.getValue().intValue()));
                                    } else if (key.equals("MMPr")) {
                                        nEquip.setMpR(
                                                (short)
                                                        (nEquip.getMpR()
                                                                + stat.getValue().intValue()));
                                    }
                                }
                                break;
                            }
                        }
                }
                if (!ItemConstants.isCleanSlate(scrollId.getItemId())
                        && !ItemConstants.ItemTypes.isSpecialScroll(scrollId.getItemId())
                        && !ItemConstants.ItemTypes.isEquipScroll(scrollId.getItemId())
                        && !ItemConstants.ItemTypes.isPotentialScroll(scrollId.getItemId())) {
                    nEquip.setUpgradeSlots((byte) (nEquip.getUpgradeSlots() - 1));
                    nEquip.setLevel((byte) (nEquip.getLevel() + 1));
                }
            } else {
                if (!ws
                        && !ItemConstants.isCleanSlate(scrollId.getItemId())
                        && !ItemConstants.ItemTypes.isSpecialScroll(scrollId.getItemId())
                        && !ItemConstants.ItemTypes.isEquipScroll(scrollId.getItemId())
                        && !ItemConstants.ItemTypes.isPotentialScroll(scrollId.getItemId())) {
                    nEquip.setUpgradeSlots((byte) (nEquip.getUpgradeSlots() - 1));
                }
                if (Randomizer.nextInt(99) < curse) {
                    return null;
                }
            }
        }
        return equip;
    }

    public final IItem getEquipById(final int equipId) {
        return getEquipById(equipId, -1);
    }

    public final IItem getEquipById(final int equipId, final int ringId) {
        final Equip nEquip = new Equip(equipId, (byte) 0, ringId, (byte) 0);
        nEquip.setQuantity((short) 1);
        final Map<String, Integer> stats = getEquipStats(equipId);
        if (stats != null) {
            for (Entry<String, Integer> stat : stats.entrySet()) {
                final String key = stat.getKey();

                if (key.equals("STR")) {
                    nEquip.setStr((short) stat.getValue().intValue());
                } else if (key.equals("DEX")) {
                    nEquip.setDex((short) stat.getValue().intValue());
                } else if (key.equals("INT")) {
                    nEquip.setInt((short) stat.getValue().intValue());
                } else if (key.equals("LUK")) {
                    nEquip.setLuk((short) stat.getValue().intValue());
                } else if (key.equals("PAD")) {
                    nEquip.setWatk((short) stat.getValue().intValue());
                } else if (key.equals("PDD")) {
                    nEquip.setWdef((short) stat.getValue().intValue());
                } else if (key.equals("MAD")) {
                    nEquip.setMatk((short) stat.getValue().intValue());
                } else if (key.equals("MDD")) {
                    nEquip.setMdef((short) stat.getValue().intValue());
                } else if (key.equals("ACC")) {
                    nEquip.setAcc((short) stat.getValue().intValue());
                } else if (key.equals("EVA")) {
                    nEquip.setAvoid((short) stat.getValue().intValue());
                } else if (key.equals("Speed")) {
                    nEquip.setSpeed((short) stat.getValue().intValue());
                } else if (key.equals("Jump")) {
                    nEquip.setJump((short) stat.getValue().intValue());
                } else if (key.equals("MHP")) {
                    nEquip.setHp((short) stat.getValue().intValue());
                } else if (key.equals("MMP")) {
                    nEquip.setMp((short) stat.getValue().intValue());
                } else if (key.equals("MHPr")) {
                    nEquip.setHpR((short) stat.getValue().intValue());
                } else if (key.equals("MMPr")) {
                    nEquip.setMpR((short) stat.getValue().intValue());
                } else if (key.equals("tuc")) {
                    nEquip.setUpgradeSlots(stat.getValue().byteValue());
                } else if (key.equals("Craft")) {
                    nEquip.setHands(stat.getValue().shortValue());
                } else if (key.equals("durability")) {
                    nEquip.setDurability(stat.getValue().intValue());
                    //                } else if (key.equals("afterImage")) {
                }
            }
        }
        equipCache.put(equipId, nEquip);
        return nEquip.copy();
    }

    private final short getRandStat(final short defaultValue, final int maxRange) {
        if (defaultValue == 0) {
            return 0;
        }
        // vary no more than ceil of 10% of stat
        final int lMaxRange = (int) Math.min(Math.ceil(defaultValue * 0.1), maxRange);

        return (short)
                ((defaultValue - lMaxRange)
                        + Math.floor(
                                ThreadLocalRandom.current().nextDouble() * (lMaxRange * 2 + 1)));
    }

    public final Equip randomizeStats(final Equip equip) {
        equip.setStr(getRandStat(equip.getStr(), 5));
        equip.setDex(getRandStat(equip.getDex(), 5));
        equip.setInt(getRandStat(equip.getInt(), 5));
        equip.setLuk(getRandStat(equip.getLuk(), 5));
        equip.setMatk(getRandStat(equip.getMatk(), 5));
        equip.setWatk(getRandStat(equip.getWatk(), 5));
        equip.setAcc(getRandStat(equip.getAcc(), 5));
        equip.setAvoid(getRandStat(equip.getAvoid(), 5));
        equip.setJump(getRandStat(equip.getJump(), 5));
        equip.setHands(getRandStat(equip.getHands(), 5));
        equip.setSpeed(getRandStat(equip.getSpeed(), 5));
        equip.setWdef(getRandStat(equip.getWdef(), 10));
        equip.setMdef(getRandStat(equip.getMdef(), 10));
        equip.setHp(getRandStat(equip.getHp(), 10));
        equip.setMp(getRandStat(equip.getMp(), 10));
        return equip;
    }

    public final MapleStatEffect getItemEffect(final int itemId) {
        MapleStatEffect ret = itemEffects.get(Integer.valueOf(itemId));
        if (ret == null) {
            final MapleData item = getItemData(itemId);
            if (item == null) {
                return null;
            }
            ret = MapleStatEffect.loadItemEffectFromData(item.getChildByPath("spec"), itemId);
            itemEffects.put(Integer.valueOf(itemId), ret);
        }
        return ret;
    }

    public final List<Pair<Integer, Integer>> getSummonMobs(final int itemId) {
        if (summonMobCache.containsKey(Integer.valueOf(itemId))) {
            return summonMobCache.get(itemId);
        }
        if (!GameConstants.isSummonSack(itemId)) {
            return null;
        }
        final MapleData data = getItemData(itemId).getChildByPath("mob");
        if (data == null) {
            return null;
        }
        final List<Pair<Integer, Integer>> mobPairs = new ArrayList<>();

        for (final MapleData child : data.getChildren()) {
            mobPairs.add(
                    new Pair<>(
                            MapleDataTool.getIntConvert("id", child),
                            MapleDataTool.getIntConvert("prob", child)));
        }
        summonMobCache.put(itemId, mobPairs);
        return mobPairs;
    }

    public final int getCardMobId(final int id) {
        if (id == 0) {
            return 0;
        }
        if (monsterBookID.containsKey(id)) {
            return monsterBookID.get(id);
        }
        final MapleData data = getItemData(id);
        final int monsterid = MapleDataTool.getIntConvert("info/mob", data, 0);

        if (monsterid == 0) { // Hack.
            return 0;
        }
        monsterBookID.put(id, monsterid);
        return monsterBookID.get(id);
    }

    public final int getWatkForProjectile(final int itemId) {
        Integer atk = projectileWatkCache.get(itemId);
        if (atk != null) {
            return atk.intValue();
        }
        final MapleData data = getItemData(itemId);
        atk = Integer.valueOf(MapleDataTool.getInt("info/incPAD", data, 0));
        projectileWatkCache.put(itemId, atk);
        return atk.intValue();
    }

    public final boolean canScroll(final int scrollid, final int itemid) {
        return (scrollid / 100) % 100 == (itemid / 10000) % 100;
    }

    public final String getName(final int itemId) {
        if (nameCache.containsKey(itemId)) {
            return nameCache.get(itemId);
        }
        final MapleData strings = getStringData(itemId);
        if (strings == null) {
            return null;
        }
        final String ret = MapleDataTool.getString("name", strings, null);
        nameCache.put(itemId, ret);
        return ret;
    }

    public final String getDesc(final int itemId) {
        if (descCache.containsKey(itemId)) {
            return descCache.get(itemId);
        }
        final MapleData strings = getStringData(itemId);
        if (strings == null) {
            return null;
        }
        final String ret = MapleDataTool.getString("desc", strings, null);
        descCache.put(itemId, ret);
        return ret;
    }

    public final String getMsg(final int itemId) {
        if (msgCache.containsKey(itemId)) {
            return msgCache.get(itemId);
        }
        final MapleData strings = getStringData(itemId);
        if (strings == null) {
            return null;
        }
        final String ret = MapleDataTool.getString("msg", strings, null);
        msgCache.put(itemId, ret);
        return ret;
    }

    public final short getItemMakeLevel(final int itemId) {
        if (itemMakeLevel.containsKey(itemId)) {
            return itemMakeLevel.get(itemId);
        }
        if (itemId / 10000 != 400) {
            return 0;
        }
        final short lvl = (short) MapleDataTool.getIntConvert("info/lv", getItemData(itemId), 0);
        itemMakeLevel.put(itemId, lvl);
        return lvl;
    }

    public final byte isConsumeOnPickup(final int itemId) {
        // 0 = not, 1 = consume on pickup, 2 = consume + party
        if (consumeOnPickupCache.containsKey(itemId)) {
            return consumeOnPickupCache.get(itemId);
        }
        final MapleData data = getItemData(itemId);
        byte consume = (byte) MapleDataTool.getIntConvert("spec/consumeOnPickup", data, 0);
        if (consume == 0) {
            consume = (byte) MapleDataTool.getIntConvert("specEx/consumeOnPickup", data, 0);
        }
        if (consume == 1) {
            if (MapleDataTool.getIntConvert("spec/party", getItemData(itemId), 0) > 0) {
                consume = 2;
            }
        }
        consumeOnPickupCache.put(itemId, consume);
        return consume;
    }

    public final boolean isDropRestricted(final int itemId) {
        if (dropRestrictionCache.containsKey(itemId)) {
            return dropRestrictionCache.get(itemId);
        }
        final MapleData data = getItemData(itemId);

        boolean trade = false;
        if (MapleDataTool.getIntConvert("info/tradeBlock", data, 0) == 1
                || MapleDataTool.getIntConvert("info/quest", data, 0) == 1) {
            trade = true;
        }
        dropRestrictionCache.put(itemId, trade);
        return trade;
    }

    public final boolean isPickupRestricted(final int itemId) {
        if (pickupRestrictionCache.containsKey(itemId)) {
            return pickupRestrictionCache.get(itemId);
        }
        final boolean bRestricted =
                MapleDataTool.getIntConvert("info/only", getItemData(itemId), 0) == 1;

        pickupRestrictionCache.put(itemId, bRestricted);
        return bRestricted;
    }

    public final boolean isAccountShared(final int itemId) {
        if (accCache.containsKey(itemId)) {
            return accCache.get(itemId);
        }
        final boolean bRestricted =
                MapleDataTool.getIntConvert("info/accountSharable", getItemData(itemId), 0) == 1;

        accCache.put(itemId, bRestricted);
        return bRestricted;
    }

    public final int getStateChangeItem(final int itemId) {
        if (stateChangeCache.containsKey(itemId)) {
            return stateChangeCache.get(itemId);
        }
        final int triggerItem =
                MapleDataTool.getIntConvert("info/stateChangeItem", getItemData(itemId), 0);
        stateChangeCache.put(itemId, triggerItem);
        return triggerItem;
    }

    public final int getMeso(final int itemId) {
        if (mesoCache.containsKey(itemId)) {
            return mesoCache.get(itemId);
        }
        final int triggerItem = MapleDataTool.getIntConvert("info/meso", getItemData(itemId), 0);
        mesoCache.put(itemId, triggerItem);
        return triggerItem;
    }

    public final boolean isKarmaEnabled(final int itemId) {
        if (karmaEnabledCache.containsKey(itemId)) {
            return karmaEnabledCache.get(itemId) == 1;
        }
        final int iRestricted =
                MapleDataTool.getIntConvert("info/tradeAvailable", getItemData(itemId), 0);

        karmaEnabledCache.put(itemId, iRestricted);
        return iRestricted == 1;
    }

    public final boolean isPKarmaEnabled(final int itemId) {
        if (karmaEnabledCache.containsKey(itemId)) {
            return karmaEnabledCache.get(itemId) == 2;
        }
        final int iRestricted =
                MapleDataTool.getIntConvert("info/tradeAvailable", getItemData(itemId), 0);

        karmaEnabledCache.put(itemId, iRestricted);
        return iRestricted == 2;
    }

    public final boolean isPickupBlocked(final int itemId) {
        if (blockPickupCache.containsKey(itemId)) {
            return blockPickupCache.get(itemId);
        }
        final boolean iRestricted =
                MapleDataTool.getIntConvert("info/pickUpBlock", getItemData(itemId), 0) == 1;

        blockPickupCache.put(itemId, iRestricted);
        return iRestricted;
    }

    public final boolean isLogoutExpire(final int itemId) {
        if (logoutExpireCache.containsKey(itemId)) {
            return logoutExpireCache.get(itemId);
        }
        final boolean iRestricted =
                MapleDataTool.getIntConvert("info/expireOnLogout", getItemData(itemId), 0) == 1;

        logoutExpireCache.put(itemId, iRestricted);
        return iRestricted;
    }

    public final boolean cantSell(final int itemId) { // true = cant sell, false = can sell
        if (notSaleCache.containsKey(itemId)) {
            return notSaleCache.get(itemId);
        }
        final boolean bRestricted =
                MapleDataTool.getIntConvert("info/notSale", getItemData(itemId), 0) == 1;

        notSaleCache.put(itemId, bRestricted);
        return bRestricted;
    }

    public final Pair<Integer, List<StructRewardItem>> getRewardItem(final int itemid) {
        if (RewardItem.containsKey(itemid)) {
            return RewardItem.get(itemid);
        }
        final MapleData data = getItemData(itemid);
        if (data == null) {
            return null;
        }
        final MapleData rewards = data.getChildByPath("reward");
        if (rewards == null) {
            return null;
        }
        int totalprob =
                0; // As there are some rewards with prob above 2000, we can't assume it's always
        // 100
        List<StructRewardItem> all = new ArrayList<>();

        for (final MapleData reward : rewards) {
            StructRewardItem struct = new StructRewardItem();

            struct.itemid = MapleDataTool.getInt("item", reward, 0);
            struct.prob = (byte) MapleDataTool.getInt("prob", reward, 0);
            struct.quantity = (short) MapleDataTool.getInt("count", reward, 0);
            struct.effect = MapleDataTool.getString("Effect", reward, "");
            struct.worldmsg = MapleDataTool.getString("worldMsg", reward, null);
            struct.period = MapleDataTool.getInt("period", reward, -1);

            totalprob += struct.prob;

            all.add(struct);
        }
        Pair<Integer, List<StructRewardItem>> toreturn = new Pair<>(totalprob, all);
        RewardItem.put(itemid, toreturn);
        return toreturn;
    }

    public final Map<String, Integer> getSkillStats(final int itemId) {
        if (SkillStatsCache.containsKey(itemId)) {
            return SkillStatsCache.get(itemId);
        }
        if (!(itemId / 10000 == 228
                || itemId / 10000 == 229
                || itemId / 10000 == 562)) { // Skillbook and mastery book
            return null;
        }
        final MapleData item = getItemData(itemId);
        if (item == null) {
            return null;
        }
        final MapleData info = item.getChildByPath("info");
        if (info == null) {
            return null;
        }
        final Map<String, Integer> ret = new LinkedHashMap<>();
        for (final MapleData data : info.getChildren()) {
            if (data.getName().startsWith("inc")) {
                ret.put(data.getName().substring(3), MapleDataTool.getIntConvert(data));
            }
        }
        ret.put("masterLevel", MapleDataTool.getInt("masterLevel", info, 0));
        ret.put("reqSkillLevel", MapleDataTool.getInt("reqSkillLevel", info, 0));
        ret.put("success", MapleDataTool.getInt("success", info, 0));

        final MapleData skill = info.getChildByPath("skill");

        for (int i = 0; i < skill.getChildren().size(); i++) { // List of allowed skillIds
            ret.put("skillid" + i, MapleDataTool.getInt(Integer.toString(i), skill, 0));
        }
        SkillStatsCache.put(itemId, ret);
        return ret;
    }

    public final List<Integer> petsCanConsume(final int itemId) {
        if (petsCanConsumeCache.get(itemId) != null) {
            return petsCanConsumeCache.get(itemId);
        }
        final List<Integer> ret = new ArrayList<>();
        final MapleData data = getItemData(itemId);
        if (data == null || data.getChildByPath("spec") == null) {
            return ret;
        }
        int curPetId = 0;
        for (MapleData c : data.getChildByPath("spec")) {
            try {
                Integer.parseInt(c.getName());
            } catch (NumberFormatException e) {
                continue;
            }
            curPetId = MapleDataTool.getInt(c, 0);
            if (curPetId == 0) {
                break;
            }
            ret.add(Integer.valueOf(curPetId));
        }
        petsCanConsumeCache.put(itemId, ret);
        return ret;
    }

    public final boolean isQuestItem(final int itemId) {
        if (isQuestItemCache.containsKey(itemId)) {
            return isQuestItemCache.get(itemId);
        }
        final boolean questItem =
                MapleDataTool.getIntConvert("info/quest", getItemData(itemId), 0) == 1;
        isQuestItemCache.put(itemId, questItem);
        return questItem;
    }

    public final Pair<Integer, List<Integer>> questItemInfo(final int itemId) {
        if (questItems.containsKey(itemId)) {
            return questItems.get(itemId);
        }
        if (itemId / 10000 != 422 || getItemData(itemId) == null) {
            return null;
        }
        final MapleData itemD = getItemData(itemId).getChildByPath("info");
        if (itemD == null || itemD.getChildByPath("consumeItem") == null) {
            return null;
        }
        final List<Integer> consumeItems = new ArrayList<>();
        for (MapleData consume : itemD.getChildByPath("consumeItem")) {
            consumeItems.add(MapleDataTool.getInt(consume, 0));
        }
        final Pair<Integer, List<Integer>> questItem =
                new Pair<>(MapleDataTool.getIntConvert("questId", itemD, 0), consumeItems);
        questItems.put(itemId, questItem);
        return questItem;
    }

    public final boolean itemExists(final int itemId) {
        if (getInventoryType(itemId) == MapleInventoryType.UNDEFINED) {
            return false;
        }
        return getItemData(itemId) != null;
    }

    public final void loadStyles(boolean reload) {
        if (reload) {
            hairList.clear();
            faceList.clear();
            LOG.info("Loading MapleItemInformationProvider.loadItems(reload) :::");
        } else {
            LOG.info("Loading MapleItemInformationProvider.loadStyles() :::");
        }

        if (!hairList.isEmpty() || !faceList.isEmpty()) {
            return;
        }
        final List<String> types = new ArrayList<>();
        types.add("Hair");
        types.add("Face");
        for (final String type : types) {
            for (MapleData c : stringData.getData("Eqp.img").getChildByPath("Eqp/" + type)) {
                try {
                    if (this.equipData.getData(
                                    type
                                            + "/"
                                            + StringUtil.getLeftPaddedStr(
                                                    new StringBuilder()
                                                            .append(c.getName())
                                                            .append(".img")
                                                            .toString(),
                                                    '0',
                                                    12))
                            != null) {
                        int dataid = Integer.parseInt(c.getName());
                        String name = MapleDataTool.getString("name", c, "無名稱");
                        if (type.equals("Hair")) {
                            hairList.put(dataid, name);
                        } else {
                            faceList.put(dataid, name);
                        }
                    }
                } catch (Exception e) {
                }
            }
        }
        LOG.info("Loaded");
    }

    public boolean hairExists(int hair) {
        return hairList.containsKey(hair);
    }

    public boolean faceExists(int face) {
        return faceList.containsKey(face);
    }

    public final Map<Integer, String> getFaceList() {
        Map<Integer, String> list = new HashMap();
        list.putAll(faceList);
        return list;
    }

    public final Map<Integer, String> getHairList() {
        Map<Integer, String> list = new HashMap();
        list.putAll(hairList);
        return list;
    }

    public final boolean isCash(final int itemId) {
        if (getEquipStats(itemId) == null) {
            return getInventoryType(itemId) == MapleInventoryType.CASH;
        }
        return getInventoryType(itemId) == MapleInventoryType.CASH
                || getEquipStats(itemId).get("cash") > 0;
    }

    public final MapleInventoryType getInventoryType(int itemId) {
        if (inventoryTypeCache.containsKey(itemId)) {
            return inventoryTypeCache.get(itemId);
        }
        MapleInventoryType ret;
        String idStr = "0" + String.valueOf(itemId);
        MapleDataDirectoryEntry root = itemData.getRoot();
        for (MapleDataDirectoryEntry topDir : root.getSubdirectories()) {
            for (MapleDataFileEntry iFile : topDir.getFiles()) {
                if (iFile.getName().equals(idStr.substring(0, 4) + ".img")) {
                    ret = MapleInventoryType.getByWZName(topDir.getName());
                    inventoryTypeCache.put(itemId, ret);
                    return ret;
                } else if (iFile.getName().equals(idStr.substring(1) + ".img")) {
                    ret = MapleInventoryType.getByWZName(topDir.getName());
                    inventoryTypeCache.put(itemId, ret);
                    return ret;
                }
            }
        }
        root = equipData.getRoot();
        for (MapleDataDirectoryEntry topDir : root.getSubdirectories()) {
            for (MapleDataFileEntry iFile : topDir.getFiles()) {
                if (iFile.getName().equals(idStr + ".img")) {
                    ret = MapleInventoryType.EQUIP;
                    inventoryTypeCache.put(itemId, ret);
                    return ret;
                }
            }
        }
        ret = MapleInventoryType.UNDEFINED;
        inventoryTypeCache.put(itemId, ret);
        return ret;
    }

    public int getPetLimitLife(int itemid) {
        if (this.petLimitLifeInfo.containsKey(itemid)) {
            return (this.petLimitLifeInfo.get(itemid));
        }
        if (!ItemConstants.ItemTypes.isPet(itemid)) {
            return 0;
        }
        MapleData item = getItemData(itemid);
        int limitLife = MapleDataTool.getIntConvert("info/limitedLife", item, 0);
        this.petLimitLifeInfo.put(itemid, limitLife);
        return limitLife;
    }

    public int getPetLife(int itemid) {
        if (this.petLifeInfo.containsKey(itemid)) {
            return (this.petLifeInfo.get(itemid));
        }
        if (!ItemConstants.ItemTypes.isPet(itemid)) {
            return 0;
        }
        MapleData item = getItemData(itemid);
        int life = MapleDataTool.getIntConvert("info/life", item, 0);
        this.petLifeInfo.put(itemid, life);
        return life;
    }

    public short getPetFlagInfo(int itemId) {
        LOG.debug("ItemID = {}", itemId);
        if (this.petFlagInfo.containsKey(itemId)) {
            return (this.petFlagInfo.get(itemId));
        }
        short flag = 0;
        if (itemId / 10000 != 500) {
            return flag;
        }
        MapleData item = getItemData(itemId);
        if (item == null) {
            return flag;
        }
        if (MapleDataTool.getIntConvert("info/pickupItem", item, 0) > 0) {
            flag = (short) (flag | 0x1);
        }
        if (MapleDataTool.getIntConvert("info/longRange", item, 0) > 0) {
            flag = (short) (flag | 0x2);
        }
        if (MapleDataTool.getIntConvert("info/pickupAll", item, 0) > 0) {
            flag = (short) (flag | 0x4);
        }
        if (MapleDataTool.getIntConvert("info/sweepForDrop", item, 0) > 0) {
            flag = (short) (flag | 0x10);
        }
        if (MapleDataTool.getIntConvert("info/consumeHP", item, 0) > 0) {
            flag = (short) (flag | 0x20);
        }
        if (MapleDataTool.getIntConvert("info/consumeMP", item, 0) > 0) {
            flag = (short) (flag | 0x40);
        }
        if (MapleDataTool.getIntConvert("info/autoBuff", item, 0) > 0) {
            flag = (short) (flag | 0x200);
        }
        this.petFlagInfo.put(itemId, flag);
        return flag;
    }

    public boolean isCashItem(int itemId) {

        MapleData item = getItemData(itemId);
        if (item == null) {
            return false;
        }
        int pEntry;
        MapleData pData = item.getChildByPath("info/cash");
        if (pData == null) {
            return false;
        }
        try {
            pEntry = MapleDataTool.getInt(pData);
        } catch (Exception e) {
            return false;
        }
        return pEntry == 1;
    }

    private ItemInformation tmpInfo = null;

    public void initItemRewardData(ResultSet sqlRewardData) throws SQLException {
        final int itemID = sqlRewardData.getInt("itemid");
        if (tmpInfo == null || tmpInfo.itemId != itemID) {
            if (!dataCache.containsKey(itemID)) {
                LOG.warn("[initItemRewardData] Tried to load an item while this is not in the cache: {}", itemID);
                return;
            }
            tmpInfo = dataCache.get(itemID);
        }

        if (tmpInfo.rewardItems == null) {
            tmpInfo.rewardItems = new ArrayList<>();
        }

        StructRewardItem add = new StructRewardItem();
        add.itemid = sqlRewardData.getInt("item");
        add.period =
                (add.itemid == 1122017
                        ? Math.max(sqlRewardData.getInt("period"), 7200)
                        : sqlRewardData.getInt("period"));
        add.prob = (short) sqlRewardData.getInt("prob");
        add.quantity = sqlRewardData.getShort("quantity");
        add.worldmsg =
                sqlRewardData.getString("worldMsg").length() <= 0
                        ? null
                        : sqlRewardData.getString("worldMsg");
        add.effect = sqlRewardData.getString("effect");

        tmpInfo.rewardItems.add(add);
    }

    public void initItemAddData(ResultSet sqlAddData) throws SQLException {
        final int itemID = sqlAddData.getInt("itemid");
        if (tmpInfo == null || tmpInfo.itemId != itemID) {
            if (!dataCache.containsKey(itemID)) {
                LOG.warn("[initItemAddData] Tried to load an item while this is not in the cache: {}", itemID);
                return;
            }
            tmpInfo = dataCache.get(itemID);
        }

        if (tmpInfo.equipAdditions == null) {
            tmpInfo.equipAdditions = new LinkedList<>();
        }

        while (sqlAddData.next()) {
            tmpInfo.equipAdditions.add(
                    new EquipAddition(
                            sqlAddData.getString("key"),
                            sqlAddData.getString("subKey"),
                            sqlAddData.getString("value")));
        }
    }

    public void initItemEquipData(ResultSet sqlEquipData) throws SQLException {
        final int itemID = sqlEquipData.getInt("itemid");
        if (tmpInfo == null || tmpInfo.itemId != itemID) {
            if (!dataCache.containsKey(itemID)) {
                LOG.warn("[initItemEquipData] Tried to load an item while this is not in the cache: {}", itemID);
                return;
            }
            tmpInfo = dataCache.get(itemID);
        }

        if (tmpInfo.equipStats == null) {
            tmpInfo.equipStats = new HashMap<>();
        }

        final int itemLevel = sqlEquipData.getInt("itemLevel");
        if (itemLevel == -1) {
            tmpInfo.equipStats.put(sqlEquipData.getString("key"), sqlEquipData.getInt("value"));
        } else {
            if (tmpInfo.equipIncs == null) {
                tmpInfo.equipIncs = new HashMap<>();
            }

            Map<String, Integer> toAdd = tmpInfo.equipIncs.get(itemLevel);
            if (toAdd == null) {
                toAdd = new HashMap<>();
                tmpInfo.equipIncs.put(itemLevel, toAdd);
            }
            toAdd.put(sqlEquipData.getString("key"), sqlEquipData.getInt("value"));
        }
    }

    public void finalizeEquipData(ItemInformation item) {
        int itemId = item.itemId;

        if (item.equipStats == null) {
            item.equipStats = new HashMap<>();
        }

        item.eq = new Equip(itemId, (byte) 0, -1, (byte) 0);
        short stats = GameConstants.getStat(itemId, 0);
        if (stats > 0) {
            item.eq.setStr(stats);
            item.eq.setDex(stats);
            item.eq.setInt(stats);
            item.eq.setLuk(stats);
        }
        stats = GameConstants.getATK(itemId, 0);
        if (stats > 0) {
            item.eq.setWatk(stats);
            item.eq.setMatk(stats);
        }
        stats = GameConstants.getHpMp(itemId, 0);
        if (stats > 0) {
            item.eq.setHp(stats);
            item.eq.setMp(stats);
        }
        stats = GameConstants.getDEF(itemId, 0);
        if (stats > 0) {
            item.eq.setWdef(stats);
            item.eq.setMdef(stats);
        }
        // 讀取裝備屬性(新增屬性在這裡)
        if (item.equipStats.size() > 0) {
            for (Entry<String, Integer> stat : item.equipStats.entrySet()) {
                final String key = stat.getKey();
                switch (key) {
                    case "STR":
                        item.eq.setStr(GameConstants.getStat(itemId, stat.getValue()));
                        break;
                    case "DEX":
                        item.eq.setDex(GameConstants.getStat(itemId, stat.getValue()));
                        break;
                    case "INT":
                        item.eq.setInt(GameConstants.getStat(itemId, stat.getValue()));
                        break;
                    case "LUK":
                        item.eq.setLuk(GameConstants.getStat(itemId, stat.getValue()));
                        break;
                    case "PAD":
                        item.eq.setWatk(GameConstants.getATK(itemId, stat.getValue()));
                        break;
                    case "PDD":
                        item.eq.setWdef(GameConstants.getDEF(itemId, stat.getValue()));
                        break;
                    case "MAD":
                        item.eq.setMatk(GameConstants.getATK(itemId, stat.getValue()));
                        break;
                    case "MDD":
                        item.eq.setMdef(GameConstants.getDEF(itemId, stat.getValue()));
                        break;
                    case "ACC":
                        item.eq.setAcc((short) (int) stat.getValue());
                        break;
                    case "EVA":
                        item.eq.setAvoid((short) (int) stat.getValue());
                        break;
                    case "Speed":
                        item.eq.setSpeed((short) (int) stat.getValue());
                        break;
                    case "Jump":
                        item.eq.setJump((short) (int) stat.getValue());
                        break;
                    case "MHP":
                        item.eq.setHp(GameConstants.getHpMp(itemId, stat.getValue()));
                        break;
                    case "MMP":
                        item.eq.setMp(GameConstants.getHpMp(itemId, stat.getValue()));
                        break;
                    case "tuc":
                        item.eq.setUpgradeSlots(stat.getValue().byteValue());
                        break;
                    case "Craft":
                        item.eq.setHands(stat.getValue().shortValue());
                        break;
                    case "durability":
                        item.eq.setDurability(stat.getValue());
                        break;
                }
            }
        }
    }

    public void initItemInformation(ResultSet sqlItemData) throws SQLException {
        final ItemInformation ret = new ItemInformation();
        final int itemId = sqlItemData.getInt("itemid");
        ret.itemId = itemId;
        ret.slotMax =
                GameConstants.getSlotMax(itemId) > 0
                        ? GameConstants.getSlotMax(itemId)
                        : sqlItemData.getShort("slotMax");
        ret.price = Double.parseDouble(sqlItemData.getString("price"));
        ret.wholePrice = sqlItemData.getInt("wholePrice");
        ret.stateChange = sqlItemData.getInt("stateChange");
        ret.name = sqlItemData.getString("name");
        ret.desc = sqlItemData.getString("desc");
        ret.msg = sqlItemData.getString("msg");

        ret.flag = sqlItemData.getInt("flags");

        ret.karmaEnabled = sqlItemData.getByte("karma");
        ret.meso = sqlItemData.getInt("meso");
        ret.monsterBook = sqlItemData.getInt("monsterBook");
        ret.itemMakeLevel = sqlItemData.getShort("itemMakeLevel");
        ret.questId = sqlItemData.getInt("questId");
        ret.create = sqlItemData.getInt("create");
        ret.replaceItem = sqlItemData.getInt("replaceId");
        ret.replaceMsg = sqlItemData.getString("replaceMsg");
        ret.afterImage = sqlItemData.getString("afterImage");
        ret.cardSet = 0;
        if (ret.monsterBook > 0 && itemId / 10000 == 238) {
            mobIds.put(ret.monsterBook, itemId);
            for (var entry : monsterBookSets.entrySet()) {
                MonsterBookSetDetail detail = entry.getValue();

                // 程式碼自己就會說話：如果套裝的「卡片物品清單」包含這個 itemId
                if (detail.cardItemIds().contains(itemId)) {
                    ret.cardSet = entry.getKey();
                    break;
                }
            }
        }

        final String scrollRq = sqlItemData.getString("scrollReqs");
        if (scrollRq.length() > 0) {
            ret.scrollReqs = new ArrayList<>();
            final String[] scroll = scrollRq.split(",");
            for (String s : scroll) {
                if (s.length() > 1) {
                    ret.scrollReqs.add(Integer.parseInt(s));
                }
            }
        }
        final String consumeItem = sqlItemData.getString("consumeItem");
        if (consumeItem.length() > 0) {
            ret.questItems = new ArrayList<>();
            final String[] scroll = scrollRq.split(",");
            for (String s : scroll) {
                if (s.length() > 1) {
                    ret.questItems.add(Integer.parseInt(s));
                }
            }
        }

        ret.totalprob = sqlItemData.getInt("totalprob");
        final String incRq = sqlItemData.getString("incSkill");
        if (incRq.length() > 0) {
            ret.incSkill = new ArrayList<>();
            final String[] scroll = incRq.split(",");
            for (String s : scroll) {
                if (s.length() > 1) {
                    ret.incSkill.add(Integer.parseInt(s));
                }
            }
        }
        dataCache.put(itemId, ret);
    }
}
