package server.maps;

import client.MapleBuffStat;
import client.MapleCharacter;
import client.MapleClient;
import client.inventory.Equip;
import client.inventory.IItem;
import client.inventory.Item;
import client.inventory.MapleInventoryType;
import client.inventory.MaplePet;
import client.status.MonsterStatus;
import client.status.MonsterStatusEffect;
import constants.GameConstants;
import database.DatabaseConnection;
import handling.MaplePacket;
import handling.channel.ChannelServer;
import handling.world.MaplePartyCharacter;
import handling.world.PartyOperation;
import handling.world.World;
import java.awt.Point;
import java.awt.Rectangle;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripting.EventManager;
import server.MapleCarnivalFactory;
import server.MapleCarnivalFactory.MCSkill;
import server.MapleInventoryManipulator;
import server.MapleItemInformationProvider;
import server.MaplePortal;
import server.MapleSquad;
import server.MapleSquad.MapleSquadType;
import server.MapleStatEffect;
import server.Randomizer;
import server.SpeedRunner;
import server.Timer.MapTimer;
import server.events.MapleEvent;
import server.life.MapleLifeFactory;
import server.life.MapleMonster;
import server.life.MapleMonsterInformationProvider;
import server.life.MapleMonsterStats;
import server.life.MapleNPC;
import server.life.MonsterDropEntry;
import server.life.MonsterGlobalDropEntry;
import server.life.SpawnPoint;
import server.life.SpawnPointAreaBoss;
import server.life.Spawns;
import server.maps.MapleNodes.MapleNodeInfo;
import server.maps.MapleNodes.MaplePlatform;
import server.maps.MapleNodes.MonsterPoint;
import tools.MaplePacketCreator;
import tools.Pair;
import tools.StringUtil;
import tools.packet.MobPacket;
import tools.packet.PetPacket;

public final class MapleMap {
    private static final Logger LOG = LoggerFactory.getLogger(MapleMap.class);

    /*
     * Holds mappings of OID -> MapleMapObject separated by MapleMapObjectType.
     * Please acquire the appropriate lock when reading and writing to the LinkedHashMaps.
     * The MapObjectType Maps themselves do not need to synchronized in any way since they should never be modified.
     */
    private final Map<MapleMapObjectType, LinkedHashMap<Integer, MapleMapObject>> mapobjects;
    private final Map<MapleMapObjectType, ReentrantReadWriteLock> mapobjectlocks;
    private final List<MapleCharacter> characters = new ArrayList<>();
    private final ReentrantReadWriteLock charactersLock = new ReentrantReadWriteLock();
    private int runningOid = 100000;
    private final Lock runningOidLock = new ReentrantLock();
    private final List<Spawns> monsterSpawn = new ArrayList<>();
    private final AtomicInteger spawnedMonstersOnMap = new AtomicInteger(0);
    private final Map<Integer, MaplePortal> portals = new HashMap<>();
    private final Map<Integer, Long> dchere = new ConcurrentHashMap<>();
    private MapleFootholdTree footholds = null;
    private float monsterRate, recoveryRate;
    private MapleMapEffect mapEffect;
    private byte channel;
    private short decHP = 0, createMobInterval = 9000;
    private int consumeItemCoolTime = 0,
            protectItem = 0,
            decHPInterval = 10000,
            mapid,
            returnMapId,
            timeLimit,
            fieldLimit,
            maxRegularSpawn = 0,
            fixedMob,
            forcedReturnMap = 999999999,
            lvForceMove = 0,
            lvLimit = 0,
            permanentWeather = 0;
    private boolean town,
            clock,
            personalShop,
            everlast = false,
            dropsDisabled = false,
            gDropsDisabled = false,
            soaring = false,
            squadTimer = false,
            isSpawns = true;
    private String mapName, streetName, onUserEnter, onFirstUserEnter, speedRunLeader = "";
    private List<Integer> dced = new ArrayList<>();
    private ScheduledFuture<?> squadSchedule;
    private long speedRunStart = 0, lastSpawnTime = 0, lastHurtTime = 0;
    private MapleNodes nodes;
    private MapleSquadType squad;
    private Map<String, Integer> environment = new LinkedHashMap<>();

    public MapleMap(
            final int mapid, final int channel, final int returnMapId, final float monsterRate) {
        this.mapid = mapid;
        this.channel = (byte) channel;
        this.returnMapId = returnMapId;
        if (this.returnMapId == 999999999) {
            this.returnMapId = mapid;
        }
        this.monsterRate = monsterRate;
        EnumMap<MapleMapObjectType, LinkedHashMap<Integer, MapleMapObject>> objsMap
                = new EnumMap<>(MapleMapObjectType.class);
        EnumMap<MapleMapObjectType, ReentrantReadWriteLock> objlockmap
                = new EnumMap<>(MapleMapObjectType.class);
        for (MapleMapObjectType type : MapleMapObjectType.values()) {
            objsMap.put(type, new LinkedHashMap<>());
            objlockmap.put(type, new ReentrantReadWriteLock());
        }
        mapobjects = Collections.unmodifiableMap(objsMap);
        mapobjectlocks = Collections.unmodifiableMap(objlockmap);
    }

    public final void setSpawns(final boolean fm) {
        this.isSpawns = fm;
    }

    public final boolean getSpawns() {
        return isSpawns;
    }

    public final void setFixedMob(int fm) {
        this.fixedMob = fm;
    }

    public final void setForceMove(int fm) {
        this.lvForceMove = fm;
    }

    public final int getForceMove() {
        return lvForceMove;
    }

    public final void setLevelLimit(int fm) {
        this.lvLimit = fm;
    }

    public final int getLevelLimit() {
        return lvLimit;
    }

    public final void setReturnMapId(int rmi) {
        this.returnMapId = rmi;
    }

    public final void setSoaring(boolean b) {
        this.soaring = b;
    }

    public final boolean canSoar() {
        return soaring;
    }

    public final void toggleDrops() {
        this.dropsDisabled = !dropsDisabled;
    }

    public final void setDrops(final boolean b) {
        this.dropsDisabled = b;
    }

    public final void toggleGDrops() {
        this.gDropsDisabled = !gDropsDisabled;
    }

    public final int getId() {
        return mapid;
    }

    public final MapleMap getReturnMap() {
        return ChannelServer.getInstance(channel).getMapFactory().getMap(returnMapId);
    }

    public final int getReturnMapId() {
        return returnMapId;
    }

    public final int getForcedReturnId() {
        return forcedReturnMap;
    }

    public final MapleMap getForcedReturnMap() {
        return ChannelServer.getInstance(channel).getMapFactory().getMap(forcedReturnMap);
    }

    public final void setForcedReturnMap(final int map) {
        this.forcedReturnMap = map;
    }

    public final float getRecoveryRate() {
        return recoveryRate;
    }

    public final void setRecoveryRate(final float recoveryRate) {
        this.recoveryRate = recoveryRate;
    }

    public final int getFieldLimit() {
        return fieldLimit;
    }

    public final void setFieldLimit(final int fieldLimit) {
        this.fieldLimit = fieldLimit;
    }

    public final void setCreateMobInterval(final short createMobInterval) {
        this.createMobInterval = createMobInterval;
    }

    public final void setTimeLimit(final int timeLimit) {
        this.timeLimit = timeLimit;
    }

    public final void setMapName(final String mapName) {
        this.mapName = mapName;
    }

    public final String getMapName() {
        return mapName;
    }

    public final String getStreetName() {
        return streetName;
    }

    public final void setFirstUserEnter(final String onFirstUserEnter) {
        this.onFirstUserEnter = onFirstUserEnter;
    }

    public final void setUserEnter(final String onUserEnter) {
        this.onUserEnter = onUserEnter;
    }

    public final boolean hasClock() {
        return clock;
    }

    public final void setClock(final boolean hasClock) {
        this.clock = hasClock;
    }

    public final boolean isTown() {
        return town;
    }

    public final void setTown(final boolean town) {
        this.town = town;
    }

    public final boolean allowPersonalShop() {
        return personalShop;
    }

    public final void setPersonalShop(final boolean personalShop) {
        this.personalShop = personalShop;
    }

    public final void setStreetName(final String streetName) {
        this.streetName = streetName;
    }

    public final void setEverlast(final boolean everlast) {
        this.everlast = everlast;
    }

    public final boolean getEverlast() {
        return everlast;
    }

    public final int getHPDec() {
        return decHP;
    }

    public final void setHPDec(final int delta) {
        if (delta > 0 || mapid == 749040100) { // pmd
            lastHurtTime = System.currentTimeMillis(); // start it up
        }
        decHP = (short) delta;
    }

    public final int getHPDecInterval() {
        return decHPInterval;
    }

    public final void setHPDecInterval(final int delta) {
        decHPInterval = delta;
    }

    public final int getHPDecProtect() {
        return protectItem;
    }

    public final void setHPDecProtect(final int delta) {
        this.protectItem = delta;
    }

    public final int getCurrentPartyId() {
        final List<MapleCharacter> snapshot;
        charactersLock.readLock().lock();
        try {
            snapshot = new ArrayList<>(characters);
        } finally {
            charactersLock.readLock().unlock();
        }
        for (MapleCharacter chr : snapshot) {
            if (chr.getPartyId() != -1) {
                return chr.getPartyId();
            }
        }
        return -1;
    }

    public final void addMapObject(final MapleMapObject mapobject) {
        final int newOid;
        runningOidLock.lock();
        try {
            newOid = ++runningOid;
        } finally {
            runningOidLock.unlock();
        }
        mapobject.setObjectId(newOid);
        mapobjectlocks.get(mapobject.getType()).writeLock().lock();
        try {
            mapobjects.get(mapobject.getType()).put(newOid, mapobject);
        } finally {
            mapobjectlocks.get(mapobject.getType()).writeLock().unlock();
        }
    }

    private void spawnAndAddRangedMapObject(
            final MapleMapObject mapobject,
            final DelayedPacketCreation packetbakery,
            final SpawnCondition condition) {
        addMapObject(mapobject);

        // 取得玩家列表快照，釋放鎖後再進行外部玩家操作，避免鎖競爭與死鎖
        final List<MapleCharacter> playersSnapshot;
        charactersLock.readLock().lock();
        try {
            playersSnapshot = new ArrayList<>(characters);
        } finally {
            charactersLock.readLock().unlock();
        }

        for (MapleCharacter chr : playersSnapshot) {
            if (condition == null || condition.canSpawn(chr)) {
                if (!chr.isClone()
                        && chr.getPosition().distanceSq(mapobject.getPosition())
                        <= GameConstants.maxViewRangeSq()) {
                    packetbakery.sendPackets(chr.getClient());
                    chr.addVisibleMapObject(mapobject);
                }
            }
        }
    }

    public final boolean removeMapObject(final MapleMapObject obj) {
        if (obj == null) {
            return false;
        }
        mapobjectlocks.get(obj.getType()).writeLock().lock();
        try {
            return mapobjects.get(obj.getType()).remove(Integer.valueOf(obj.getObjectId())) != null;
        } finally {
            mapobjectlocks.get(obj.getType()).writeLock().unlock();
        }
    }

    public final Point calcPointBelow(final Point initial) {
        final MapleFoothold fh = footholds.findBelow(initial);
        if (fh == null) {
            return null;
        }
        int dropY = fh.getY1();
        if (!fh.isWall() && fh.getY1() != fh.getY2()) {
            final double s1 = Math.abs(fh.getY2() - fh.getY1());
            final double s2 = Math.abs(fh.getX2() - fh.getX1());
            if (fh.getY2() < fh.getY1()) {
                dropY
                        = fh.getY1()
                        - (int) (Math.cos(Math.atan(s2 / s1))
                        * (Math.abs(initial.x - fh.getX1())
                        / Math.cos(Math.atan(s1 / s2))));
            } else {
                dropY
                        = fh.getY1()
                        + (int) (Math.cos(Math.atan(s2 / s1))
                        * (Math.abs(initial.x - fh.getX1())
                        / Math.cos(Math.atan(s1 / s2))));
            }
        }
        return new Point(initial.x, dropY);
    }

    public final Point calcDropPos(final Point initial, final Point fallback) {
        final Point ret = calcPointBelow(new Point(initial.x, initial.y - 50));
        if (ret == null) {
            return fallback;
        }
        return ret;
    }

    private void dropFromMonster(final MapleCharacter chr, final MapleMonster mob) {
        final ChannelServer cserv = ChannelServer.getInstance(channel);
        if (mob == null
                || chr == null
                || cserv == null
                || dropsDisabled
                || mob.dropsDisabled()
                || chr.getPyramidSubway() != null) {
            return;
        }

        // Cache frequently accessed objects and values to avoid repeated calls,
        // reduce lock contention on shared getters, and maintain thread-safety.
        final MapleMonsterInformationProvider mi = MapleMonsterInformationProvider.getInstance();
        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        final MapleMonsterStats stats = mob.getStats();
        final Point mobPosition = mob.getPosition();
        final int mobId = mob.getId();
        final int stolen = mob.getStolen();
        final double dropMod = chr.getDropMod();
        final double dropBuff = chr.getStat().dropBuff;
        final double mesoBuff = chr.getStat().mesoBuff;
        final double cashBuff = chr.getStat().cashBuff;
        final boolean hasParty = chr.getParty() != null;
        final int cmServerrate = cserv.getMesoRate();
        final int chServerrate = cserv.getDropRate();
        final int caServerrate = cserv.getCashRate();

        final byte droptype
                = (byte) (stats.isExplosiveReward() ? 3 : stats.isFfaLoot() ? 2 : hasParty ? 1 : 0);
        final int mobpos = mobPosition.x;
        IItem idrop;
        byte d = 1;
        Point pos = new Point(0, mobPosition.y);
        double showdown = 100.0;
        final MonsterStatusEffect mse = mob.getBuff(MonsterStatus.SHOWDOWN);
        if (mse != null) {
            showdown += mse.getX();
        }

        // Copy the drop list before shuffling to avoid modifying shared cache
        // and to prevent ConcurrentModificationException.
        final List<MonsterDropEntry> dropEntry = new ArrayList<>(mi.retrieveDrop(mobId));
        Collections.shuffle(dropEntry);

        for (final MonsterDropEntry de : dropEntry) {
            if (de.itemId == stolen) {
                continue;
            }
            if (Randomizer.nextInt(999999)
                    < (int) (de.chance
                    * chServerrate
                    * dropMod
                    * (dropBuff / 100.0)
                    * (showdown / 100.0))) {
                if (droptype == 3) {
                    pos.x = (mobpos + (d % 2 == 0 ? (40 * (d + 1) / 2) : -(40 * (d / 2))));
                } else {
                    pos.x = (mobpos + ((d % 2 == 0) ? (25 * (d + 1) / 2) : -(25 * (d / 2))));
                }
                if (de.itemId == 0) { // meso
                    int mesos
                            = Randomizer.nextInt(1 + Math.abs(de.Maximum - de.Minimum)) + de.Minimum;
                    if (mesos > 0) {
                        spawnMobMesoDrop(
                                (int) (mesos * (mesoBuff / 100.0) * dropMod * cmServerrate),
                                calcDropPos(pos, mobPosition),
                                mob,
                                chr,
                                false,
                                droptype);
                    }
                } else {
                    if (GameConstants.getInventoryType(de.itemId) == MapleInventoryType.EQUIP) {
                        idrop = ii.randomizeStats((Equip) ii.getEquipById(de.itemId));
                    } else {
                        final int range = Math.abs(de.Maximum - de.Minimum);
                        idrop
                                = new Item(
                                        de.itemId,
                                        (byte) 0,
                                        (short) (de.Maximum != 1
                                                ? Randomizer.nextInt(range <= 0 ? 1 : range)
                                                + de.Minimum
                                                : 1),
                                        (byte) 0);
                    }
                    spawnMobDrop(
                            idrop, calcDropPos(pos, mobPosition), mob, chr, droptype, de.questid);
                }
                d++;
            }
        }

        final List<MonsterGlobalDropEntry> globalEntry = new ArrayList<>(mi.getGlobalDrop());
        Collections.shuffle(globalEntry);
        final boolean boss = stats.isBoss();
        final int hpDisplayType = stats.getHPDisplayType();
        final int mobExp = mob.getMobExp();
        final long mobMaxHp = mob.getMobMaxHp();
        final int cashz = (int) ((boss && hpDisplayType == 0 ? 20 : 1) * caServerrate);
        final int cashModifier = (int) ((boss ? 0 : (mobExp / 1000 + mobMaxHp / 10000)));

        // Pre-calculate continent divisions to avoid repeated arithmetic inside the loop.
        final int mapidDiv100M = mapid / 100000000;
        final int mapidDiv10M = mapid / 10000000;
        final int mapidDiv1M = mapid / 1000000;

        for (final MonsterGlobalDropEntry de : globalEntry) {
            if (Randomizer.nextInt(999999) < de.chance
                    && (de.continent < 0
                    || (de.continent < 10 && mapidDiv100M == de.continent)
                    || (de.continent < 100 && mapidDiv10M == de.continent)
                    || (de.continent < 1000 && mapidDiv1M == de.continent))) {
                if (droptype == 3) {
                    pos.x = (mobpos + (d % 2 == 0 ? (40 * (d + 1) / 2) : -(40 * (d / 2))));
                } else {
                    pos.x = (mobpos + ((d % 2 == 0) ? (25 * (d + 1) / 2) : -(25 * (d / 2))));
                }
                if (de.itemId == 0) {
                    chr.modifyCSPoints(
                            1,
                            (int) ((Randomizer.nextInt(cashz) + cashz + cashModifier)
                            * (cashBuff / 100.0)),
                            true);
                } else if (!gDropsDisabled) {
                    if (GameConstants.getInventoryType(de.itemId) == MapleInventoryType.EQUIP) {
                        idrop = ii.randomizeStats((Equip) ii.getEquipById(de.itemId));
                    } else {
                        idrop
                                = new Item(
                                        de.itemId,
                                        (byte) 0,
                                        (short) (de.Maximum != 1
                                                ? Randomizer.nextInt(
                                                        de.Maximum - de.Minimum)
                                                + de.Minimum
                                                : 1),
                                        (byte) 0);
                    }
                    spawnMobDrop(
                            idrop,
                            calcDropPos(pos, mobPosition),
                            mob,
                            chr,
                            de.onlySelf ? 0 : droptype,
                            de.questid);
                    d++;
                }
            }
        }
    }

