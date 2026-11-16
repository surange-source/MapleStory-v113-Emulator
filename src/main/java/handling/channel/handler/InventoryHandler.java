package handling.channel.handler;

import client.ISkill;
import client.MapleCharacter;
import client.MapleClient;
import client.MapleStat;
import client.PlayerStats;
import client.SkillFactory;
import client.anticheat.CheatingOffense;
import client.inventory.Equip;
import client.inventory.IEquip;
import client.inventory.IEquip.ScrollResult;
import client.inventory.IItem;
import client.inventory.ItemFlag;
import client.inventory.MapleInventory;
import client.inventory.MapleInventoryType;
import client.inventory.MapleMount;
import client.inventory.MaplePet;
import client.inventory.MaplePet.PetFlag;
import constants.GameConstants;
import constants.ItemConstants;
import handling.channel.ChannelServer;
import handling.world.MaplePartyCharacter;
import handling.world.World;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import scripting.NPCScriptManager;
import server.AutobanManager;
import server.MapleInventoryManipulator;
import server.MapleItemInformationProvider;
import server.MapleShopFactory;
import server.RandomRewards;
import server.Randomizer;
import server.StructPotentialItem;
import server.StructRewardItem;
import server.life.MapleLifeFactory;
import server.life.MapleMonster;
import server.maps.FieldLimitType;
import server.maps.MapleMap;
import server.maps.MapleMapItem;
import server.maps.MapleMapObject;
import server.maps.MapleMapObjectType;
import server.maps.MapleMist;
import server.maps.SavedLocationType;
import server.quest.MapleQuest;
import server.shops.HiredMerchant;
import tools.MaplePacketCreator;
import tools.Pair;
import tools.data.input.SeekableLittleEndianAccessor;
import tools.packet.MTSCSPacket;
import tools.packet.PetPacket;
import tools.packet.PlayerShopPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InventoryHandler {
    private static final Logger log = LoggerFactory.getLogger(InventoryHandler.class);

    public static final void ItemMove(
            final SeekableLittleEndianAccessor slea, final MapleClient c) {
        final MapleCharacter player = c.getPlayer();
        if (player == null
                || player.getPlayerShop() != null
                || player.getConversation() > 0
                || player.getTrade() != null) { // hack
            return;
        }
        player.updateTick(slea.readInt());
        final MapleInventoryType type = MapleInventoryType.getByType(slea.readByte()); // 04
        final short src = slea.readShort(); // 01 00
        final short dst = slea.readShort(); // 00 00
        final short quantity = slea.readShort(); // 53 01

        if (src < 0 && dst > 0) {
            MapleInventoryManipulator.unequip(c, src, dst);
        } else if (dst < 0) {
            MapleInventoryManipulator.equip(c, src, dst);
        } else if (dst == 0) {
            MapleInventoryManipulator.drop(c, type, src, quantity);
        } else {
            MapleInventoryManipulator.move(c, type, src, dst);
        }
    }

    public static final void ItemSort(
            final SeekableLittleEndianAccessor slea, final MapleClient c) {
        c.getPlayer().updateTick(slea.readInt());

        final MapleInventoryType pInvType = MapleInventoryType.getByType(slea.readByte());
        if (pInvType == MapleInventoryType.UNDEFINED) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        final MapleInventory pInv =
                c.getPlayer()
                        .getInventory(pInvType); // Mode should correspond with MapleInventoryType

        // 快取 slot limit，避免迴圈中重複呼叫
        final byte slotLimit = pInv.getSlotLimit();

        // 以「壓縮」方式掃描：從最前面開始，把後方有物品的空位依序往前搬
        // 每次搬移後 freeSlot 會前進，整體為 O(n) 而非原本 O(n^2) 的 while 重掃
        byte freeSlot = (byte) pInv.getNextFreeSlot();
        while (freeSlot != -1 && freeSlot <= slotLimit) {
            byte itemSlot = -1;
            for (byte i = (byte) (freeSlot + 1); i <= slotLimit; i++) {
                if (pInv.getItem(i) != null) {
                    itemSlot = i;
                    break;
                }
            }
            if (itemSlot <= 0) {
                break;
            }
            MapleInventoryManipulator.move(c, pInvType, itemSlot, freeSlot);
            // 搬移後原本 itemSlot 變空，下一個空位即為 itemSlot
            freeSlot = itemSlot;
        }

        c.sendPacket(MaplePacketCreator.finishedSort(pInvType.getType()));
        c.sendPacket(MaplePacketCreator.enableActions());
    }

    public static final void ItemGather(
            final SeekableLittleEndianAccessor slea, final MapleClient c) {
        // [41 00] [E5 1D 55 00] [01]
        // [32 00] [01] [01] // Sent after

        c.getPlayer().updateTick(slea.readInt());
        final byte mode = slea.readByte();
        final MapleInventoryType invType = MapleInventoryType.getByType(mode);
        final MapleInventory Inv = c.getPlayer().getInventory(invType);

        // Snapshot the inventory once to avoid repeated iteration over a live collection.
        final List<IItem> itemMap = new ArrayList<>(Inv.list().size());
        for (IItem item : Inv.list()) {
            itemMap.add(item.copy()); // clone all items T___T.
        }

        // Remove originals in a single pass; removeById already handles quantity bookkeeping.
        for (IItem itemStats : itemMap) {
            MapleInventoryManipulator.removeById(
                    c, invType, itemStats.getItemId(), itemStats.getQuantity(), true, false);
        }

        // Sort the cloned items and re-add them; reuse the same list to avoid a second allocation.
        final List<IItem> sortedItems = sortItems(itemMap);
        for (IItem item : sortedItems) {
            MapleInventoryManipulator.addFromDrop(c, item, false);
        }

        c.sendPacket(MaplePacketCreator.finishedGather(mode));
        c.sendPacket(MaplePacketCreator.enableActions());

        // Explicit clears retained for parity with original behavior (release references promptly).
        itemMap.clear();
        sortedItems.clear();
    }

    private static final List<IItem> sortItems(final List<IItem> passedMap) {
        // 預先配置容量，避免 ArrayList 擴容造成的多次陣列複製
        final int size = passedMap.size();
        final List<Integer> itemIds = new ArrayList<>(size);
        for (IItem item : passedMap) {
            itemIds.add(item.getItemId());
        }
        Collections.sort(itemIds);

        // 使用 HashMap 建立 itemId -> 佇列(IItem) 的索引，避免 O(n^2) 的雙層迴圈
        // 同一 itemId 可能有多個 item，因此用 LinkedList 作為 FIFO 佇列，保持原本「取第一個匹配」的語意
        final Map<Integer, LinkedList<IItem>> idToItems = new java.util.HashMap<>(size * 2);
        for (IItem item : passedMap) {
            final Integer key = item.getItemId();
            LinkedList<IItem> bucket = idToItems.get(key);
            if (bucket == null) {
                bucket = new LinkedList<>();
                idToItems.put(key, bucket);
            }
            bucket.add(item);
        }

        final List<IItem> sortedList = new LinkedList<>();
        for (Integer val : itemIds) {
            final LinkedList<IItem> bucket = idToItems.get(val);
            if (bucket != null && !bucket.isEmpty()) {
                sortedList.add(bucket.pollFirst());
            }
        }

        // 清空來源清單（保留原本 remove 的副作用語意）
        passedMap.clear();
        return sortedList;
    }

    public static final boolean UseRewardItem(
            final byte slot, final int itemId, final MapleClient c, final MapleCharacter chr) {
        final MapleInventoryType invType = GameConstants.getInventoryType(itemId);
        final IItem toUse = c.getPlayer().getInventory(invType).getItem(slot);
        c.sendPacket(MaplePacketCreator.enableActions());
        if (toUse == null || toUse.getQuantity() < 1 || toUse.getItemId() != itemId) {
            return false;
        }
        if (chr.getInventory(MapleInventoryType.EQUIP).getNextFreeSlot() <= -1
                || chr.getInventory(MapleInventoryType.USE).getNextFreeSlot() <= -1
                || chr.getInventory(MapleInventoryType.SETUP).getNextFreeSlot() <= -1
                || chr.getInventory(MapleInventoryType.ETC).getNextFreeSlot() <= -1) {
            chr.dropMessage(6, "你有一個欄位滿了 請空出來再打開");
            return false;
        }
        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        final Pair<Integer, List<StructRewardItem>> rewards = ii.getRewardItem(itemId);
        if (rewards == null || rewards.left() <= 0) {
            chr.dropMessage(6, "Unknown error.");
            return false;
        }
        final List<StructRewardItem> rewardList = rewards.right();
        final int totalProb = rewards.left();
        final int rewardCount = rewardList.size();
        while (true) {
            for (int i = 0; i < rewardCount; i++) {
                final StructRewardItem reward = rewardList.get(i);
                if (reward.prob > 0 && Randomizer.nextInt(totalProb) < reward.prob) { // Total prob
                    if (GameConstants.getInventoryType(reward.itemid) == MapleInventoryType.EQUIP) {
                        final IItem item = ii.getEquipById(reward.itemid);
                        if (reward.period > 0) {
                            item.setExpiration(
                                    System.currentTimeMillis() + (reward.period * 60 * 60 * 10));
                        }
                        MapleInventoryManipulator.addbyItem(c, item);
                    } else {
                        MapleInventoryManipulator.addById(c, reward.itemid, reward.quantity);
                    }
                    MapleInventoryManipulator.removeById(c, invType, itemId, 1, false, false);

                    c.sendPacket(
                            MaplePacketCreator.showRewardItemAnimation(
                                    reward.itemid, reward.effect));
                    chr.getMap()
                            .broadcastMessage(
                                    chr,
                                    MaplePacketCreator.showRewardItemAnimation(
                                            reward.itemid, reward.effect, chr.getId()),
                                    false);
                    return true;
                }
            }
        }
    }

    public static final void UseItem(
            final SeekableLittleEndianAccessor slea,
            final MapleClient c,
            final MapleCharacter chr) {
        if (chr == null
                || !chr.isAlive()
                || chr.getMapId() == 749040100
                || chr.getMap() == null /* || chr.hasDisease(MapleDisease.POTION)*/) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        final long time = System.currentTimeMillis();
        if (chr.getNextConsume() > time) {
            chr.dropMessage(5, "You may not use this item yet.");
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        c.getPlayer().updateTick(slea.readInt());
        final byte slot = (byte) slea.readShort();
        final int itemId = slea.readInt();
        final IItem toUse = chr.getInventory(MapleInventoryType.USE).getItem(slot);

        if (toUse == null || toUse.getQuantity() < 1 || toUse.getItemId() != itemId) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        final MapleMap map = chr.getMap();
        if (!FieldLimitType.PotionUse.check(map.getFieldLimit())
                || chr.getMapId() == 610030600) { // cwk quick hack
            if (MapleItemInformationProvider.getInstance()
                    .getItemEffect(toUse.getItemId())
                    .applyTo(chr)) {
                MapleInventoryManipulator.removeFromSlot(
                        c, MapleInventoryType.USE, slot, (short) 1, false);
                final int consumeCool = map.getConsumeItemCoolTime();
                if (consumeCool > 0) {
                    chr.setNextConsume(time + (consumeCool * 1000));
                }
            }
        } else {
            c.sendPacket(MaplePacketCreator.enableActions());
        }
    }

    public static final void UseReturnScroll(
            final SeekableLittleEndianAccessor slea,
            final MapleClient c,
            final MapleCharacter chr) {
        if (!chr.isAlive() || chr.getMapId() == 749040100) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        chr.updateTick(slea.readInt());
        final byte slot = (byte) slea.readShort();
        final int itemId = slea.readInt();
        final IItem toUse = chr.getInventory(MapleInventoryType.USE).getItem(slot);

        if (toUse == null || toUse.getQuantity() < 1 || toUse.getItemId() != itemId) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        if (FieldLimitType.PotionUse.check(chr.getMap().getFieldLimit())) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        if (MapleItemInformationProvider.getInstance()
                .getItemEffect(toUse.getItemId())
                .applyReturnScroll(chr)) {
            MapleInventoryManipulator.removeFromSlot(
                    c, MapleInventoryType.USE, slot, (short) 1, false);
        } else {
            c.sendPacket(MaplePacketCreator.enableActions());
        }
    }

    public static final void UseMagnify(
            final SeekableLittleEndianAccessor slea, final MapleClient c) {
        c.getPlayer().updateTick(slea.readInt());
        final IItem magnify =
                c.getPlayer().getInventory(MapleInventoryType.USE).getItem((byte) slea.readShort());
        final IItem toReveal =
                c.getPlayer()
                        .getInventory(MapleInventoryType.EQUIP)
                        .getItem((byte) slea.readShort());
        if (magnify == null || toReveal == null) {
            c.sendPacket(MaplePacketCreator.getInventoryFull());
            return;
        }
        final Equip eqq = (Equip) toReveal;
        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        final int reqLevel = ii.getReqLevel(eqq.getItemId()) / 10;
        final int magnifyId = magnify.getItemId();
        if (eqq.getState() == 1
                && (magnifyId == 2460003
                        || (magnifyId == 2460002 && reqLevel <= 12)
                        || (magnifyId == 2460001 && reqLevel <= 7)
                        || (magnifyId == 2460000 && reqLevel <= 3))) {
            final List<List<StructPotentialItem>> pots =
                    new LinkedList<>(ii.getAllPotentialInfo().values());
            final int potSize = pots.size();
            int new_state = Math.abs(eqq.getPotential1());
            if (new_state > 7 || new_state < 5) { // luls
                new_state = 5;
            }
            final int lines = (eqq.getPotential2() != 0 ? 3 : 2);
            while (eqq.getState() != new_state) {
                // 31001 = haste, 31002 = door, 31003 = se, 31004 = hb
                for (int i = 0; i < lines; i++) { // 2 or 3 line
                    boolean rewarded = false;
                    while (!rewarded) {
                        final List<StructPotentialItem> potList =
                                pots.get(Randomizer.nextInt(potSize));
                        if (potList == null || reqLevel >= potList.size()) {
                            continue;
                        }
                        final StructPotentialItem pot = potList.get(reqLevel);
                        if (pot != null
                                && pot.reqLevel / 10 <= reqLevel
                                && GameConstants.optionTypeFits(pot.optionType, eqq.getItemId())
                                && GameConstants.potentialIDFits(
                                        pot.potentialID, new_state, i)) { // optionType
                            // have to research optionType before making this truely sea-like
                            if (i == 0) {
                                eqq.setPotential1(pot.potentialID);
                            } else if (i == 1) {
                                eqq.setPotential2(pot.potentialID);
                            } else if (i == 2) {
                                eqq.setPotential3(pot.potentialID);
                            }
                            rewarded = true;
                        }
                    }
                }
            }
            c.sendPacket(MaplePacketCreator.scrolledItem(magnify, toReveal, false, true));
            c.getPlayer()
                    .getMap()
                    .broadcastMessage(
                            MaplePacketCreator.getPotentialReset(
                                    c.getPlayer().getId(), eqq.getPosition()));
            MapleInventoryManipulator.removeFromSlot(
                    c, MapleInventoryType.USE, magnify.getPosition(), (short) 1, false);
        } else {
            c.sendPacket(MaplePacketCreator.getInventoryFull());
            return;
        }
    }

    public static final boolean UseUpgradeScroll(
            final byte slot,
            final byte dst,
            final byte ws,
            final MapleClient c,
            final MapleCharacter chr) {
        return UseUpgradeScroll(slot, dst, ws, c, chr, 0);
    }

    public static final boolean UseUpgradeScroll(
            final byte slot,
            final byte dst,
            final byte ws,
            final MapleClient c,
            final MapleCharacter chr,
            final int vegas) {
        boolean whiteScroll = (ws & 2) == 2;
        boolean legendarySpirit = false;
        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        final MapleInventory useInventory = chr.getInventory(MapleInventoryType.USE);
        final boolean equipped = dst < 0;
        final MapleInventory targetInventory =
                equipped
                        ? chr.getInventory(MapleInventoryType.EQUIPPED)
                        : chr.getInventory(MapleInventoryType.EQUIP);

        IEquip toScroll;
        if (dst < 0) {
            toScroll = (IEquip) targetInventory.getItem(dst);
        } else {
            legendarySpirit = true;
            toScroll = (IEquip) targetInventory.getItem(dst);
        }
        if (toScroll == null) {
            return false;
        }
        final byte oldLevel = toScroll.getLevel();
        final byte oldEnhance = toScroll.getEnhance();
        final byte oldState = toScroll.getState();
        final byte oldFlag = toScroll.getFlag();
        final byte oldSlots = toScroll.getUpgradeSlots();

        IItem scroll = useInventory.getItem(slot);
        if (scroll == null) {
            c.sendPacket(MaplePacketCreator.getInventoryFull());
            return false;
        }
        final int scrollId = scroll.getItemId();
        final int targetItemId = toScroll.getItemId();
        final boolean isCashTarget = ii.isCash(targetItemId);
        final boolean isCleanSlate = ItemConstants.isCleanSlate(scrollId);
        final boolean isTablet = ItemConstants.isTablet(scrollId);
        final boolean isEquipScroll = ItemConstants.ItemTypes.isEquipScroll(scrollId);
        final boolean isPotentialScroll = ItemConstants.ItemTypes.isPotentialScroll(scrollId);
        final boolean isAccessoryScroll = ItemConstants.ItemTypes.isAccessoryScroll(scrollId);
        final boolean isChaosScroll = ItemConstants.ItemTypes.isChaosScroll(scrollId);
        final boolean isSpecialScroll = ItemConstants.ItemTypes.isSpecialScroll(scrollId);

        if (!isSpecialScroll && !isCleanSlate && !isEquipScroll && !isPotentialScroll) {
            if (toScroll.getUpgradeSlots() < 1) {
                c.sendPacket(MaplePacketCreator.getInventoryFull());
                return false;
            }
        } else if (isEquipScroll) {
            if (toScroll.getUpgradeSlots() >= 1
                    || toScroll.getEnhance() >= 100
                    || vegas > 0
                    || isCashTarget) {
                c.sendPacket(MaplePacketCreator.getInventoryFull());
                return false;
            }
        } else if (isPotentialScroll) {
            if (toScroll.getState() >= 1
                    || (toScroll.getLevel() == 0 && toScroll.getUpgradeSlots() == 0)
                    || vegas > 0
                    || isCashTarget) {
                c.sendPacket(MaplePacketCreator.getInventoryFull());
                return false;
            }
        }
        if (!ItemConstants.Scrolls.canScroll(targetItemId)
                && !ItemConstants.ItemTypes.isChaosScroll(targetItemId)) {
            c.sendPacket(MaplePacketCreator.getInventoryFull());
            return false;
        }
        if ((isCleanSlate || isTablet || isChaosScroll) && (vegas > 0 || isCashTarget)) {
            c.sendPacket(MaplePacketCreator.getInventoryFull());
            return false;
        }
        if (isTablet && toScroll.getDurability() < 0) {
            c.sendPacket(MaplePacketCreator.getInventoryFull());
            return false;
        } else if (!isTablet && toScroll.getDurability() >= 0) {
            c.sendPacket(MaplePacketCreator.getInventoryFull());
            return false;
        }

        IItem wscroll = null;
        List<Integer> scrollReqs = ii.getScrollReqs(scrollId);
        if (scrollReqs.size() > 0 && !scrollReqs.contains(targetItemId)) {
            c.sendPacket(MaplePacketCreator.getInventoryFull());
            return false;
        }

        if (whiteScroll) {
            wscroll = useInventory.findById(2340000);
            if (wscroll == null) {
                whiteScroll = false;
            }
        }
        if (scrollId == 2049115 && targetItemId != 1003068) {
            return false;
        }
        if (isTablet) {
            switch (scrollId % 1000 / 100) {
                case 0:
                    if (ItemConstants.ItemTypes.isTwoHanded(targetItemId)
                            || !ItemConstants.ItemTypes.isWeapon(targetItemId)) {
                        return false;
                    }
                    break;
                case 1:
                    if (!ItemConstants.ItemTypes.isTwoHanded(targetItemId)
                            || !ItemConstants.ItemTypes.isWeapon(targetItemId)) {
                        return false;
                    }
                    break;
                case 2:
                    if (ItemConstants.ItemTypes.isAccessories(targetItemId)
                            || ItemConstants.ItemTypes.isWeapon(targetItemId)) {
                        return false;
                    }
                    break;
                case 3:
                    if (!ItemConstants.ItemTypes.isAccessories(targetItemId)
                            || ItemConstants.ItemTypes.isWeapon(targetItemId)) {
                        return false;
                    }
                    break;
            }
        } else if (!isAccessoryScroll
                && !isChaosScroll
                && !isCleanSlate
                && !isEquipScroll
                && !isPotentialScroll) {
            if (!ii.canScroll(scrollId, targetItemId)) {
                return false;
            }
        }
        if (isAccessoryScroll && !ItemConstants.ItemTypes.isAccessories(targetItemId)) {
            return false;
        }
        if (scroll.getQuantity() <= 0) {
            return false;
        }

        if (legendarySpirit && vegas == 0) {
            boolean hasLegendarySpirit = false;
            int[] skillIds = {1003, 10001003, 20001003, 20011003, 30001003};
            for (int skillId : skillIds) {
                if (chr.getSkillLevel(SkillFactory.getSkill(skillId)) > 0) {
                    hasLegendarySpirit = true;
                    break;
                }
            }
            if (!hasLegendarySpirit) {
                AutobanManager.getInstance()
                        .addPoints(
                                c,
                                50,
                                120000,
                                "Using the Skill 'Legendary Spirit' without having it.");
                return false;
            }
        }

        final IEquip scrolled =
                (IEquip) ii.scrollEquipWithId(toScroll, scroll, whiteScroll, chr, vegas);
        ScrollResult scrollSuccess;
        if (scrolled == null) {
            scrollSuccess = IEquip.ScrollResult.CURSE;
        } else if (scrolled.getLevel() > oldLevel
                || scrolled.getEnhance() > oldEnhance
                || scrolled.getState() > oldState
                || scrolled.getFlag() > oldFlag) {
            scrollSuccess = IEquip.ScrollResult.SUCCESS;
        } else if (isCleanSlate && scrolled.getUpgradeSlots() > oldSlots) {
            scrollSuccess = IEquip.ScrollResult.SUCCESS;
        } else {
            scrollSuccess = IEquip.ScrollResult.FAIL;
        }

        useInventory.removeItem(scroll.getPosition(), (short) 1, false);
        if (whiteScroll) {
            MapleInventoryManipulator.removeFromSlot(
                    c, MapleInventoryType.USE, wscroll.getPosition(), (short) 1, false, false);
        }

        final boolean vegasZero = vegas == 0;
        if (scrollSuccess == IEquip.ScrollResult.CURSE) {
            c.sendPacket(MaplePacketCreator.scrolledItem(scroll, toScroll, true, false));
            targetInventory.removeItem(toScroll.getPosition());
        } else if (vegasZero) {
            c.sendPacket(MaplePacketCreator.scrolledItem(scroll, scrolled, false, false));
        }

        chr.getMap()
                .broadcastMessage(
                        chr,
                        MaplePacketCreator.getScrollEffect(
                                c.getPlayer().getId(), scrollSuccess, legendarySpirit),
                        vegasZero);

        if (dst < 0
                && (scrollSuccess == IEquip.ScrollResult.SUCCESS
                        || scrollSuccess == IEquip.ScrollResult.CURSE)
                && vegasZero) {
            chr.equipChanged();
        }
        return true;
    }

    public static final void UseCatchItem(
            final SeekableLittleEndianAccessor slea,
            final MapleClient c,
            final MapleCharacter chr) {
        chr.updateTick(slea.readInt());
        final byte slot = (byte) slea.readShort();
        final int itemid = slea.readInt();
        final MapleMonster mob = chr.getMap().getMonsterByOid(slea.readInt());
        final IItem toUse = chr.getInventory(MapleInventoryType.USE).getItem(slot);

        if (toUse != null
                && toUse.getQuantity() > 0
                && toUse.getItemId() == itemid
                && mob != null) {
            final MapleMap map = chr.getMap();
            final boolean halfHp = mob.getHp() <= mob.getMobMaxHp() / 2;
            switch (itemid) {
                case 2270004:
                    { // Purification Marble
                        if (halfHp) {
                            map.broadcastMessage(
                                    MaplePacketCreator.catchMonster(mob.getId(), itemid, (byte) 1));
                            map.killMonster(mob, chr, true, false, (byte) 0);
                            MapleInventoryManipulator.removeById(
                                    c, MapleInventoryType.USE, itemid, 1, false, false);
                            MapleInventoryManipulator.addById(c, 4001169, (short) 1);
                        } else {
                            map.broadcastMessage(
                                    MaplePacketCreator.catchMonster(mob.getId(), itemid, (byte) 0));
                            chr.dropMessage(
                                    5,
                                    "The monster has too much physical strength, so you cannot catch it.");
                        }
                        break;
                    }
                case 2270002:
                    { // Characteristic Stone
                        if (halfHp) {
                            map.broadcastMessage(
                                    MaplePacketCreator.catchMonster(mob.getId(), itemid, (byte) 1));
                            map.killMonster(mob, chr, true, false, (byte) 0);
                            MapleInventoryManipulator.removeById(
                                    c, MapleInventoryType.USE, itemid, 1, false, false);
                        } else {
                            map.broadcastMessage(
                                    MaplePacketCreator.catchMonster(mob.getId(), itemid, (byte) 0));
                            chr.dropMessage(
                                    5,
                                    "The monster has too much physical strength, so you cannot catch it.");
                        }
                        break;
                    }
                case 2270000:
                    { // Pheromone Perfume
                        if (mob.getId() != 9300101) {
                            break;
                        }
                        map.broadcastMessage(
                                MaplePacketCreator.catchMonster(mob.getId(), itemid, (byte) 1));
                        map.killMonster(mob, chr, true, false, (byte) 0);
                        MapleInventoryManipulator.addById(c, 1902000, (short) 1, null);
                        MapleInventoryManipulator.removeById(
                                c, MapleInventoryType.USE, itemid, 1, false, false);
                        break;
                    }
                case 2270003:
                    { // Cliff's Magic Cane
                        if (mob.getId() != 9500320) {
                            break;
                        }
                        if (halfHp) {
                            map.broadcastMessage(
                                    MaplePacketCreator.catchMonster(mob.getId(), itemid, (byte) 1));
                            map.killMonster(mob, chr, true, false, (byte) 0);
                            MapleInventoryManipulator.removeById(
                                    c, MapleInventoryType.USE, itemid, 1, false, false);
                        } else {
                            map.broadcastMessage(
                                    MaplePacketCreator.catchMonster(mob.getId(), itemid, (byte) 0));
                            chr.dropMessage(
                                    5,
                                    "The monster has too much physical strength, so you cannot catch it.");
                        }
                        break;
                    }
            }
        }
        c.sendPacket(MaplePacketCreator.enableActions());
    }

    public static final void UseMountFood(
            final SeekableLittleEndianAccessor slea,
            final MapleClient c,
            final MapleCharacter chr) {
        chr.updateTick(slea.readInt());
        final short slot = slea.readShort();
        final int itemid = slea.readInt(); // 2260000 usually
        final IItem toUse = chr.getInventory(MapleInventoryType.USE).getItem(slot);
        final MapleMount mount = chr.getMount();

        if (toUse != null
                && toUse.getQuantity() > 0
                && toUse.getItemId() == itemid
                && mount != null) {
            final int fatigue = mount.getFatigue();

            boolean levelup = false;
            mount.setFatigue((byte) -30);

            if (fatigue > 0) {
                mount.increaseExp();
                final int level = mount.getLevel();
                if (level < 31
                        && mount.getExp() >= GameConstants.getMountExpNeededForLevel(level + 1)) {
                    mount.setLevel((byte) (level + 1));
                    levelup = true;
                }
            }
            chr.getMap().broadcastMessage(MaplePacketCreator.updateMount(chr, levelup));
            MapleInventoryManipulator.removeFromSlot(
                    c, MapleInventoryType.USE, slot, (short) 1, false);
        }
        c.sendPacket(MaplePacketCreator.enableActions());
    }

    public static final void UseScriptedNPCItem(
            final SeekableLittleEndianAccessor slea,
            final MapleClient c,
            final MapleCharacter chr) {
        final MapleCharacter player = c.getPlayer();
        player.updateTick(slea.readInt());
        final byte slot = (byte) slea.readShort();
        final int itemId = slea.readInt();
        final MapleInventory useInventory = player.getInventory(MapleInventoryType.USE);
        final IItem toUse = useInventory.getItem(slot);
        long expiration_days = 0L;
        int mountid = 0;

        if (toUse != null && toUse.getQuantity() >= 1 && toUse.getItemId() == itemId) {
            switch (toUse.getItemId()) {
                case 2430007:
                    { // Blank Compass
                        final MapleInventory setupInventory =
                                player.getInventory(MapleInventoryType.SETUP);
                        MapleInventoryManipulator.removeFromSlot(
                                c, MapleInventoryType.USE, slot, (byte) 1, false);

                        final int countNorth = setupInventory.countById(3994102);
                        final int countSouth = setupInventory.countById(3994103);
                        final int countEast = setupInventory.countById(3994104);
                        final int countWest = setupInventory.countById(3994105);

                        if (countNorth >= 20
                                && countSouth >= 20
                                && countEast >= 20
                                && countWest >= 20) {
                            MapleInventoryManipulator.addById(
                                    c, 2430008, (short) 1); // Gold Compass
                            MapleInventoryManipulator.removeById(
                                    c, MapleInventoryType.SETUP, 3994102, 20, false, false);
                            MapleInventoryManipulator.removeById(
                                    c, MapleInventoryType.SETUP, 3994103, 20, false, false);
                            MapleInventoryManipulator.removeById(
                                    c, MapleInventoryType.SETUP, 3994104, 20, false, false);
                            MapleInventoryManipulator.removeById(
                                    c, MapleInventoryType.SETUP, 3994105, 20, false, false);
                        } else {
                            MapleInventoryManipulator.addById(
                                    c, 2430007, (short) 1); // Blank Compass
                        }
                        NPCScriptManager.getInstance().start(c, 2084001);
                        break;
                    }
                case 2430008:
                    { // Gold Compass
                        player.saveLocation(SavedLocationType.RICHIE);
                        MapleMap map;
                        boolean warped = false;

                        final ChannelServer channelServer = c.getChannelServer();
                        for (int i = 390001000; i <= 390001004; i++) {
                            map = channelServer.getMapFactory().getMap(i);
                            if (map.getCharactersSize() == 0) {
                                player.changeMap(map, map.getPortal(0));
                                warped = true;
                                break;
                            }
                        }
                        if (warped) {
                            MapleInventoryManipulator.removeById(
                                    c, MapleInventoryType.USE, 2430008, 1, false, false);
                        } else {
                            player.dropMessage(
                                    5, "All maps are currently in use, please try again later.");
                        }
                        break;
                    }
                case 2430112:
                    { // miracle cube
                        if (useInventory.getNumFreeSlot() >= 1) {
                            final int cubeCount = useInventory.countById(2430112);
                            if (cubeCount >= 25) {
                                if (MapleInventoryManipulator.checkSpace(c, 2049400, 1, "")
                                        && MapleInventoryManipulator.removeById(
                                                c,
                                                MapleInventoryType.USE,
                                                2430112,
                                                25,
                                                true,
                                                false)) {
                                    MapleInventoryManipulator.addById(c, 2049400, (short) 1);
                                } else {
                                    player.dropMessage(5, "Please make some space.");
                                }
                            } else if (cubeCount >= 10) {
                                if (MapleInventoryManipulator.checkSpace(c, 2049400, 1, "")
                                        && MapleInventoryManipulator.removeById(
                                                c,
                                                MapleInventoryType.USE,
                                                2430112,
                                                10,
                                                true,
                                                false)) {
                                    MapleInventoryManipulator.addById(c, 2049401, (short) 1);
                                } else {
                                    player.dropMessage(5, "Please make some space.");
                                }
                            } else {
                                player.dropMessage(
                                        5,
                                        "There needs to be 10 Fragments for a Potential Scroll, 25 for Advanced Potential Scroll.");
                            }
                        } else {
                            player.dropMessage(5, "Please make some space.");
                        }
                        break;
                    }
                case 2430036: // croco 1 day
                    mountid = 1027;
                    expiration_days = 1;
                    break;
                case 2430037: // black scooter 1 day
                    mountid = 1028;
                    expiration_days = 1;
                    break;
                case 2430038: // pink scooter 1 day
                    mountid = 1029;
                    expiration_days = 1;
                    break;
                case 2430039: // clouds 1 day
                    mountid = 1030;
                    expiration_days = 1;
                    break;
                case 2430040: // balrog 1 day
                    mountid = 1031;
                    expiration_days = 1;
                    break;
                case 2430053: // croco 30 day (original code sets 1, preserved)
                    mountid = 1027;
                    expiration_days = 1;
                    break;
                case 2430054: // black scooter 30 day
                    mountid = 1028;
                    expiration_days = 30;
                    break;
                case 2430055: // pink scooter 30 day
                    mountid = 1029;
                    expiration_days = 30;
                    break;
                case 2430056: // mist rog 30 day
                    mountid = 1035;
                    expiration_days = 30;
                    break;
                case 2430072: // ZD tiger 7 day
                    mountid = 1034;
                    expiration_days = 7;
                    break;
                case 2430073: // lion 15 day
                    mountid = 1036;
                    expiration_days = 15;
                    break;
                case 2430074: // unicorn 15 day
                    mountid = 1037;
                    expiration_days = 15;
                    break;
                case 2430075: // low rider 15 day
                    mountid = 1038;
                    expiration_days = 15;
                    break;
                case 2430076: // red truck 15 day
                    mountid = 1039;
                    expiration_days = 15;
                    break;
                case 2430077: // gargoyle 15 day
                    mountid = 1040;
                    expiration_days = 15;
                    break;
                case 2430080: // shinjo 20 day
                    mountid = 1042;
                    expiration_days = 20;
                    break;
                case 2430082: // orange mush 7 day
                    mountid = 1044;
                    expiration_days = 7;
                    break;
                case 2430091: // nightmare 10 day
                    mountid = 1049;
                    expiration_days = 10;
                    break;
                case 2430092: // yeti 10 day
                    mountid = 1050;
                    expiration_days = 10;
                    break;
                case 2430093: // ostrich 10 day
                    mountid = 1051;
                    expiration_days = 10;
                    break;
                case 2430101: // pink bear 10 day
                    mountid = 1052;
                    expiration_days = 10;
                    break;
                case 2430102: // transformation robo 10 day
                    mountid = 1053;
                    expiration_days = 10;
                    break;
                case 2430103: // chicken 30 day
                    mountid = 1054;
                    expiration_days = 30;
                    break;
                case 2430117: // lion 1 year
                    mountid = 1036;
                    expiration_days = 365;
                    break;
                case 2430118: // red truck 1 year
                    mountid = 1039;
                    expiration_days = 365;
                    break;
                case 2430119: // gargoyle 1 year
                    mountid = 1040;
                    expiration_days = 365;
                    break;
                case 2430120: // unicorn 1 year
                    mountid = 1037;
                    expiration_days = 365;
                    break;
                case 2430136: // owl 30 day
                    mountid = 1069;
                    expiration_days = 30;
                    break;
                case 2430137: // owl 1 year
                    mountid = 1069;
                    expiration_days = 365;
                    break;
                case 2430201: // giant bunny 60 day
                    mountid = 1096;
                    expiration_days = 60;
                    break;
                case 2430228: // tiny bunny 60 day
                    mountid = 1101;
                    expiration_days = 60;
                    break;
                case 2430229: // bunny rickshaw 60 day
                    mountid = 1102;
                    expiration_days = 60;
                    break;
            }
        }

        if (mountid > 0) {
            final int job = player.getJob();
            mountid +=
                    (GameConstants.isAran(job)
                            ? 20000000
                            : (GameConstants.isKOC(job) ? 10000000 : 0));
            if (player.getSkillLevel(mountid) > 0) {
                player.dropMessage(5, "You already have this skill.");
            } else if (expiration_days > 0) {
                MapleInventoryManipulator.removeFromSlot(
                        c, MapleInventoryType.USE, slot, (byte) 1, false);
                player.changeSkillLevel(
                        SkillFactory.getSkill(mountid),
                        (byte) 1,
                        (byte) 1,
                        System.currentTimeMillis() + (expiration_days * 24L * 60L * 60L * 1000L));
                player.dropMessage(5, "The skill has been attained.");
            }
        }
        c.sendPacket(MaplePacketCreator.enableActions());
    }

    public static final void UseSummonBag(
            final SeekableLittleEndianAccessor slea,
            final MapleClient c,
            final MapleCharacter chr) {
        if (!chr.isAlive()) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        chr.updateTick(slea.readInt());
        final byte slot = (byte) slea.readShort();
        final int itemId = slea.readInt();
        final IItem toUse = chr.getInventory(MapleInventoryType.USE).getItem(slot);

        if (toUse != null && toUse.getQuantity() >= 1 && toUse.getItemId() == itemId) {

            MapleInventoryManipulator.removeFromSlot(
                    c, MapleInventoryType.USE, slot, (short) 1, false);

            if (chr.isGM() || !FieldLimitType.SummoningBag.check(chr.getMap().getFieldLimit())) {
                final List<Pair<Integer, Integer>> toSpawn =
                        MapleItemInformationProvider.getInstance().getSummonMobs(itemId);

                if (toSpawn == null) {
                    c.sendPacket(MaplePacketCreator.enableActions());
                    return;
                }

                final int size = toSpawn.size();
                final Point pos = chr.getPosition();
                final MapleMap map = chr.getMap();
                final int type = 0;

                for (int i = 0; i < size; i++) {
                    final Pair<Integer, Integer> spawn = toSpawn.get(i);
                    if (Randomizer.nextInt(99) <= spawn.right()) {
                        final MapleMonster ht = MapleLifeFactory.getMonster(spawn.left());
                        map.spawnMonster_sSack(ht, pos, type);
                    }
                }
            }
        }
        c.sendPacket(MaplePacketCreator.enableActions());
    }

    public static final void UseTreasureChest(
            final SeekableLittleEndianAccessor slea,
            final MapleClient c,
            final MapleCharacter chr) {
        final short slot = slea.readShort();
        final int itemid = slea.readInt();

        final IItem toUse = chr.getInventory(MapleInventoryType.ETC).getItem((byte) slot);
        if (toUse == null || toUse.getQuantity() <= 0 || toUse.getItemId() != itemid) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }

        final int reward;
        final int keyIDforRemoval;
        final String box;

        switch (toUse.getItemId()) {
            case 4280000: // Gold box
                reward = RandomRewards.getInstance().getGoldBoxReward();
                keyIDforRemoval = 5490000;
                box = "金寶箱";
                break;
            case 4280001: // Silver box
                reward = RandomRewards.getInstance().getSilverBoxReward();
                keyIDforRemoval = 5490001;
                box = "銀寶箱";
                break;
            default: // Up to no good
                return;
        }

        // Get the quantity
        int amount = 1;
        switch (reward) {
            case 2000004:
                amount = 200; // Elixir
                break;
            case 2000005:
                amount = 100; // Power Elixir
                break;
        }

        if (chr.getInventory(MapleInventoryType.CASH).countById(keyIDforRemoval) > 0) {
            final IItem item =
                    MapleInventoryManipulator.addbyId_Gachapon(c, reward, (short) amount);

            if (item == null) {
                chr.dropMessage(5, "請確認是否有金鑰匙或者你身上的空間滿了.");
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            MapleInventoryManipulator.removeFromSlot(
                    c, MapleInventoryType.ETC, (byte) slot, (short) 1, true);
            MapleInventoryManipulator.removeById(
                    c, MapleInventoryType.CASH, keyIDforRemoval, 1, true, false);
            c.sendPacket(MaplePacketCreator.getShowItemGain(reward, (short) amount, true));

            if (GameConstants.gachaponRareItem(item.getItemId()) > 0) {
                World.Broadcast.broadcastMessage(
                        MaplePacketCreator.getGachaponMega(
                                        "[" + box + "] " + c.getPlayer().getName(),
                                        " : 從金寶箱中獲得",
                                        item,
                                        (byte) 2)
                                .getBytes());
            }
        } else {
            chr.dropMessage(5, "請確認是否有銀鑰匙或者你身上的空間滿了.");
            c.sendPacket(MaplePacketCreator.enableActions());
        }
    }

    public static final void UseCashItem(
            final SeekableLittleEndianAccessor slea, final MapleClient c) {
        //        c.getPlayer().updateTick(slea.readInt());
        final byte slot = (byte) slea.readShort();
        final int itemId = slea.readInt();

        final IItem toUse = c.getPlayer().getInventory(MapleInventoryType.CASH).getItem(slot);
        if (toUse == null || toUse.getItemId() != itemId || toUse.getQuantity() < 1) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }

        boolean used = false, cc = false;
        switch (itemId) {
            case 5043001: // NPC Teleport Rock
            case 5043000:
                { // NPC Teleport Rock
                    final short questid = slea.readShort();
                    final int npcid = slea.readInt();
                    final MapleQuest quest = MapleQuest.getInstance(questid);

                    if (c.getPlayer().getQuest(quest).getStatus() == 1
                            && quest.canComplete(c.getPlayer(), npcid)) {
                        final int mapId = MapleLifeFactory.getNPCLocation(npcid);
                        if (mapId != -1) {
                            final MapleMap map = c.getChannelServer().getMapFactory().getMap(mapId);
                            if (map.containsNPC(npcid)
                                    && !FieldLimitType.VipRock.check(
                                            c.getPlayer().getMap().getFieldLimit())
                                    && !FieldLimitType.VipRock.check(map.getFieldLimit())
                                    && c.getPlayer().getEventInstance() == null) {
                                c.getPlayer().changeMap(map, map.getPortal(0));
                            }
                            used = true;
                        } else {
                            c.getPlayer().dropMessage(1, "Unknown error has occurred.");
                        }
                    }
                    break;
                }
            case 5042000:
                if (c.getPlayer().getEventInstance() == null) {
                    final MapleMap target = c.getChannelServer().getMapFactory().getMap(701000000);
                    c.getPlayer().changeMap(target, target.getPortal(0));
                    used = true;
                }
                break;
            case 5042001:
                if (c.getPlayer().getEventInstance() == null) {
                    final MapleMap target = c.getChannelServer().getMapFactory().getMap(741000000);
                    c.getPlayer().changeMap(target, target.getPortal(0));
                    used = true;
                }
                break;
            case 5560000: // The Teleport tick
            case 5561000:
                { // The VIP Teleport tick
                    slea.skip(1);
                    final MapleMap target =
                            c.getChannelServer().getMapFactory().getMap(slea.readInt());
                    if (c.getPlayer().getEventInstance()
                            == null) { // Makes sure this map doesn't have a forced return map
                        c.getPlayer().changeMap(target, target.getPortal(0));
                        used = true;
                    }
                    break;
                }
            case 2320000: // The Teleport Rock
            case 5041000: // VIP Teleport Rock
            case 5040000: // The Teleport Rock
            case 5040001:
                { // Teleport Coke
                    if (slea.readByte() == 0) { // Rocktype
                        final MapleMap target =
                                c.getChannelServer().getMapFactory().getMap(slea.readInt());
                        if ((itemId == 5041000 && c.getPlayer().isRockMap(target.getId()))
                                || (itemId != 5041000
                                        && c.getPlayer().isRegRockMap(target.getId()))) {
                            if (!FieldLimitType.VipRock.check(
                                            c.getPlayer().getMap().getFieldLimit())
                                    && !FieldLimitType.VipRock.check(target.getFieldLimit())
                                    && c.getPlayer().getEventInstance()
                                            == null) { // Makes sure this map doesn't have a forced
                                // return map
                                c.getPlayer().changeMap(target, target.getPortal(0));
                                used = true;
                            }
                        }
                    } else {
                        final MapleCharacter victim =
                                c.getChannelServer()
                                        .getPlayerStorage()
                                        .getCharacterByName(slea.readMapleAsciiString());
                        if (victim != null
                                && !victim.isGM()
                                && c.getPlayer().getEventInstance() == null
                                && victim.getEventInstance() == null) {
                            if (!FieldLimitType.VipRock.check(
                                            c.getPlayer().getMap().getFieldLimit())
                                    && !FieldLimitType.VipRock.check(
                                            c.getChannelServer()
                                                    .getMapFactory()
                                                    .getMap(victim.getMapId())
                                                    .getFieldLimit())) {
                                if (itemId == 5041000
                                        || (victim.getMapId() / 100000000)
                                                == (c.getPlayer().getMapId()
                                                        / 100000000)) { // Viprock or same continent
                                    c.getPlayer()
                                            .changeMap(
                                                    victim.getMap(),
                                                    victim.getMap()
                                                            .findClosestSpawnpoint(
                                                                    victim.getPosition()));
                                    used = true;
                                }
                            }
                        }
                    }
                    break;
                }
            case 5050000:
                { // AP Reset
                    List<Pair<MapleStat, Integer>> statupdate = new ArrayList<>(2);
                    final int apto = slea.readInt();
                    final int apfrom = slea.readInt();

                    if (apto == apfrom) {
                        break; // Hack
                    }
                    final int job = c.getPlayer().getJob();
                    final PlayerStats playerst = c.getPlayer().getStat();
                    used = true;

                    switch (apto) { // AP to
                        case 64: // str
                            if (playerst.getStr() >= 999) {
                                used = false;
                            }
                            break;
                        case 128: // dex
                            if (playerst.getDex() >= 999) {
                                used = false;
                            }
                            break;
                        case 256: // int
                            if (playerst.getInt() >= 999) {
                                used = false;
                            }
                            break;
                        case 512: // luk
                            if (playerst.getLuk() >= 999) {
                                used = false;
                            }
                            break;
                        case 2048: // hp
                            if (playerst.getMaxHp() >= 30000) {
                                used = false;
                            }
                            break;
                        case 8192: // mp
                            if (playerst.getMaxMp() >= 30000) {
                                used = false;
                            }
                            break;
                    }
                    switch (apfrom) { // AP to
                        case 64: // str
                            if (playerst.getStr() <= 4) {
                                used = false;
                            }
                            break;
                        case 128: // dex
                            if (playerst.getDex() <= 4) {
                                used = false;
                            }
                            break;
                        case 256: // int
                            if (playerst.getInt() <= 4) {
                                used = false;
                            }
                            break;
                        case 512: // luk
                            if (playerst.getLuk() <= 4) {
                                used = false;
                            }
                            break;
                        case 2048: // hp
                            if (
                            /*playerst.getMaxMp() < ((c.getPlayer().getLevel() * 14) + 134) || */ c.getPlayer()
                                                    .getHpApUsed()
                                            <= 0
                                    || c.getPlayer().getHpApUsed() >= 10000) {
                                used = false;
                            }
                            break;
                        case 8192: // mp
                            if (
                            /*playerst.getMaxMp() < ((c.getPlayer().getLevel() * 14) + 134) || */ c.getPlayer()
                                                    .getHpApUsed()
                                            <= 0
                                    || c.getPlayer().getHpApUsed() >= 10000) {
                                used = false;
                            }
                            break;
                    }
                    if (used) {
                        switch (apto) { // AP to
                            case 64:
                                { // str
                                    final int toSet = playerst.getStr() + 1;
                                    playerst.setStr((short) toSet);
                                    statupdate.add(new Pair<>(MapleStat.STR, toSet));
                                    break;
                                }
                            case 128:
                                { // dex
                                    final int toSet = playerst.getDex() + 1;
                                    playerst.setDex((short) toSet);
                                    statupdate.add(new Pair<>(MapleStat.DEX, toSet));
                                    break;
                                }
                            case 256:
                                { // int
                                    final int toSet = playerst.getInt() + 1;
                                    playerst.setInt((short) toSet);
                                    statupdate.add(new Pair<>(MapleStat.INT, toSet));
                                    break;
                                }
                            case 512:
                                { // luk
                                    final int toSet = playerst.getLuk() + 1;
                                    playerst.setLuk((short) toSet);
                                    statupdate.add(new Pair<>(MapleStat.LUK, toSet));
                                    break;
                                }
                            case 2048: // hp
                                short maxhp = playerst.getMaxHp();

                                if (job == 0) { // Beginner
                                    maxhp += Randomizer.rand(8, 12);
                                } else if ((job >= 100 && job <= 132)
                                        || (job >= 3200 && job <= 3212)) { // Warrior
                                    ISkill improvingMaxHP = SkillFactory.getSkill(1000001);
                                    int improvingMaxHPLevel =
                                            c.getPlayer().getSkillLevel(improvingMaxHP);
                                    maxhp += Randomizer.rand(20, 25);
                                    if (improvingMaxHPLevel >= 1) {
                                        maxhp +=
                                                improvingMaxHP
                                                        .getEffect(improvingMaxHPLevel)
                                                        .getY();
                                    }
                                    //                            } else if ((job >= 200 && job <=
                                    // 232) ||
                                    // (GameConstants.isEvan(job))) { // Magician
                                    //                                maxhp += Randomizer.rand(10,
                                    // 20);
                                } else if ((job >= 300 && job <= 322)
                                        || (job >= 400 && job <= 434)
                                        || (job >= 1300 && job <= 1312)
                                        || (job >= 1400 && job <= 1412)
                                        || (job >= 3300 && job <= 3312)) { // Bowman
                                    maxhp += Randomizer.rand(16, 20);
                                } else if ((job >= 500 && job <= 522)
                                        || (job >= 3500 && job <= 3512)) { // Pirate
                                    ISkill improvingMaxHP = SkillFactory.getSkill(5100000);
                                    int improvingMaxHPLevel =
                                            c.getPlayer().getSkillLevel(improvingMaxHP);
                                    maxhp += Randomizer.rand(18, 22);
                                    if (improvingMaxHPLevel >= 1) {
                                        maxhp +=
                                                improvingMaxHP
                                                        .getEffect(improvingMaxHPLevel)
                                                        .getY();
                                    }
                                } else if (job >= 1500 && job <= 1512) { // Pirate
                                    ISkill improvingMaxHP = SkillFactory.getSkill(15100000);
                                    int improvingMaxHPLevel =
                                            c.getPlayer().getSkillLevel(improvingMaxHP);
                                    maxhp += Randomizer.rand(18, 22);
                                    if (improvingMaxHPLevel >= 1) {
                                        maxhp +=
                                                improvingMaxHP
                                                        .getEffect(improvingMaxHPLevel)
                                                        .getY();
                                    }
                                } else if (job >= 1100 && job <= 1112) { // Soul Master
                                    ISkill improvingMaxHP = SkillFactory.getSkill(11000000);
                                    int improvingMaxHPLevel =
                                            c.getPlayer().getSkillLevel(improvingMaxHP);
                                    maxhp += Randomizer.rand(36, 42);
                                    if (improvingMaxHPLevel >= 1) {
                                        maxhp +=
                                                improvingMaxHP
                                                        .getEffect(improvingMaxHPLevel)
                                                        .getY();
                                    }
                                } else if (job >= 1200 && job <= 1212) { // Flame Wizard
                                    maxhp += Randomizer.rand(15, 21);
                                } else if (job >= 2000 && job <= 2112) { // Aran
                                    maxhp += Randomizer.rand(40, 50);
                                } else { // GameMaster
                                    maxhp += Randomizer.rand(50, 100);
                                }
                                maxhp = (short) Math.min(30000, Math.abs(maxhp));
                                c.getPlayer()
                                        .setHpApUsed((short) (c.getPlayer().getHpApUsed() + 1));
                                playerst.setMaxHp(maxhp);
                                statupdate.add(new Pair<>(MapleStat.MAXHP, (int) maxhp));
                                break;

                            case 8192: // mp
                                short maxmp = playerst.getMaxMp();

                                if (job == 0) { // Beginner
                                    maxmp += Randomizer.rand(6, 8);
                                } else if (job >= 100 && job <= 132) { // Warrior
                                    maxmp += Randomizer.rand(5, 7);
                                    //                            } else if ((job >= 200 && job <=
                                    // 232) ||
                                    // (GameConstants.isEvan(job)) || (job >= 3200 && job <= 3212))
                                    // { // Magician
                                } else if ((job >= 200 && job <= 232)
                                        || (job >= 3200 && job <= 3212)) { // Magician
                                    ISkill improvingMaxMP = SkillFactory.getSkill(2000001);
                                    int improvingMaxMPLevel =
                                            c.getPlayer().getSkillLevel(improvingMaxMP);
                                    maxmp += Randomizer.rand(18, 20);
                                    if (improvingMaxMPLevel >= 1) {
                                        maxmp +=
                                                improvingMaxMP.getEffect(improvingMaxMPLevel).getY()
                                                        * 2;
                                    }
                                } else if ((job >= 300 && job <= 322)
                                        || (job >= 400 && job <= 434)
                                        || (job >= 500 && job <= 522)
                                        || (job >= 3200 && job <= 3212)
                                        || (job >= 3500 && job <= 3512)
                                        || (job >= 1300 && job <= 1312)
                                        || (job >= 1400 && job <= 1412)
                                        || (job >= 1500 && job <= 1512)) { // Bowman
                                    maxmp += Randomizer.rand(10, 12);
                                } else if (job >= 1100 && job <= 1112) { // Soul Master
                                    maxmp += Randomizer.rand(6, 9);
                                } else if (job >= 1200 && job <= 1212) { // Flame Wizard
                                    ISkill improvingMaxMP = SkillFactory.getSkill(12000000);
                                    int improvingMaxMPLevel =
                                            c.getPlayer().getSkillLevel(improvingMaxMP);
                                    maxmp += Randomizer.rand(18, 20);
                                    if (improvingMaxMPLevel >= 1) {
                                        maxmp +=
                                                improvingMaxMP.getEffect(improvingMaxMPLevel).getY()
                                                        * 2;
                                    }
                                } else if (job >= 2000 && job <= 2112) { // Aran
                                    maxmp += Randomizer.rand(6, 9);
                                } else { // GameMaster
                                    maxmp += Randomizer.rand(50, 100);
                                }
                                maxmp = (short) Math.min(30000, Math.abs(maxmp));
                                c.getPlayer()
                                        .setHpApUsed((short) (c.getPlayer().getHpApUsed() + 1));
                                playerst.setMaxMp(maxmp);
                                statupdate.add(new Pair<>(MapleStat.MAXMP, (int) maxmp));
                                break;
                        }
                        switch (apfrom) { // AP from
                            case 64:
                                { // str
                                    final int toSet = playerst.getStr() - 1;
                                    playerst.setStr((short) toSet);
                                    statupdate.add(new Pair<>(MapleStat.STR, toSet));
                                    break;
                                }
                            case 128:
                                { // dex
                                    final int toSet = playerst.getDex() - 1;
                                    playerst.setDex((short) toSet);
                                    statupdate.add(new Pair<>(MapleStat.DEX, toSet));
                                    break;
                                }
                            case 256:
                                { // int
                                    final int toSet = playerst.getInt() - 1;
                                    playerst.setInt((short) toSet);
                                    statupdate.add(new Pair<>(MapleStat.INT, toSet));
                                    break;
                                }
                            case 512:
                                { // luk
                                    final int toSet = playerst.getLuk() - 1;
                                    playerst.setLuk((short) toSet);
                                    statupdate.add(new Pair<>(MapleStat.LUK, toSet));
                                    break;
                                }
                            case 2048: // HP
                                short maxhp = playerst.getMaxHp();
                                if (job == 0) { // Beginner
                                    maxhp -= 12;
                                } else if (job >= 100 && job <= 132) { // Warrior
                                    ISkill improvingMaxHP = SkillFactory.getSkill(1000001);
                                    int improvingMaxHPLevel =
                                            c.getPlayer().getSkillLevel(improvingMaxHP);
                                    maxhp -= 24;
                                    if (improvingMaxHPLevel >= 1) {
                                        maxhp -=
                                                improvingMaxHP
                                                        .getEffect(improvingMaxHPLevel)
                                                        .getY();
                                    }
                                } else if (job >= 200 && job <= 232) { // Magician
                                    maxhp -= 10;
                                } else if ((job >= 300 && job <= 322)
                                        || (job >= 400 && job <= 434)
                                        || (job >= 1300 && job <= 1312)
                                        || (job >= 1400 && job <= 1412)
                                        || (job >= 3300 && job <= 3312)
                                        || (job >= 3500 && job <= 3512)) { // Bowman, Thief
                                    maxhp -= 15;
                                } else if (job >= 500 && job <= 522) { // Pirate
                                    ISkill improvingMaxHP = SkillFactory.getSkill(5100000);
                                    int improvingMaxHPLevel =
                                            c.getPlayer().getSkillLevel(improvingMaxHP);
                                    maxhp -= 15;
                                    if (improvingMaxHPLevel > 0) {
                                        maxhp -=
                                                improvingMaxHP
                                                        .getEffect(improvingMaxHPLevel)
                                                        .getY();
                                    }
                                } else if (job >= 1500 && job <= 1512) { // Pirate
                                    ISkill improvingMaxHP = SkillFactory.getSkill(15100000);
                                    int improvingMaxHPLevel =
                                            c.getPlayer().getSkillLevel(improvingMaxHP);
                                    maxhp -= 15;
                                    if (improvingMaxHPLevel > 0) {
                                        maxhp -=
                                                improvingMaxHP
                                                        .getEffect(improvingMaxHPLevel)
                                                        .getY();
                                    }
                                } else if (job >= 1100 && job <= 1112) { // Soul Master
                                    ISkill improvingMaxHP = SkillFactory.getSkill(11000000);
                                    int improvingMaxHPLevel =
                                            c.getPlayer().getSkillLevel(improvingMaxHP);
                                    maxhp -= 27;
                                    if (improvingMaxHPLevel >= 1) {
                                        maxhp -=
                                                improvingMaxHP
                                                        .getEffect(improvingMaxHPLevel)
                                                        .getY();
                                    }
                                } else if (job >= 1200 && job <= 1212) { // Flame Wizard
                                    maxhp -= 12;
                                } else if ((job >= 2000 && job <= 2112)
                                        || (job >= 3200 && job <= 3212)) { // Aran
                                    maxhp -= 40;
                                } else { // GameMaster
                                    maxhp -= 20;
                                }
                                c.getPlayer()
                                        .setHpApUsed((short) (c.getPlayer().getHpApUsed() - 1));
                                playerst.setHp(maxhp);
                                playerst.setMaxHp(maxhp);
                                statupdate.add(new Pair<>(MapleStat.MAXHP, (int) maxhp));
                                break;
                            case 8192: // MP
                                short maxmp = playerst.getMaxMp();
                                if (job == 0) { // Beginner
                                    maxmp -= 8;
                                } else if (job >= 100 && job <= 132) { // Warrior
                                    maxmp -= 4;
                                } else if (job >= 200 && job <= 232) { // Magician
                                    ISkill improvingMaxMP = SkillFactory.getSkill(2000001);
                                    int improvingMaxMPLevel =
                                            c.getPlayer().getSkillLevel(improvingMaxMP);
                                    maxmp -= 20;
                                    if (improvingMaxMPLevel >= 1) {
                                        maxmp -=
                                                improvingMaxMP
                                                        .getEffect(improvingMaxMPLevel)
                                                        .getY();
                                    }
                                } else if ((job >= 500 && job <= 522)
                                        || (job >= 300 && job <= 322)
                                        || (job >= 400 && job <= 434)
                                        || (job >= 1300 && job <= 1312)
                                        || (job >= 1400 && job <= 1412)
                                        || (job >= 1500 && job <= 1512)
                                        || (job >= 3300 && job <= 3312)
                                        || (job >= 3500 && job <= 3512)) { // Pirate, Bowman. Thief
                                    maxmp -= 10;
                                } else if (job >= 1100 && job <= 1112) { // Soul Master
                                    maxmp -= 6;
                                } else if (job >= 1200 && job <= 1212) { // Flame Wizard
                                    ISkill improvingMaxMP = SkillFactory.getSkill(12000000);
                                    int improvingMaxMPLevel =
                                            c.getPlayer().getSkillLevel(improvingMaxMP);
                                    maxmp -= 25;
                                    if (improvingMaxMPLevel >= 1) {
                                        maxmp -=
                                                improvingMaxMP
                                                        .getEffect(improvingMaxMPLevel)
                                                        .getY();
                                    }
                                } else if (job >= 2000 && job <= 2112) { // Aran
                                    maxmp -= 5;
                                } else { // GameMaster
                                    maxmp -= 20;
                                }
                                c.getPlayer()
                                        .setHpApUsed((short) (c.getPlayer().getHpApUsed() - 1));
                                playerst.setMp(maxmp);
                                playerst.setMaxMp(maxmp);
                                statupdate.add(new Pair<>(MapleStat.MAXMP, (int) maxmp));
                                break;
                        }
                        c.sendPacket(MaplePacketCreator.updatePlayerStats(statupdate, true));
                    }
                    break;
                }
            case 5050001: // SP Reset (1st job)
            case 5050002: // SP Reset (2nd job)
            case 5050003: // SP Reset (3rd job)
            case 5050004:
                { // SP Reset (4th job)
                    int skill1 = slea.readInt();
                    int skill2 = slea.readInt();

                    ISkill skillSPTo = SkillFactory.getSkill(skill1);
                    ISkill skillSPFrom = SkillFactory.getSkill(skill2);

                    if (skillSPTo.isBeginnerSkill() || skillSPFrom.isBeginnerSkill()) {
                        break;
                    }
                    if ((c.getPlayer().getSkillLevel(skillSPTo) + 1 <= skillSPTo.getMaxLevel())
                            && c.getPlayer().getSkillLevel(skillSPFrom) > 0) {
                        c.getPlayer()
                                .changeSkillLevel(
                                        skillSPFrom,
                                        (byte) (c.getPlayer().getSkillLevel(skillSPFrom) - 1),
                                        c.getPlayer().getMasterLevel(skillSPFrom));
                        c.getPlayer()
                                .changeSkillLevel(
                                        skillSPTo,
                                        (byte) (c.getPlayer().getSkillLevel(skillSPTo) + 1),
                                        c.getPlayer().getMasterLevel(skillSPTo));
                        used = true;
                    }
                    break;
                }
            case 5500000:
                { // Magic Hourglass 1 day
                    final IItem item =
                            c.getPlayer()
                                    .getInventory(MapleInventoryType.EQUIPPED)
                                    .getItem(slea.readShort());
                    final MapleItemInformationProvider ii =
                            MapleItemInformationProvider.getInstance();
                    final int days = 1;
                    if (item != null
                            && !ItemConstants.ItemTypes.isAccessories(item.getItemId())
                            && item.getExpiration() > -1
                            && !ii.isCash(item.getItemId())
                            && System.currentTimeMillis() + (100 * 24 * 60 * 60 * 1000L)
                                    > item.getExpiration() + (days * 24 * 60 * 60 * 1000L)) {
                        boolean change = true;
                        for (String z : GameConstants.getReserved()) {
                            if (c.getPlayer().getName().indexOf(z) != -1
                                    || item.getOwner().indexOf(z) != -1) {
                                change = false;
                            }
                        }
                        if (change) {
                            item.setExpiration(item.getExpiration() + (days * 24 * 60 * 60 * 1000));
                            c.getPlayer().forceReAddItem(item, MapleInventoryType.EQUIPPED);
                            used = true;
                        } else {
                            c.getPlayer().dropMessage(1, "It may not be used on this item.");
                        }
                    }
                    break;
                }
            case 5500001:
                { // Magic Hourglass 7 day
                    final IItem item =
                            c.getPlayer()
                                    .getInventory(MapleInventoryType.EQUIPPED)
                                    .getItem(slea.readShort());
                    final MapleItemInformationProvider ii =
                            MapleItemInformationProvider.getInstance();
                    final int days = 7;
                    if (item != null
                            && !ItemConstants.ItemTypes.isAccessories(item.getItemId())
                            && item.getExpiration() > -1
                            && !ii.isCash(item.getItemId())
                            && System.currentTimeMillis() + (100 * 24 * 60 * 60 * 1000L)
                                    > item.getExpiration() + (days * 24 * 60 * 60 * 1000L)) {
                        boolean change = true;
                        for (String z : GameConstants.getReserved()) {
                            if (c.getPlayer().getName().indexOf(z) != -1
                                    || item.getOwner().indexOf(z) != -1) {
                                change = false;
                            }
                        }
                        if (change) {
                            item.setExpiration(item.getExpiration() + (days * 24 * 60 * 60 * 1000));
                            c.getPlayer().forceReAddItem(item, MapleInventoryType.EQUIPPED);
                            used = true;
                        } else {
                            c.getPlayer().dropMessage(1, "It may not be used on this item.");
                        }
                    }
                    break;
                }
            case 5500002:
                { // Magic Hourglass 20 day
                    final IItem item =
                            c.getPlayer()
                                    .getInventory(MapleInventoryType.EQUIPPED)
                                    .getItem(slea.readShort());
                    final MapleItemInformationProvider ii =
                            MapleItemInformationProvider.getInstance();
                    final int days = 20;
                    if (item != null
                            && !ItemConstants.ItemTypes.isAccessories(item.getItemId())
                            && item.getExpiration() > -1
                            && !ii.isCash(item.getItemId())
                            && System.currentTimeMillis() + (100 * 24 * 60 * 60 * 1000L)
                                    > item.getExpiration() + (days * 24 * 60 * 60 * 1000L)) {
                        boolean change = true;
                        for (String z : GameConstants.getReserved()) {
                            if (c.getPlayer().getName().indexOf(z) != -1
                                    || item.getOwner().indexOf(z) != -1) {
                                change = false;
                            }
                        }
                        if (change) {
                            item.setExpiration(item.getExpiration() + (days * 24 * 60 * 60 * 1000));
                            c.getPlayer().forceReAddItem(item, MapleInventoryType.EQUIPPED);
                            used = true;
                        } else {
                            c.getPlayer().dropMessage(1, "It may not be used on this item.");
                        }
                    }
                    break;
                }
            case 5500005:
                { // Magic Hourglass 50 day
                    final IItem item =
                            c.getPlayer()
                                    .getInventory(MapleInventoryType.EQUIPPED)
                                    .getItem(slea.readShort());
                    final MapleItemInformationProvider ii =
                            MapleItemInformationProvider.getInstance();
                    final int days = 50;
                    if (item != null
                            && !ItemConstants.ItemTypes.isAccessories(item.getItemId())
                            && item.getExpiration() > -1
                            && !ii.isCash(item.getItemId())
                            && System.currentTimeMillis() + (100 * 24 * 60 * 60 * 1000L)
                                    > item.getExpiration() + (days * 24 * 60 * 60 * 1000L)) {
                        boolean change = true;
                        for (String z : GameConstants.getReserved()) {
                            if (c.getPlayer().getName().indexOf(z) != -1
                                    || item.getOwner().indexOf(z) != -1) {
                                change = false;
                            }
                        }
                        if (change) {
                            item.setExpiration(item.getExpiration() + (days * 24 * 60 * 60 * 1000));
                            c.getPlayer().forceReAddItem(item, MapleInventoryType.EQUIPPED);
                            used = true;
                        } else {
                            c.getPlayer().dropMessage(1, "It may not be used on this item.");
                        }
                    }
                    break;
                }
            case 5500006:
                { // Magic Hourglass 99 day
                    final IItem item =
                            c.getPlayer()
                                    .getInventory(MapleInventoryType.EQUIPPED)
                                    .getItem(slea.readShort());
                    final MapleItemInformationProvider ii =
                            MapleItemInformationProvider.getInstance();
                    final int days = 99;
                    if (item != null
                            && !ItemConstants.ItemTypes.isAccessories(item.getItemId())
                            && item.getExpiration() > -1
                            && !ii.isCash(item.getItemId())
                            && System.currentTimeMillis() + (100 * 24 * 60 * 60 * 1000L)
                                    > item.getExpiration() + (days * 24 * 60 * 60 * 1000L)) {
                        boolean change = true;
                        for (String z : GameConstants.getReserved()) {
                            if (c.getPlayer().getName().indexOf(z) != -1
                                    || item.getOwner().indexOf(z) != -1) {
                                change = false;
                            }
                        }
                        if (change) {
                            item.setExpiration(item.getExpiration() + (days * 24 * 60 * 60 * 1000));
                            c.getPlayer().forceReAddItem(item, MapleInventoryType.EQUIPPED);
                            used = true;
                        } else {
                            c.getPlayer().dropMessage(1, "It may not be used on this item.");
                        }
                    }
                    break;
                }
            case 5060000:
                { // Item Tag
                    final IItem item =
                            c.getPlayer()
                                    .getInventory(MapleInventoryType.EQUIPPED)
                                    .getItem(slea.readByte());

                    if (item != null && item.getOwner().equals("")) {
                        boolean change = true;
                        for (String z : GameConstants.getReserved()) {
                            if (c.getPlayer().getName().indexOf(z) != -1
                                    || item.getOwner().indexOf(z) != -1) {
                                change = false;
                            }
                        }
                        if (change) {
                            item.setOwner(c.getPlayer().getName());
                            c.getPlayer().forceReAddItem(item, MapleInventoryType.EQUIPPED);
                            used = true;
                        }
                    }
                    break;
                }
            case 5062000:
                { // miracle cube
                    final IItem item =
                            c.getPlayer()
                                    .getInventory(MapleInventoryType.EQUIP)
                                    .getItem((byte) slea.readInt());
                    if (item != null
                            && c.getPlayer().getInventory(MapleInventoryType.USE).getNumFreeSlot()
                                    >= 1) {
                        final Equip eq = (Equip) item;
                        if (eq.getState() >= 5) {
                            eq.renewPotential();
                            c.sendPacket(MaplePacketCreator.scrolledItem(toUse, item, false, true));
                            // c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.getPotentialEffect(c.getPlayer().getId(), eq.getItemId()));
                            c.getPlayer().forceReAddItem_NoUpdate(item, MapleInventoryType.EQUIP);
                            MapleInventoryManipulator.addById(c, 2430112, (short) 1);
                            used = true;
                        } else {
                            c.getPlayer()
                                    .dropMessage(5, "Make sure your equipment has a potential.");
                        }
                    } else {
                        c.getPlayer().dropMessage(5, "Make sure you have room for a Fragment.");
                    }
                    break;
                }
            case 5520001: // p.karma
            case 5520000:
                { // Karma
                    final MapleInventoryType type =
                            MapleInventoryType.getByType((byte) slea.readInt());
                    final IItem item =
                            c.getPlayer().getInventory(type).getItem((byte) slea.readInt());

                    if (item != null
                            && !ItemFlag.KARMA_EQ.check(item.getFlag())
                            && !ItemFlag.KARMA_USE.check(item.getFlag())) {
                        if ((itemId == 5520000
                                        && MapleItemInformationProvider.getInstance()
                                                .isKarmaEnabled(item.getItemId()))
                                || (itemId == 5520001
                                        && MapleItemInformationProvider.getInstance()
                                                .isPKarmaEnabled(item.getItemId()))) {
                            byte flag = item.getFlag();
                            if (type == MapleInventoryType.EQUIP) {
                                flag |= ItemFlag.KARMA_EQ.getValue();
                            } else {
                                flag |= ItemFlag.KARMA_USE.getValue();
                            }
                            item.setFlag(flag);

                            c.getPlayer().forceReAddItem_Flag(item, type);
                            used = true;
                        }
                    }
                    break;
                }
            case 5570000:
                { // Vicious Hammer
                    slea.readInt(); // Inventory type, Hammered eq is always EQ.
                    final Equip item =
                            (Equip)
                                    c.getPlayer()
                                            .getInventory(MapleInventoryType.EQUIP)
                                            .getItem((byte) slea.readInt());
                    // another int here, D3 49 DC 00
                    if (item != null) {
                        if (ItemConstants.Scrolls.canHammer(item.getItemId())
                                && MapleItemInformationProvider.getInstance()
                                                .getSlots(item.getItemId())
                                        > 0
                                && item.getViciousHammer() <= 2) {
                            item.setViciousHammer((byte) (item.getViciousHammer() + 1));
                            item.setUpgradeSlots((byte) (item.getUpgradeSlots() + 1));

                            c.getPlayer().forceReAddItem(item, MapleInventoryType.EQUIP);
                            // c.sendPacket(MTSCSPacket.ViciousHammer(true, (byte)
                            // item.getViciousHammer()));
                            // c.sendPacket(MTSCSPacket.ViciousHammer(false, (byte) 0));
                            used = true;
                            cc = true;
                        } else {
                            c.getPlayer().dropMessage(5, "You may not use it on this item.");
                            cc = true;
                        }
                    }

                    break;
                }
            case 5610001:
            case 5610000:
                { // Vega 30
                    slea.readInt(); // Inventory type, always eq
                    final byte dst = (byte) slea.readInt();
                    slea.readInt(); // Inventory type, always use
                    final byte src = (byte) slea.readInt();
                    used =
                            UseUpgradeScroll(
                                    src,
                                    dst,
                                    (byte) 2,
                                    c,
                                    c.getPlayer(),
                                    itemId); // cannot use ws with vega but we dont care
                    cc = used;
                    break;
                }
            case 5060001:
                { // Sealing Lock
                    final MapleInventoryType type =
                            MapleInventoryType.getByType((byte) slea.readInt());
                    final IItem item =
                            c.getPlayer().getInventory(type).getItem((byte) slea.readInt());
                    // another int here, lock = 5A E5 F2 0A, 7 day = D2 30 F3 0A
                    if (item != null && item.getExpiration() == -1) {
                        byte flag = item.getFlag();
                        flag |= ItemFlag.LOCK.getValue();
                        item.setFlag(flag);

                        c.getPlayer().forceReAddItem_Flag(item, type);
                        used = true;
                    }
                    break;
                }
            case 5061000:
                { // Sealing Lock 7 days
                    final MapleInventoryType type =
                            MapleInventoryType.getByType((byte) slea.readInt());
                    final IItem item =
                            c.getPlayer().getInventory(type).getItem((byte) slea.readInt());
                    // another int here, lock = 5A E5 F2 0A, 7 day = D2 30 F3 0A
                    if (item != null && item.getExpiration() == -1) {
                        byte flag = item.getFlag();
                        flag |= ItemFlag.LOCK.getValue();
                        item.setFlag(flag);
                        item.setExpiration(System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000));

                        c.getPlayer().forceReAddItem_Flag(item, type);
                        used = true;
                    }
                    break;
                }
            case 5061001:
                { // Sealing Lock 30 days
                    final MapleInventoryType type =
                            MapleInventoryType.getByType((byte) slea.readInt());
                    final IItem item =
                            c.getPlayer().getInventory(type).getItem((byte) slea.readInt());
                    // another int here, lock = 5A E5 F2 0A, 7 day = D2 30 F3 0A
                    if (item != null && item.getExpiration() == -1) {
                        byte flag = item.getFlag();
                        flag |= ItemFlag.LOCK.getValue();
                        item.setFlag(flag);

                        item.setExpiration(System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000));

                        c.getPlayer().forceReAddItem_Flag(item, type);
                        used = true;
                    }
                    break;
                }
            case 5061002:
                { // Sealing Lock 90 days
                    final MapleInventoryType type =
                            MapleInventoryType.getByType((byte) slea.readInt());
                    final IItem item =
                            c.getPlayer().getInventory(type).getItem((byte) slea.readInt());
                    // another int here, lock = 5A E5 F2 0A, 7 day = D2 30 F3 0A
                    if (item != null && item.getExpiration() == -1) {
                        byte flag = item.getFlag();
                        flag |= ItemFlag.LOCK.getValue();
                        item.setFlag(flag);

                        item.setExpiration(System.currentTimeMillis() + (90 * 24 * 60 * 60 * 1000));

                        c.getPlayer().forceReAddItem_Flag(item, type);
                        used = true;
                    }
                    break;
                }
            case 5060003:
                { // peanut
                    IItem item =
                            c.getPlayer().getInventory(MapleInventoryType.ETC).findById(4170023);
                    if (item == null || item.getQuantity() <= 0) { // hacking{
                        return;
                    }
                    if (getIncubatedItems(c)) {
                        MapleInventoryManipulator.removeFromSlot(
                                c, MapleInventoryType.ETC, item.getPosition(), (short) 1, false);
                        used = true;
                    }
                }
                break;
            case 5070000:
                { // Megaphone
                    if (c.getPlayer().getLevel() < 10) {
                        c.getPlayer().dropMessage(5, "必須等級10級以上才可以使用.");
                        break;
                    }
                    if (!c.getPlayer().getCheatTracker().canAvatarSmega2()) {
                        c.getPlayer().dropMessage(6, "很抱歉為了防止刷廣,所以你每10秒只能用一次.");
                        break;
                    }
                    if (!c.getChannelServer().getMegaphoneMuteState()) {
                        final String message = slea.readMapleAsciiString();

                        if (message.length() > 65) {
                            break;
                        }
                        final StringBuilder sb = new StringBuilder();
                        addMedalString(c.getPlayer(), sb);
                        sb.append(c.getPlayer().getName());
                        sb.append(" : ");
                        sb.append(message);
                        final boolean ear = slea.readByte() != 0;

                        c.getPlayer()
                                .getMap()
                                .broadcastMessage(
                                        MaplePacketCreator.serverNotice(2, sb.toString()));
                        used = true;
                    } else {
                        c.getPlayer().dropMessage(5, "目前喇叭停止使用.");
                    }
                    break;
                }
            case 5071000:
                { // Megaphone
                    if (c.getPlayer().getLevel() < 10) {
                        c.getPlayer().dropMessage(5, "必須等級10級以上才可以使用.");
                        break;
                    }
                    if (!c.getPlayer().getCheatTracker().canAvatarSmega2()) {
                        c.getPlayer().dropMessage(6, "很抱歉為了防止刷廣,所以你每10秒只能用一次.");
                        break;
                    }
                    if (!c.getChannelServer().getMegaphoneMuteState()) {
                        final String message = slea.readMapleAsciiString();

                        if (message.length() > 65) {
                            break;
                        }
                        final boolean ear = slea.readByte() != 0;

                        final StringBuilder sb = new StringBuilder();
                        addMedalString(c.getPlayer(), sb);
                        sb.append(c.getPlayer().getName());
                        sb.append(" : ");
                        sb.append(message);
                        c.getPlayer()
                                .getMap()
                                .broadcastMessage(
                                        MaplePacketCreator.serverNotice(2, sb.toString()));

                        used = true;
                    } else {
                        c.getPlayer().dropMessage(5, "目前喇叭停止使用.");
                    }
                    break;
                }
            case 5077000:
                { // 3 line Megaphone
                    if (c.getPlayer().getLevel() < 10) {
                        c.getPlayer().dropMessage(5, "必須等級10級以上才可以使用.");
                        break;
                    }
                    if (!c.getPlayer().getCheatTracker().canAvatarSmega2()) {
                        c.getPlayer().dropMessage(6, "很抱歉為了防止刷廣,所以你每10秒只能用一次.");
                        break;
                    }
                    if (!c.getChannelServer().getMegaphoneMuteState()) {
                        final byte numLines = slea.readByte();
                        if (numLines > 3) {
                            return;
                        }
                        final List<String> messages = new LinkedList<>();
                        String message;
                        for (int i = 0; i < numLines; i++) {
                            message = slea.readMapleAsciiString();
                            if (message.length() > 65) {
                                break;
                            }
                            messages.add(c.getPlayer().getName() + " : " + message);
                        }
                        final boolean ear = slea.readByte() > 0;

                        World.Broadcast.broadcastSmega(
                                MaplePacketCreator.tripleSmega(messages, ear, c.getChannel())
                                        .getBytes());

                        used = true;
                    } else {
                        c.getPlayer().dropMessage(5, "目前喇叭停止使用.");
                    }
                    break;
                }
            case 5073000:
                { // Heart Megaphone
                    if (c.getPlayer().getLevel() < 10) {
                        c.getPlayer().dropMessage(5, "必須等級10級以上才可以使用.");
                        break;
                    }
                    if (!c.getPlayer().getCheatTracker().canAvatarSmega2()) {
                        c.getPlayer().dropMessage(6, "很抱歉為了防止刷廣,所以你每10秒只能用一次.");
                        break;
                    }
                    if (!c.getChannelServer().getMegaphoneMuteState()) {
                        final String message = slea.readMapleAsciiString();

                        if (message.length() > 65) {
                            break;
                        }
                        final StringBuilder sb = new StringBuilder();
                        addMedalString(c.getPlayer(), sb);
                        sb.append(c.getPlayer().getName());
                        sb.append(" : ");
                        sb.append(message);

                        final boolean ear = slea.readByte() != 0;

                        World.Broadcast.broadcastSmega(
                                MaplePacketCreator.serverNotice(
                                                11, c.getChannel(), sb.toString(), ear)
                                        .getBytes());

                        used = true;
                    } else {
                        c.getPlayer().dropMessage(5, "目前喇叭停止使用.");
                    }
                    break;
                }
            case 5074000:
                { // Skull Megaphone
                    if (c.getPlayer().getLevel() < 10) {
                        c.getPlayer().dropMessage(5, "必須等級10級以上才可以使用.");
                        break;
                    }
                    if (!c.getPlayer().getCheatTracker().canAvatarSmega2()) {
                        c.getPlayer().dropMessage(6, "很抱歉為了防止刷廣,所以你每10秒只能用一次.");
                        break;
                    }
                    if (!c.getChannelServer().getMegaphoneMuteState()) {
                        final String message = slea.readMapleAsciiString();

                        if (message.length() > 65) {
                            break;
                        }
                        final StringBuilder sb = new StringBuilder();
                        addMedalString(c.getPlayer(), sb);
                        sb.append(c.getPlayer().getName());
                        sb.append(" : ");
                        sb.append(message);

                        final boolean ear = slea.readByte() != 0;
                        if (c.getPlayer().isPlayer() && message.indexOf("幹") != -1
                                || message.indexOf("豬") != -1
                                || message.indexOf("笨") != -1
                                || message.indexOf("靠") != -1
                                || message.indexOf("腦包") != -1
                                || message.indexOf("腦") != -1
                                || message.indexOf("智障") != -1
                                || message.indexOf("白目") != -1
                                || message.indexOf("白吃") != -1) {
                            c.getPlayer().dropMessage("說髒話是不禮貌的，請勿說髒話。");
                            c.sendPacket(MaplePacketCreator.enableActions());
                            return;
                        }
                        World.Broadcast.broadcastSmega(
                                MaplePacketCreator.serverNotice(
                                                12, c.getChannel(), sb.toString(), ear)
                                        .getBytes());

                        used = true;
                    } else {
                        c.getPlayer().dropMessage(5, "目前喇叭停止使用.");
                    }
                    break;
                }
            case 5072000:
                { // Super Megaphone
                    if (c.getPlayer().getLevel() < 10) {
                        c.getPlayer().dropMessage(5, "必須要10等以上才能使用.");
                        break;
                    }
                    if (!c.getPlayer().getCheatTracker().canAvatarSmega2()) {
                        c.getPlayer().dropMessage(6, "很抱歉為了防止刷廣,所以你每10秒只能用一次.");
                        break;
                    }
                    if (!c.getChannelServer().getMegaphoneMuteState()) {
                        final String message = slea.readMapleAsciiString();

                        if (message.length() > 65) {
                            break;
                        }
                        final StringBuilder sb = new StringBuilder();
                        addMedalString(c.getPlayer(), sb);
                        sb.append(c.getPlayer().getName());
                        sb.append(" : ");
                        sb.append(message);

                        final boolean ear = slea.readByte() != 0;

                        World.Broadcast.broadcastSmega(
                                MaplePacketCreator.serverNotice(
                                                3, c.getChannel(), sb.toString(), ear)
                                        .getBytes());

                        used = true;
                    } else {
                        c.getPlayer().dropMessage(5, "目前喇叭停止使用.");
                    }
                    break;
                }
            case 5076000:
                { // Item Megaphone
                    if (c.getPlayer().getLevel() < 10) {
                        c.getPlayer().dropMessage(5, "必須等級10級以上才可以使用.");
                        break;
                    }
                    if (!c.getPlayer().getCheatTracker().canAvatarSmega2()) {
                        c.getPlayer().dropMessage(6, "很抱歉為了防止刷廣,所以你每10秒只能用一次.");
                        break;
                    }
                    if (!c.getChannelServer().getMegaphoneMuteState()) {
                        final String message = slea.readMapleAsciiString();

                        if (message.length() > 65) {
                            break;
                        }
                        final StringBuilder sb = new StringBuilder();
                        addMedalString(c.getPlayer(), sb);
                        sb.append(c.getPlayer().getName());
                        sb.append(" : ");
                        sb.append(message);

                        final boolean ear = slea.readByte() > 0;

                        IItem item = null;
                        if (slea.readByte() == 1) { // item
                            byte invType = (byte) slea.readInt();
                            byte pos = (byte) slea.readInt();
                            item =
                                    c.getPlayer()
                                            .getInventory(MapleInventoryType.getByType(invType))
                                            .getItem(pos);
                        }
                        World.Broadcast.broadcastSmega(
                                MaplePacketCreator.itemMegaphone(
                                                sb.toString(), ear, c.getChannel(), item)
                                        .getBytes());
                        used = true;
                    } else {
                        c.getPlayer().dropMessage(5, "目前喇叭停止使用.");
                    }
                    break;
                }
            case 5075000: // MapleTV Messenger
            case 5075001: // MapleTV Star Messenger
            case 5075002:
                { // MapleTV Heart Messenger
                    c.getPlayer()
                            .dropMessage(5, "There are no MapleTVs to broadcast the message to.");
                    break;
                }
            case 5075003:
            case 5075004:
            case 5075005:
                {
                    if (c.getPlayer().getLevel() < 10) {
                        c.getPlayer().dropMessage(5, "必須等級10級以上才可以使用.");
                        break;
                    }
                    int tvType = itemId % 10;
                    if (tvType == 3) {
                        slea.readByte(); // who knows
                    }
                    boolean ear =
                            tvType != 1
                                    && tvType != 2
                                    && slea.readByte() > 1; // for tvType 1/2, there is no byte.
                    MapleCharacter victim =
                            tvType == 1 || tvType == 4
                                    ? null
                                    : c.getChannelServer()
                                            .getPlayerStorage()
                                            .getCharacterByName(
                                                    slea.readMapleAsciiString()); // for tvType
                    // 4, there is
                    // no string.
                    if (tvType == 0 || tvType == 3) { // doesn't allow two
                        victim = null;
                    } else if (victim == null) {
                        c.getPlayer().dropMessage(1, "That character is not in the channel.");
                        break;
                    }
                    String message = slea.readMapleAsciiString();
                    World.Broadcast.broadcastSmega(
                            MaplePacketCreator.serverNotice(
                                            3,
                                            c.getChannel(),
                                            c.getPlayer().getName() + " : " + message,
                                            ear)
                                    .getBytes());
                    break;
                }
            case 5090100: // Wedding Invitation Card
            case 5090000:
                { // Note
                    final String sendTo = slea.readMapleAsciiString();
                    final String msg = slea.readMapleAsciiString();
                    c.getPlayer().sendNote(sendTo, msg);
                    used = true;
                    break;
                }
            case 5100000:
                { // Congratulatory Song
                    c.getPlayer()
                            .getMap()
                            .broadcastMessage(
                                    MTSCSPacket.playCashSong(5100000, c.getPlayer().getName()));
                    used = true;
                    break;
                }
            case 5190001:
            case 5190002:
            case 5190003:
            case 5190004:
            case 5190005:
            case 5190006:
            case 5190007:
            case 5190008:
            case 5190000:
                { // Pet Flags
                    final int uniqueid = (int) slea.readLong();
                    MaplePet pet = c.getPlayer().getPet(0);
                    int slo = 0;

                    if (pet == null) {
                        break;
                    }
                    if (pet.getUniqueId() != uniqueid) {
                        pet = c.getPlayer().getPet(1);
                        slo = 1;
                        if (pet != null) {
                            if (pet.getUniqueId() != uniqueid) {
                                pet = c.getPlayer().getPet(2);
                                slo = 2;
                                if (pet != null) {
                                    if (pet.getUniqueId() != uniqueid) {
                                        break;
                                    }
                                } else {
                                    break;
                                }
                            }
                        } else {
                            break;
                        }
                    }
                    PetFlag zz = PetFlag.getByAddId(itemId);
                    if (zz != null && !zz.check(pet.getFlags())) {
                        pet.setFlags(pet.getFlags() | zz.getValue());
                        c.sendPacket(
                                PetPacket.updatePet(
                                        pet,
                                        c.getPlayer()
                                                .getInventory(MapleInventoryType.CASH)
                                                .getItem((byte) pet.getInventoryPosition())));
                        c.sendPacket(MaplePacketCreator.enableActions());
                        c.sendPacket(MTSCSPacket.changePetFlag(uniqueid, true, zz.getValue()));
                        used = true;
                    }
                    break;
                }
            case 5191001:
            case 5191002:
            case 5191003:
            case 5191004:
            case 5191000:
                { // Pet Flags
                    final int uniqueid = (int) slea.readLong();
                    MaplePet pet = c.getPlayer().getPet(0);
                    int slo = 0;

                    if (pet == null) {
                        break;
                    }
                    if (pet.getUniqueId() != uniqueid) {
                        pet = c.getPlayer().getPet(1);
                        slo = 1;
                        if (pet != null) {
                            if (pet.getUniqueId() != uniqueid) {
                                pet = c.getPlayer().getPet(2);
                                slo = 2;
                                if (pet != null) {
                                    if (pet.getUniqueId() != uniqueid) {
                                        break;
                                    }
                                } else {
                                    break;
                                }
                            }
                        } else {
                            break;
                        }
                    }
                    PetFlag zz = PetFlag.getByDelId(itemId);
                    if (zz != null && zz.check(pet.getFlags())) {
                        pet.setFlags(pet.getFlags() - zz.getValue());
                        c.sendPacket(
                                PetPacket.updatePet(
                                        pet,
                                        c.getPlayer()
                                                .getInventory(MapleInventoryType.CASH)
                                                .getItem((byte) pet.getInventoryPosition())));
                        c.sendPacket(MaplePacketCreator.enableActions());
                        c.sendPacket(MTSCSPacket.changePetFlag(uniqueid, false, zz.getValue()));
                        used = true;
                    }
                    break;
                }
            case 5170000:
                { // Pet name change
                    /*                final int uniqueid = (int) slea.readLong();*/
                    MaplePet pet = c.getPlayer().getPet(0);
                    int slo = 0;

                    if (pet == null) {
                        break;
                    }
                    /*                if (pet.getUniqueId() != uniqueid) {
                    pet = c.getPlayer().getPet(1);
                    slo = 1;
                    if (pet != null) {
                    if (pet.getUniqueId() != uniqueid) {
                    pet = c.getPlayer().getPet(2);
                    slo = 2;
                    if (pet != null) {
                    if (pet.getUniqueId() != uniqueid) {
                    break;
                    }
                    } else {
                    break;
                    }
                    }
                    } else {
                    break;
                    }
                    }*/
                    String nName = slea.readMapleAsciiString();
                    /*                for (String z : GameConstants.getReserved()) {
                    if (pet.getName().indexOf(z) != -1 || nName.indexOf(z) != -1) {
                    break;
                    }
                    }*/
                    //                if (MapleCharacterUtil.canChangePetName(nName)) {
                    pet.setName(nName);
                    c.sendPacket(
                            PetPacket.updatePet(
                                    pet,
                                    c.getPlayer()
                                            .getInventory(MapleInventoryType.CASH)
                                            .getItem((byte) pet.getInventoryPosition())));
                    c.sendPacket(MaplePacketCreator.enableActions());
                    c.getPlayer()
                            .getMap()
                            .broadcastMessage(MTSCSPacket.changePetName(c.getPlayer(), nName, slo));
                    used = true;
                    //                }
                    break;
                }
            case 5240000:
            case 5240001:
            case 5240002:
            case 5240003:
            case 5240004:
            case 5240005:
            case 5240006:
            case 5240007:
            case 5240008:
            case 5240009:
            case 5240010:
            case 5240011:
            case 5240012:
            case 5240013:
            case 5240014:
            case 5240015:
            case 5240016:
            case 5240017:
            case 5240018:
            case 5240019:
            case 5240020:
            case 5240021:
            case 5240022:
            case 5240023:
            case 5240024:
            case 5240025:
            case 5240026:
            case 5240027:
            case 5240028:
                { // Pet food
                    MaplePet pet = c.getPlayer().getPet(0);

                    if (pet == null) {
                        break;
                    }
                    if (!pet.canConsume(itemId)) {
                        pet = c.getPlayer().getPet(1);
                        if (pet != null) {
                            if (!pet.canConsume(itemId)) {
                                pet = c.getPlayer().getPet(2);
                                if (pet != null) {
                                    if (!pet.canConsume(itemId)) {
                                        break;
                                    }
                                } else {
                                    break;
                                }
                            }
                        } else {
                            break;
                        }
                    }
                    final byte petindex = c.getPlayer().getPetIndex(pet);
                    pet.setFullness(100);
                    if (pet.getCloseness() < 30000) {
                        if (pet.getCloseness() + 100 > 30000) {
                            pet.setCloseness(30000);
                        } else {
                            pet.setCloseness(pet.getCloseness() + 100);
                        }
                        if (pet.getCloseness()
                                >= GameConstants.getClosenessNeededForLevel(pet.getLevel() + 1)) {
                            pet.setLevel(pet.getLevel() + 1);
                            c.sendPacket(
                                    PetPacket.showOwnPetLevelUp(c.getPlayer().getPetIndex(pet)));
                            c.getPlayer()
                                    .getMap()
                                    .broadcastMessage(
                                            PetPacket.showPetLevelUp(c.getPlayer(), petindex));
                        }
                    }
                    c.sendPacket(
                            PetPacket.updatePet(
                                    pet,
                                    c.getPlayer()
                                            .getInventory(MapleInventoryType.CASH)
                                            .getItem(pet.getInventoryPosition())));
                    c.getPlayer()
                            .getMap()
                            .broadcastMessage(
                                    c.getPlayer(),
                                    PetPacket.commandResponse(
                                            c.getPlayer().getId(), (byte) 1, petindex, true, true),
                                    true);
                    used = true;
                    break;
                }
            case 5230000:
                { // owl of minerva
                    final int itemSearch = slea.readInt();
                    final List<HiredMerchant> hms = c.getChannelServer().searchMerchant(itemSearch);
                    if (hms.size() > 0) {
                        c.sendPacket(MaplePacketCreator.getOwlSearched(itemSearch, hms));
                        used = true;
                    } else {
                        c.getPlayer().dropMessage(1, "找不到物品");
                    }
                    break;
                }
            case 5281001: // idk, but probably
            case 5280001: // Gas Skill
            case 5281000:
                { // Passed gas
                    Rectangle bounds =
                            new Rectangle(
                                    (int) c.getPlayer().getPosition().getX(),
                                    (int) c.getPlayer().getPosition().getY(),
                                    1,
                                    1);
                    MapleMist mist = new MapleMist(bounds, c.getPlayer());
                    c.getPlayer().getMap().spawnMist(mist, 10000, true);
                    c.getPlayer()
                            .getMap()
                            .broadcastMessage(
                                    MaplePacketCreator.getChatText(
                                            c.getPlayer().getId(), "Oh no, I farted!", false, 1));
                    c.sendPacket(MaplePacketCreator.enableActions());
                    used = true;
                    break;
                }
            case 5370000:
                { // Chalkboard
                    if (c.getPlayer().getMapId() / 1000000 == 109) {
                        c.getPlayer().dropMessage(1, "請勿在活動地圖使用黑板");
                    } else {
                        c.getPlayer().setChalkboard(slea.readMapleAsciiString());
                    }
                    break;
                }
            case 5370001:
                { // BlackBoard
                    if (c.getPlayer().getMapId() / 1000000 == 109) {
                        c.getPlayer().dropMessage(1, "請勿在活動地圖使用黑板");
                    } else {
                        c.getPlayer().setChalkboard(slea.readMapleAsciiString());
                    }
                    break;
                }
            case 5390000: // Diablo Messenger
            case 5390001: // Cloud 9 Messenger
            case 5390002: // Loveholic Messenger
            case 5390003: // New Year Megassenger 1
            case 5390004: // New Year Megassenger 2
            case 5390005: // Cute Tiger Messenger
            case 5390006:
                { // Tiger Roar's Messenger
                    if (c.getPlayer().getLevel() < 10) {
                        c.getPlayer().dropMessage(5, "必須等級10級以上才可以使用.");
                        break;
                    }
                    if (!c.getPlayer().getCheatTracker().canAvatarSmega2()) {
                        c.getPlayer().dropMessage(6, "很抱歉為了防止刷廣,所以你每10秒只能用一次.");
                        break;
                    }
                    if (!c.getChannelServer().getMegaphoneMuteState()) {
                        final String text = slea.readMapleAsciiString();
                        if (text.length() > 55) {
                            break;
                        }
                        final boolean ear = slea.readByte() != 0;
                        World.Broadcast.broadcastSmega(
                                MaplePacketCreator.getAvatarMega(
                                                c.getPlayer(), c.getChannel(), itemId, text, ear)
                                        .getBytes());
                        used = true;
                    }
                    break;
                }

            case 5450000:
                { // Mu Mu the Travelling Merchant
                    MapleShopFactory.getInstance().getShop(61).sendShop(c);
                    // used = true;
                    break;
                }
            case 5152100:
            case 5152101:
            case 5152102:
            case 5152103:
            case 5152104:
            case 5152105:
            case 5152106:
            case 5152107:
                {
                    int color_num = (itemId - 5152100);
                    int current =
                            (c.getPlayer().getFace() % 100)
                                    + ((int) (c.getPlayer().getFace() / 1000)) * 1000;
                    int newface = current + (color_num * 100);
                    if (MapleItemInformationProvider.getInstance().faceExists(newface)) {
                        c.getPlayer().setFace(newface);
                        c.getPlayer().updateSingleStat(MapleStat.FACE, newface);
                        c.getPlayer().equipChanged();
                        used = true;
                    } else {
                        c.getPlayer().dropMessage(6, "該臉部沒有此瞳孔顏色唷");
                    }
                    break;
                }
            default:
                if (itemId / 10000 == 512) {
                    final MapleItemInformationProvider ii =
                            MapleItemInformationProvider.getInstance();
                    // final String msg = ii.getMsg(itemId).replaceFirst("%s",
                    // c.getPlayer().getName()).replaceFirst("%s", slea.readMapleAsciiString());
                    final String msg = slea.readMapleAsciiString();
                    c.getPlayer().getMap().startMapEffect(msg, itemId);

                    final int buff = ii.getStateChangeItem(itemId);
                    if (buff != 0) {
                        for (MapleCharacter mChar :
                                c.getPlayer().getMap().getCharactersThreadsafe()) {
                            ii.getItemEffect(buff).applyTo(mChar);
                        }
                    }
                    used = true;
                } else if (itemId / 10000 == 510) {
                    c.getPlayer().getMap().startJukebox(c.getPlayer().getName(), itemId);
                    used = true;
                } else if (itemId / 10000 == 520) {
                    final int mesars = MapleItemInformationProvider.getInstance().getMeso(itemId);
                    if (mesars > 0 && c.getPlayer().getMeso() < (Integer.MAX_VALUE - mesars)) {
                        used = true;
                        if (ThreadLocalRandom.current().nextDouble() > 0.1) {
                            final int gainmes = Randomizer.nextInt(mesars);
                            c.getPlayer().gainMeso(gainmes, false);
                            c.sendPacket(MTSCSPacket.sendMesobagSuccess(gainmes));
                        } else {
                            c.sendPacket(MTSCSPacket.sendMesobagFailed());
                        }
                    }
                    /*                } else if (itemId / 10000 == 562) {
                    UseSkillBook(slot, itemId, c, c.getPlayer()); //this should handle removing*/
                } else if (itemId / 10000 == 553) {
                    UseRewardItem(slot, itemId, c, c.getPlayer()); // this too
                } else {
                    log.warn("Unhandled CS item : {}", itemId);
                    log.debug("{}", slea.toString(true));
                }
                break;
        }

        if (used) {
            MapleInventoryManipulator.removeFromSlot(
                    c, MapleInventoryType.CASH, slot, (short) 1, false, true);
        }
        c.sendPacket(MaplePacketCreator.enableActions());
        if (cc) {
            if (!c.getPlayer().isAlive()
                    || c.getPlayer().getEventInstance() != null
                    || FieldLimitType.ChannelSwitch.check(c.getPlayer().getMap().getFieldLimit())) {
                c.getPlayer().dropMessage(1, "Auto change channel failed.");
                return;
            }
            c.getPlayer().dropMessage(5, "Auto changing channels. Please wait.");
            c.getPlayer()
                    .changeChannel(
                            c.getChannel() == ChannelServer.getChannelCount()
                                    ? 1
                                    : (c.getChannel() + 1));
        }
    }

    public static final void Pickup_Player(
            final SeekableLittleEndianAccessor slea, MapleClient c, final MapleCharacter chr) {
        if (chr == null) {
            return;
        }
        if (c.getPlayer().getPlayerShop() != null
                || c.getPlayer().getConversation() > 0
                || c.getPlayer().getTrade() != null) { // hack
            return;
        }
        chr.updateTick(slea.readInt());
        slea.skip(1); // [4] Seems to be tickcount, [1] always 0
        final Point Client_Reportedpos = slea.readPos();
        final MapleMapObject ob =
                chr.getMap().getMapObject(slea.readInt(), MapleMapObjectType.ITEM);

        if (ob == null) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        final MapleMapItem mapitem = (MapleMapItem) ob;
        final Lock lock = mapitem.getLock();
        lock.lock();
        try {
            if (mapitem.isPickedUp()) {
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            if (mapitem.getOwner() != chr.getId()
                    && ((!mapitem.isPlayerDrop() && mapitem.getDropType() == 0)
                            || (mapitem.isPlayerDrop() && chr.getMap().getEverlast()))) {
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            if (!mapitem.isPlayerDrop()
                    && mapitem.getDropType() == 1
                    && mapitem.getOwner() != chr.getId()
                    && (chr.getParty() == null
                            || chr.getParty().getMemberById(mapitem.getOwner()) == null)) {
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            final double Distance = Client_Reportedpos.distanceSq(mapitem.getPosition());
            if (Distance > 2500) {
                chr.getCheatTracker()
                        .registerOffense(CheatingOffense.ITEMVAC_CLIENT, String.valueOf(Distance));
            } else if (chr.getPosition().distanceSq(mapitem.getPosition()) > 640000.0) {
                chr.getCheatTracker().registerOffense(CheatingOffense.ITEMVAC_SERVER);
            }
            if (mapitem.getMeso() > 0) {
                if (chr.getParty() != null && mapitem.getOwner() != chr.getId()) {
                    final Collection<MaplePartyCharacter> members = chr.getParty().getMembers();
                    final List<MapleCharacter> toGive = new ArrayList<>(members.size());
                    for (MaplePartyCharacter z : members) {
                        MapleCharacter m = chr.getMap().getCharacterById(z.getId());
                        if (m != null) {
                            toGive.add(m);
                        }
                    }
                    final int size = toGive.size();
                    if (size > 0) {
                        final int baseShare = mapitem.getMeso() / size;
                        final int bonusShare = (int) (mapitem.getMeso() / 20.0);
                        for (final MapleCharacter m : toGive) {
                            m.gainMeso(
                                    baseShare + (m.getStat().hasPartyBonus ? bonusShare : 0),
                                    true,
                                    true);
                        }
                    }
                } else {
                    chr.gainMeso(mapitem.getMeso(), true, true);
                }
                removeItem(chr, mapitem, ob);
            } else {
                if (MapleItemInformationProvider.getInstance()
                        .isPickupBlocked(mapitem.getItem().getItemId())) {
                    c.sendPacket(MaplePacketCreator.enableActions());
                    c.getPlayer().dropMessage(5, "This item cannot be picked up.");
                } else if (useItem(c, mapitem.getItemId())) {
                    removeItem(c.getPlayer(), mapitem, ob);
                } else if (MapleInventoryManipulator.checkSpace(
                        c,
                        mapitem.getItem().getItemId(),
                        mapitem.getItem().getQuantity(),
                        mapitem.getItem().getOwner())) {
                    if (mapitem.getItem().getQuantity() >= 50
                            && ItemConstants.ItemTypes.isUpgradeScroll(
                                    mapitem.getItem().getItemId())) {
                        c.setMonitored(true); // hack check
                    }
                    if (MapleInventoryManipulator.addFromDrop(
                            c,
                            mapitem.getItem(),
                            true,
                            mapitem.getDropper() instanceof MapleMonster)) {
                        removeItem(chr, mapitem, ob);
                    }
                } else {
                    c.sendPacket(MaplePacketCreator.getInventoryFull());
                    c.sendPacket(MaplePacketCreator.getShowInventoryFull());
                    c.sendPacket(MaplePacketCreator.enableActions());
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public static final void Pickup_Pet(
            final SeekableLittleEndianAccessor slea,
            final MapleClient c,
            final MapleCharacter chr) {
        if (chr == null) {
            return;
        }
        final byte petz = (byte) c.getPlayer().getPetIndex((int) slea.readLong /*Int*/());
        final MaplePet pet = chr.getPet(petz);
        slea.skip(1); // [4] Zero, [4] Seems to be tickcount, [1] Always zero
        chr.updateTick(slea.readInt());
        final Point Client_Reportedpos = slea.readPos();
        final MapleMapObject ob =
                chr.getMap().getMapObject(slea.readInt(), MapleMapObjectType.ITEM);

        if (ob == null || pet == null) {
            return;
        }
        final MapleMapItem mapitem = (MapleMapItem) ob;
        final Lock lock = mapitem.getLock();
        lock.lock();
        try {
            if (mapitem.isPickedUp()) {
                c.sendPacket(MaplePacketCreator.getInventoryFull());
                return;
            }
            if (mapitem.getOwner() != chr.getId() && mapitem.isPlayerDrop()) {
                return;
            }
            if (mapitem.getOwner() != chr.getId()
                    && ((!mapitem.isPlayerDrop() && mapitem.getDropType() == 0)
                            || (mapitem.isPlayerDrop() && chr.getMap().getEverlast()))) {
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            if (!mapitem.isPlayerDrop()
                    && mapitem.getDropType() == 1
                    && mapitem.getOwner() != chr.getId()
                    && (chr.getParty() == null
                            || chr.getParty().getMemberById(mapitem.getOwner()) == null)) {
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            final double Distance = Client_Reportedpos.distanceSq(mapitem.getPosition());
            //            if (Distance > 10000 && (mapitem.getMeso() > 0 || mapitem.getItemId() !=
            // 4001025)) {
            //
            // chr.getCheatTracker().registerOffense(CheatingOffense.PET_ITEMVAC_CLIENT,
            // String.valueOf(Distance));
            //            } else if (pet.getPos().distanceSq(mapitem.getPosition()) > 640000.0) {
            //
            // chr.getCheatTracker().registerOffense(CheatingOffense.PET_ITEMVAC_SERVER);
            //            }

            if (mapitem.getMeso() > 0) {
                if (chr.getParty() != null && mapitem.getOwner() != chr.getId()) {
                    final List<MapleCharacter> toGive = new ArrayList<>();
                    final int splitMeso = mapitem.getMeso() * 40 / 100;
                    for (MaplePartyCharacter z : chr.getParty().getMembers()) {
                        MapleCharacter m = chr.getMap().getCharacterById(z.getId());
                        if (m != null && m.getId() != chr.getId()) {
                            toGive.add(m);
                        }
                    }
                    final int toGiveSize = toGive.size();
                    if (toGiveSize > 0) {
                        final int perMember = splitMeso / toGiveSize;
                        for (final MapleCharacter m : toGive) {
                            m.gainMeso(
                                    perMember
                                            + (m.getStat().hasPartyBonus
                                                    ? (int) (mapitem.getMeso() / 20.0)
                                                    : 0),
                                    true);
                        }
                    }
                    chr.gainMeso(mapitem.getMeso() - splitMeso, true);
                } else {
                    chr.gainMeso(mapitem.getMeso(), true);
                }
                removeItem_Pet(chr, mapitem, petz);
            } else {
                if (MapleItemInformationProvider.getInstance().isPickupBlocked(mapitem.getItemId())
                        || mapitem.getItemId() / 10000 == 291) {
                    c.sendPacket(MaplePacketCreator.enableActions());
                } else if (useItem(c, mapitem.getItemId())) {
                    removeItem_Pet(chr, mapitem, petz);
                } else if (MapleInventoryManipulator.checkSpace(
                        c,
                        mapitem.getItemId(),
                        mapitem.getItem().getQuantity(),
                        mapitem.getItem().getOwner())) {
                    if (mapitem.getItem().getQuantity() >= 50 && mapitem.getItemId() == 2340000) {
                        c.setMonitored(true); // hack check
                    }
                    MapleInventoryManipulator.addFromDrop(
                            c,
                            mapitem.getItem(),
                            true,
                            mapitem.getDropper() instanceof MapleMonster);
                    removeItem_Pet(chr, mapitem, petz);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public static final boolean useItem(final MapleClient c, final int id) {
        if (!ItemConstants.ItemTypes.isUse(id)) {
            return false;
        }
        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        final byte consumeval = ii.isConsumeOnPickup(id);
        if (consumeval <= 0) {
            return false;
        }
        final MapleCharacter player = c.getPlayer();
        if (consumeval == 2 && player.getParty() != null) {
            final Collection<MaplePartyCharacter> members = player.getParty().getMembers();
            final MapleMap map = player.getMap();
            for (MaplePartyCharacter member : members) {
                final MapleCharacter chr = map.getCharacterById(member.getId());
                if (chr != null) {
                    ii.getItemEffect(id).applyTo(chr);
                }
            }
        } else {
            ii.getItemEffect(id).applyTo(player);
        }
        c.sendPacket(MaplePacketCreator.getShowItemGain(id, (byte) 1));
        return true;
    }

    public static final void removeItem_Pet(
            final MapleCharacter chr, final MapleMapItem mapitem, int pet) {
        mapitem.setPickedUp(true);
        chr.getMap()
                .broadcastMessage(
                        MaplePacketCreator.removeItemFromMap(
                                mapitem.getObjectId(), 5, chr.getId(), pet),
                        mapitem.getPosition());
        chr.getMap().removeMapObject(mapitem);
        if (mapitem.isRandDrop()) {
            chr.getMap().spawnRandDrop();
        }
    }

    private static final void removeItem(
            final MapleCharacter chr, final MapleMapItem mapitem, final MapleMapObject ob) {
        mapitem.setPickedUp(true);
        chr.getMap()
                .broadcastMessage(
                        MaplePacketCreator.removeItemFromMap(mapitem.getObjectId(), 2, chr.getId()),
                        mapitem.getPosition());
        chr.getMap().removeMapObject(ob);
        if (mapitem.isRandDrop()) {
            chr.getMap().spawnRandDrop();
        }
    }

    private static final void addMedalString(final MapleCharacter c, final StringBuilder sb) {
        final IItem medal = c.getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -21);
        if (medal != null) { // Medal
            sb.append("<");
            sb.append(MapleItemInformationProvider.getInstance().getName(medal.getItemId()));
            sb.append("> ");
        }
    }

    private static final boolean getIncubatedItems(MapleClient c) {
        final MapleCharacter player = c.getPlayer();
        if (player.getInventory(MapleInventoryType.EQUIP).getNumFreeSlot() < 2
                || player.getInventory(MapleInventoryType.USE).getNumFreeSlot() < 2
                || player.getInventory(MapleInventoryType.SETUP).getNumFreeSlot() < 2) {
            player.dropMessage(5, "Please make room in your inventory.");
            return false;
        }
        final int[] ids = {
            2430091, 2430092, 2430093, 2430101, 2430102, // mounts
            2340000, // rares
            1152000, 1152001, 1152004, 1152005, 1152006, 1152007,
            1152008, // toenail only comes when db is out.
            1000040, 1102246, 1082276, 1050169, 1051210, 1072447, 1442106, // blizzard
            3010019, // chairs
            1001060, 1002391, 1102004, 1050039, 1102040, 1102041, 1102042, 1102043, // equips
            1082145, 1082146, 1082147, 1082148, 1082149, 1082150, // wg
            2043704, 2040904, 2040409, 2040307, 2041030, 2040015, 2040109, 2041035, 2041036,
            2040009, 2040511, 2040408, 2043804, 2044105, 2044903, 2044804, 2043009, 2043305,
            2040610, 2040716, 2041037, 2043005, 2041032, 2040305, // scrolls
            2040211, 2040212, 1022097, // dragon glasses
            2049000, 2049001, 2049002, 2049003, // clean slate
            1012058, 1012059, 1012060, 1012061, // pinocchio nose msea only.
            1332100, 1382058, 1402073, 1432066, 1442090, 1452058, 1462076, 1472069, 1482051,
            1492024, 1342009, // durability weapons level 105
            2049400, 2049401, 2049301
        };
        // out of 1000
        final int[] chances = {
            100, 100, 100, 100, 100, 1, 10, 10, 10, 10, 10, 10, 10, 5, 5, 5, 5, 5, 5, 5, 2, 10, 10,
            10, 10, 10, 10, 10, 10, 5, 5, 5, 5, 5, 5, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10,
            10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 5, 5, 10, 10, 10, 10, 10, 5, 5, 5,
            5, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 2, 1, 2
        };
        final int len = ids.length;
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int z = rnd.nextInt(len);
        while (chances[z] < rnd.nextInt(1000)) {
            z = rnd.nextInt(len);
        }
        int z_2 = rnd.nextInt(len);
        while (z_2 == z || chances[z_2] < rnd.nextInt(1000)) {
            z_2 = rnd.nextInt(len);
        }
        c.sendPacket(MaplePacketCreator.getPeanutResult(ids[z], (short) 1, ids[z_2], (short) 1));
        return MapleInventoryManipulator.addById(c, ids[z], (short) 1)
                && MapleInventoryManipulator.addById(c, ids[z_2], (short) 1);
    }

    public static final void OwlMinerva(
            final SeekableLittleEndianAccessor slea, final MapleClient c) {
        final byte slot = (byte) slea.readShort();
        final int itemid = slea.readInt();
        final MapleCharacter player = c.getPlayer();
        final IItem toUse = player.getInventory(MapleInventoryType.USE).getItem(slot);
        if (toUse != null
                && toUse.getQuantity() > 0
                && toUse.getItemId() == itemid
                && itemid == 2310000) {
            final int itemSearch = slea.readInt();
            final List<HiredMerchant> hms = c.getChannelServer().searchMerchant(itemSearch);
            if (hms != null && !hms.isEmpty()) {
                c.sendPacket(MaplePacketCreator.getOwlSearched(itemSearch, hms));
                MapleInventoryManipulator.removeById(
                        c, MapleInventoryType.USE, itemid, 1, true, false);
            } else {
                player.dropMessage(1, "找不到物品");
            }
        }
        c.sendPacket(MaplePacketCreator.enableActions());
    }

    public static final void Owl(final SeekableLittleEndianAccessor slea, final MapleClient c) {
        if (c.getPlayer().haveItem(5230000, 1, true, true)
                || c.getPlayer().haveItem(2310000, 1, true, true)) {
            if (c.getPlayer().getMapId() >= 910000000 && c.getPlayer().getMapId() <= 910000022) {
                c.sendPacket(MaplePacketCreator.getOwlOpen());
                log.debug("TEST");
            } else {
                c.getPlayer().dropMessage(5, "只能在自由市場使用");
                c.sendPacket(MaplePacketCreator.enableActions());
            }
        }
    }

    public static final int OWL_ID = 2; // don't change. 0 = owner ID, 1 = store ID, 2 = object ID

    public static final void UseSkillBook(
            final SeekableLittleEndianAccessor slea,
            final MapleClient c,
            final MapleCharacter chr) {
        slea.skip(4);
        final byte slot = (byte) slea.readShort();
        final int itemId = slea.readInt();
        final IItem toUse = chr.getInventory(MapleInventoryType.USE).getItem(slot);

        if (toUse == null || toUse.getQuantity() < 1 || toUse.getItemId() != itemId) {
            return;
        }
        final Map<String, Integer> skilldata =
                MapleItemInformationProvider.getInstance().getSkillStats(toUse.getItemId());
        if (skilldata == null) { // Hacking or used an unknown item
            return;
        }
        boolean canuse = false, success = false;
        int skill = 0, maxlevel = 0;

        final Integer successRateObj = skilldata.get("success");
        final Integer reqSkillLevelObj = skilldata.get("reqSkillLevel");
        final Integer masterLevelObj = skilldata.get("masterLevel");
        if (successRateObj == null || reqSkillLevelObj == null || masterLevelObj == null) {
            return;
        }
        final int SuccessRate = successRateObj;
        final int ReqSkillLevel = reqSkillLevelObj;
        final int MasterLevel = masterLevelObj;

        final int job = chr.getJob();
        byte i = 0;
        for (; ; ) {
            final Integer CurrentLoopedSkillId = skilldata.get("skillid" + i);
            i++;
            if (CurrentLoopedSkillId == null) {
                break; // End of data
            }
            if (CurrentLoopedSkillId / 10000 == job) {
                final ISkill CurrSkillData = SkillFactory.getSkill(CurrentLoopedSkillId);
                if (chr.getSkillLevel(CurrSkillData) >= ReqSkillLevel
                        && chr.getMasterLevel(CurrSkillData) < MasterLevel) {
                    canuse = true;
                    if (SuccessRate != 0 && Randomizer.nextInt(99) <= SuccessRate) {
                        success = true;
                        chr.changeSkillLevel(
                                CurrSkillData,
                                chr.getSkillLevel(CurrSkillData),
                                (byte) MasterLevel);
                    } else {
                        success = false;
                    }
                    MapleInventoryManipulator.removeFromSlot(
                            c, MapleInventoryType.USE, slot, (short) 1, false);
                    break;
                } else { // Failed to meet skill requirements
                    canuse = false;
                }
            }
        }
        c.sendPacket(MaplePacketCreator.useSkillBook(chr, skill, maxlevel, canuse, success));
    }

    public static final void OwlWarp(final SeekableLittleEndianAccessor slea, final MapleClient c) {
        c.sendPacket(MaplePacketCreator.enableActions());
        final MapleCharacter player = c.getPlayer();
        final int playerMapId = player.getMapId();
        if (playerMapId < 910000000 || playerMapId > 910000022 || player.getPlayerShop() != null) {
            return;
        }
        final int id = slea.readInt();
        final int map = slea.readInt();
        if (map < 910000001 || map > 910000022) {
            return;
        }
        final MapleMap mapp = c.getChannelServer().getMapFactory().getMap(map);
        player.changeMap(mapp, mapp.getPortal(0));
        HiredMerchant merchant = null;
        switch (OWL_ID) {
            case 0:
                {
                    final List<MapleMapObject> objects = mapp.getAllHiredMerchantsThreadsafe();
                    for (int i = 0, size = objects.size(); i < size; i++) {
                        final MapleMapObject ob = objects.get(i);
                        if (ob instanceof HiredMerchant merch && merch.getOwnerId() == id) {
                            merchant = merch;
                            break;
                        }
                    }
                    break;
                }
            case 1:
                {
                    final List<MapleMapObject> objects = mapp.getAllHiredMerchantsThreadsafe();
                    for (int i = 0, size = objects.size(); i < size; i++) {
                        final MapleMapObject ob = objects.get(i);
                        if (ob instanceof HiredMerchant merch && merch.getStoreId() == id) {
                            merchant = merch;
                            break;
                        }
                    }
                    break;
                }
            default:
                {
                    final MapleMapObject ob =
                            mapp.getMapObject(id, MapleMapObjectType.HIRED_MERCHANT);
                    if (ob instanceof HiredMerchant hiredMerchant) {
                        merchant = hiredMerchant;
                    }
                    break;
                }
        }
        if (merchant == null) {
            player.dropMessage(1, "This shop is in maintenance, please come by later.");
            return;
        }
        if (merchant.isOwner(player)) {
            merchant.setOpen(false);
            merchant.removeAllVisitors((byte) 16, (byte) 0);
            player.setPlayerShop(merchant);
            c.sendPacket(PlayerShopPacket.getHiredMerch(player, merchant, false));
            return;
        }
        if (!merchant.isOpen() || !merchant.isAvailable()) {
            player.dropMessage(1, "This shop is in maintenance, please come by later.");
        } else if (merchant.getFreeSlot() == -1) {
            player.dropMessage(
                    1, "This shop has reached it's maximum capacity, please come by later.");
        } else if (merchant.isInBlackList(player.getName())) {
            player.dropMessage(1, "You have been banned from this store.");
        } else {
            player.setPlayerShop(merchant);
            merchant.addVisitor(player);
            c.sendPacket(PlayerShopPacket.getHiredMerch(player, merchant, false));
        }
    }

    public static void owlse(final MapleClient c, int point, int itemid) {
        final int itemSearch = itemid;

        final List<HiredMerchant> hms = new ArrayList<>();
        for (ChannelServer cserv : ChannelServer.getAllInstances()) {
            final List<HiredMerchant> found = cserv.searchMerchant(itemSearch);
            if (found != null && !found.isEmpty()) {
                hms.addAll(found);
            }
        }

        if (!hms.isEmpty()) {
            final MapleCharacter chr = c.getPlayer();
            if (chr.haveItem(5230000, 1)) {
                MapleInventoryManipulator.removeById(
                        c, MapleInventoryType.CASH, 5230000, 1, true, false);
            } else if (chr.getCSPoints(point) >= 5) {
                chr.modifyCSPoints(point, -5, true);
            } else {
                chr.dropMessage(1, "點數不足，無法查詢！");
                if (NPCScriptManager.getInstance().getCM(c) != null) {
                    NPCScriptManager.getInstance().dispose(c);
                    c.sendPacket(MaplePacketCreator.enableActions());
                }
                return;
            }
            if (NPCScriptManager.getInstance().getCM(c) != null) {
                NPCScriptManager.getInstance().dispose(c);
            }
            c.sendPacket(MaplePacketCreator.getOwlSearched(itemSearch, hms));
        } else {
            if (NPCScriptManager.getInstance().getCM(c) != null) {
                NPCScriptManager.getInstance().dispose(c);
                c.sendPacket(MaplePacketCreator.enableActions());
            }
            c.getPlayer().dropMessage(1, "找不到物品");
        }
    }
}
