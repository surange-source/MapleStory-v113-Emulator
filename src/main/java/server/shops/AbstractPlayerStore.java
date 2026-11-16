package server.shops;

import client.MapleCharacter;
import client.MapleClient;
import client.inventory.IItem;
import client.inventory.ItemLoader;
import client.inventory.MapleInventoryType;
import constants.GameConstants;
import constants.ItemConstants;
import database.DatabaseConnection;
import handling.MaplePacket;
import handling.channel.ChannelServer;
import handling.world.World;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.maps.AbstractMapleMapObject;
import server.maps.MapleMap;
import server.maps.MapleMapObjectType;
import tools.Pair;
import tools.packet.PlayerShopPacket;

public abstract class AbstractPlayerStore extends AbstractMapleMapObject
        implements IMaplePlayerShop {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractPlayerStore.class);

    protected boolean open = false, available = false;
    protected String ownerName, des, pass;
    protected int ownerId, owneraccount, itemId, channel, map;
    protected AtomicInteger meso = new AtomicInteger(0);
    protected WeakReference<MapleCharacter> chrs[];
    protected List<String> visitors = new LinkedList<>();
    protected List<BoughtItem> bought = new LinkedList<>();
    protected List<MaplePlayerShopItem> items = new LinkedList<>();

    public AbstractPlayerStore(
            MapleCharacter owner, int itemId, String desc, String pass, int slots) {
        this.setPosition(owner.getPosition());
        this.ownerName = owner.getName();
        this.ownerId = owner.getId();
        this.owneraccount = owner.getAccountID();
        this.itemId = itemId;
        this.des = desc;
        this.pass = pass;
        this.map = owner.getMapId();
        this.channel = owner.getClient().getChannel();
        chrs = new WeakReference[slots];
        for (int i = 0; i < chrs.length; i++) {
            chrs[i] = new WeakReference<>(null);
        }
    }

    @Override
    public int getMaxSize() {
        return chrs.length + 1;
    }

    @Override
    public int getSize() {
        return getFreeSlot() == -1 ? getMaxSize() : getFreeSlot();
    }

    @Override
    public void broadcastToVisitors(MaplePacket packet) {
        broadcastToVisitors(packet, true);
    }

    public void broadcastToVisitors(MaplePacket packet, boolean owner) {
        for (WeakReference<MapleCharacter> chr : chrs) {
            if (chr != null && chr.get() != null) {
                chr.get().getClient().sendPacket(packet);
            }
        }
        if (getShopType() != IMaplePlayerShop.HIRED_MERCHANT && owner) {
            final MapleCharacter mcOwner = getMCOwner();
            if (mcOwner != null) {
                mcOwner.getClient().sendPacket(packet);
            }
        }
    }

    public void broadcastToVisitors(MaplePacket packet, int exception) {
        for (WeakReference<MapleCharacter> chr : chrs) {
            if (chr != null && chr.get() != null && getVisitorSlot(chr.get()) != exception) {
                chr.get().getClient().sendPacket(packet);
            }
        }
        if (getShopType() != IMaplePlayerShop.HIRED_MERCHANT && exception != ownerId) {
            final MapleCharacter mcOwner = getMCOwner();
            if (mcOwner != null) {
                mcOwner.getClient().sendPacket(packet);
            }
        }
    }

    @Override
    public int getMeso() {
        return meso.get();
    }

    @Override
    public void setMeso(int meso) {
        this.meso.set(meso);
    }

    @Override
    public void setOpen(boolean open) {
        this.open = open;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    public boolean saveItems() {
        return saveItems(true);
    }

    public boolean saveItems(boolean isMerch) {
        if (getShopType() != IMaplePlayerShop.HIRED_MERCHANT
                && getShopType() != IMaplePlayerShop.HIRED_FISHING) { // hired only
            return false;
        }
        // 白名單表名，避免 SQL 拼接
        final String table_name;
        if (isMerch) {
            table_name = "hiredmerch";
        } else {
            table_name = "hiredfishing";
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return false;
            }
            try {
                try (PreparedStatement ps =
                        con.prepareStatement(
                                "DELETE FROM "
                                        + table_name
                                        + " WHERE accountid = ? OR characterid = ?")) {
                    ps.setInt(1, owneraccount);
                    ps.setInt(2, ownerId);
                    ps.execute();
                }

                final int packageid;
                try (PreparedStatement ps =
                        con.prepareStatement(
                                "INSERT INTO "
                                        + table_name
                                        + " (characterid, accountid, Mesos, time) VALUES (?, ?, ?, ?)",
                                DatabaseConnection.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, ownerId);
                    ps.setInt(2, owneraccount);
                    ps.setInt(3, meso.get());
                    ps.setLong(4, System.currentTimeMillis());
                    ps.executeUpdate();

                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) {
                            throw new RuntimeException("Error, adding " + table_name + " to DB");
                        }
                        packageid = rs.getInt(1);
                    }
                }

                List<Pair<IItem, MapleInventoryType>> iters = new ArrayList<>();
                IItem item;
                for (MaplePlayerShopItem pItems : items) {
                    if (pItems.item == null || pItems.bundles <= 0) {
                        continue;
                    }
                    if (pItems.item.getQuantity() <= 0
                            && !ItemConstants.ItemTypes.isRechargable(pItems.item.getItemId())) {
                        continue;
                    }
                    item = pItems.item.copy();
                    item.setQuantity((short) (item.getQuantity() * pItems.bundles));
                    iters.add(new Pair<>(item, GameConstants.getInventoryType(item.getItemId())));
                }
                if (isMerch) {
                    ItemLoader.HIRED_MERCHANT.saveItems(iters, packageid, owneraccount, ownerId);
                } else {
                    ItemLoader.HIRED_FISHING.saveItems(iters, packageid, owneraccount, ownerId);
                }
                return true;
            } catch (SQLException se) {
                LOG.error("AbstractPlayerStore.saveItems 發生例外", se);
            }
        } catch (SQLException se) {
            LOG.error("AbstractPlayerStore.saveItems 發生例外", se);
        }
        return false;
    }

    public MapleCharacter getVisitor(int num) {
        return chrs[num].get();
    }

    @Override
    public void update() {
        if (isAvailable()) {
            if (getShopType() == IMaplePlayerShop.HIRED_MERCHANT) {
                getMap().broadcastMessage(
                                PlayerShopPacket.updateHiredMerchant((HiredMerchant) this));
            } else if (getShopType() == IMaplePlayerShop.HIRED_FISHING) {
                getMap().broadcastMessage(PlayerShopPacket.spawnHiredFishing((HiredFishing) this));
            } else if (getMCOwner() != null) {
                getMap().broadcastMessage(PlayerShopPacket.sendPlayerShopBox(getMCOwner()));
            }
        }
    }

    @Override
    public void addVisitor(MapleCharacter visitor) {
        int i = getFreeSlot();
        if (i > 0) {
            if (getShopType() >= 3) {
                broadcastToVisitors(
                        PlayerShopPacket.getMiniGameNewVisitor(visitor, i, (MapleMiniGame) this));
            } else {
                broadcastToVisitors(PlayerShopPacket.shopVisitorAdd(visitor, i));
            }
            chrs[i - 1] = new WeakReference<>(visitor);
            if (!isOwner(visitor)) {
                visitors.add(visitor.getName());
            }
            if (i == 3) {
                update();
            }
        }
    }

    @Override
    public void removeVisitor(MapleCharacter visitor) {
        final byte slot = getVisitorSlot(visitor);
        boolean shouldUpdate = getFreeSlot() == -1;
        if (slot > 0) {
            broadcastToVisitors(PlayerShopPacket.shopVisitorLeave(slot), slot);
            chrs[slot - 1] = new WeakReference<>(null);
            if (shouldUpdate) {
                update();
            }
        }
    }

    @Override
    public byte getVisitorSlot(MapleCharacter visitor) {
        if (visitor == null) {
            return -1;
        }
        final int visitorId = visitor.getId();
        for (byte i = 0; i < chrs.length; i++) {
            final MapleCharacter chr = chrs[i] == null ? null : chrs[i].get();
            if (chr != null && chr.getId() == visitorId) {
                return (byte) (i + 1);
            }
        }
        if (visitorId == ownerId) { // can visit own store in merch, otherwise not.
            return 0;
        }
        return -1;
    }

    @Override
    public void removeAllVisitors(int error, int type) {
        for (int i = 0; i < chrs.length; i++) {
            MapleCharacter visitor = getVisitor(i);
            if (visitor != null) {
                if (type != -1) {
                    visitor.getClient().sendPacket(PlayerShopPacket.shopErrorMessage(error, type));
                }
                broadcastToVisitors(
                        PlayerShopPacket.shopVisitorLeave(getVisitorSlot(visitor)),
                        getVisitorSlot(visitor));
                visitor.setPlayerShop(null);
                chrs[i] = new WeakReference<>(null);
            }
        }
        update();
    }

    @Override
    public String getOwnerName() {
        return ownerName;
    }

    @Override
    public int getOwnerId() {
        return ownerId;
    }

    @Override
    public int getOwnerAccId() {
        return owneraccount;
    }

    @Override
    public String getDescription() {
        if (des == null) {
            return "";
        }
        return des;
    }

    @Override
    public List<Pair<Byte, MapleCharacter>> getVisitors() {
        List<Pair<Byte, MapleCharacter>> chrz = new LinkedList<>();
        for (byte i = 0; i < chrs.length; i++) { // include owner or no
            if (chrs[i] != null && chrs[i].get() != null) {
                chrz.add(new Pair<>((byte) (i + 1), chrs[i].get()));
            }
        }
        return chrz;
    }

    @Override
    public List<MaplePlayerShopItem> getItems() {
        return items;
    }

    @Override
    public void addItem(MaplePlayerShopItem item) {
        //       System.out.println("Adding item ... 2");
        items.add(item);
    }

    @Override
    public boolean removeItem(int item) {
        return false;
    }

    @Override
    public void removeFromSlot(int slot) {
        items.remove(slot);
    }

    @Override
    public byte getFreeSlot() {
        for (byte i = 0; i < chrs.length; i++) {
            if (chrs[i] == null || chrs[i].get() == null) {
                return (byte) (i + 1);
            }
        }
        return -1;
    }

    @Override
    public int getItemId() {
        return itemId;
    }

    @Override
    public boolean isOwner(MapleCharacter chr) {
        return chr.getId() == ownerId && chr.getName().equals(ownerName);
    }

    @Override
    public String getPassword() {
        if (pass == null) {
            return "";
        }
        return pass;
    }

    @Override
    public void sendDestroyData(MapleClient client) {}

    @Override
    public void sendSpawnData(MapleClient client) {}

    @Override
    public MapleMapObjectType getType() {
        return MapleMapObjectType.SHOP;
    }

    public MapleCharacter getMCOwner() {
        MapleMap m = getMap();
        if (m == null) {
            return null;
        }
        return m.getCharacterById(ownerId);
    }

    public MapleCharacter getMCOwnerWorld() {
        int ourChannel = World.Find.findChannel(ownerId);
        if (ourChannel <= 0) {
            return null;
        }
        return ChannelServer.getInstance(ourChannel).getPlayerStorage().getCharacterById(ownerId);
    }

    public MapleMap getMap() {
        return ChannelServer.getInstance(channel).getMapFactory().getMap(map);
    }

    @Override
    public int getGameType() {
        if (getShopType() == IMaplePlayerShop.HIRED_MERCHANT) { // hiredmerch
            return 5;
        } else if (getShopType() == IMaplePlayerShop.PLAYER_SHOP) { // shop lol
            return 4;
        } else if (getShopType() == IMaplePlayerShop.OMOK) { // omok
            return 1;
        } else if (getShopType() == IMaplePlayerShop.MATCH_CARD) { // matchcard
            return 2;
        }
        return 0;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void setAvailable(boolean b) {
        this.available = b;
    }

    @Override
    public List<BoughtItem> getBoughtItems() {
        return bought;
    }

    public static final class BoughtItem {

        public int id;
        public int quantity;
        public int totalPrice;
        public String buyer;

        public BoughtItem(
                final int id, final int quantity, final int totalPrice, final String buyer) {
            this.id = id;
            this.quantity = quantity;
            this.totalPrice = totalPrice;
            this.buyer = buyer;
        }
    }
}
