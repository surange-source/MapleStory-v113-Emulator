package server;

import database.DatabaseConnection;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import provider.MapleData;
import provider.MapleDataProvider;
import provider.MapleDataProviderFactory;
import provider.MapleDataTool;
import server.CashItemInfo.CashModInfo;

public final class CashItemFactory {
    private static final Logger LOG = LoggerFactory.getLogger(CashItemFactory.class);

    private static final class SingletonHolder {
        private static final CashItemFactory INSTANCE = new CashItemFactory();
    }

    private static final int[] bestItems =
            new int[] {50400003, 50300092, 30200034, 50100010, 50300099};
    private volatile boolean initialized = false;
    private final Map<Integer, CashItemInfo> itemStats = new HashMap<>();
    private final Map<Integer, CashItemInfo[]> itemPackage = new HashMap<>();
    private final Map<Integer, CashModInfo> itemMods = new HashMap<>();
    private final MapleDataProvider data =
            MapleDataProviderFactory.getDataProvider(
                    new File(ServerProperties.getProperty("tms.Wzpath") + "/Etc.wz"));

    public static final CashItemFactory getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private CashItemFactory() {}

    public void initialize() {
        if (initialized) {
            return;
        }
        final long startTime = System.currentTimeMillis();
        LOG.info("Loading CashItemFactory :::");

        final MapleData commodityData = data.getData("Commodity.img");
        if (commodityData != null) {
            for (MapleData field : commodityData.getChildren()) {
                final int itemId = MapleDataTool.getIntConvert("ItemId", field, 0);
                final int SN = MapleDataTool.getIntConvert("SN", field, 0);

                if (SN > 0) {
                    final CashItemInfo stats =
                            new CashItemInfo(
                                    itemId,
                                    MapleDataTool.getIntConvert("Count", field, 1),
                                    MapleDataTool.getIntConvert("Price", field, 0),
                                    SN,
                                    MapleDataTool.getIntConvert("Period", field, 0),
                                    MapleDataTool.getIntConvert("Gender", field, 2),
                                    MapleDataTool.getIntConvert("OnSale", field, 0) > 0);
                    itemStats.put(SN, stats);
                }
            }
        }

        // 1. 一次性從資料庫載入所有修改過的商城商品
        loadAllModInfo();

        // 2. 直接遍歷 CashPackage.img 的實際禮包節點，避免對上萬個一般道具做無效路徑搜尋
        loadAllPackages();

        // 3. 僅針對有修改紀錄的商品初始化 CashModInfo 內部的 citem 快取
        for (Map.Entry<Integer, CashModInfo> entry : itemMods.entrySet()) {
            final CashModInfo mod = entry.getValue();
            if (mod != null && mod.showUp) {
                mod.toCItem(itemStats.get(entry.getKey()));
            }
        }

        initialized = true;
        LOG.info(" Loaded in {}ms", System.currentTimeMillis() - startTime);
    }

    private void loadAllPackages() {
        final MapleData packageData = data.getData("CashPackage.img");
        if (packageData == null) {
            return;
        }
        for (MapleData pkgNode : packageData.getChildren()) {
            final int packageItemId;
            try {
                packageItemId = Integer.parseInt(pkgNode.getName());
            } catch (NumberFormatException e) {
                continue;
            }
            final MapleData snNode = pkgNode.getChildByPath("SN");
            if (snNode == null) {
                continue;
            }
            final List<MapleData> children = snNode.getChildren();
            final CashItemInfo[] items = new CashItemInfo[children.size()];
            int idx = 0;
            for (MapleData d : children) {
                final int sn = MapleDataTool.getIntConvert(d, 0);
                items[idx++] = itemStats.get(sn);
            }
            itemPackage.put(packageItemId, items);
        }
    }

    private void loadAllModInfo() {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }
            try (PreparedStatement ps =
                            con.prepareStatement("SELECT * FROM cashshop_modified_items");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    final int sn = rs.getInt("serial");
                    final CashModInfo ret =
                            new CashModInfo(
                                    sn,
                                    rs.getInt("discount_price"),
                                    rs.getInt("mark"),
                                    rs.getInt("showup") > 0,
                                    rs.getInt("itemid"),
                                    rs.getInt("priority"),
                                    rs.getInt("package") > 0,
                                    rs.getInt("period"),
                                    rs.getInt("gender"),
                                    rs.getInt("count"),
                                    rs.getInt("meso"),
                                    rs.getInt("unk_1"),
                                    rs.getInt("unk_2"),
                                    rs.getInt("unk_3"),
                                    rs.getInt("extra_flags"));
                    itemMods.put(sn, ret);
                }
            }
        } catch (Exception e) {
            LOG.error("CashItemFactory.loadAllModInfo 發生例外", e);
        }
    }

    public final CashItemInfo getItem(int sn) {
        final CashItemInfo stats = itemStats.get(sn);
        final CashModInfo z = getModInfo(sn);
        if (z != null && z.showUp) {
            return z.toCItem(stats);
        }
        if (stats == null || !stats.onSale()) {
            return null;
        }
        return stats;
    }

    public final List<CashItemInfo> getPackageItems(int itemId) {
        final CashItemInfo[] cached = itemPackage.get(itemId);
        if (cached != null) {
            final List<CashItemInfo> list = new ArrayList<>(cached.length);
            Collections.addAll(list, cached);
            return list;
        }
        if (initialized) {
            return null;
        }
        final MapleData b = data.getData("CashPackage.img");
        if (b == null || b.getChildByPath(itemId + "/SN") == null) {
            return null;
        }
        final List<MapleData> children = b.getChildByPath(itemId + "/SN").getChildren();
        final CashItemInfo[] packageItems = new CashItemInfo[children.size()];
        final List<CashItemInfo> result = new ArrayList<>(children.size());
        int idx = 0;
        for (MapleData d : children) {
            final CashItemInfo info = itemStats.get(MapleDataTool.getIntConvert(d));
            packageItems[idx++] = info;
            result.add(info);
        }
        itemPackage.put(itemId, packageItems);
        return result;
    }

    public final CashModInfo getModInfo(int sn) {
        CashModInfo ret = itemMods.get(sn);
        if (ret == null) {
            if (initialized) {
                return null;
            }
            try (Connection con = DatabaseConnection.getConnection()) {
                if (con == null) {
                    return null;
                }
                try (PreparedStatement ps =
                        con.prepareStatement(
                                "SELECT * FROM cashshop_modified_items WHERE serial = ?")) {
                    ps.setInt(1, sn);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            ret =
                                    new CashModInfo(
                                            sn,
                                            rs.getInt("discount_price"),
                                            rs.getInt("mark"),
                                            rs.getInt("showup") > 0,
                                            rs.getInt("itemid"),
                                            rs.getInt("priority"),
                                            rs.getInt("package") > 0,
                                            rs.getInt("period"),
                                            rs.getInt("gender"),
                                            rs.getInt("count"),
                                            rs.getInt("meso"),
                                            rs.getInt("unk_1"),
                                            rs.getInt("unk_2"),
                                            rs.getInt("unk_3"),
                                            rs.getInt("extra_flags"));
                            itemMods.put(sn, ret);
                        }
                    }
                }
            } catch (Exception e) {
                LOG.error("CashItemFactory.getModInfo 發生例外", e);
            }
        }
        return ret;
    }

    public final Collection<CashModInfo> getAllModInfo() {
        if (!initialized) {
            initialize();
        }
        return itemMods.values();
    }

    public final int[] getBestItems() {
        return bestItems;
    }
}