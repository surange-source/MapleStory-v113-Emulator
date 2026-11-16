package handling.channel.handler;

import client.MapleCharacter;
import client.MapleCharacterUtil;
import client.MapleClient;
import client.inventory.IItem;
import client.inventory.ItemFlag;
import client.inventory.ItemLoader;
import client.inventory.MapleInventoryType;
import constants.GameConstants;
import constants.ItemConstants;
import database.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.MapleDueyActions;
import server.MapleInventoryManipulator;
import server.MapleItemInformationProvider;
import tools.MaplePacketCreator;
import tools.Pair;
import tools.data.input.SeekableLittleEndianAccessor;

public class DueyHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DueyHandler.class.getName());

    /*
     * 19 = Successful
     * 18 = One-of-a-kind Item is already in Reciever's delivery
     * 17 = The Character is unable to recieve the parcel
     * 15 = Same account
     * 14 = Name does not exist
     */
    public static final void DueyOperation(
            final SeekableLittleEndianAccessor slea, final MapleClient c) {

        final byte operation = slea.readByte();

        switch (operation) {
            case 1:
                { // Start Duey, 13 digit AS
                    final String AS13Digit = slea.readMapleAsciiString();
                    // int unk = slea.readInt(); // Theres an int here, value = 1
                    // 9 = error
                    final int conv = c.getPlayer().getConversation();

                    if (conv == 2) { // Duey
                        c.sendPacket(
                                MaplePacketCreator.sendDuey((byte) 10, loadItems(c.getPlayer())));
                    }
                    break;
                }
            case 3:
                { // Send Item
                    if (c.getPlayer().getConversation() != 2) {
                        return;
                    }
                    final byte inventId = slea.readByte();
                    final short itemPos = slea.readShort();
                    final short amount = slea.readShort();
                    final int mesos = slea.readInt();
                    final String recipient = slea.readMapleAsciiString();
                    final boolean quickdelivery = slea.readByte() > 0;

                    final int finalcost =
                            mesos + GameConstants.getTaxAmount(mesos) + (quickdelivery ? 0 : 5000);

                    if (mesos >= 0 && mesos <= 100000000 && c.getPlayer().getMeso() >= finalcost) {
                        final int accid = MapleCharacterUtil.getIdByName(recipient);
                        if (accid != -1) {
                            if (accid != c.getAccID()) {
                                final boolean recipientOn = false;
                                final MapleClient rClient = null;
                                /*        try {
                                int channel = c.getChannelServer().getWorldInterface().find(recipient);
                                if (channel > -1) {
                                recipientOn = true;
                                ChannelServer rcserv = ChannelServer.getInstance(channel);
                                rClient = rcserv.getPlayerStorage().getCharacterByName(recipient).getClient();
                                }
                                } catch (RemoteException re) {
                                c.getChannelServer().reconnectWorld();
                                }*/

                                if (inventId > 0) {
                                    final MapleInventoryType inv =
                                            MapleInventoryType.getByType(inventId);
                                    final IItem item =
                                            c.getPlayer().getInventory(inv).getItem(itemPos);
                                    if (item == null) {
                                        c.sendPacket(
                                                MaplePacketCreator.sendDuey(
                                                        (byte) 17, null)); // Unsuccessfull
                                        return;
                                    }
                                    final byte flag = item.getFlag();
                                    if (ItemFlag.UNTRADEABLE.check(flag)
                                            || ItemFlag.LOCK.check(flag)) {
                                        c.sendPacket(MaplePacketCreator.enableActions());
                                        return;
                                    }
                                    if (c.getPlayer().getItemQuantity(item.getItemId(), false)
                                            >= amount) {
                                        final MapleItemInformationProvider ii =
                                                MapleItemInformationProvider.getInstance();
                                        if (!ii.isDropRestricted(item.getItemId())
                                                && !ii.isAccountShared(item.getItemId())) {
                                            if (addItemToDB(
                                                    item,
                                                    amount,
                                                    mesos,
                                                    c.getPlayer().getName(),
                                                    accid,
                                                    recipientOn)) {
                                                if (ItemConstants.ItemTypes.isThrowingStar(
                                                                item.getItemId())
                                                        || ItemConstants.ItemTypes.isBullet(
                                                                item.getItemId())) {
                                                    MapleInventoryManipulator.removeFromSlot(
                                                            c,
                                                            inv,
                                                            itemPos,
                                                            item.getQuantity(),
                                                            true);
                                                } else {
                                                    MapleInventoryManipulator.removeFromSlot(
                                                            c, inv, itemPos, amount, true, false);
                                                }
                                                c.getPlayer().gainMeso(-finalcost, false);
                                                c.sendPacket(
                                                        MaplePacketCreator.sendDuey(
                                                                (byte) 19, null)); // Successfull
                                            } else {
                                                c.sendPacket(
                                                        MaplePacketCreator.sendDuey(
                                                                (byte) 17, null)); // Unsuccessful
                                            }
                                        } else {
                                            c.sendPacket(
                                                    MaplePacketCreator.sendDuey(
                                                            (byte) 17, null)); // Unsuccessfull
                                        }
                                    } else {
                                        c.sendPacket(
                                                MaplePacketCreator.sendDuey(
                                                        (byte) 17, null)); // Unsuccessfull
                                    }
                                } else {
                                    if (addMesoToDB(
                                            mesos, c.getPlayer().getName(), accid, recipientOn)) {
                                        c.getPlayer().gainMeso(-finalcost, false);

                                        c.sendPacket(
                                                MaplePacketCreator.sendDuey(
                                                        (byte) 19, null)); // Successfull
                                    } else {
                                        c.sendPacket(
                                                MaplePacketCreator.sendDuey(
                                                        (byte) 17, null)); // Unsuccessfull
                                    }
                                }
                                // if (recipientOn && rClient != null) {
                                //
                                // rClient.sendPacket(MaplePacketCreator.sendDueyMSG(Actions.PACKAGE_MSG.getCode()));
                                // }
                            } else {
                                c.sendPacket(
                                        MaplePacketCreator.sendDuey(
                                                (byte) 15, null)); // Same acc error
                            }
                        } else {
                            c.sendPacket(
                                    MaplePacketCreator.sendDuey(
                                            (byte) 14, null)); // Name does not exist
                        }
                    } else {
                        c.sendPacket(
                                MaplePacketCreator.sendDuey((byte) 12, null)); // Not enough mesos
                    }
                    break;
                }
            case 5:
                { // Recieve Package
                    if (c.getPlayer().getConversation() != 2) {
                        return;
                    }
                    final int packageid = slea.readInt();
                    // System.out.println("Item attempted : " + packageid);
                    final MapleDueyActions dp = loadSingleItem(packageid, c.getPlayer().getId());
                    if (dp == null) {
                        return;
                    }
                    if (dp.getItem() != null
                            && !MapleInventoryManipulator.checkSpace(
                                    c,
                                    dp.getItem().getItemId(),
                                    dp.getItem().getQuantity(),
                                    dp.getItem().getOwner())) {
                        c.sendPacket(
                                MaplePacketCreator.sendDuey((byte) 16, null)); // Not enough Space
                        return;
                    } else if (dp.getMesos() < 0 || (dp.getMesos() + c.getPlayer().getMeso()) < 0) {
                        c.sendPacket(MaplePacketCreator.sendDuey((byte) 17, null)); // Unsuccessfull
                        return;
                    }
                    removeItemFromDB(packageid, c.getPlayer().getId()); // Remove first
                    // System.out.println("Item removed : " + packageid);
                    if (dp.getItem() != null) {
                        MapleInventoryManipulator.addFromDrop(c, dp.getItem(), false);
                    }
                    if (dp.getMesos() != 0) {
                        c.getPlayer().gainMeso(dp.getMesos(), false);
                    }
                    c.sendPacket(MaplePacketCreator.removeItemFromDuey(false, packageid));
                    break;
                }
            case 6:
                { // Remove package
                    if (c.getPlayer().getConversation() != 2) {
                        return;
                    }
                    final int packageid = slea.readInt();
                    removeItemFromDB(packageid, c.getPlayer().getId());
                    c.sendPacket(MaplePacketCreator.removeItemFromDuey(true, packageid));
                    break;
                }
            case 8:
                { // Close Duey
                    c.getPlayer().setConversation(0);
                    break;
                }
            default:
                {
                    IO.println("Unhandled Duey operation : " + slea.toString());
                    break;
                }
        }
    }

    private static final boolean addMesoToDB(
            final int mesos, final String sName, final int recipientID, final boolean isOn) {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return false;
            }
            try (PreparedStatement ps =
                    con.prepareStatement(
                            "INSERT INTO dueypackages (RecieverId, SenderName, Mesos, TimeStamp, Checked, Type) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, recipientID);
                ps.setString(2, sName);
                ps.setInt(3, mesos);
                ps.setLong(4, System.currentTimeMillis());
                ps.setInt(5, isOn ? 0 : 1);
                ps.setInt(6, 3);

                ps.executeUpdate();

                return true;
            }
        } catch (SQLException se) {
            LOG.error("Failed to add meso to duey DB for recipient " + recipientID, se);
            return false;
        }
    }

    private static final boolean addItemToDB(
            final IItem item,
            final int quantity,
            final int mesos,
            final String sName,
            final int recipientID,
            final boolean isOn) {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return false;
            }
            try (PreparedStatement ps =
                    con.prepareStatement(
                            "INSERT INTO dueypackages (RecieverId, SenderName, Mesos, TimeStamp, Checked, Type) VALUES (?, ?, ?, ?, ?, ?)",
                            DatabaseConnection.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, recipientID);
                ps.setString(2, sName);
                ps.setInt(3, mesos);
                ps.setLong(4, System.currentTimeMillis() + (15L * 24L * 60L * 60L * 1000L));
                ps.setInt(5, isOn ? 0 : 1);

                ps.setInt(6, item.getType());
                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        ItemLoader.DUEY.saveItems(
                                Collections.singletonList(
                                        new Pair<>(
                                                item,
                                                GameConstants.getInventoryType(item.getItemId()))),
                                rs.getInt(1));
                    }
                }

                return true;
            }
        } catch (SQLException se) {
            LOG.error("Failed to add item to duey DB for recipient " + recipientID, se);
            return false;
        }
    }

    public static final List<MapleDueyActions> loadItems(final MapleCharacter chr) {
        if (chr == null) {
            return Collections.emptyList();
        }
        final List<MapleDueyActions> packages = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return Collections.emptyList();
            }
            try (PreparedStatement ps =
                    con.prepareStatement("SELECT * FROM dueypackages WHERE RecieverId = ?")) {
                ps.setInt(1, chr.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        final int packageid = rs.getInt("packageid");
                        final MapleDueyActions dueypack = getItemByPID(packageid);
                        dueypack.setSender(rs.getString("SenderName"));
                        dueypack.setMesos(rs.getInt("Mesos"));
                        dueypack.setSentTime(rs.getLong("TimeStamp"));
                        if (dueypack.getSentTime() > System.currentTimeMillis()) {
                            packages.add(dueypack);
                        } else {
                            removeItemFromDB(packageid, chr.getId());
                        }
                    }
                }
            }
            return packages;
        } catch (SQLException se) {
            LOG.error("Failed to load duey items for character " + chr.getId(), se);
            return Collections.emptyList();
        }
    }

    public static final MapleDueyActions loadSingleItem(final int packageid, final int charid) {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return null;
            }
            try (PreparedStatement ps =
                    con.prepareStatement(
                            "SELECT * FROM dueypackages WHERE PackageId = ? and RecieverId = ?")) {
                ps.setInt(1, packageid);
                ps.setInt(2, charid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        final MapleDueyActions dueypack = getItemByPID(packageid);
                        dueypack.setSender(rs.getString("SenderName"));
                        dueypack.setMesos(rs.getInt("Mesos"));
                        dueypack.setSentTime(rs.getLong("TimeStamp"));
                        if (dueypack.getSentTime() > System.currentTimeMillis()) {
                            return dueypack;
                        } else {
                            removeItemFromDB(packageid, charid);
                            return null;
                        }
                    } else {
                        return null;
                    }
                }
            }
        } catch (SQLException se) {
            LOG.warn("Failed to load single duey item " + packageid + " for character " + charid,
                    se);
            return null;
        }
    }

    public static final void reciveMsg(final MapleClient c, final int recipientId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }
            try (PreparedStatement ps =
                    con.prepareStatement(
                            "UPDATE dueypackages SET Checked = 0 where RecieverId = ?")) {
                ps.setInt(1, recipientId);
                ps.executeUpdate();
            }
        } catch (SQLException se) {
            LOG.error("Failed to update duey checked status for recipient " + recipientId,
                    se);
        }
    }

    private static final void removeItemFromDB(final int packageid, final int charid) {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }
            try (PreparedStatement ps =
                    con.prepareStatement(
                            "DELETE FROM dueypackages WHERE PackageId = ? and RecieverId = ?")) {
                ps.setInt(1, packageid);
                ps.setInt(2, charid);
                ps.executeUpdate();
            }
        } catch (SQLException se) {
            LOG.error("Failed to remove duey package " + packageid + " for character " + charid,
                    se);
        }
    }

    private static final MapleDueyActions getItemByPID(final int packageid) {
        try {
            final Map<Integer, Pair<IItem, MapleInventoryType>> iter =
                    ItemLoader.DUEY.loadItems(false, packageid);
            if (iter != null && !iter.isEmpty()) {
                for (Pair<IItem, MapleInventoryType> i : iter.values()) {
                    return new MapleDueyActions(packageid, i.left());
                }
            }
        } catch (Exception se) {
            LOG.warn("Failed to load duey item payload for package " + packageid, se);
        }
        return new MapleDueyActions(packageid);
    }
}