    public void removeMonster(final MapleMonster monster) {
        if (removeMapObject(monster)) {
            spawnedMonstersOnMap.updateAndGet(v -> v > 0 ? v - 1 : 0);
            broadcastMessage(MobPacket.killMonster(monster.getObjectId(), 0));
        }
    }

    private void killMonster(final MapleMonster monster) { // For mobs with removeAfter
        if (removeMapObject(monster)) {
            spawnedMonstersOnMap.updateAndGet(v -> v > 0 ? v - 1 : 0);
            monster.setHp(0);
            monster.spawnRevives(this);
            broadcastMessage(MobPacket.killMonster(monster.getObjectId(), 1));
        }
    }

    public final void killMonster(
            final MapleMonster monster,
            final MapleCharacter chr,
            final boolean withDrops,
            final boolean second,
            byte animation) {
        killMonster(monster, chr, withDrops, second, animation, 0);
    }

    public final void killMonster(
            final MapleMonster monster,
            final MapleCharacter chr,
            final boolean withDrops,
            final boolean second,
            byte animation,
            final int lastSkill) {
        if ((monster.getId() == 8810122 || monster.getId() == 8810018) && !second) {
            MapTimer.getInstance()
                    .schedule(
                            new Runnable() {

                        @Override
                        public void run() {
                            killMonster(monster, chr, true, true, (byte) 1);
                            killAllMonsters(true);
                        }
                    },
                            3000);
            return;
        }
        if (monster.getId() == 8820014) { // pb sponge, kills pb(w) first before dying
            killMonster(8820000);
        } else if (monster.getId() == 9300166) { // ariant pq bomb
            animation = 4; // or is it 3?
        }
        spawnedMonstersOnMap.decrementAndGet();
        removeMapObject(monster);
        int dropOwner = monster.killBy(chr, lastSkill);
        broadcastMessage(MobPacket.killMonster(monster.getObjectId(), animation));

        if (monster.getBuffToGive() > -1) {
            final int buffid = monster.getBuffToGive();
            final MapleStatEffect buff
                    = MapleItemInformationProvider.getInstance().getItemEffect(buffid);

            // 先取得角色快照，避免長時間佔用 charactersLock 讀鎖
            final List<MapleCharacter> charactersSnapshot;
            charactersLock.readLock().lock();
            try {
                charactersSnapshot = new ArrayList<>(characters);
            } finally {
                charactersLock.readLock().unlock();
            }

            for (final MapleCharacter mc : charactersSnapshot) {
                if (mc.isAlive()) {
                    buff.applyTo(mc);

                    switch (monster.getId()) {
                        case 8810018:
                        case 8810122:
                        case 8820001:
                            mc.getClient()
                                    .sendPacket(
                                            MaplePacketCreator.showOwnBuffEffect(
                                                    buffid, 11)); // HT nine spirit
                            broadcastMessage(
                                    mc,
                                    MaplePacketCreator.showBuffeffect(mc.getId(), buffid, 11),
                                    false); // HT nine spirit
                            break;
                    }
                }
            }
        }
        final int mobid = monster.getId();
        SpeedRunType type = SpeedRunType.NULL;
        final MapleSquad sqd = getSquadByMap();
        if (mobid == 8810018 && mapid == 240060200) { // Horntail
            World.Broadcast.broadcastMessage(
                    MaplePacketCreator.serverNotice(
                            6,
                            "經過無數次的挑戰，"
                            + chr.getName()
                            + "所帶領的隊伍終於擊破了闇黑龍王的遠征隊！你們才是龍之林的真正英雄~")
                            .getBytes());
            /*for (MapleCharacter c : getCharactersThreadsafe()) {
            c.finishAchievement(16);
            }*/
            LOG.info(MapDebug_Log());
            if (speedRunStart > 0) {
                type = SpeedRunType.Horntail;
            }
            if (sqd != null) {
                doShrine(true);
            }
        } else if (mobid == 8810122 && mapid == 240060201) { // Horntail
            World.Broadcast.broadcastMessage(
                    MaplePacketCreator.serverNotice(
                            6,
                            "經過無數次的挑戰，"
                            + chr.getName()
                            + "所帶領的隊伍最終擊破了混屯闇黑龍王的遠征隊！你們才是龍之林的真正英雄~")
                            .getBytes());
            /*for (MapleCharacter c : getCharactersThreadsafe()) {
            c.finishAchievement(24);
            }*/
            LOG.info(MapDebug_Log());
            if (speedRunStart > 0) {
                type = SpeedRunType.ChaosHT;
            }
            if (sqd != null) {
                doShrine(true);
            }
        } else if (mobid == 8500002 && mapid == 220080001) {
            if (speedRunStart > 0) {
                type = SpeedRunType.Papulatus;
            }
        } else if (mobid == 9400266 && mapid == 802000111) {
            if (speedRunStart > 0) {
                type = SpeedRunType.Nameless_Magic_Monster;
            }
            if (sqd != null) {
                doShrine(true);
            }
        } else if (mobid == 9400265 && mapid == 802000211) {
            if (speedRunStart > 0) {
                type = SpeedRunType.Vergamot;
            }
            if (sqd != null) {
                doShrine(true);
            }
        } else if (mobid == 9400270 && mapid == 802000411) {
            if (speedRunStart > 0) {
                type = SpeedRunType.Dunas;
            }
            if (sqd != null) {
                doShrine(true);
            }
        } else if (mobid == 9400273 && mapid == 802000611) {
            if (speedRunStart > 0) {
                type = SpeedRunType.Nibergen;
            }
            if (sqd != null) {
                doShrine(true);
            }
        } else if (mobid == 9400294 && mapid == 802000711) {
            if (speedRunStart > 0) {
                type = SpeedRunType.Dunas_2;
            }
            if (sqd != null) {
                doShrine(true);
            }
        } else if (mobid == 9400296 && mapid == 802000803) {
            if (speedRunStart > 0) {
                type = SpeedRunType.Core_Blaze;
            }
            if (sqd != null) {
                doShrine(true);
            }
        } else if (mobid == 9400289 && mapid == 802000821) {
            if (speedRunStart > 0) {
                type = SpeedRunType.Aufhaven;
            }
            if (sqd != null) {
                doShrine(true);
            }
        } else if ((mobid == 9420549 || mobid == 9420544) && mapid == 551030200) {
            if (speedRunStart > 0) {
                if (mobid == 9420549) {
                    type = SpeedRunType.Scarlion;
                } else {
                    type = SpeedRunType.Targa;
                }
            }
            // INSERT HERE: 2095_tokyo
        } else if (mobid == 8820001 && mapid == 270050100) {
            World.Broadcast.broadcastMessage(
                    MaplePacketCreator.serverNotice(
                            6,
                            chr.getName()
                            + " 經過帶領的隊伍經過無數次的挑戰，終於擊破了時間的寵兒－皮卡丘的遠征隊！你們才是時間神殿的真正英雄~")
                            .getBytes());
            /*for (MapleCharacter c : getCharactersThreadsafe()) {
            c.finishAchievement(17);
            }*/
            if (speedRunStart > 0) {
                type = SpeedRunType.Pink_Bean;
            }
            if (sqd != null) {
                doShrine(true);
            }
            LOG.info(MapDebug_Log());
        } else if (mobid == 8800002 && mapid == 280030000) {
            /*for (MapleCharacter c : getCharactersThreadsafe()) {
            c.finishAchievement(15);
            }*/
            LOG.info(MapDebug_Log());
            if (speedRunStart > 0) {
                type = SpeedRunType.Zakum;
            }
            if (sqd != null) {
                doShrine(true);
            }
        } else if (mobid == 8800102 && mapid == 280030001) {
            /*for (MapleCharacter c : getCharactersThreadsafe()) {
            c.finishAchievement(23);
            }*/
            LOG.info(MapDebug_Log());
            if (speedRunStart > 0) {
                type = SpeedRunType.Chaos_Zakum;
            }

            if (sqd != null) {
                doShrine(true);
            }
        } else if (mobid >= 8800003 && mobid <= 8800010) {
            boolean makeZakReal = true;
            final Collection<MapleMonster> monsters = getAllMonstersThreadsafe();

            for (final MapleMonster mons : monsters) {
                if (mons.getId() >= 8800003 && mons.getId() <= 8800010) {
                    makeZakReal = false;
                    break;
                }
            }
            if (makeZakReal) {
                for (final MapleMapObject object : monsters) {
                    final MapleMonster mons = ((MapleMonster) object);
                    if (mons.getId() == 8800000) {
                        final Point pos = mons.getPosition();
                        this.killAllMonsters(true);
                        spawnMonsterOnGroundBelow(MapleLifeFactory.getMonster(8800000), pos);
                        break;
                    }
                }
            }
        } else if (mobid >= 8800103 && mobid <= 8800110) {
            boolean makeZakReal = true;
            final Collection<MapleMonster> monsters = getAllMonstersThreadsafe();

            for (final MapleMonster mons : monsters) {
                if (mons.getId() >= 8800103 && mons.getId() <= 8800110) {
                    makeZakReal = false;
                    break;
                }
            }
            if (makeZakReal) {
                for (final MapleMonster mons : monsters) {
                    if (mons.getId() == 8800100) {
                        final Point pos = mons.getPosition();
                        this.killAllMonsters(true);
                        spawnMonsterOnGroundBelow(MapleLifeFactory.getMonster(8800100), pos);
                        break;
                    }
                }
            }
        }
        if (type != SpeedRunType.NULL) {
            if (speedRunStart > 0 && speedRunLeader.length() > 0) {
                long endTime = System.currentTimeMillis();
                String time = StringUtil.getReadableMillis(speedRunStart, endTime);
                broadcastMessage(
                        MaplePacketCreator.serverNotice(
                                5, speedRunLeader + "'遠征隊花了 " + time + " 時間打敗了 " + type + "!"));
                getRankAndAdd(
                        speedRunLeader,
                        time,
                        type,
                        (endTime - speedRunStart),
                        (sqd == null ? null : sqd.getMembers()));
                endSpeedRun();
            }
        }
        if (mobid == 8820008) { // wipe out statues and respawn
            for (final MapleMapObject mmo : getAllMonstersThreadsafe()) {
                MapleMonster mons = (MapleMonster) mmo;
                if (mons.getLinkOid() != monster.getObjectId()) {
                    killMonster(mons, chr, false, false, animation);
                }
            }
        } else if (mobid >= 8820010 && mobid <= 8820014) {
            for (final MapleMapObject mmo : getAllMonstersThreadsafe()) {
                MapleMonster mons = (MapleMonster) mmo;
                if (mons.getId() != 8820000
                        && mons.getObjectId() != monster.getObjectId()
                        && mons.getLinkOid() != monster.getObjectId()) {
                    killMonster(mons, chr, false, false, animation);
                }
            }
        }
        if (withDrops) {
            MapleCharacter drop = null;
            if (dropOwner <= 0) {
                drop = chr;
            } else {
                drop = getCharacterById(dropOwner);
                if (drop == null) {
                    drop = chr;
                }
            }
            dropFromMonster(drop, monster);
        }
    }

    public List<MapleReactor> getAllReactor() {
        return getAllReactorsThreadsafe();
    }

    public List<MapleReactor> getAllReactorsThreadsafe() {
        ArrayList<MapleReactor> ret = new ArrayList<>();
        mapobjectlocks.get(MapleMapObjectType.REACTOR).readLock().lock();
        try {
            for (MapleMapObject mmo : mapobjects.get(MapleMapObjectType.REACTOR).values()) {
                ret.add((MapleReactor) mmo);
            }
        } finally {
            mapobjectlocks.get(MapleMapObjectType.REACTOR).readLock().unlock();
        }
        return ret;
    }

    public List<MapleMapObject> getAllDoor() {
        return getAllDoorsThreadsafe();
    }

    public List<MapleMapObject> getAllDoorsThreadsafe() {
        ArrayList<MapleMapObject> ret = new ArrayList<>();
        mapobjectlocks.get(MapleMapObjectType.DOOR).readLock().lock();
        try {
            for (MapleMapObject mmo : mapobjects.get(MapleMapObjectType.DOOR).values()) {
                ret.add(mmo);
            }
        } finally {
            mapobjectlocks.get(MapleMapObjectType.DOOR).readLock().unlock();
        }
        return ret;
    }

    public List<MapleMapObject> getAllMerchant() {
        return getAllHiredMerchantsThreadsafe();
    }

    public List<MapleMapObject> getAllHiredMerchantsThreadsafe() {
        ArrayList<MapleMapObject> ret = new ArrayList<>();
        mapobjectlocks.get(MapleMapObjectType.HIRED_MERCHANT).readLock().lock();
        try {
            for (MapleMapObject mmo : mapobjects.get(MapleMapObjectType.HIRED_MERCHANT).values()) {
                ret.add(mmo);
            }
        } finally {
            mapobjectlocks.get(MapleMapObjectType.HIRED_MERCHANT).readLock().unlock();
        }
        return ret;
    }

    public List<MapleMonster> getAllMonster() {
        return getAllMonstersThreadsafe();
    }

    public List<MapleMonster> getAllMonstersThreadsafe() {
        ArrayList<MapleMonster> ret = new ArrayList<>();
        mapobjectlocks.get(MapleMapObjectType.MONSTER).readLock().lock();
        try {
            for (MapleMapObject mmo : mapobjects.get(MapleMapObjectType.MONSTER).values()) {
                ret.add((MapleMonster) mmo);
            }
        } finally {
            mapobjectlocks.get(MapleMapObjectType.MONSTER).readLock().unlock();
        }
        return ret;
    }

