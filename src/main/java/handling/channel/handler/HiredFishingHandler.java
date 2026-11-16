package handling.channel.handler;

import client.MapleCharacter;
import client.MapleClient;
import client.inventory.IItem;
import client.inventory.ItemLoader;
import client.inventory.MapleInventoryType;
import constants.GameConstants;
import database.DatabaseConnection;
import handling.world.World;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.MapleInventoryManipulator;
import server.MerchItemPackage;
import server.shops.HiredFishing;
import tools.Pair;
import tools.data.input.SeekableLittleEndianAccessor;
import tools.packet.PlayerShopPacket;

public class HiredFishingHandler {
    private static final Logger LOG = LoggerFactory.getLogger(HiredFishingHandler.class);

    public static final void UseHiredFishing(
            final SeekableLittleEndianAccessor slea, final MapleClient c) {

        final MapleCharacter player = c.getPlayer();
        if (player == null) {
            return;
        }

        final int mapId = player.getMapId();
        if (mapId >= 749050500 && mapId <= 749050502) {
            final byte state = checkExistance(player.getAccountID(), player.getId());

            switch (state) {
                case 1:
                    player.dropMessage(1, "請先去找富蘭德里領取你之前所釣的東西");
                    break;
                case 0:
                    final HiredFishing fishing = World.hasFishing(player.getAccountID());
                    final MerchItemPackage pack =
                            loadItemFrom_Database(player.getId(), player.getAccountID());
                    if (fishing == null && pack == null) {
                        c.sendPacket(PlayerShopPacket.sendFishBox());
                    } else if (fishing == null && pack != null) {
                        if (pack.getItems().size() <= 0) {
                            if (!check(player, pack)) {
                                c.sendPacket(PlayerShopPacket.merchItem_Message((byte) 0x21));
                                return;
                            }
                            if (deletePackage(
                                    player.getId(), player.getAccountID(), pack.getPackageid())) {
                                player.gainMeso(pack.getMesos(), false);
                                c.sendPacket(PlayerShopPacket.merchItem_Message((byte) 0x1d));
                            } else {
                                player.dropMessage(1, "An unknown error occured.");
                            }
                        } else {
                            c.sendPacket(PlayerShopPacket.fishingItemStore_ItemData(pack));
                        }
                    } else {
                        // 或許得做頻道判斷
                        c.sendPacket(PlayerShopPacket.closeHiredFishing(fishing));
                    }
                    break;
                default:
                    player.dropMessage(1, "An unknown error occured.");
                    break;
            }

        } else {
            c.getSession().close();
        }
    }

