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
import tools.Pair;
import tools.data.input.SeekableLittleEndianAccessor;
import tools.packet.PlayerShopPacket;

public class HiredMerchantHandler {
    private static final Logger LOG = LoggerFactory.getLogger(HiredMerchantHandler.class);

    public static final void UseHiredMerchant(
            final SeekableLittleEndianAccessor slea, final MapleClient c) {
        //	slea.readInt(); // TimeStamp

        if (c.getPlayer().getMap().allowPersonalShop()) {
            final byte state = checkExistance(c.getPlayer().getAccountID(), c.getPlayer().getId());

            switch (state) {
                case 1:
                    c.getPlayer().dropMessage(1, "請先去找富蘭德里領取你之前擺的東西");
                    break;
                case 0:
                    boolean merch = World.hasMerchant(c.getPlayer().getAccountID());
                    if (!merch) {
                        c.sendPacket(PlayerShopPacket.sendTitleBox());
                    } else {
                        c.getPlayer().dropMessage(1, "請換個地方開或者是你已經有開了");
                    }
                    break;
                default:
                    c.getPlayer().dropMessage(1, "An unknown error occured.");
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
            try (PreparedStatement ps
                    = con.prepareStatement(
                            "SELECT 1 FROM hiredmerch WHERE accountid = ? OR characterid = ?")) {
                ps.setInt(1, accid);
                ps.setInt(2, charid);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? (byte) 1 : (byte) 0;
                }
            }
        } catch (SQLException se) {
            return -1;
        }
    }

    public static final void MerchantItemStore(
            final SeekableLittleEndianAccessor slea, final MapleClient c) {
        if (c.getPlayer() == null) {
            return;
        }
        final byte operation = slea.readByte();

        switch (operation) {
            case 20: {
                slea.readMapleAsciiString();

                final int conv = c.getPlayer().getConversation();
                boolean merch = World.hasMerchant(c.getPlayer().getAccountID());
                if (merch) {
                    c.getPlayer().dropMessage(1, "請關閉商店後在試一次.");
                    c.getPlayer().setConversation(0);
                } else if (conv == 3) { // Hired Merch
                    final MerchItemPackage pack
                            = loadItemFrom_Database(
                                    c.getPlayer().getId(), c.getPlayer().getAccountID());

                    if (pack == null) {
                        c.getPlayer().dropMessage(1, "你沒有在這邊置放道具!");
                        c.getPlayer().setConversation(0);
                    } else if (pack.getItems().size() <= 0) { // error fix for complainers.
                        if (!check(c.getPlayer(), pack)) {
                            c.sendPacket(PlayerShopPacket.merchItem_Message((byte) 0x21));
                            return;
                        }
                        if (deletePackage(
                                c.getPlayer().getId(),
                                c.getPlayer().getAccountID(),
                                pack.getPackageid())) {
                            c.getPlayer().gainMeso(pack.getMesos(), false);
                            c.sendPacket(PlayerShopPacket.merchItem_Message((byte) 0x1d));
                        } else {
                            c.getPlayer().dropMessage(1, "An unknown error occured.");
                        }
                    } else {
                        c.sendPacket(PlayerShopPacket.merchItemStore_ItemData(pack));
                    }
                }
                break;
            }
            case 25: { // Request take out iteme
                if (c.getPlayer().getConversation() != 3) {
                    return;
                }
                c.sendPacket(PlayerShopPacket.merchItemStore((byte) 0x24));
                break;
            }
            case 26: { // Take out item
                if (c.getPlayer().getConversation() != 3) {
                    return;
                }
                final MerchItemPackage pack
                        = loadItemFrom_Database(
                                c.getPlayer().getId(), c.getPlayer().getAccountID());

                if (pack == null) {
                    c.getPlayer().dropMessage(1, "An unknown error occured.");
                    return;
                }
                if (!check(c.getPlayer(), pack)) {
                    c.sendPacket(PlayerShopPacket.merchItem_Message((byte) 0x21));
                    return;
                }
                if (deletePackage(
                        c.getPlayer().getId(),
                        c.getPlayer().getAccountID(),
                        pack.getPackageid())) {
                    c.getPlayer().gainMeso(pack.getMesos(), false);
                    for (IItem item : pack.getItems()) {
                        MapleInventoryManipulator.addFromDrop(c, item, false);
                    }
                    c.sendPacket(PlayerShopPacket.merchItem_Message((byte) 0x1d));
                } else {
                    c.getPlayer().dropMessage(1, "An unknown error occured.");
                }
                break;
            }
            case 27: { // Exit
                c.getPlayer().setConversation(0);
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
            /* if (MapleItemInformationProvider.getInstance().isPickupRestricted(item.getItemId()) && chr.haveItem(item.getItemId(), 1)) {
            return false;
            }*/
        }
        /* if (chr.getInventory(MapleInventoryType.EQUIP).getNumFreeSlot() < eq || chr.getInventory(MapleInventoryType.USE).getNumFreeSlot() < use || chr.getInventory(MapleInventoryType.SETUP).getNumFreeSlot() < setup || chr.getInventory(MapleInventoryType.ETC).getNumFreeSlot() < etc || chr.getInventory(MapleInventoryType.CASH).getNumFreeSlot() < cash) {
        return false;
        }*/
        if (chr.getInventory(MapleInventoryType.EQUIP).getNumFreeSlot() <= eq
                || chr.getInventory(MapleInventoryType.USE).getNumFreeSlot() <= use
                || chr.getInventory(MapleInventoryType.SETUP).getNumFreeSlot() <= setup
                || chr.getInventory(MapleInventoryType.ETC).getNumFreeSlot() <= etc
                || chr.getInventory(MapleInventoryType.CASH).getNumFreeSlot() <= cash) {
            return false;
        }
        return true;
    }

    private static final boolean deletePackage(final int charid, final int accid, final int packageid) {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return false;
            }
            try (PreparedStatement ps
                    = con.prepareStatement("DELETE from hiredmerch where characterid = ? OR accountid = ? OR packageid = ?")) {
                ps.setInt(1, charid);
                ps.setInt(2, accid);
                ps.setInt(3, packageid);
                ps.execute();
                ItemLoader.HIRED_MERCHANT.saveItems(null, packageid, accid, charid);
                return true;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private static final MerchItemPackage loadItemFrom_Database(final int charid, final int accountid) {
        final int packageid;
        final MerchItemPackage pack = new MerchItemPackage();
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return null;
            }
            try (PreparedStatement ps
                    = con.prepareStatement(
                            "SELECT * FROM hiredmerch WHERE characterid = ? OR accountid = ?")) {
                ps.setInt(1, charid);
                ps.setInt(2, accountid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    packageid = rs.getInt("PackageId");
                    pack.setPackageid(packageid);
                    pack.setMesos(rs.getInt("Mesos"));
                    pack.setSentTime(rs.getLong("time"));
                }
            }
        } catch (SQLException e) {
            LOG.error("HiredMerchantHandler.loadItemFrom_Database 發生例外", e);
            return null;
        }

        try {
            Map<Integer, Pair<IItem, MapleInventoryType>> items
                    = ItemLoader.HIRED_MERCHANT.loadItems(false, packageid, accountid, charid);
            if (items != null) {
                List<IItem> iters = new ArrayList<>();
                for (Pair<IItem, MapleInventoryType> z : items.values()) {
                    iters.add(z.left());
                }
                pack.setItems(iters);
            }
        } catch (SQLException e) {
            LOG.error("HiredMerchantHandler.loadItemFrom_Database 發生例外", e);
            return null;
        }

        return pack;
    }
}