    public final void killAllMonsters(final boolean animate) {
        for (final MapleMonster monster : getAllMonstersThreadsafe()) {
            if (removeMapObject(monster)) {
                spawnedMonstersOnMap.updateAndGet(v -> v > 0 ? v - 1 : 0);
                monster.setHp(0);
                broadcastMessage(MobPacket.killMonster(monster.getObjectId(), animate ? 1 : 0));
                monster.killed();
            }
        }
        spawnedMonstersOnMap.set(0);
    }

    public final void killMonster(final int monsId) {
        for (final MapleMonster monster : getAllMonstersThreadsafe()) {
            if (monster.getId() == monsId) {
                if (removeMapObject(monster)) {
                    spawnedMonstersOnMap.updateAndGet(v -> v > 0 ? v - 1 : 0);
                    broadcastMessage(MobPacket.killMonster(monster.getObjectId(), 1));
                }
                break;
            }
        }
    }

    private String MapDebug_Log() {
        final StringBuilder sb = new StringBuilder("Defeat time : ");
        sb.append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        sb.append(" | Mapid : ").append(this.mapid);

        // 取得 characters 的輕量級快照，避免在持有集合鎖時呼叫外部玩家同步方法，並避免死鎖風險
        final List<MapleCharacter> charactersSnapshot;
        charactersLock.readLock().lock();
        try {
            charactersSnapshot = new ArrayList<>(characters);
        } finally {
            charactersLock.readLock().unlock();
        }

        sb.append(" Users [").append(charactersSnapshot.size()).append("] | ");
        for (MapleCharacter mc : charactersSnapshot) {
            sb.append(mc.getName()).append(", ");
        }
        return sb.toString();
    }

    public final void limitReactor(final int rid, final int num) {
        List<MapleReactor> toDestroy = new ArrayList<>();
        Map<Integer, Integer> contained = new LinkedHashMap<>();
        for (MapleReactor mr : getAllReactorsThreadsafe()) {
            if (contained.containsKey(mr.getReactorId())) {
                if (contained.get(mr.getReactorId()) >= num) {
                    toDestroy.add(mr);
                } else {
                    contained.put(mr.getReactorId(), contained.get(mr.getReactorId()) + 1);
                }
            } else {
                contained.put(mr.getReactorId(), 1);
            }
        }
        for (MapleReactor mr : toDestroy) {
            destroyReactor(mr.getObjectId());
        }
    }

    public final void destroyReactors(final int first, final int last) {
        final List<Integer> toDestroyIds = new ArrayList<>();
        for (MapleReactor mr : getAllReactorsThreadsafe()) {
            if (mr.getReactorId() >= first && mr.getReactorId() <= last) {
                toDestroyIds.add(mr.getObjectId());
            }
        }
        for (int oid : toDestroyIds) {
            destroyReactor(oid);
        }
    }

    public final void destroyReactor(final int oid) {
        final MapleReactor reactor = getReactorByOid(oid);
        if (reactor == null) {
            return;
        }
        // 先取得封包快照，避免在持有任何集合鎖時呼叫外部同步方法
        final MaplePacket destroyPacket = MaplePacketCreator.destroyReactor(reactor);
        // 先標記狀態（在鎖外，僅為物件自身狀態），再廣播
        reactor.setAlive(false);
        reactor.setTimerActive(false);
        // 廣播封包（broadcastMessage 內部自行處理快照與鎖外發送）
        broadcastMessage(destroyPacket);
        // 從地圖物件集合移除（removeMapObject 內部使用細粒度鎖）
        removeMapObject(reactor);

        final int delay = reactor.getDelay();
        if (delay > 0) {
            MapTimer.getInstance()
                    .schedule(
                            new Runnable() {

                        @Override
                        public final void run() {
                            respawnReactor(reactor);
                        }
                    },
                            delay);
        }
    }

    public final void reloadReactors() {
        List<MapleReactor> toSpawn = new ArrayList<>();
        ReentrantReadWriteLock reactorLock = mapobjectlocks.get(MapleMapObjectType.REACTOR);
        reactorLock.readLock().lock();
        try {
            for (MapleMapObject obj : mapobjects.get(MapleMapObjectType.REACTOR).values()) {
                toSpawn.add((MapleReactor) obj);
            }
        } finally {
            reactorLock.readLock().unlock();
        }
        for (MapleReactor reactor : toSpawn) {
            broadcastMessage(MaplePacketCreator.destroyReactor(reactor));
            reactor.setAlive(false);
            reactor.setTimerActive(false);
        }
        for (MapleReactor r : toSpawn) {
            removeMapObject(r);
            if (r.getReactorId() != 9980000 && r.getReactorId() != 9980001) { // guardians cpq
                respawnReactor(r);
            }
        }
    }

    /*
     * command to reset all item-reactors in a map to state 0 for GM/NPC use - not tested (broken reactors get removed
     * from mapobjects when destroyed) Should create instances for multiple copies of non-respawning reactors...
     */
    public final void resetReactors() {
        setReactorState((byte) 0);
    }

    public final void setReactorState() {
        setReactorState((byte) 1);
    }

    public final void setReactorState(final byte state) {
        mapobjectlocks.get(MapleMapObjectType.REACTOR).readLock().lock();
        try {
            for (MapleMapObject obj : mapobjects.get(MapleMapObjectType.REACTOR).values()) {
                ((MapleReactor) obj).forceHitReactor((byte) state);
            }
        } finally {
            mapobjectlocks.get(MapleMapObjectType.REACTOR).readLock().unlock();
        }
    }

    /*
     * command to shuffle the positions of all reactors in a map for PQ purposes (such as ZPQ/LMPQ)
     */
    public final void shuffleReactors() {
        shuffleReactors(0, 9999999); // all
    }

