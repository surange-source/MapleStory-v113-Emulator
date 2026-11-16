package server.shops;

import client.MapleCharacter;
import client.MapleClient;
import handling.channel.ChannelServer;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import server.Timer.EtcTimer;
import server.maps.MapleMapObjectType;
import tools.MaplePacketCreator;
import tools.packet.PlayerShopPacket;

public class HiredFishing extends AbstractPlayerStore {

    public ScheduledFuture<?> schedule;
    private int storeid;
    private long start;
    private ReentrantReadWriteLock FishingLock = new ReentrantReadWriteLock();

    public HiredFishing(MapleCharacter owner, int itemId) {
        super(owner, itemId, "", "", 3);
        start = System.currentTimeMillis();
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
        return IMaplePlayerShop.HIRED_FISHING;
    }

    public final void setStoreid(final int storeid) {
        this.storeid = storeid;
    }

    @Override
    public void buy(MapleClient c, int item, short quantity) {
        // hack ?
        c.getPlayer().dropMessage(1, "Hacking?");
        c.sendPacket(MaplePacketCreator.enableActions());
        return;
    }

    @Override
    public void closeShop(boolean saveItems, boolean remove) {
        FishingLock.writeLock().lock();
        try {
            if (schedule != null) {
                schedule.cancel(false);
            }
            if (saveItems) {
                saveItems(false);
                //              items.clear();
            }
            if (remove) {
                ChannelServer.getInstance(channel).removeFishing(this);
                getMap().broadcastMessage(PlayerShopPacket.destroyHiredMerchant(getOwnerId()));
            }
            getMap().removeMapObject(this);
            schedule = null;
        } finally {
            FishingLock.writeLock().unlock();
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
        return MapleMapObjectType.HIRED_FISHING;
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
            client.sendPacket(PlayerShopPacket.spawnHiredFishing(this));
        }
    }
}
