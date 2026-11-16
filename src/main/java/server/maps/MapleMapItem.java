package server.maps;

import client.MapleCharacter;
import client.MapleClient;
import client.inventory.IItem;
import java.awt.Point;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import tools.MaplePacketCreator;

public class MapleMapItem extends AbstractMapleMapObject {

    protected volatile IItem item;
    protected volatile MapleMapObject dropper;
    protected volatile int character_ownerid;
    protected final AtomicInteger meso = new AtomicInteger(0);
    protected volatile int questid = -1;
    protected volatile byte type;
    protected final AtomicBoolean pickedUp = new AtomicBoolean(false);
    protected volatile boolean playerDrop;
    protected volatile boolean randDrop = false;
    protected final AtomicLong nextExpiry = new AtomicLong(0);
    protected final AtomicLong nextFFA = new AtomicLong(0);
    private final ReentrantLock lock = new ReentrantLock();

    public MapleMapItem(
            IItem item,
            Point position,
            MapleMapObject dropper,
            MapleCharacter owner,
            byte type,
            boolean playerDrop) {
        setPosition(position);
        this.item = item;
        this.dropper = dropper;
        this.character_ownerid = owner.getId();
        this.type = type;
        this.playerDrop = playerDrop;
    }

    public MapleMapItem(
            IItem item,
            Point position,
            MapleMapObject dropper,
            MapleCharacter owner,
            byte type,
            boolean playerDrop,
            int questid) {
        setPosition(position);
        this.item = item;
        this.dropper = dropper;
        this.character_ownerid = owner.getId();
        this.type = type;
        this.playerDrop = playerDrop;
        this.questid = questid;
    }

    public MapleMapItem(
            int meso,
            Point position,
            MapleMapObject dropper,
            MapleCharacter owner,
            byte type,
            boolean playerDrop) {
        setPosition(position);
        this.item = null;
        this.dropper = dropper;
        this.character_ownerid = owner.getId();
        this.meso.set(meso);
        this.type = type;
        this.playerDrop = playerDrop;
    }

    public MapleMapItem(Point position, IItem item) {
        setPosition(position);
        this.item = item;
        this.character_ownerid = 0;
        this.type = 2;
        this.playerDrop = false;
        this.randDrop = true;
    }

    public final IItem getItem() {
        return item;
    }

    public void setItem(IItem z) {
        this.item = z;
    }

    public final int getQuest() {
        return questid;
    }

    public final int getItemId() {
        int m = meso.get();
        if (m > 0) {
            return m;
        }
        IItem it = item;
        return it == null ? 0 : it.getItemId();
    }

    public final MapleMapObject getDropper() {
        return dropper;
    }

    public final int getOwner() {
        return character_ownerid;
    }

    public final int getMeso() {
        return meso.get();
    }

    public final boolean isPlayerDrop() {
        return playerDrop;
    }

    public final boolean isPickedUp() {
        return pickedUp.get();
    }

    public void setPickedUp(final boolean pickedUp) {
        this.pickedUp.set(pickedUp);
    }

    public byte getDropType() {
        return type;
    }

    public void setDropType(byte z) {
        this.type = z;
    }

    public final boolean isRandDrop() {
        return randDrop;
    }

    @Override
    public final MapleMapObjectType getType() {
        return MapleMapObjectType.ITEM;
    }

    @Override
    public void sendSpawnData(final MapleClient client) {
        if (questid <= 0 || client.getPlayer().getQuestStatus(questid) == 1) {
            client.sendPacket(
                    MaplePacketCreator.dropItemFromMapObject(this, null, getPosition(), (byte) 2));
        }
    }

    @Override
    public void sendDestroyData(final MapleClient client) {
        client.sendPacket(MaplePacketCreator.removeItemFromMap(getObjectId(), 1, 0));
    }

    public Lock getLock() {
        return lock;
    }

    public void registerExpire(final long time) {
        nextExpiry.set(System.currentTimeMillis() + time);
    }

    public void registerFFA(final long time) {
        nextFFA.set(System.currentTimeMillis() + time);
    }

    public boolean shouldExpire() {
        long exp = nextExpiry.get();
        return !pickedUp.get() && exp > 0 && exp < System.currentTimeMillis();
    }

    public boolean shouldFFA() {
        long ffa = nextFFA.get();
        return !pickedUp.get() && type < 2 && ffa > 0 && ffa < System.currentTimeMillis();
    }

    public void expire(final MapleMap map) {
        if (!pickedUp.compareAndSet(false, true)) {
            return;
        }
        map.broadcastMessage(MaplePacketCreator.removeItemFromMap(getObjectId(), 0, 0));
        map.removeMapObject(this);
        if (randDrop) {
            map.spawnRandDrop();
        }
    }
}