    public final void shuffleReactors(int first, int last) {
        ReentrantReadWriteLock lock = mapobjectlocks.get(MapleMapObjectType.REACTOR);
        lock.writeLock().lock();
        try {
            Map<Integer, MapleMapObject> reactorMap = mapobjects.get(MapleMapObjectType.REACTOR);
            List<MapleReactor> reactors = new ArrayList<>();
            List<Point> points = new ArrayList<>();
            for (MapleMapObject obj : reactorMap.values()) {
                MapleReactor mr = (MapleReactor) obj;
                if (mr.getReactorId() >= first && mr.getReactorId() <= last) {
                    reactors.add(mr);
                    points.add(mr.getPosition());
                }
            }
            Collections.shuffle(points);
            int i = 0;
            for (MapleReactor mr : reactors) {
                mr.setPosition(points.get(i++));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Automagically finds a new controller for the given monster from the chars
     * on the map...
     *
     * @param monster
     */
    public final void updateMonsterController(final MapleMonster monster) {
        if (!monster.isAlive()) {
            return;
        }
        if (monster.getController() != null) {
            if (monster.getController().getMap() != this) {
                monster.getController().stopControllingMonster(monster);
            } else {
                return;
            }
        }

        final List<MapleCharacter> snapshot;
        charactersLock.readLock().lock();
        try {
            snapshot = new ArrayList<>(characters);
        } finally {
            charactersLock.readLock().unlock();
        }

        int mincontrolled = -1;
        MapleCharacter newController = null;
        for (MapleCharacter chr : snapshot) {
            if (!chr.isHidden()
                    && !chr.isClone()
                    && (mincontrolled == -1 || chr.getControlledSize() < mincontrolled)) {
                mincontrolled = chr.getControlledSize();
                newController = chr;
            }
        }

        if (newController != null) {
            if (monster.isFirstAttack()) {
                newController.controlMonster(monster, true);
                monster.setControllerHasAggro(true);
                monster.setControllerKnowsAboutAggro(true);
            } else {
                newController.controlMonster(monster, false);
            }
        }
    }

    public final MapleMapObject getMapObject(int oid, MapleMapObjectType type) {
        mapobjectlocks.get(type).readLock().lock();
        try {
            return mapobjects.get(type).get(oid);
        } finally {
            mapobjectlocks.get(type).readLock().unlock();
        }
    }

    public final boolean containsNPC(int npcid) {
        final ReentrantReadWriteLock lock = mapobjectlocks.get(MapleMapObjectType.NPC);
        final List<MapleMapObject> npcSnapshot;
        lock.readLock().lock();
        try {
            npcSnapshot = new ArrayList<>(mapobjects.get(MapleMapObjectType.NPC).values());
        } finally {
            lock.readLock().unlock();
        }
        for (MapleMapObject mapObject : npcSnapshot) {
            final MapleNPC npc = (MapleNPC) mapObject;
            if (npc.getId() == npcid) {
                return true;
            }
        }
        return false;
    }

    public MapleNPC getNPCById(int id) {
        Map<Integer, MapleMapObject> npcMap = mapobjects.get(MapleMapObjectType.NPC);
        if (npcMap == null || npcMap.isEmpty()) {
            return null;
        }
        ReentrantReadWriteLock lock = mapobjectlocks.get(MapleMapObjectType.NPC);
        lock.readLock().lock();
        try {
            for (MapleMapObject obj : npcMap.values()) {
                MapleNPC npc = (MapleNPC) obj;
                if (npc.getId() == id) {
                    return npc;
                }
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public MapleMonster getMonsterById(int id) {
        final ReentrantReadWriteLock lock = mapobjectlocks.get(MapleMapObjectType.MONSTER);
        lock.readLock().lock();
        try {
            for (MapleMapObject obj : mapobjects.get(MapleMapObjectType.MONSTER).values()) {
                final MapleMonster monster = (MapleMonster) obj;
                if (monster.getId() == id) {
                    return monster;
                }
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int countMonsterById(int id) {
        final ReentrantReadWriteLock lock = mapobjectlocks.get(MapleMapObjectType.MONSTER);
        final Map<Integer, MapleMapObject> monsters = mapobjects.get(MapleMapObjectType.MONSTER);
        lock.readLock().lock();
        try {
            int ret = 0;
            for (MapleMapObject obj : monsters.values()) {
                if (((MapleMonster) obj).getId() == id) {
                    ret++;
                }
            }
            return ret;
        } finally {
            lock.readLock().unlock();
        }
    }

    public MapleReactor getReactorById(int id) {
        final ReentrantReadWriteLock reactorLock = mapobjectlocks.get(MapleMapObjectType.REACTOR);
        final MapleMapObject[] snapshot;
        reactorLock.readLock().lock();
        try {
            final Collection<MapleMapObject> reactorObjects
                    = mapobjects.get(MapleMapObjectType.REACTOR).values();
            snapshot = reactorObjects.toArray(new MapleMapObject[reactorObjects.size()]);
        } finally {
            reactorLock.readLock().unlock();
        }
        for (final MapleMapObject mapObject : snapshot) {
            final MapleReactor reactor = (MapleReactor) mapObject;
            if (reactor.getReactorId() == id) {
                return reactor;
            }
        }
        return null;
    }

    /**
     * returns a monster with the given oid, if no such monster exists returns
     * null
     *
     * @param oid
     * @return
     */
    public final MapleMonster getMonsterByOid(final int oid) {
        MapleMapObject mmo = getMapObject(oid, MapleMapObjectType.MONSTER);
        if (mmo == null) {
            return null;
        }
        return (MapleMonster) mmo;
    }

    public final MapleNPC getNPCByOid(final int oid) {
        MapleMapObject mmo = getMapObject(oid, MapleMapObjectType.NPC);
        if (mmo == null) {
            return null;
        }
        return (MapleNPC) mmo;
    }

    public final MapleReactor getReactorByOid(final int oid) {
        MapleMapObject mmo = getMapObject(oid, MapleMapObjectType.REACTOR);
        if (mmo == null) {
            return null;
        }
        return (MapleReactor) mmo;
    }

    public final MapleReactor getReactorByName(final String name) {
        mapobjectlocks.get(MapleMapObjectType.REACTOR).readLock().lock();
        try {
            for (MapleMapObject obj : mapobjects.get(MapleMapObjectType.REACTOR).values()) {
                MapleReactor mr = ((MapleReactor) obj);
                if (mr.getName().equalsIgnoreCase(name)) {
                    return mr;
                }
            }
            return null;
        } finally {
            mapobjectlocks.get(MapleMapObjectType.REACTOR).readLock().unlock();
        }
    }

    public final void spawnNpc(final int id, final Point pos) {
        final MapleNPC npc = MapleLifeFactory.getNPC(id);
        npc.setPosition(pos);
        npc.setCy(pos.y);
        npc.setRx0(pos.x + 50);
        npc.setRx1(pos.x - 50);
        npc.setFh(getFootholds().findBelow(pos).getId());
        npc.setCustom(true);
        addMapObject(npc);
        broadcastMessage(MaplePacketCreator.spawnNPC(npc, true));
    }

    public final void removeNpc(final int npcid) {
        final List<Integer> toRemove = new ArrayList<>();
        final ReentrantReadWriteLock lock = mapobjectlocks.get(MapleMapObjectType.NPC);
        lock.writeLock().lock();
        try {
            Iterator<MapleMapObject> itr
                    = mapobjects.get(MapleMapObjectType.NPC).values().iterator();
            while (itr.hasNext()) {
                MapleNPC npc = (MapleNPC) itr.next();
                if (npc.isCustom() && npc.getId() == npcid) {
                    toRemove.add(npc.getObjectId());
                    itr.remove();
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
        for (int oid : toRemove) {
            broadcastMessage(MaplePacketCreator.removeNPC(oid));
        }
    }

    public final void spawnMonster_sSack(
            final MapleMonster mob, final Point pos, final int spawnType) {
        final Point spos = calcPointBelow(new Point(pos.x, pos.y - 1));
        mob.setPosition(spos);
        spawnMonster(mob, spawnType);
    }

    public final void spawnMonsterOnGroundBelow(final MapleMonster mob, final Point pos) {
        spawnMonster_sSack(mob, pos, -2);
    }

    public final int spawnMonsterWithEffectBelow(
            final MapleMonster mob, final Point pos, final int effect) {
        final Point spos = calcPointBelow(new Point(pos.x, pos.y - 1));
        return spawnMonsterWithEffect(mob, effect, spos);
    }

    public final void spawnZakum(final int x, final int y) {
        final Point pos = new Point(x, y);
        final MapleMonster mainb = MapleLifeFactory.getMonster(8800000);
        final Point spos = calcPointBelow(new Point(pos.x, pos.y - 1));
        mainb.setPosition(spos);
        mainb.setFake(true);

        // Might be possible to use the map object for reference in future.
        spawnFakeMonster(mainb);

        final int[] zakpart = {
            8800003, 8800004, 8800005, 8800006, 8800007, 8800008, 8800009, 8800010
        };

        for (final int i : zakpart) {
            final MapleMonster part = MapleLifeFactory.getMonster(i);
            part.setPosition(spos);

            spawnMonster(part, -2);
        }
        if (squadSchedule != null) {
            cancelSquadSchedule();
            broadcastMessage(MaplePacketCreator.stopClock());
            broadcastMessage(MaplePacketCreator.getClock(60000 * 600));
        }
    }

    public final void spawnChaosZakum(final int x, final int y) {
        final Point pos = new Point(x, y);
        final MapleMonster mainb = MapleLifeFactory.getMonster(8800100);
        final Point spos = calcPointBelow(new Point(pos.x, pos.y - 1));
        mainb.setPosition(spos);
        mainb.setFake(true);
        spawnFakeMonster(mainb);

        final int[] zakpart = {
            8800103, 8800104, 8800105, 8800106, 8800107, 8800108, 8800109, 8800110
        };

        for (final int i : zakpart) {
            final MapleMonster part = MapleLifeFactory.getMonster(i);
            part.setPosition(spos);
            spawnMonster(part, -2);
        }

        // 優化併發安全性：
        // 1. 先取得 squadSchedule 的區域快照，避免之後多次讀取非 volatile 欄位造成競態。
        // 2. 整合兩個全域廣播為一次對玩家快照的遍歷，減少重複取得 charactersLock 讀鎖的次數，
        //    並在鎖外發送封包，符合「鎖外廣播」原則，避免持有集合鎖時呼叫玩家同步方法。
        final ScheduledFuture<?> schedule = squadSchedule;
        if (schedule != null) {
            cancelSquadSchedule();

            final List<MapleCharacter> players = getCharactersThreadsafe();
            final MaplePacket stopClockPacket = MaplePacketCreator.stopClock();
            final MaplePacket clockPacket = MaplePacketCreator.getClock(60000 * 600);

            for (MapleCharacter chr : players) {
                if (chr != null && chr.getClient() != null) {
                    // 保持封包順序：先 stopClock，再 getClock
                    chr.getClient().sendPacket(stopClockPacket);
                    chr.getClient().sendPacket(clockPacket);
                }
            }
        }
    }

    public List<MapleMist> getAllMistsThreadsafe() {
        ArrayList<MapleMist> ret = new ArrayList<>();
        mapobjectlocks.get(MapleMapObjectType.MIST).readLock().lock();
        try {
            for (MapleMapObject mmo : mapobjects.get(MapleMapObjectType.MIST).values()) {
                ret.add((MapleMist) mmo);
            }
        } finally {
            mapobjectlocks.get(MapleMapObjectType.MIST).readLock().unlock();
        }
        return ret;
    }

    public final void spawnFakeMonsterOnGroundBelow(final MapleMonster mob, final Point pos) {
        Point spos = calcPointBelow(new Point(pos.x, pos.y - 1));
        spos.y -= 1;
        mob.setPosition(spos);
        spawnFakeMonster(mob);
    }

    private void checkRemoveAfter(final MapleMonster monster) {
        final int ra = monster.getStats().getRemoveAfter();

        if (ra > 0) {
            MapTimer.getInstance()
                    .schedule(
                            new Runnable() {

                        @Override
                        public final void run() {
                            if (monster != null
                                    && monster
                                    == getMapObject(
                                            monster.getObjectId(),
                                            monster.getType())) {
                                killMonster(monster);
                            }
                        }
                    },
                            ra * 1000);
        }
    }

    public final void spawnRevives(final MapleMonster monster, final int oid) {
        monster.setMap(this);
        checkRemoveAfter(monster);
        monster.setLinkOid(oid);
        spawnAndAddRangedMapObject(
                monster,
                new DelayedPacketCreation() {

            @Override
            public final void sendPackets(MapleClient c) {
                c.sendPacket(MobPacket.spawnMonster(monster, -2, 0, oid)); // TODO effect
            }
        },
                null);
        updateMonsterController(monster);

        spawnedMonstersOnMap.incrementAndGet();
    }

    public final void spawnMonster(final MapleMonster monster, final int spawnType) {
        monster.setMap(this);
        checkRemoveAfter(monster);

        spawnAndAddRangedMapObject(
                monster,
                new DelayedPacketCreation() {

            public final void sendPackets(MapleClient c) {
                c.sendPacket(MobPacket.spawnMonster(monster, spawnType, 0, 0));
            }
        },
                null);
        updateMonsterController(monster);

        spawnedMonstersOnMap.incrementAndGet();
    }

    public final int spawnMonsterWithEffect(
            final MapleMonster monster, final int effect, Point pos) {
        try {
            monster.setMap(this);
            monster.setPosition(pos);

            spawnAndAddRangedMapObject(
                    monster,
                    new DelayedPacketCreation() {

                @Override
                public final void sendPackets(MapleClient c) {
                    c.sendPacket(MobPacket.spawnMonster(monster, -2, effect, 0));
                }
            },
                    null);
            updateMonsterController(monster);

            spawnedMonstersOnMap.incrementAndGet();
            return monster.getObjectId();
        } catch (Exception e) {
            return -1;
        }
    }

    public final void spawnFakeMonster(final MapleMonster monster) {
        monster.setMap(this);
        monster.setFake(true);

        spawnAndAddRangedMapObject(
                monster,
                new DelayedPacketCreation() {

            @Override
            public final void sendPackets(MapleClient c) {
                c.sendPacket(MobPacket.spawnMonster(monster, -2, 0xfc, 0));
                //		c.sendPacket(MobPacket.spawnFakeMonster(monster, 0));
            }
        },
                null);
        updateMonsterController(monster);

        spawnedMonstersOnMap.incrementAndGet();
    }

    public final void spawnReactor(final MapleReactor reactor) {
        reactor.setMap(this);

        spawnAndAddRangedMapObject(
                reactor,
                new DelayedPacketCreation() {

            @Override
            public final void sendPackets(MapleClient c) {
                c.sendPacket(MaplePacketCreator.spawnReactor(reactor));
            }
        },
                null);
    }

    private void respawnReactor(final MapleReactor reactor) {
        reactor.setState((byte) 0);
        reactor.setAlive(true);
        spawnReactor(reactor);
    }

    public final void spawnDoor(final MapleDoor door) {
        spawnAndAddRangedMapObject(
                door,
                new DelayedPacketCreation() {

            public final void sendPackets(MapleClient c) {
                c.sendPacket(
                        MaplePacketCreator.spawnDoor(
                                door.getOwner().getId(), door.getTargetPosition(), false));
                if (door.getOwner().getParty() != null
                        && (door.getOwner() == c.getPlayer()
                        || door.getOwner()
                                .getParty()
                                .containsMembers(
                                        new MaplePartyCharacter(c.getPlayer())))) {
                    c.sendPacket(
                            MaplePacketCreator.partyPortal(
                                    door.getTown().getId(),
                                    door.getTarget().getId(),
                                    door.getSkill(),
                                    door.getTargetPosition()));
                }
                c.sendPacket(
                        MaplePacketCreator.spawnPortal(
                                door.getTown().getId(),
                                door.getTarget().getId(),
                                door.getSkill(),
                                door.getTargetPosition()));
                c.sendPacket(MaplePacketCreator.enableActions());
            }
        },
                new SpawnCondition() {

            public final boolean canSpawn(final MapleCharacter chr) {
                return door.getTarget().getId() == chr.getMapId()
                        || door.getOwnerId() == chr.getId()
                        || (door.getOwner() != null
                        && door.getOwner().getParty() != null
                        && door.getOwner().getParty().getMemberById(chr.getId())
                        != null);
            }
        });
    }

    public final void spawnSummon(final MapleSummon summon) {
        summon.updateMap(this);
        spawnAndAddRangedMapObject(
                summon,
                new DelayedPacketCreation() {

            @Override
            public void sendPackets(MapleClient c) {
                if (summon != null) {
                    c.sendPacket(MaplePacketCreator.spawnSummon(summon, true));
                }
            }
        },
                null);
    }

    public final void spawnDragon(final MapleDragon summon) {
        spawnAndAddRangedMapObject(
                summon,
                new DelayedPacketCreation() {

            @Override
            public void sendPackets(MapleClient c) {
                c.sendPacket(MaplePacketCreator.spawnDragon(summon));
            }
        },
                null);
    }

    public final void spawnMist(final MapleMist mist, final int duration, boolean fake) {
        spawnAndAddRangedMapObject(
                mist,
                new DelayedPacketCreation() {

            @Override
            public void sendPackets(MapleClient c) {
                mist.sendSpawnData(c);
            }
        },
                null);

        final Rectangle mistBox = mist.getBox();
        final MapTimer tMan = MapTimer.getInstance();
        final ScheduledFuture<?> poisonSchedule;
        switch (mist.isPoisonMist()) {
            case 1:
                // poison: 0 = none, 1 = poisonous, 2 = recovery aura
                final MapleCharacter owner = getCharacterById(mist.getOwnerId());
                poisonSchedule
                        = tMan.register(
                                new Runnable() {

                            @Override
                            public void run() {
                                final List<MapleMapObject> monstersInRect
                                        = new ArrayList<>();
                                final ReentrantReadWriteLock monsterLock
                                        = mapobjectlocks.get(MapleMapObjectType.MONSTER);
                                monsterLock.readLock().lock();
                                try {
                                    final LinkedHashMap<Integer, MapleMapObject> monsterMap
                                            = mapobjects.get(
                                                    MapleMapObjectType.MONSTER);
                                    if (monsterMap != null) {
                                        monstersInRect.addAll(monsterMap.values());
                                    }
                                } finally {
                                    monsterLock.readLock().unlock();
                                }

                                int d = 0;
                                for (final MapleMapObject mo : monstersInRect) {
                                    if (!mistBox.contains(mo.getPosition())) {
                                        continue;
                                    }
                                    if (mist.makeChanceResult()
                                            && !((MapleMonster) mo)
                                                    .isBuffed(MonsterStatus.POISON)
                                            && ((MapleMonster) mo).getHp() > 1) {
                                        ((MapleMonster) mo)
                                                .applyStatus(
                                                        owner,
                                                        new MonsterStatusEffect(
                                                                MonsterStatus.POISON,
                                                                1,
                                                                mist.getSourceSkill()
                                                                        .getId(),
                                                                null,
                                                                false),
                                                        true,
                                                        duration,
                                                        false,
                                                        d);
                                        d += 137;
                                    }
                                }
                            }
                        },
                                2000,
                                2500);
                break;
            case 2:
                poisonSchedule
                        = tMan.register(
                                new Runnable() {

                            @Override
                            public void run() {
                                final List<MapleMapObject> playersInRect
                                        = new ArrayList<>();
                                final ReentrantReadWriteLock playerLock
                                        = mapobjectlocks.get(MapleMapObjectType.PLAYER);
                                playerLock.readLock().lock();
                                try {
                                    final LinkedHashMap<Integer, MapleMapObject> playerMap
                                            = mapobjects.get(MapleMapObjectType.PLAYER);
                                    if (playerMap != null) {
                                        playersInRect.addAll(playerMap.values());
                                    }
                                } finally {
                                    playerLock.readLock().unlock();
                                }

                                for (final MapleMapObject mo : playersInRect) {
                                    if (!mistBox.contains(mo.getPosition())) {
                                        continue;
                                    }
                                    if (mist.makeChanceResult()) {
                                        final MapleCharacter chr = ((MapleCharacter) mo);
                                        chr.addMP(
                                                (int) (mist.getSource().getX()
                                                * (chr.getStat().getMaxMp()
                                                / 100.0)));
                                    }
                                }
                            }
                        },
                                2000,
                                2500);
                break;
            default:
                poisonSchedule = null;
                break;
        }
        tMan.schedule(
                new Runnable() {

            @Override
            public void run() {
                // broadcastMessage(MaplePacketCreator.removeMist(mist.getObjectId()));
                broadcastMessage(MaplePacketCreator.removeMist(mist.getObjectId(), false));
                removeMapObject(mist);
                if (poisonSchedule != null) {
                    poisonSchedule.cancel(false);
                }
            }
        },
                duration);
    }

    public final void disappearingItemDrop(
            final MapleMapObject dropper,
            final MapleCharacter owner,
            final IItem item,
            final Point pos) {
        final Point droppos = calcDropPos(pos, pos);
        final MapleMapItem drop = new MapleMapItem(item, droppos, dropper, owner, (byte) 1, false);
        broadcastMessage(
                MaplePacketCreator.dropItemFromMapObject(
                        drop, dropper.getPosition(), droppos, (byte) 3),
                drop.getPosition());
    }

    public final void spawnMesoDrop(
            final int meso,
            final Point position,
            final MapleMapObject dropper,
            final MapleCharacter owner,
            final boolean playerDrop,
            final byte droptype) {
        final Point droppos = calcDropPos(position, position);
        final MapleMapItem mdrop
                = new MapleMapItem(meso, droppos, dropper, owner, droptype, playerDrop);

        spawnAndAddRangedMapObject(
                mdrop,
                new DelayedPacketCreation() {

            @Override
            public void sendPackets(MapleClient c) {
                c.sendPacket(
                        MaplePacketCreator.dropItemFromMapObject(
                                mdrop, dropper.getPosition(), droppos, (byte) 1));
            }
        },
                null);
        if (!everlast) {
            mdrop.registerExpire(120000);
            if (droptype == 0 || droptype == 1) {
                mdrop.registerFFA(30000);
            }
        }
    }

    public final void spawnMobMesoDrop(
            final int meso,
            final Point position,
            final MapleMapObject dropper,
            final MapleCharacter owner,
            final boolean playerDrop,
            final byte droptype) {
        final MapleMapItem mdrop
                = new MapleMapItem(meso, position, dropper, owner, droptype, playerDrop);

        // 先取得 dropper 位置的輕量快照，避免在延遲封包建立/發送階段
        // 再次呼叫外部物件（MapleMapObject）的同步方法而產生鎖耦合。
        final Point dropperPos = dropper.getPosition();

        spawnAndAddRangedMapObject(
                mdrop,
                new DelayedPacketCreation() {

            @Override
            public void sendPackets(MapleClient c) {
                c.sendPacket(
                        MaplePacketCreator.dropItemFromMapObject(
                                mdrop, dropperPos, position, (byte) 1));
            }
        },
                null);

        mdrop.registerExpire(120000);
        if (droptype == 0 || droptype == 1) {
            mdrop.registerFFA(30000);
        }
    }

    public final void spawnMobDrop(
            final IItem idrop,
            final Point dropPos,
            final MapleMonster mob,
            final MapleCharacter chr,
            final byte droptype,
            final short questid) {
        final MapleMapItem mdrop
                = new MapleMapItem(idrop, dropPos, mob, chr, droptype, false, questid);

        // 於鎖外先取得怪物位置快照，避免在廣播/回呼期間持有怪物鎖或地圖物件鎖
        final Point mobPos = mob.getPosition();

        spawnAndAddRangedMapObject(
                mdrop,
                new DelayedPacketCreation() {

            @Override
            public void sendPackets(MapleClient c) {
                if (questid <= 0 || c.getPlayer().getQuestStatus(questid) == 1) {
                    c.sendPacket(
                            MaplePacketCreator.dropItemFromMapObject(
                                    mdrop, mobPos, dropPos, (byte) 1));
                }
            }
        },
                null);
        //	broadcastMessage(MaplePacketCreator.dropItemFromMapObject(mdrop, mob.getPosition(),
        // dropPos,
        // (byte) 0));

        mdrop.registerExpire(120000);
        if (droptype == 0 || droptype == 1) {
            mdrop.registerFFA(30000);
        }
        activateItemReactors(mdrop, chr.getClient());
    }

    public final void spawnRandDrop() {
        if (mapid != 910000000 || channel != 1) {
            return; // fm, ch1
        }

        // 使用線程安全快照，避免直接持有地圖物件鎖進行遍歷，降低鎖競爭並防止 ConcurrentModificationException
        for (MapleMapItem item : getAllItemsThreadsafe()) {
            if (item.isRandDrop()) {
                return;
            }
        }

        MapTimer.getInstance()
                .schedule(
                        new Runnable() {

                    @Override
                    public void run() {
                        final Point pos = new Point(Randomizer.nextInt(800) + 531, -806);
                        final int theItem = Randomizer.nextInt(1000);
                        int itemid = 0;
                        if (theItem
                                < 950) { // 0-949 = normal, 950-989 = rare, 990-999 = super
                            itemid
                                    = GameConstants.normalDrops[Randomizer.nextInt(
                                            GameConstants.normalDrops.length)];
                        } else if (theItem < 990) {
                            itemid
                                    = GameConstants.rareDrops[Randomizer.nextInt(
                                            GameConstants.rareDrops.length)];
                        } else {
                            itemid
                                    = GameConstants.superDrops[Randomizer.nextInt(
                                            GameConstants.superDrops.length)];
                        }
                        spawnAutoDrop(itemid, pos);
                    }
                },
                        20000);
    }

    public final void spawnAutoDrop(final int itemid, final Point pos) {
        IItem idrop = null;
        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        if (GameConstants.getInventoryType(itemid) == MapleInventoryType.EQUIP) {
            idrop = ii.randomizeStats((Equip) ii.getEquipById(itemid));
        } else {
            idrop = new Item(itemid, (byte) 0, (short) 1, (byte) 0);
        }
        final MapleMapItem mdrop = new MapleMapItem(pos, idrop);
        spawnAndAddRangedMapObject(
                mdrop,
                new DelayedPacketCreation() {

            @Override
            public void sendPackets(MapleClient c) {
                c.sendPacket(
                        MaplePacketCreator.dropItemFromMapObject(
                                mdrop, pos, pos, (byte) 1));
            }
        },
                null);
        broadcastMessage(MaplePacketCreator.dropItemFromMapObject(mdrop, pos, pos, (byte) 0));
        mdrop.registerExpire(120000);
    }

    public final void spawnItemDrop(
            final MapleMapObject dropper,
            final MapleCharacter owner,
            final IItem item,
            Point pos,
            final boolean ffaDrop,
            final boolean playerDrop) {
        final Point droppos = calcDropPos(pos, pos);
        final MapleMapItem drop
                = new MapleMapItem(item, droppos, dropper, owner, (byte) 2, playerDrop);

        // 先取得 dropper 位置的輕量快照，避免在鎖內或廣播時呼叫外部物件同步方法
        final Point dropperPos = dropper.getPosition();

        spawnAndAddRangedMapObject(
                drop,
                new DelayedPacketCreation() {

            @Override
            public void sendPackets(MapleClient c) {
                c.sendPacket(
                        MaplePacketCreator.dropItemFromMapObject(
                                drop, dropperPos, droppos, (byte) 1));
            }
        },
                null);
        broadcastMessage(
                MaplePacketCreator.dropItemFromMapObject(drop, dropperPos, droppos, (byte) 0));

        if (!everlast) {
            drop.registerExpire(120000);
            activateItemReactors(drop, owner.getClient());
        }
    }

    private void activateItemReactors(final MapleMapItem drop, final MapleClient c) {
        final IItem item = drop.getItem();

        final ReentrantReadWriteLock reactorLock = mapobjectlocks.get(MapleMapObjectType.REACTOR);
        final Map<Integer, MapleMapObject> reactorMap = mapobjects.get(MapleMapObjectType.REACTOR);
        if (reactorLock == null || reactorMap == null) {
            return;
        }

        final List<MapleMapObject> reactors;
        reactorLock.readLock().lock();
        try {
            reactors = new ArrayList<>(reactorMap.values());
        } finally {
            reactorLock.readLock().unlock();
        }

        boolean scheduled = false;
        for (final MapleMapObject o : reactors) {
            final MapleReactor react = (MapleReactor) o;

            if (react.getReactorType() == 100
                    && GameConstants.isCustomReactItem(
                            react.getReactorId(), item.getItemId(), react.getReactItem().left())
                    && react.getReactItem().right() == item.getQuantity()
                    && react.getArea().contains(drop.getPosition())) {
                synchronized (react) {
                    if (!react.isTimerActive()) {
                        MapTimer.getInstance()
                                .schedule(new ActivateItemReactor(drop, react, c), 5000);
                        react.setTimerActive(true);
                        scheduled = true;
                    }
                }
                if (scheduled) {
                    break;
                }
            }
        }
    }

    public int getItemsSize() {
        return mapobjects.get(MapleMapObjectType.ITEM).size();
    }

    public List<MapleMapItem> getAllItems() {
        return getAllItemsThreadsafe(); // Which genius wrote this?
    }

    public List<MapleMapItem> getAllItemsThreadsafe() {
        ArrayList<MapleMapItem> ret = new ArrayList<>();
        mapobjectlocks.get(MapleMapObjectType.ITEM).readLock().lock();
        try {
            for (MapleMapObject mmo : mapobjects.get(MapleMapObjectType.ITEM).values()) {
                ret.add((MapleMapItem) mmo);
            }
        } finally {
            mapobjectlocks.get(MapleMapObjectType.ITEM).readLock().unlock();
        }
        return ret;
    }

    public final void returnEverLastItem(final MapleCharacter chr) {
        final List<MapleMapItem> toReturn = new ArrayList<>();
        for (final MapleMapObject o : getAllItemsThreadsafe()) {
            final MapleMapItem item = ((MapleMapItem) o);
            if (item.getOwner() == chr.getId()) {
                item.setPickedUp(true);
                toReturn.add(item);
            }
        }
        for (final MapleMapItem item : toReturn) {
            broadcastMessage(
                    MaplePacketCreator.removeItemFromMap(item.getObjectId(), 2, chr.getId()),
                    item.getPosition());
            if (item.getMeso() > 0) {
                chr.gainMeso(item.getMeso(), false);
            } else {
                MapleInventoryManipulator.addFromDrop(chr.getClient(), item.getItem(), false);
            }
            removeMapObject(item);
        }
        spawnRandDrop();
    }

    public final void talkMonster(final String msg, final int itemId, final int objectid) {
        if (itemId > 0) {
            startMapEffect(msg, itemId, false);
        }
        broadcastMessage(MobPacket.talkMonster(objectid, itemId, msg)); // 5120035
        broadcastMessage(MobPacket.removeTalkMonster(objectid));
    }

    public final void startMapEffect(final String msg, final int itemId) {
        startMapEffect(msg, itemId, false);
    }

    public final void startMapEffect(final String msg, final int itemId, final boolean jukebox) {
        final MapleMapEffect effect;
        synchronized (this) {
            if (mapEffect != null) {
                return;
            }
            effect = new MapleMapEffect(msg, itemId);
            effect.setJukebox(jukebox);
            mapEffect = effect;
        }
        broadcastMessage(effect.makeStartData());
        MapTimer.getInstance()
                .schedule(
                        new Runnable() {

                    @Override
                    public void run() {
                        broadcastMessage(effect.makeDestroyData());
                        synchronized (MapleMap.this) {
                            if (mapEffect == effect) {
                                mapEffect = null;
                            }
                        }
                    }
                },
                        jukebox ? 300000 : 30000);
    }

    public final void startExtendedMapEffect(final String msg, final int itemId) {
        broadcastMessage(MaplePacketCreator.startMapEffect(msg, itemId, true));
        MapTimer.getInstance()
                .schedule(
                        new Runnable() {

                    @Override
                    public void run() {
                        broadcastMessage(MaplePacketCreator.removeMapEffect());
                        broadcastMessage(
                                MaplePacketCreator.startMapEffect(msg, itemId, false));
                        // dont remove mapeffect.
                    }
                },
                        60000);
    }

    public final void startJukebox(final String msg, final int itemId) {
        startMapEffect(msg, itemId, true);
    }

    public final void addPlayer(final MapleCharacter chr) {
        final ReentrantReadWriteLock playerMapLock = mapobjectlocks.get(MapleMapObjectType.PLAYER);
        final Map<Integer, MapleMapObject> playerMap = mapobjects.get(MapleMapObjectType.PLAYER);
        playerMapLock.writeLock().lock();
        try {
            playerMap.put(chr.getObjectId(), chr);
        } finally {
            playerMapLock.writeLock().unlock();
        }

        charactersLock.writeLock().lock();
        try {
            characters.add(chr);
        } finally {
            charactersLock.writeLock().unlock();
        }

        if (mapid == 109080000
                || mapid == 109080001
                || mapid == 109080002
                || mapid == 109080003
                || mapid == 109080010
                || mapid == 109080011
                || mapid == 109080012) {
            chr.setCoconutTeam(getAndSwitchTeam() ? 0 : 1);
        }
        if (!chr.isHidden()) {
            broadcastMessage(chr, MaplePacketCreator.spawnPlayerMapobject(chr), false);
            if (chr.isGM() && speedRunStart > 0) {
                endSpeedRun();
                broadcastMessage(MaplePacketCreator.serverNotice(5, "The speed run has ended."));
            }
        }
        if (!chr.isClone()) {
            if (!onFirstUserEnter.equals("")) {
                if (getCharactersSize() == 1) {
                    MapScriptMethods.startScript_FirstUser(chr.getClient(), onFirstUserEnter);
                }
            }
            sendObjectPlacement(chr);

            chr.getClient().sendPacket(MaplePacketCreator.spawnPlayerMapobject(chr));

            if (!onUserEnter.equals("")) {
                MapScriptMethods.startScript_User(chr.getClient(), onUserEnter);
            }
            switch (mapid) {
                case 109030001:
                case 109040000:
                case 109060001:
                case 109080000:
                case 109080010:
                    chr.getClient().sendPacket(MaplePacketCreator.showEventInstructions());
                    break;
                /*                case 109080000: // coconut shit
                case 109080001:
                case 109080002:
                case 109080003:
                case 109080010:
                case 109080011:
                case 109080012:
                chr.getClient().sendPacket(MaplePacketCreator.showEquipEffect(chr.getCoconutTeam()));
                break;*/
                case 809000101:
                case 809000201:
                    chr.getClient().sendPacket(MaplePacketCreator.showEquipEffect());
                    break;
            }
        }
        for (final MaplePet pet : chr.getPets()) {
            if (pet.getSummoned()) {
                broadcastMessage(chr, PetPacket.showPet(chr, pet, false, false), false);
            }
        }
        if (chr.getParty() != null && !chr.isClone()) {
            chr.silentPartyUpdate();
            chr.getClient()
                    .sendPacket(
                            MaplePacketCreator.updateParty(
                                    chr.getClient().getChannel(),
                                    chr.getParty(),
                                    PartyOperation.SILENT_UPDATE,
                                    null));
            chr.updatePartyMemberHP();
            chr.receivePartyMemberHP();
        }
        final MapleStatEffect stat = chr.getStatForBuff(MapleBuffStat.SUMMON);
        if (stat != null && !chr.isClone()) {
            final MapleSummon summon = chr.getSummons().get(stat.getSourceId());
            if (summon != null) {
                summon.setPosition(chr.getPosition());
                final MapleFootholdTree fhTree = getFootholds();
                final MapleFoothold fh
                        = fhTree == null ? null : fhTree.findBelow(chr.getPosition());
                summon.setFh(fh == null ? 0 : fh.getId()); // 找不到立足點時設為 0，可由移動修正
                chr.addVisibleMapObject(summon);
                this.spawnSummon(summon);
            }
        }
        if (mapEffect != null) {
            mapEffect.sendStartData(chr.getClient());
        }
        if (timeLimit > 0 && getForcedReturnMap() != null && !chr.isClone()) {
            chr.startMapTimeLimitTask(timeLimit, getForcedReturnMap());
        }
        if (chr.getBuffedValue(MapleBuffStat.MONSTER_RIDING) != null) {
            if (FieldLimitType.Mount.check(fieldLimit)) {
                chr.cancelBuffStats(MapleBuffStat.MONSTER_RIDING);
            }
        }
        if (!chr.isClone()) {
            if (chr.getEventInstance() != null
                    && chr.getEventInstance().isTimerStarted()
                    && !chr.isClone()) {
                chr.getClient()
                        .sendPacket(
                                MaplePacketCreator.getClock(
                                        (int) (chr.getEventInstance().getTimeLeft() / 1000)));
            }
            if (hasClock()) {
                final Calendar cal = Calendar.getInstance();
                chr.getClient()
                        .sendPacket(
                                (MaplePacketCreator.getClockTime(
                                        cal.get(Calendar.HOUR_OF_DAY),
                                        cal.get(Calendar.MINUTE),
                                        cal.get(Calendar.SECOND))));
            }
            if (chr.getCarnivalParty() != null && chr.getEventInstance() != null) {
                chr.getEventInstance().onMapLoad(chr);
            }
            MapleEvent.mapLoad(chr, channel);
            if (getSquadBegin() != null
                    && getSquadBegin().getTimeLeft() > 0
                    && getSquadBegin().getStatus() == 1) {
                chr.getClient()
                        .sendPacket(
                                MaplePacketCreator.getClock(
                                        (int) (getSquadBegin().getTimeLeft() / 1000)));
            }
            if (mapid / 1000 != 105100
                    && mapid / 100 != 8020003
                    && mapid / 100
                    != 8020008) { // no boss_balrog/2095/coreblaze/auf. but coreblaze/auf
                // does AFTER
                final MapleSquad sqd = getSquadByMap(); // for all squads
                if (!squadTimer
                        && sqd != null
                        && chr.getName().equals(sqd.getLeaderName())
                        && !chr.isClone()) {
                    // leader? display
                    doShrine(false);
                    squadTimer = true;
                }
            }
            if (getNumMonsters() > 0
                    && (mapid == 280030001
                    || mapid == 240060201
                    || mapid == 280030000
                    || mapid == 240060200
                    || mapid == 220080001
                    || mapid == 541020800
                    || mapid == 541010100)) {
                String music = "Bgm09/TimeAttack";
                switch (mapid) {
                    case 240060200:
                    case 240060201:
                        music = "Bgm14/HonTale";
                        break;
                    case 280030000:
                    case 280030001:
                        music = "Bgm06/FinalFight";
                        break;
                    case 200090000:
                    case 200090010:
                        music = "Bgm04/ArabPirate";
                        break;
                }
                chr.getClient().sendPacket(MaplePacketCreator.musicChange(music));
                // maybe timer too for zak/ht
            }
            for (final WeakReference<MapleCharacter> chrz : chr.getClones()) {
                if (chrz.get() != null) {
                    chrz.get().setPosition(new Point(chr.getPosition()));
                    chrz.get().setMap(this);
                    addPlayer(chrz.get());
                }
            }
            if (mapid == 914000000) {
                chr.getClient().sendPacket(MaplePacketCreator.temporaryStats_Aran());
            } else if (mapid == 105100300 && chr.getLevel() >= 91) {
                chr.getClient().sendPacket(MaplePacketCreator.temporaryStats_Balrog(chr));
            } else if (mapid == 140090000
                    || mapid == 105100301
                    || mapid == 105100401
                    || mapid == 105100100) {
                chr.getClient().sendPacket(MaplePacketCreator.temporaryStats_Reset());
            }
        }
        //        if (GameConstants.isEvan(chr.getJob()) && chr.getJob() >= 2200 &&
        // chr.getBuffedValue(MapleBuffStat.MONSTER_RIDING) == null) {
        //            if (chr.getDragon() == null) {
        //                chr.makeDragon();
        //            }
        //            spawnDragon(chr.getDragon());
        //            if (!chr.isClone()) {
        //                updateMapObjectVisibility(chr, chr.getDragon());
        //            }
        //        }
        if ((mapid == 10000 && chr.getJob() == 0)
                || (mapid == 130030000 && chr.getJob() == 1000)
                || (mapid == 914000000 && chr.getJob() == 2000)
                || (mapid == 900010000 && chr.getJob() == 2001)) {
            //            chr.getClient().sendPacket(MaplePacketCreator.startMapEffect("Welcome to "
            // +
            // chr.getClient().getChannelServer().getServerName() + "!", 5122000, true));
            //            chr.dropMessage(1, "Welcome to " +
            // chr.getClient().getChannelServer().getServerName() + ", " + chr.getName() + " !
            // \r\nUse
            // @joyce to collect your Item Of Appreciation once you're level 10! \r\nUse @help for
            // commands. \r\nGood luck and have fun!");
            //            chr.dropMessage(5, "Use @joyce to collect your Item Of Appreciation once
            // you're
            // level 10! Use @help for commands. Good luck and have fun!");

        }
        if (permanentWeather > 0) {
            chr.getClient()
                    .sendPacket(
                            MaplePacketCreator.startMapEffect(
                                    "", permanentWeather, false)); // snow, no msg
        }
        if (getPlatforms().size() > 0) {
            chr.getClient().sendPacket(MaplePacketCreator.getMovingPlatforms(this));
        }
        if (environment.size() > 0) {
            chr.getClient().sendPacket(MaplePacketCreator.getUpdateEnvironment(this));
        }
        if (isTown()) {
            chr.cancelEffectFromBuffStat(MapleBuffStat.RAINING_MINES);
        }
    }

    public int getNumItems() {
        mapobjectlocks.get(MapleMapObjectType.ITEM).readLock().lock();
        try {
            return mapobjects.get(MapleMapObjectType.ITEM).size();
        } finally {
            mapobjectlocks.get(MapleMapObjectType.ITEM).readLock().unlock();
        }
    }

    public int getNumMonsters() {
        mapobjectlocks.get(MapleMapObjectType.MONSTER).readLock().lock();
        try {
            return mapobjects.get(MapleMapObjectType.MONSTER).size();
        } finally {
            mapobjectlocks.get(MapleMapObjectType.MONSTER).readLock().unlock();
        }
    }

    public void doShrine(final boolean spawned) {
        if (squadSchedule != null) {
            cancelSquadSchedule();
        }
        final int mode
                = (mapid == 280030000
                        ? 1
                        : (mapid == 280030001
                                ? 2
                                : (mapid == 240060200 || mapid == 240060201 ? 3 : 0)));
        final MapleSquad sqd = getSquadByMap();
        final EventManager em = getEMByMap();
        if (sqd != null && em != null && getCharactersSize() > 0) {
            final String leaderName = sqd.getLeaderName();
            final String state = em.getProperty("state");
            final Runnable run;
            MapleMap returnMapa = getForcedReturnMap();
            if (returnMapa == null || returnMapa.getId() == mapid) {
                returnMapa = getReturnMap();
            }
            final MapleMap returnMapz = returnMapa;

            if (mode == 1) { // zakum
                broadcastMessage(MaplePacketCreator.showZakumShrine(spawned, 5));
            } else if (mode == 2) { // chaoszakum
                broadcastMessage(MaplePacketCreator.showChaosZakumShrine(spawned, 5));
            } else if (mode == 3) { // ht/chaosht
                broadcastMessage(MaplePacketCreator.showChaosHorntailShrine(spawned, 5));
            } else {
                broadcastMessage(MaplePacketCreator.showHorntailShrine(spawned, 5));
            }
            if (mode == 1 || spawned) { // both of these together dont go well
                broadcastMessage(MaplePacketCreator.getClock(300)); // 5 min
            }

            if (!spawned) { // no monsters yet; enforce timer to spawn it quickly
                final List<MapleMonster> initialMonsters = getAllMonstersThreadsafe();
                final int initialMonsterCount = initialMonsters.size();
                final java.util.Set<Integer> initialMonsterIds
                        = new java.util.HashSet<Integer>(initialMonsterCount);
                for (MapleMonster m : initialMonsters) {
                    initialMonsterIds.add(m.getObjectId());
                }

                run
                        = new Runnable() {
                    @Override
                    public void run() {
                        final MapleSquad sqnow = getSquadByMap();
                        final EventManager emNow = getEMByMap();
                        if (getCharactersSize() > 0
                                && getNumMonsters() == initialMonsterCount
                                && sqnow != null
                                && sqnow.getStatus() == 2
                                && sqnow.getLeaderName().equals(leaderName)
                                && emNow != null
                                && emNow.getProperty("state").equals(state)) {
                            boolean passed = initialMonsterIds.isEmpty();
                            if (!passed) {
                                for (MapleMonster m : getAllMonstersThreadsafe()) {
                                    if (initialMonsterIds.contains(m.getObjectId())) {
                                        passed = true;
                                        break;
                                    }
                                }
                            }
                            if (passed) {
                                MaplePacket packet;
                                if (mode == 1) { // zakum
                                    packet = MaplePacketCreator.showZakumShrine(spawned, 0);
                                } else if (mode == 2) { // chaoszakum
                                    packet
                                            = MaplePacketCreator.showChaosZakumShrine(
                                                    spawned, 0);
                                } else {
                                    packet
                                            = MaplePacketCreator.showHorntailShrine(
                                                    spawned, 0);
                                }
                                for (MapleCharacter chr : getCharactersThreadsafe()) {
                                    chr.getClient().sendPacket(packet);
                                    chr.changeMap(returnMapz, returnMapz.getPortal(0));
                                }
                                checkStates("");
                                resetFully();
                            }
                        }
                    }
                };
            } else { // enforce timer to gtfo
                run
                        = new Runnable() {
                    @Override
                    public void run() {
                        final MapleSquad sqnow = getSquadByMap();
                        final EventManager emNow = getEMByMap();
                        if (getCharactersSize() > 0
                                && sqnow != null
                                && sqnow.getStatus() == 2
                                && sqnow.getLeaderName().equals(leaderName)
                                && emNow != null
                                && emNow.getProperty("state").equals(state)) {
                            MaplePacket packet;
                            if (mode == 1) { // zakum
                                packet = MaplePacketCreator.showZakumShrine(spawned, 0);
                            } else if (mode == 2) { // chaoszakum
                                packet
                                        = MaplePacketCreator.showChaosZakumShrine(spawned, 0);
                            } else {
                                packet = MaplePacketCreator.showHorntailShrine(spawned, 0);
                            }
                            for (MapleCharacter chr : getCharactersThreadsafe()) {
                                chr.getClient().sendPacket(packet);
                                chr.changeMap(returnMapz, returnMapz.getPortal(0));
                            }
                            checkStates("");
                            resetFully();
                        }
                    }
                };
            }
            squadSchedule = MapTimer.getInstance().schedule(run, 300000); // 5 mins
        }
    }

    public final MapleSquad getSquadByMap() {
        MapleSquadType zz = null;
        switch (mapid) {
            case 105100400:
            case 105100300:
                zz = MapleSquadType.bossbalrog;
                break;
            case 280030000:
                zz = MapleSquadType.zak;
                break;
            case 280030001:
                zz = MapleSquadType.chaoszak;
                break;
            case 240060200:
                zz = MapleSquadType.horntail;
                break;
            case 240060201:
                zz = MapleSquadType.chaosht;
                break;
            case 270050100:
                zz = MapleSquadType.pinkbean;
                break;
            case 802000111:
                zz = MapleSquadType.nmm_squad;
                break;
            case 802000211:
                zz = MapleSquadType.vergamot;
                break;
            case 802000311:
                zz = MapleSquadType.tokyo_2095;
                break;
            case 802000411:
                zz = MapleSquadType.dunas;
                break;
            case 802000611:
                zz = MapleSquadType.nibergen_squad;
                break;
            case 802000711:
                zz = MapleSquadType.dunas2;
                break;
            case 802000801:
            case 802000802:
            case 802000803:
                zz = MapleSquadType.core_blaze;
                break;
            case 802000821:
            case 802000823:
                zz = MapleSquadType.aufheben;
                break;
            case 211070100:
            case 211070101:
            case 211070110:
                zz = MapleSquadType.vonleon;
                break;
            case 551030200:
                zz = MapleSquadType.scartar;
                break;
            case 271040100:
                zz = MapleSquadType.cygnus;
                break;
            default:
                return null;
        }
        return ChannelServer.getInstance(channel).getMapleSquad(zz);
    }

    public final MapleSquad getSquadBegin() {
        if (squad != null) {
            return ChannelServer.getInstance(channel).getMapleSquad(squad);
        }
        return null;
    }

    public final EventManager getEMByMap() {
        String em = null;
        switch (mapid) {
            case 105100400:
                em = "BossBalrog_EASY";
                break;
            case 105100300:
                em = "BossBalrog_NORMAL";
                break;
            case 280030000:
                em = "ZakumBattle";
                break;
            case 240060200:
                em = "HorntailBattle";
                break;
            case 280030001:
                em = "ChaosZakum";
                break;
            case 240060201:
                em = "ChaosHorntail";
                break;
            case 270050100:
                em = "PinkBeanBattle";
                break;
            case 802000111:
                em = "NamelessMagicMonster";
                break;
            case 802000211:
                em = "Vergamot";
                break;
            case 802000311:
                em = "2095_tokyo";
                break;
            case 802000411:
                em = "Dunas";
                break;
            case 802000611:
                em = "Nibergen";
                break;
            case 802000711:
                em = "Dunas2";
                break;
            case 802000801:
            case 802000802:
            case 802000803:
                em = "CoreBlaze";
                break;
            case 802000821:
            case 802000823:
                em = "Aufhaven";
                break;
            case 211070100:
            case 211070101:
            case 211070110:
                em = "VonLeonBattle";
                break;
            case 551030200:
                em = "ScarTarBattle";
                break;
            case 271040100:
                em = "CygnusBattle";
                break;
            case 262030300:
                em = "HillaBattle";
                break;
            case 262031300:
                em = "DarkHillaBattle";
                break;
            case 272020110:
            case 272030400:
                em = "ArkariumBattle";
                break;
            case 955000100:
            case 955000200:
            case 955000300:
                em = "AswanOffSeason";
                break;
            /*      */ case 280030100:
                /* 2829 */ em = "ZakumBattle";
                /* 2830 */ break;
            /*      */ case 272020200:
                /* 2862 */ em = "Akayile";
                /* 2863 */ break;
            /*      */ case 689013000:
                /* 2888 */ em = "PinkZakum";
                /* 2889 */ break;
            /*      */ case 703200400:
                /* 2891 */ em = "0AllBoss";
                /* 2892 */ break;
            // case 689010000:
            //    em = "PinkZakumEntrance";
            //    break;
            // case 689013000:
            //    em = "PinkZakumFight";
            //    break;
            default:
                return null;
        }
        return ChannelServer.getInstance(channel).getEventSM().getEventManager(em);
    }

    public final void removePlayer(final MapleCharacter chr) {
        if (everlast) {
            returnEverLastItem(chr);
        }

        charactersLock.writeLock().lock();
        try {
            characters.remove(chr);
        } finally {
            charactersLock.writeLock().unlock();
        }

        removeMapObject(chr);
        chr.checkFollow();
        broadcastMessage(MaplePacketCreator.removePlayerFromMap(chr.getId()));

        if (!chr.isClone()) {
            // Snapshot the controlled monsters to avoid ConcurrentModificationException.
            final Collection<MapleMonster> controlled = chr.getControlled();
            final List<MapleMonster> controlledSnapshot = new ArrayList<>(controlled);
            final List<MapleMonster> update = new ArrayList<>();

            for (final MapleMonster monster : controlledSnapshot) {
                if (monster != null) {
                    monster.setController(null);
                    monster.setControllerHasAggro(false);
                    monster.setControllerKnowsAboutAggro(false);
                    controlled.remove(monster);
                    update.add(monster);
                }
            }

            for (final MapleMonster mons : update) {
                updateMonsterController(mons);
            }

            chr.leaveMap();
            checkStates(chr.getName());

            if (mapid == 109020001) {
                chr.canTalk(true);
            }

            for (final WeakReference<MapleCharacter> chrz : chr.getClones()) {
                if (chrz.get() != null) {
                    removePlayer(chrz.get());
                }
            }
        }

        chr.cancelEffectFromBuffStat(MapleBuffStat.PUPPET);
        chr.cancelEffectFromBuffStat(MapleBuffStat.REAPER);
        boolean cancelSummons = false;

        final List<MapleSummon> summonsSnapshot = new ArrayList<>(chr.getSummons().values());
        for (final MapleSummon summon : summonsSnapshot) {
            broadcastMessage(MaplePacketCreator.removeSummon(summon, true));
            summon.setChangedMap(true);
            removeMapObject(summon);
            if (summon.getMovementType() == SummonMovementType.STATIONARY
                    || summon.getMovementType() == SummonMovementType.CIRCLE_STATIONARY
                    || summon.getMovementType() == SummonMovementType.WALK_STATIONARY) {
                cancelSummons = true;
            }
        }

        if (cancelSummons) {
            chr.cancelEffectFromBuffStat(MapleBuffStat.SUMMON);
        }
    }

    public final void broadcastMessage(final MaplePacket packet) {
        broadcastMessage(null, packet, Double.POSITIVE_INFINITY, null);
    }

    public final void broadcastMessage(
            final MapleCharacter source, final MaplePacket packet, final boolean repeatToSource) {
        broadcastMessage(
                repeatToSource ? null : source,
                packet,
                Double.POSITIVE_INFINITY,
                source.getPosition());
    }

    /*	public void broadcastMessage(MapleCharacter source, MaplePacket packet, boolean repeatToSource, boolean ranged) {
    broadcastMessage(repeatToSource ? null : source, packet, ranged ? MapleCharacter.MAX_VIEW_RANGE_SQ : Double.POSITIVE_INFINITY, source.getPosition());
    }*/
    public final void broadcastMessage(final MaplePacket packet, final Point rangedFrom) {
        broadcastMessage(null, packet, GameConstants.maxViewRangeSq(), rangedFrom);
    }

    public final void broadcastMessage(
            final MapleCharacter source, final MaplePacket packet, final Point rangedFrom) {
        broadcastMessage(source, packet, GameConstants.maxViewRangeSq(), rangedFrom);
    }

    private void broadcastMessage(
            final MapleCharacter source,
            final MaplePacket packet,
            final double rangeSq,
            final Point rangedFrom) {
        final List<MapleCharacter> snapshot;
        charactersLock.readLock().lock();
        try {
            snapshot = new ArrayList<>(characters);
        } finally {
            charactersLock.readLock().unlock();
        }
        for (MapleCharacter chr : snapshot) {
            if (chr != source) {
                if (rangeSq < Double.POSITIVE_INFINITY) {
                    if (rangedFrom.distanceSq(chr.getPosition()) <= rangeSq) {
                        chr.getClient().sendPacket(packet);
                    }
                } else {
                    chr.getClient().sendPacket(packet);
                }
            }
        }
    }

    private void sendObjectPlacement(final MapleCharacter c) {
        if (c == null || c.isClone()) {
            return;
        }
        // 先取得怪物快照，避免在持有任何集合鎖時呼叫 updateMonsterController（可能觸發外部同步）
        final List<MapleMonster> monstersSnapshot = this.getAllMonstersThreadsafe();
        for (final MapleMonster m : monstersSnapshot) {
            updateMonsterController(m);
        }
        // 取得視野範圍內的物件快照（getMapObjectsInRange 內部已使用讀鎖並回傳新集合）
        final List<MapleMapObject> visibleObjects
                = getMapObjectsInRange(
                        c.getPosition(),
                        GameConstants.maxViewRangeSq(),
                        GameConstants.rangedMapobjectTypes);
        // 在鎖外逐一發送封包，保留原有順序與距離判定邏輯
        for (final MapleMapObject o : visibleObjects) {
            if (o.getType() == MapleMapObjectType.REACTOR) {
                if (!((MapleReactor) o).isAlive()) {
                    continue;
                }
            }
            o.sendSpawnData(c.getClient());
            c.addVisibleMapObject(o);
        }
    }

    public final List<MapleMapObject> getMapObjectsInRange(final Point from, final double rangeSq) {
        final List<MapleMapObject> ret = new ArrayList<>();
        if (from == null) {
            return ret;
        }
        for (MapleMapObjectType type : MapleMapObjectType.values()) {
            final ReentrantReadWriteLock lock = mapobjectlocks.get(type);
            if (lock == null) {
                continue;
            }
            final List<MapleMapObject> snapshot;
            lock.readLock().lock();
            try {
                final LinkedHashMap<Integer, MapleMapObject> typedMap = mapobjects.get(type);
                if (typedMap == null) {
                    continue;
                }
                snapshot = new ArrayList<>(typedMap.values());
            } finally {
                lock.readLock().unlock();
            }
            for (MapleMapObject mmo : snapshot) {
                if (mmo == null || mmo.getPosition() == null) {
                    continue;
                }
                if (from.distanceSq(mmo.getPosition()) <= rangeSq) {
                    ret.add(mmo);
                }
            }
        }
        return ret;
    }

    public List<MapleMapObject> getItemsInRange(Point from, double rangeSq) {
        return getMapObjectsInRange(from, rangeSq, Arrays.asList(MapleMapObjectType.ITEM));
    }

    public final List<MapleMapObject> getMapObjectsInRange(
            final Point from,
            final double rangeSq,
            final List<MapleMapObjectType> MapObject_types) {
        final List<MapleMapObject> ret = new ArrayList<>();
        for (MapleMapObjectType type : MapObject_types) {
            final ReentrantReadWriteLock lock = mapobjectlocks.get(type);
            final List<MapleMapObject> snapshot;
            lock.readLock().lock();
            try {
                // 在讀鎖保護下取得快照，避免遍歷時 ConcurrentModificationException
                snapshot = new ArrayList<>(mapobjects.get(type).values());
            } finally {
                lock.readLock().unlock();
            }
            // 鎖外遍歷快照，減少鎖持有時間，降低鎖競爭
            for (MapleMapObject mmo : snapshot) {
                if (from.distanceSq(mmo.getPosition()) <= rangeSq) {
                    ret.add(mmo);
                }
            }
        }
        return ret;
    }

    public final List<MapleMapObject> getMapObjectsInRect(
            final Rectangle box, final List<MapleMapObjectType> MapObject_types) {
        final List<MapleMapObject> ret = new ArrayList<>();
        for (MapleMapObjectType type : MapObject_types) {
            final ReentrantReadWriteLock lock = mapobjectlocks.get(type);
            final List<MapleMapObject> snapshot;
            lock.readLock().lock();
            try {
                snapshot = new ArrayList<>(mapobjects.get(type).values());
            } finally {
                lock.readLock().unlock();
            }
            for (MapleMapObject mmo : snapshot) {
                if (box.contains(mmo.getPosition())) {
                    ret.add(mmo);
                }
            }
        }
        return ret;
    }

    public final List<MapleCharacter> getPlayersInRectAndInList(
            final Rectangle box, final List<MapleCharacter> chrList) {
        if (chrList.isEmpty()) {
            return new LinkedList<>();
        }

        final List<MapleCharacter> character = new LinkedList<>();
        final List<MapleCharacter> snapshot;

        charactersLock.readLock().lock();
        try {
            snapshot = new ArrayList<>(characters);
        } finally {
            charactersLock.readLock().unlock();
        }

        for (final MapleCharacter a : snapshot) {
            if (chrList.contains(a) && box.contains(a.getPosition())) {
                character.add(a);
            }
        }
        return character;
    }

    public final void addPortal(final MaplePortal myPortal) {
        portals.put(myPortal.getId(), myPortal);
    }

    public final MaplePortal getPortal(final String portalname) {
        for (final MaplePortal port : portals.values()) {
            if (port.getName().equals(portalname)) {
                return port;
            }
        }
        return null;
    }

    public final MaplePortal getPortal(final int portalid) {
        return portals.get(portalid);
    }

    public final void resetPortals() {
        for (final MaplePortal port : portals.values()) {
            port.setPortalState(true);
        }
    }

    public final void setFootholds(final MapleFootholdTree footholds) {
        this.footholds = footholds;
    }

    public final MapleFootholdTree getFootholds() {
        return footholds;
    }

    public final void loadMonsterRate(final boolean first) {
        final List<Spawns> spawnSnapshot;
        synchronized (monsterSpawn) {
            spawnSnapshot = new ArrayList<>(monsterSpawn);
        }
        final int spawnSize = spawnSnapshot.size();
        maxRegularSpawn = Math.round(spawnSize * monsterRate);
        if (maxRegularSpawn < 2) {
            maxRegularSpawn = 2;
        }
        // else if (maxRegularSpawn > spawnSize) {
        //     maxRegularSpawn = spawnSize - (spawnSize / 15);
        // }
        if (fixedMob > 0) {
            maxRegularSpawn = fixedMob;
        }
        final Collection<Spawns> newSpawn = new LinkedList<>();
        final Collection<Spawns> newBossSpawn = new LinkedList<>();
        for (final Spawns s : spawnSnapshot) {
            if (s.getCarnivalTeam() >= 2) {
                continue; // Remove carnival spawned mobs
            }
            if (s.getMonster().getStats().isBoss()) {
                newBossSpawn.add(s);
            } else {
                newSpawn.add(s);
            }
        }
        synchronized (monsterSpawn) {
            monsterSpawn.clear();
            monsterSpawn.addAll(newBossSpawn);
            monsterSpawn.addAll(newSpawn);
        }

        if (first && spawnSize > 0) {
            lastSpawnTime = System.currentTimeMillis();
            if (GameConstants.isForceRespawn(mapid)) {
                createMobInterval = 15000;
            }
        }
    }

    public final SpawnPoint addMonsterSpawn(
            final MapleMonster monster,
            final int mobTime,
            final byte carnivalTeam,
            final String msg) {
        final Point newpos = calcPointBelow(monster.getPosition());
        newpos.y -= 1;
        final SpawnPoint sp = new SpawnPoint(monster, newpos, mobTime, carnivalTeam, msg);
        synchronized (monsterSpawn) {
            if (carnivalTeam > -1) {
                monsterSpawn.add(0, sp); // at the beginning
            } else {
                monsterSpawn.add(sp);
            }
        }
        return sp;
    }

    public final void addAreaMonsterSpawn(
            final MapleMonster monster,
            Point pos1,
            Point pos2,
            Point pos3,
            final int mobTime,
            final String msg) {
        pos1 = calcPointBelow(pos1);
        pos2 = calcPointBelow(pos2);
        pos3 = calcPointBelow(pos3);
        if (pos1 != null) {
            pos1.y -= 1;
        }
        if (pos2 != null) {
            pos2.y -= 1;
        }
        if (pos3 != null) {
            pos3.y -= 1;
        }
        if (pos1 == null && pos2 == null && pos3 == null) {
            LOG.warn("WARNING: mapid {}, monster {} could not be spawned.", mapid, monster.getId());

            return;
        }
        // 以第一個非 null 的座標作為其他缺漏座標的預設值
        final Point fallback;
        if (pos1 != null) {
            fallback = pos1;
        } else if (pos2 != null) {
            fallback = pos2;
        } else {
            fallback = pos3;
        }
        if (pos1 == null) {
            pos1 = new Point(fallback);
        }
        if (pos2 == null) {
            pos2 = new Point(fallback);
        }
        if (pos3 == null) {
            pos3 = new Point(fallback);
        }
        // 建立不可變的 SpawnPointAreaBoss 實例後，於鎖外完成所有計算，
        // 僅在最後以細粒度鎖保護 monsterSpawn 的寫入，避免與其他 spawn 執行緒競爭。
        final SpawnPointAreaBoss spawn
                = new SpawnPointAreaBoss(monster, pos1, pos2, pos3, mobTime, msg);
        synchronized (monsterSpawn) {
            monsterSpawn.add(spawn);
        }
    }

    public final List<MapleCharacter> getCharacters() {
        return getCharactersThreadsafe();
    }

    public final List<MapleCharacter> getCharactersThreadsafe() {
        final List<MapleCharacter> chars = new ArrayList<>();

        charactersLock.readLock().lock();
        try {
            for (MapleCharacter mc : characters) {
                chars.add(mc);
            }
        } finally {
            charactersLock.readLock().unlock();
        }
        return chars;
    }

    public final MapleCharacter getCharacterById_InMap(final int id) {
        return getCharacterById(id);
    }

    public final MapleCharacter getCharacterById(final int id) {
        charactersLock.readLock().lock();
        try {
            for (MapleCharacter mc : characters) {
                if (mc.getId() == id) {
                    return mc;
                }
            }
        } finally {
            charactersLock.readLock().unlock();
        }
        return null;
    }

    public final void updateMapObjectVisibility(final MapleCharacter chr, final MapleMapObject mo) {
        if (chr == null || chr.isClone()) {
            return;
        }
        // 先取得輕量級快照：位置與 client 參照，避免在後續呼叫外部同步方法時持有任何地圖集合鎖
        final Point chrPos = chr.getPosition();
        final MapleClient client = chr.getClient();
        final MapleMapObjectType moType = mo.getType();
        final Point moPos = mo.getPosition();
        final boolean visible = chr.isMapObjectVisible(mo);
        if (!visible) { // monster entered view range
            if (moType == MapleMapObjectType.SUMMON
                    || moPos.distanceSq(chrPos) <= GameConstants.maxViewRangeSq()) {
                // 在鎖外呼叫玩家與地圖物件的同步方法，避免死鎖
                chr.addVisibleMapObject(mo);
                mo.sendSpawnData(client);
            }
        } else { // monster left view range
            if (moType != MapleMapObjectType.SUMMON
                    && moPos.distanceSq(chrPos) > GameConstants.maxViewRangeSq()) {
                chr.removeVisibleMapObject(mo);
                mo.sendDestroyData(client);
            }
        }
    }

    public void moveMonster(MapleMonster monster, Point reportedPos) {
        monster.setPosition(reportedPos);

        charactersLock.readLock().lock();
        try {
            for (MapleCharacter mc : characters) {
                updateMapObjectVisibility(mc, monster);
            }
        } finally {
            charactersLock.readLock().unlock();
        }
    }

    public void movePlayer(final MapleCharacter player, final Point newPosition) {
        player.setPosition(newPosition);
        if (player.isClone()) {
            return;
        }

        // 取得當前視野範圍內的物件快照（使用地圖層級的讀鎖，避免之後在玩家鎖內嵌套地圖鎖）
        List<MapleMapObject> rangeObjects
                = getMapObjectsInRange(player.getPosition(), GameConstants.maxViewRangeSq());

        // 短暫取得玩家寫鎖，僅複製 visibleObjects 快照後立刻釋放，
        // 避免在持有玩家鎖時進行任何外部地圖物件查詢或封包操作。
        List<MapleMapObject> visibleSnapshot;
        Collection<MapleMapObject> visibleObjects = player.getAndWriteLockVisibleMapObjects();
        try {
            visibleSnapshot = new ArrayList<>(visibleObjects);
        } finally {
            player.unlockWriteVisibleMapObjects();
        }

        // 在無任何集合鎖的狀態下，逐一檢查快照中的物件是否仍存在於地圖中。
        // 這避免了原本在玩家鎖內呼叫 getMapObject 造成的鎖嵌套與競爭。
        List<MapleMapObject> toRemove = new ArrayList<>();
        List<MapleMapObject> toUpdate = new ArrayList<>();
        for (MapleMapObject mo : visibleSnapshot) {
            if (mo == null) {
                continue;
            }
            if (getMapObject(mo.getObjectId(), mo.getType()) == mo) {
                toUpdate.add(mo);
            } else {
                toRemove.add(mo);
            }
        }

        // 重新取得玩家寫鎖，進行最終的 visibleObjects 更新。
        // 新增物件的收集在鎖內完成，以確保與當前集合狀態一致。
        List<MapleMapObject> toAdd = new ArrayList<>();
        visibleObjects = player.getAndWriteLockVisibleMapObjects();
        try {
            for (MapleMapObject mo : toRemove) {
                visibleObjects.remove(mo);
            }
            for (MapleMapObject mo : toUpdate) {
                updateMapObjectVisibility(player, mo);
            }
            for (MapleMapObject mo : rangeObjects) {
                if (mo != null && !visibleObjects.contains(mo)) {
                    visibleObjects.add(mo);
                    toAdd.add(mo);
                }
            }
        } finally {
            player.unlockWriteVisibleMapObjects();
        }

        // 在鎖外發送新增物件的 spawnData，避免在持有玩家鎖時呼叫外部玩家/怪物方法。
        for (MapleMapObject mo : toAdd) {
            mo.sendSpawnData(player.getClient());
        }
    }

    public MaplePortal findClosestSpawnpoint(Point from) {
        MaplePortal closest = null;
        double distance, shortestDistance = Double.POSITIVE_INFINITY;
        for (MaplePortal portal : portals.values()) {
            distance = portal.getPosition().distanceSq(from);
            if (portal.getType() >= 0
                    && portal.getType() <= 2
                    && distance < shortestDistance
                    && portal.getTargetMapId() == 999999999) {
                closest = portal;
                shortestDistance = distance;
            }
        }
        return closest;
    }

    public String spawnDebug() {
        StringBuilder sb = new StringBuilder("Mapobjects in map : ");
        sb.append(this.getMapObjectSize());
        sb.append(" spawnedMonstersOnMap: ");
        sb.append(spawnedMonstersOnMap);
        sb.append(" spawnpoints: ");
        sb.append(monsterSpawn.size());
        sb.append(" maxRegularSpawn: ");
        sb.append(maxRegularSpawn);
        sb.append(" actual monsters: ");
        sb.append(getNumMonsters());

        return sb.toString();
    }

    public int characterSize() {
        return characters.size();
    }

    public final int getMapObjectSize() {
        return mapobjects.size() + getCharactersSize() - characters.size();
    }

    public final int getCharactersSize() {
        MapleCharacter[] snapshot = null;
        charactersLock.readLock().lock();
        try {
            snapshot = characters.toArray(new MapleCharacter[0]);
        } finally {
            charactersLock.readLock().unlock();
        }
        int ret = 0;
        for (MapleCharacter chr : snapshot) {
            if (!chr.isClone()) {
                ret++;
            }
        }
        return ret;
    }

    public Collection<MaplePortal> getPortals() {
        return Collections.unmodifiableCollection(portals.values());
    }

    public int getSpawnedMonstersOnMap() {
        return spawnedMonstersOnMap.get();
    }

    public List<Integer> getAllUniqueMonsters() {
        List<Integer> ret = new ArrayList<>();
        List<MapleMapObject> snapshot;
        ReentrantReadWriteLock lock = mapobjectlocks.get(MapleMapObjectType.MONSTER);
        lock.readLock().lock();
        try {
            snapshot = new ArrayList<>(mapobjects.get(MapleMapObjectType.MONSTER).values());
        } finally {
            lock.readLock().unlock();
        }
        java.util.Set<Integer> uniqueIds = new java.util.LinkedHashSet<>();
        for (MapleMapObject mmo : snapshot) {
            uniqueIds.add(((MapleMonster) mmo).getId());
        }
        ret.addAll(uniqueIds);
        return ret;
    }

    private class ActivateItemReactor implements Runnable {

        private final MapleMapItem mapitem;
        private final MapleReactor reactor;
        private final WeakReference<MapleClient> clientRef;

        public ActivateItemReactor(MapleMapItem mapitem, MapleReactor reactor, MapleClient c) {
            this.mapitem = mapitem;
            this.reactor = reactor;
            this.clientRef = new WeakReference<>(c);
        }

        @Override
        public void run() {
            final MapleClient c = clientRef.get();
            if (c != null
                    && c.getPlayer() != null
                    && mapitem != null
                    && mapitem == getMapObject(mapitem.getObjectId(), mapitem.getType())) {
                if (mapitem.isPickedUp()) {
                    reactor.setTimerActive(false);
                    return;
                }
                mapitem.expire(MapleMap.this);
                reactor.hitReactor(c);
                reactor.setTimerActive(false);

                if (reactor.getDelay() > 0) {
                    MapTimer.getInstance()
                            .schedule(
                                    new Runnable() {

                                @Override
                                public void run() {
                                    reactor.forceHitReactor((byte) 0);
                                }
                            },
                                    reactor.getDelay());
                }
            } else {
                reactor.setTimerActive(false);
            }
        }
    }

    public void respawn(final boolean force) {
        lastSpawnTime = System.currentTimeMillis();
        // 取得 monsterSpawn 的輕量級快照，避免在遍歷時與其他執行緒的 add/remove 產生 CME
        final List<Spawns> spawnSnapshot;
        synchronized (monsterSpawn) {
            spawnSnapshot = new ArrayList<>(monsterSpawn);
        }
        if (force) { // cpq quick hack
            final int numShouldSpawn = spawnSnapshot.size() - spawnedMonstersOnMap.get();

            if (numShouldSpawn > 0) {
                int spawned = 0;

                for (Spawns spawnPoint : spawnSnapshot) {
                    spawnPoint.spawnMonster(this);
                    spawned++;
                    if (spawned >= numShouldSpawn) {
                        break;
                    }
                }
            }
        } else {
            final int numShouldSpawn = maxRegularSpawn - spawnedMonstersOnMap.get();
            if (numShouldSpawn > 0) {
                int spawned = 0;

                final List<Spawns> randomSpawn = new ArrayList<>(spawnSnapshot);
                Collections.shuffle(randomSpawn);

                for (Spawns spawnPoint : randomSpawn) {
                    if (spawnPoint.shouldSpawn() || GameConstants.isForceRespawn(mapid)) {
                        spawnPoint.spawnMonster(this);
                        spawned++;
                    }
                    if (spawned >= numShouldSpawn) {
                        break;
                    }
                }
            }
        }
    }

    private static interface DelayedPacketCreation {

        void sendPackets(MapleClient c);
    }

    private static interface SpawnCondition {

        boolean canSpawn(MapleCharacter chr);
    }

    public String getSnowballPortal() {
        int[] teamss = new int[2];
        for (MapleCharacter chr : getCharactersThreadsafe()) {
            if (chr.getPosition().y > -80) {
                teamss[0]++;
            } else {
                teamss[1]++;
            }
        }
        if (teamss[0] > teamss[1]) {
            return "st01";
        } else {
            return "st00";
        }
    }

    public boolean isDisconnected(int id) {
        return dced.contains(Integer.valueOf(id));
    }

    public void addDisconnected(int id) {
        dced.add(Integer.valueOf(id));
    }

    public void resetDisconnected() {
        dced.clear();
    }

    public void startSpeedRun() {
        final MapleSquad squad = getSquadByMap();
        if (squad != null) {
            for (MapleCharacter chr : getCharactersThreadsafe()) {
                if (chr.getName().equals(squad.getLeaderName())) {
                    startSpeedRun(chr.getName());
                    return;
                }
            }
        }
    }

    public void startSpeedRun(String leader) {
        speedRunStart = System.currentTimeMillis();
        speedRunLeader = leader;
    }

    public void endSpeedRun() {
        speedRunStart = 0;
        speedRunLeader = "";
    }

    public void getRankAndAdd(
            String leader, String time, SpeedRunType type, long timz, Collection<String> squad) {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }
            // Pair<String, Map<Integer, String>>
            StringBuilder rett = new StringBuilder();
            if (squad != null) {
                for (String chr : squad) {
                    rett.append(chr);
                    rett.append(",");
                }
            }
            String z = rett.toString();
            if (squad != null) {
                z = z.substring(0, z.length() - 1);
            }
            try (PreparedStatement ps
                    = con.prepareStatement(
                            "INSERT INTO speedruns(`type`, `leader`, `timestring`, `time`, `members`) VALUES (?,?,?,?,?)")) {
                ps.setString(1, type.name());
                ps.setString(2, leader);
                ps.setString(3, time);
                ps.setLong(4, timz);
                ps.setString(5, z);
                ps.executeUpdate();
            }

            if (SpeedRunner.getInstance().getSpeedRunData(type) == null) { // great, we just add it
                SpeedRunner.getInstance()
                        .addSpeedRunData(
                                type,
                                SpeedRunner.getInstance()
                                        .addSpeedRunData(
                                                new StringBuilder(
                                                        "#rThese are the speedrun times for "
                                                        + type
                                                        + ".#k\r\n\r\n"),
                                                new HashMap<>(),
                                                z,
                                                leader,
                                                1,
                                                time));
            } else {
                // i wish we had a way to get the rank
                // TODO revamp
                SpeedRunner.getInstance().removeSpeedRunData(type);
                SpeedRunner.getInstance().loadSpeedRunData(type);
            }
        } catch (Exception e) {
            LOG.error("MapleMap.getRankAndAdd 發生例外", e);
        }
    }

    public long getSpeedRunStart() {
        return speedRunStart;
    }

    public final void disconnectAll() {
        for (MapleCharacter chr : getCharactersThreadsafe()) {
            if (!chr.isGM()) {
                chr.getClient().disconnect(true, false);
                chr.getClient().getSession().close();
            }
        }
    }

    public List<MapleNPC> getAllNPCs() {
        return getAllNPCsThreadsafe();
    }

    public List<MapleNPC> getAllNPCsThreadsafe() {
        ArrayList<MapleNPC> ret = new ArrayList<>();
        mapobjectlocks.get(MapleMapObjectType.NPC).readLock().lock();
        try {
            for (MapleMapObject mmo : mapobjects.get(MapleMapObjectType.NPC).values()) {
                ret.add((MapleNPC) mmo);
            }
        } finally {
            mapobjectlocks.get(MapleMapObjectType.NPC).readLock().unlock();
        }
        return ret;
    }

    public final void resetNPCs() {
        List<MapleNPC> npcs = getAllNPCsThreadsafe();
        for (MapleNPC npc : npcs) {
            if (npc.isCustom()) {
                broadcastMessage(MaplePacketCreator.spawnNPC(npc, false));
                removeMapObject(npc);
            }
        }
    }

    public final void resetFully() {
        resetFully(true);
    }

    public final void resetFully(final boolean respawn) {
        killAllMonsters(false);
        reloadReactors();
        removeDrops();
        resetNPCs();
        resetSpawns();
        resetDisconnected();
        endSpeedRun();
        cancelSquadSchedule();
        resetPortals();
        environment.clear();
        if (respawn) {
            respawn(true);
        }
        dchere.clear();
    }

    public final void cancelSquadSchedule() {
        squadTimer = false;
        if (squadSchedule != null) {
            squadSchedule.cancel(false);
            squadSchedule = null;
        }
    }

    public final void removeDrops() {
        List<MapleMapItem> items = this.getAllItemsThreadsafe();
        for (MapleMapItem i : items) {
            i.expire(this);
        }
    }

    public final void resetAllSpawnPoint(int mobid, int mobTime) {
        // 1. 先取得 monsterSpawn 的輕量級快照（在鎖外進行，避免持有鎖時呼叫外部方法）
        final List<Spawns> sss;
        synchronized (monsterSpawn) {
            sss = new ArrayList<>(monsterSpawn);
        }

        // 2. 重置地圖狀態（resetFully 內部自行管理其鎖，此處不持有 monsterSpawn 鎖）
        resetFully();

        // 3. 清空 spawn 列表（短暫持有鎖，僅做 clear，不呼叫任何外部同步方法）
        synchronized (monsterSpawn) {
            monsterSpawn.clear();
        }

        // 4. 在鎖外逐一建構新的怪物與 spawn 點，避免在持有集合鎖時呼叫 MapleMonster 的同步方法
        for (Spawns s : sss) {
            MapleMonster newMons = MapleLifeFactory.getMonster(mobid);
            MapleMonster oldMons = s.getMonster();
            newMons.setCy(oldMons.getCy());
            newMons.setF(oldMons.getF());
            newMons.setFh(oldMons.getFh());
            newMons.setRx0(oldMons.getRx0());
            newMons.setRx1(oldMons.getRx1());
            newMons.setPosition(new Point(oldMons.getPosition()));
            newMons.setHide(oldMons.isHidden());
            // addMonsterSpawn 內部自行管理 monsterSpawn 的鎖，此處不持有鎖，避免死鎖
            addMonsterSpawn(newMons, mobTime, (byte) -1, null);
        }

        // 5. 重新載入怪物生成速率（loadMonsterRate 內部自行管理鎖）
        loadMonsterRate(true);
    }

    public final void resetSpawns() {
        boolean changed = false;
        Iterator<Spawns> sss = monsterSpawn.iterator();
        while (sss.hasNext()) {
            if (sss.next().getCarnivalId() > -1) {
                sss.remove();
                changed = true;
            }
        }
        setSpawns(true);
        if (changed) {
            loadMonsterRate(true);
        }
    }

    public final boolean makeCarnivalSpawn(
            final int team, final MapleMonster newMons, final int num) {
        MonsterPoint ret = null;
        // 先取得 monsterSpawn 的輕量級快照，避免在遍歷時持有集合鎖並呼叫外部同步方法
        final List<Spawns> spawnSnapshot;
        synchronized (monsterSpawn) {
            spawnSnapshot = new ArrayList<>(monsterSpawn);
        }
        final List<MonsterPoint> monsterPoints = nodes.getMonsterPoints();
        for (MonsterPoint mp : monsterPoints) {
            if (mp.team == team || mp.team == -1) {
                final Point newpos = calcPointBelow(new Point(mp.x, mp.y));
                newpos.y -= 1;
                boolean found = false;
                for (Spawns s : spawnSnapshot) {
                    if (s.getCarnivalId() > -1
                            && (mp.team == -1 || s.getCarnivalTeam() == mp.team)
                            && s.getPosition().x == newpos.x
                            && s.getPosition().y == newpos.y) {
                        found = true;
                        break; // this point has already been used.
                    }
                }
                if (!found) {
                    ret = mp; // this point is safe for use.
                    break;
                }
            }
        }
        if (ret != null) {
            newMons.setCy(ret.cy);
            newMons.setF(0); // always.
            newMons.setFh(ret.fh);
            newMons.setRx0(ret.x + 50);
            newMons.setRx1(ret.x - 50); // does this matter
            newMons.setPosition(new Point(ret.x, ret.y));
            newMons.setHide(false);
            final SpawnPoint sp = addMonsterSpawn(newMons, 1, (byte) team, null);
            sp.setCarnival(num);
        }
        return ret != null;
    }

    public final boolean makeCarnivalReactor(final int team, final int num) {
        final MapleReactor old = getReactorByName(team + "" + num);
        if (old != null && old.getState() < 5) { // already exists
            return false;
        }
        Point guardz = null;
        final List<MapleReactor> react = getAllReactorsThreadsafe();
        for (Pair<Point, Integer> guard : nodes.getGuardians()) {
            if (guard.right() == team || guard.right() == -1) {
                boolean found = false;
                for (MapleReactor r : react) {
                    if (r.getPosition().x == guard.left().x
                            && r.getPosition().y == guard.left().y
                            && r.getState() < 5) {
                        found = true;
                        break; // already used
                    }
                }
                if (!found) {
                    guardz = guard.left(); // this point is safe for use.
                    break;
                }
            }
        }
        if (guardz != null) {
            final MapleReactorStats stats = MapleReactorFactory.getReactor(9980000 + team);
            final MapleReactor my = new MapleReactor(stats, 9980000 + team);
            stats.setFacingDirection((byte) 0); // always
            my.setPosition(guardz);
            my.setState((byte) 1);
            my.setDelay(0);
            my.setName(team + "" + num); // lol
            // with num. -> guardians in factory
            spawnReactor(my);
            final MCSkill skil = MapleCarnivalFactory.getInstance().getGuardian(num);
            // 先取得怪物快照，避免在持有任何地圖集合鎖時呼叫怪物同步方法
            final List<MapleMonster> monstersSnapshot = getAllMonstersThreadsafe();
            for (MapleMonster mons : monstersSnapshot) {
                if (mons.getCarnivalTeam() == team) {
                    skil.getMobSkill().applyEffect(null, mons, false);
                }
            }
        }
        return guardz != null;
    }

    public final void blockAllPortal() {
        for (MaplePortal p : portals.values()) {
            p.setPortalState(false);
        }
    }

    public boolean getAndSwitchTeam() {
        return getCharactersSize() % 2 != 0;
    }

    public void setSquad(MapleSquadType s) {
        this.squad = s;
    }

    public int getChannel() {
        return channel;
    }

    public int getConsumeItemCoolTime() {
        return consumeItemCoolTime;
    }

    public void setConsumeItemCoolTime(int ciit) {
        this.consumeItemCoolTime = ciit;
    }

    public void setPermanentWeather(int pw) {
        this.permanentWeather = pw;
    }

    public int getPermanentWeather() {
        return permanentWeather;
    }

    public void checkStates(final String chr) {
        final MapleSquad sqd = getSquadByMap();
        final EventManager em = getEMByMap();
        final int size = getCharactersSize();
        if (sqd != null) {
            sqd.removeMember(chr);
            if (em != null) {
                if (sqd.getLeaderName().equals(chr)) {
                    em.setProperty("leader", "false");
                }
                if (chr.equals("") || size == 0) {
                    sqd.clear();
                    em.setProperty("state", "0");
                    em.setProperty("leader", "true");
                    cancelSquadSchedule();
                }
            }
        }
        if (em != null && em.getProperty("state") != null) {
            if (size == 0) {
                em.setProperty("state", "0");
                if (em.getProperty("leader") != null) {
                    em.setProperty("leader", "true");
                }
            }
        }
        if (speedRunStart > 0 && speedRunLeader.equalsIgnoreCase(chr)) {
            if (size > 0) {
                broadcastMessage(
                        MaplePacketCreator.serverNotice(
                                5, "The leader is not in the map! Your speedrun has failed"));
            }
            endSpeedRun();
        }
    }

    public void setNodes(final MapleNodes mn) {
        this.nodes = mn;
    }

    public final List<MaplePlatform> getPlatforms() {
        return nodes.getPlatforms();
    }

    public Collection<MapleNodeInfo> getNodes() {
        return nodes.getNodes();
    }

    public MapleNodeInfo getNode(final int index) {
        return nodes.getNode(index);
    }

    public final List<Rectangle> getAreas() {
        return nodes.getAreas();
    }

    public final Rectangle getArea(final int index) {
        return nodes.getArea(index);
    }

    public final void changeEnvironment(final String ms, final int type) {
        broadcastMessage(MaplePacketCreator.environmentChange(ms, type));
    }

    public final void toggleEnvironment(final String ms) {
        if (environment.containsKey(ms)) {
            moveEnvironment(ms, environment.get(ms) == 1 ? 2 : 1);
        } else {
            moveEnvironment(ms, 1);
        }
    }

    public final void moveEnvironment(final String ms, final int type) {
        broadcastMessage(MaplePacketCreator.environmentMove(ms, type));
        environment.put(ms, type);
    }

    public final Map<String, Integer> getEnvironment() {
        return environment;
    }

    public final int getNumPlayersInArea(final int index) {
        int ret = 0;
        final Rectangle area = getArea(index); // 先取得區域，避免在持有角色鎖時呼叫可能引入其他鎖的方法
        final List<MapleCharacter> snapshot;
        charactersLock.readLock().lock();
        try {
            snapshot = new ArrayList<>(characters); // 在讀鎖內建立快照，鎖定時間極短
        } finally {
            charactersLock.readLock().unlock();
        }
        for (MapleCharacter chr : snapshot) {
            if (area.contains(chr.getPosition())) { // 在鎖外檢查玩家位置，符合鐵律1
                ret++;
            }
        }
        return ret;
    }

    public void broadcastGMMessage(
            MapleCharacter source, MaplePacket packet, boolean repeatToSource) {
        broadcastGMMessage(
                repeatToSource ? null : source,
                packet,
                Double.POSITIVE_INFINITY,
                source.getPosition());
    }

    private void broadcastGMMessage(
            MapleCharacter source, MaplePacket packet, double rangeSq, Point rangedFrom) {
        List<MapleCharacter> snapshot;
        charactersLock.readLock().lock();
        try {
            snapshot = new ArrayList<>(characters);
        } finally {
            charactersLock.readLock().unlock();
        }
        if (source == null) {
            for (MapleCharacter chr : snapshot) {
                if (chr.isStaff()) {
                    chr.getClient().sendPacket(packet);
                }
            }
        } else {
            for (MapleCharacter chr : snapshot) {
                if (chr != source && (chr.getGMLevel() >= source.getGMLevel())) {
                    chr.getClient().sendPacket(packet);
                }
            }
        }
    }

    public final List<Pair<Integer, Integer>> getMobsToSpawn() {
        return nodes.getMobsToSpawn();
    }

    public final List<Integer> getSkillIds() {
        return nodes.getSkillIds();
    }

    public final boolean canSpawn() {
        return lastSpawnTime > 0
                && isSpawns
                && lastSpawnTime + createMobInterval < System.currentTimeMillis();
    }

    public final boolean canHurt() {
        if (lastHurtTime > 0 && lastHurtTime + decHPInterval < System.currentTimeMillis()) {
            lastHurtTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    private short top = 0, bottom = 0, left = 0, right = 0;

    public short getTop() {
        return top;
    }

    public short getBottom() {
        return bottom;
    }

    public short getLeft() {
        return left;
    }

    public short getRight() {
        return right;
    }

    public void setTop(int ii) {
        this.top = (short) ii;
    }

    public void setBottom(int ii) {
        this.bottom = (short) ii;
    }

    public void addDChere(final int charid) {
        final long now = System.currentTimeMillis();
        if (this.dchere.size() > 64) {
            this.dchere.entrySet().removeIf(e -> e.getValue() + 300000L < now);
        }
        this.dchere.put(charid, now);
    }

    public void removeDChere(final int charid) {
        this.dchere.remove(charid);
    }

    public boolean isDChere(final int charid) {
        final Long dcTime = this.dchere.get(charid);
        if (dcTime == null) {
            return false;
        }
        if (dcTime + 300000L < System.currentTimeMillis()) {
            this.dchere.remove(charid, dcTime);
            return false;
        }
        return true;
    }

    public void setLeft(int ii) {
        this.left = (short) ii;
    }

    public void setRight(int ii) {
        this.right = (short) ii;
    }
}
