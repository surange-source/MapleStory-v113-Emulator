package server.shops;

import client.MapleCharacter;
import client.MapleClient;
import client.inventory.IItem;
import client.inventory.ItemFlag;
import constants.GameConstants;
import handling.channel.ChannelServer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import server.MapleInventoryManipulator;
import server.MapleItemInformationProvider;
import server.Timer.EtcTimer;
import server.maps.MapleMapObjectType;
import tools.MaplePacketCreator;
import tools.packet.PlayerShopPacket;

public class HiredMerchant extends AbstractPlayerStore {

    public ScheduledFuture<?> schedule;
    private List<String> blacklist;
    private Map<String, Byte> MsgList;
    private int storeid;
    private long start;
    private ReentrantReadWriteLock merchantLock = new ReentrantReadWriteLock();

    public HiredMerchant(MapleCharacter owner, int itemId, String desc) {
        super(owner, itemId, desc, "", 3);
        start = System.currentTimeMillis();
        blacklist = new LinkedList<>();
        MsgList = new HashMap<>();
        this.schedule =
                EtcTimer.getInstance()
                        .schedule(
                                new Runnable() {

                                    @Override
                                    public void run() {
                                        closeShop(true, true);
                                    }
                                },
                                1000 * 60 * 60 * 24);
    }

    public byte getShopType() {
        return IMaplePlayerShop.HIRED_MERCHANT;
    }

    public final void setStoreid(final int storeid) {
        this.storeid = storeid;
    }

    public List<MaplePlayerShopItem> searchItem(final int itemSearch) {
        final List<MaplePlayerShopItem> itemz = new LinkedList<>();
        for (MaplePlayerShopItem item : items) {
            if (item.item.getItemId() == itemSearch && item.bundles > 0) {
                itemz.add(item);
            }
        }
        return itemz;
    }

    @Override
    public void buy(MapleClient c, int item, short quantity) {
        final MaplePlayerShopItem pItem = items.get(item);
        synchronized (pItem) {
            final IItem shopItem = pItem.item;
            final IItem newItem = shopItem.copy();
            final short perbundle = newItem.getQuantity();
            final int theQuantity = (pItem.price * quantity);
            newItem.setQuantity((short) (quantity * perbundle));

            byte flag = newItem.getFlag();

            if (ItemFlag.KARMA_EQ.check(flag)) {
                newItem.setFlag((byte) (flag - ItemFlag.KARMA_EQ.getValue()));
            } else if (ItemFlag.KARMA_USE.check(flag)) {
                newItem.setFlag((byte) (flag - ItemFlag.KARMA_USE.getValue()));
            }

            if (MapleInventoryManipulator.checkSpace(
                    c, newItem.getItemId(), newItem.getQuantity(), newItem.getOwner())) {
                final int gainmeso =
                        getMeso() + theQuantity - GameConstants.EntrustedStoreTax(theQuantity);
                if (gainmeso > 0) {
                    if (MapleInventoryManipulator.addFromDrop(c, newItem, false)) {
                        setMeso(gainmeso);
                        pItem.bundles -= quantity; // Number remaining in the store
                        bought.add(
                                new BoughtItem(
                                        newItem.getItemId(),
                                        quantity,
                                        theQuantity,
                                        c.getPlayer().getName()));
                        c.getPlayer().gainMeso(-theQuantity, false);
                        MapleCharacter chr = getMCOwnerWorld();
                        if (chr != null) {
                            chr.dropMessage(
                                    5,
                                    "物品 "
                                            + MapleItemInformationProvider.getInstance()
                                                    .getName(newItem.getItemId())
                                            + " ("
                                            + perbundle
                                            + ") x "
                                            + quantity
                                            + " 已從精靈商店賣出. 還剩下 "
                                            + pItem.bundles
                                            + "個");
                        }
                    } else {
                        c.getPlayer().dropMessage(1, "您的背包滿了.");
                        c.sendPacket(MaplePacketCreator.enableActions());
                    }
                } else {
                    c.getPlayer().dropMessage(1, "拍賣家有太多錢了.");
                    c.sendPacket(MaplePacketCreator.enableActions());
                }
            } else {
                c.getPlayer().dropMessage(1, "您的背包滿了.");
                c.sendPacket(MaplePacketCreator.enableActions());
            }
        }
    }

    @Override
    public void closeShop(boolean saveItems, boolean remove) {
        merchantLock.writeLock().lock();
        try {
            if (schedule != null) {
                schedule.cancel(false);
            }
            if (saveItems) {
                saveItems();
                items.clear();
            }
            if (remove) {
                ChannelServer.getInstance(channel).removeMerchant(this);
                getMap().broadcastMessage(PlayerShopPacket.destroyHiredMerchant(getOwnerId()));
            }
            getMap().removeMapObject(this);
            schedule = null;
        } finally {
            merchantLock.writeLock().unlock();
        }
    }

    public int getTimeLeft() {
        return (int) ((System.currentTimeMillis() - start) / 1000);
    }

    public final int getStoreId() {
        return storeid;
    }

    @Override
    public MapleMapObjectType getType() {
        return MapleMapObjectType.HIRED_MERCHANT;
    }

    @Override
    public void sendDestroyData(MapleClient client) {
        if (isAvailable()) {
            client.sendPacket(PlayerShopPacket.destroyHiredMerchant(getOwnerId()));
        }
    }

    @Override
    public void sendSpawnData(MapleClient client) {
        if (isAvailable()) {
            client.sendPacket(PlayerShopPacket.spawnHiredMerchant(this));
        }
    }

    public final boolean isInBlackList(final String bl) {
        return blacklist.contains(bl);
    }

    public final void addBlackList(final String bl) {
        blacklist.add(bl);
    }

    public final void removeBlackList(final String bl) {
        blacklist.remove(bl);
    }

    public final void sendBlackList(final MapleClient c) {
        c.sendPacket(PlayerShopPacket.MerchantBlackListView(blacklist));
    }

    public final void sendVisitor(final MapleClient c) {
        c.sendPacket(PlayerShopPacket.MerchantVisitorView(visitors));
    }

    public final void addMsg(final String msg, final byte slot) {
        MsgList.put(msg, slot);
    }

    public final void SendMsg(final MapleClient c) {
        for (final Entry<String, Byte> s : MsgList.entrySet()) {
            c.sendPacket(PlayerShopPacket.shopChat(s.getKey(), s.getValue()));
        }
    }
}
