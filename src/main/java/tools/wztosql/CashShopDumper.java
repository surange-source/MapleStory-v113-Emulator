/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tools.wztosql;

import client.inventory.MapleInventoryType;
import database.DatabaseConnection;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import provider.MapleData;
import provider.MapleDataProvider;
import provider.MapleDataProviderFactory;
import provider.MapleDataTool;
import server.CashItemFactory;
import server.CashItemInfo.CashModInfo;
import server.MapleItemInformationProvider;
import server.ServerProperties;

/**
 * @author Flower
 */
public class CashShopDumper {
    private static final Logger LOG = LoggerFactory.getLogger(CashShopDumper.class);

    private static final MapleDataProvider data =
            MapleDataProviderFactory.getDataProvider(
                    new File(ServerProperties.getProperty("tms.Wzpath") + "/Etc.wz"));
    private static final String OUT_ALL = "cashshop_items.sql";

    public static final CashModInfo getModInfo(int sn) {
        CashModInfo ret = null;

        try (Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps =
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
                }
            }

        } catch (Exception ex) {
            LOG.error("取得現金商店模組資訊失敗", ex);
        }

        return ret;
    }

    private static String esc(String s) {
        if (s == null) {
            return "NULL";
        }
        return "'" + s.replace("\\", "\\\\").replace("'", "''") + "'";
    }

    private static void writeFile(String path, List<String> lines) throws IOException {
        try (BufferedWriter bw =
                new BufferedWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(path), StandardCharsets.UTF_8))) {
            for (String line : lines) {
                bw.write(line);
                bw.newLine();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        try {
            System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));
            System.setErr(new java.io.PrintStream(System.err, true, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            LOG.error("CashShopDumper.main 發生例外", e);
        }

        CashModInfo m = getModInfo(20000393);
        CashItemFactory.getInstance().initialize();
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        ii.getAllItems();
        Collection<CashModInfo> list = CashItemFactory.getInstance().getAllModInfo();
        // Connection con = DatabaseConnection.getConnection();

        Map<Integer, List<String>> dics = new HashMap<>();
        List<String> all = new ArrayList<>();
        all.add(
                "INSERT INTO cashshop_modified_items (serial, showup, itemid, priority, period, gender, `count`, meso, discount_price, mark, unk_1, unk_2, unk_3, name) VALUES ");
        int total = 0, skipped = 0, nullName = 0;
        for (MapleData field : data.getData("Commodity.img").getChildren()) {
            try {
                final int itemId = MapleDataTool.getIntConvert("ItemId", field, 0);
                final int sn = MapleDataTool.getIntConvert("SN", field, 0);
                final int count = MapleDataTool.getIntConvert("Count", field, 0);
                final int price = MapleDataTool.getIntConvert("Price", field, 0);
                final int priority = MapleDataTool.getIntConvert("Priority", field, 0);
                final int period = MapleDataTool.getIntConvert("Period", field, 0);
                final int gender = MapleDataTool.getIntConvert("Gender", field, -1);
                final int meso = MapleDataTool.getIntConvert("Meso", field, 0);
                // if(qq.contains(itemId))
                //    continue;
                if (itemId == 0) {
                    nullName++;
                    continue;
                }

                int cat = itemId / 10000;
                if (dics.get(cat) == null) {
                    dics.put(cat, new ArrayList());
                }
                boolean check = false;
                if (meso > 0) {
                    check = true;
                }
                if (MapleItemInformationProvider.getInstance().getInventoryType(itemId)
                        == MapleInventoryType.EQUIP) {
                    if (!MapleItemInformationProvider.getInstance().isCashItem(itemId)) {
                        check = true;
                    }
                }
                if (MapleItemInformationProvider.getInstance().getInventoryType(itemId)
                        == MapleInventoryType.EQUIP) {
                    if (period > 0) {
                        check = true;
                    }
                }
                String itemName = esc(MapleItemInformationProvider.getInstance().getName(itemId));

                if (check) {
                    LOG.info("skipped: {}", itemName);
                    skipped++;
                    continue;
                }
                String sql =
                        "("
                                + sn
                                + ", "
                                + "1, "
                                + "0, "
                                + "0, "
                                + period
                                + ", "
                                + gender
                                + ", "
                                + (count > 1 ? count : 0)
                                + ", "
                                + meso
                                + ", "
                                + price
                                + ", "
                                + "0, "
                                + "0, "
                                + "0, "
                                + "0, "
                                + itemName
                                + "),";

                String block = sql;
                int cati = itemId / 10000;
                dics.computeIfAbsent(cati, k -> new ArrayList<>()).add(block);
                all.add(block);
                total++;

            } catch (Exception ex) {
                LOG.error("處理現金商店物品失敗", ex);
            }
        }

        writeFile(OUT_ALL, all);
        LOG.info("完成：輸出 {} 筆，略過 {} 筆，名稱為 null {} 筆", total, skipped, nullName);
        LOG.info("輸出位置：{}", new File(OUT_ALL).getAbsolutePath());
    }
}