    private static final byte checkExistance(final int accid, final int charid) {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return -1;
            }
            try (PreparedStatement ps =
                    con.prepareStatement(
                            "SELECT * from hiredfishing where accountid = ? OR characterid = ?")) {
                ps.setInt(1, accid);
                ps.setInt(2, charid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return 1;
                    }
                }
                return 0;
            }
        } catch (SQLException se) {
            return -1;
        }
    }

    public static final void OpenFishingItemStore(final MapleClient c) {
        final MapleCharacter player = c.getPlayer();
        if (player == null) {
            return;
        }
        final HiredFishing fishing = World.hasFishing(player.getAccountID());
        if (fishing != null) {
            player.dropMessage(1, "請關閉釣魚小精靈後在試一次.");
            player.setConversation(0);
        } else { // Hired fishing
            final MerchItemPackage pack =
                    loadItemFrom_Database(player.getId(), player.getAccountID());

            if (pack == null) {
                player.dropMessage(1, "你沒有在這邊置放道具!");
                player.setConversation(0);
            } else if (pack.getItems().size() <= 0) { // error fix for complainers.
                if (!check(player, pack)) {
                    c.sendPacket(PlayerShopPacket.merchItem_Message((byte) 0x21));
                    return;
                }
                if (deletePackage(player.getId(), player.getAccountID(), pack.getPackageid())) {
                    player.gainMeso(pack.getMesos(), false);
                    c.sendPacket(PlayerShopPacket.merchItem_Message((byte) 0x1d));
                } else {
                    player.dropMessage(1, "An unknown error occured.");
                }
            } else {
                c.sendPacket(PlayerShopPacket.fishingItemStore_ItemData(pack));
            }
        }
        return;
    }

    public static final void FishingItemStore(
            final SeekableLittleEndianAccessor slea, final MapleClient c) {
        final MapleCharacter player = c.getPlayer();
        if (player == null) {
            return;
        }
        final byte operation = slea.readByte();

        switch (operation) {
            case 25:
                { // Request take out iteme
                    if (player.getConversation() != 6) {
                        return;
                    }
                    c.sendPacket(PlayerShopPacket.merchItemStore((byte) 0x24));
                    break;
                }
            case 26:
                { // Take out item
                    if (player.getConversation() != 6) {
                        return;
                    }
                    final MerchItemPackage pack =
                            loadItemFrom_Database(player.getId(), player.getAccountID());

                    if (pack == null) {
                        player.dropMessage(1, "An unknown error occured.");
                        return;
                    }
                    if (!check(player, pack)) {
                        c.sendPacket(PlayerShopPacket.merchItem_Message((byte) 0x21));
                        return;
                    }
                    if (deletePackage(player.getId(), player.getAccountID(), pack.getPackageid())) {
                        player.gainMeso(pack.getMesos(), false);
                        for (IItem item : pack.getItems()) {
                            MapleInventoryManipulator.addFromDrop(c, item, false);
                        }
                        c.sendPacket(PlayerShopPacket.merchItem_Message((byte) 0x1d));
                    } else {
                        player.dropMessage(1, "An unknown error occured.");
                    }
                    break;
                }
            case 27:
                { // Exit
                    player.setConversation(0);
                    break;
                }
        }
    }

    private static final boolean check(final MapleCharacter chr, final MerchItemPackage pack) {
        if (chr.getMeso() + pack.getMesos() < 0) {
            return false;
        }
        byte eq = 0, use = 0, setup = 0, etc = 0, cash = 0;
        for (IItem item : pack.getItems()) {
            final MapleInventoryType invtype = GameConstants.getInventoryType(item.getItemId());
            if (invtype == MapleInventoryType.EQUIP) {
                eq++;
            } else if (invtype == MapleInventoryType.USE) {
                use++;
            } else if (invtype == MapleInventoryType.SETUP) {
                setup++;
            } else if (invtype == MapleInventoryType.ETC) {
                etc++;
            } else if (invtype == MapleInventoryType.CASH) {
                cash++;
            }
        }

        if (chr.getInventory(MapleInventoryType.EQUIP).getNumFreeSlot() <= eq
                || chr.getInventory(MapleInventoryType.USE).getNumFreeSlot() <= use
                || chr.getInventory(MapleInventoryType.SETUP).getNumFreeSlot() <= setup
                || chr.getInventory(MapleInventoryType.ETC).getNumFreeSlot() <= etc
                || chr.getInventory(MapleInventoryType.CASH).getNumFreeSlot() <= cash) {
            return false;
        }
        return true;
    }

    private static final boolean deletePackage(
            final int charid, final int accid, final int packageid) {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return false;
            }
            try (PreparedStatement ps =
                    con.prepareStatement(
                            "DELETE from hiredfishing where characterid = ? OR accountid = ? OR packageid = ?")) {
                ps.setInt(1, charid);
                ps.setInt(2, accid);
                ps.setInt(3, packageid);
                ps.execute();
                ItemLoader.HIRED_FISHING.saveItems(null, packageid, accid, charid);
                return true;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private static final MerchItemPackage loadItemFrom_Database(
            final int charid, final int accountid) {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return null;
            }

            try (PreparedStatement ps =
                    con.prepareStatement(
                            "SELECT * from hiredfishing where characterid = ? OR accountid = ?")) {
                ps.setInt(1, charid);
                ps.setInt(2, accountid);

                final int packageid;
                final MerchItemPackage pack = new MerchItemPackage();
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    packageid = rs.getInt("PackageId");
                    pack.setPackageid(packageid);
                    pack.setMesos(rs.getInt("Mesos"));
                    pack.setSentTime(rs.getLong("time"));
                }

                Map<Integer, Pair<IItem, MapleInventoryType>> items =
                        ItemLoader.HIRED_FISHING.loadItems(false, packageid, accountid, charid);
                if (items != null) {
                    List<IItem> iters = new ArrayList<>();
                    for (Pair<IItem, MapleInventoryType> z : items.values()) {
                        iters.add(z.left());
                    }
                    pack.setItems(iters);
                }

                return pack;
            }
        } catch (SQLException e) {
            LOG.error("HiredFishingHandler.loadItemFrom_Database 發生例外", e);
            return null;
        }
    }
}
