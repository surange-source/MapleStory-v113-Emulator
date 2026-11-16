package handling.channel.handler;

import client.ISkill;
import client.MapleBuffStat;
import client.MapleCharacter;
import client.PlayerStats;
import client.SkillFactory;
import client.anticheat.CheatTracker;
import client.anticheat.CheatingOffense;
import client.inventory.IItem;
import client.inventory.MapleInventoryType;
import client.status.MonsterStatus;
import client.status.MonsterStatusEffect;
import constants.GameConstants;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import server.MapleStatEffect;
import server.Randomizer;
import server.Timer.MapTimer;
import server.life.Element;
import server.life.MapleMonster;
import server.life.MapleMonsterStats;
import server.maps.MapleMap;
import server.maps.MapleMapItem;
import server.maps.MapleMapObject;
import server.maps.MapleMapObjectType;
import tools.AttackPair;
import tools.MaplePacketCreator;
import tools.Pair;
import tools.data.input.LittleEndianAccessor;

public class DamageParse {

    private static final int[] charges = {1211005, 1211006};

    public static void applyAttack(
            final AttackInfo attack,
            final ISkill theSkill,
            final MapleCharacter player,
            int attackCount,
            final double maxDamagePerMonster,
            final MapleStatEffect effect,
            final AttackType attack_type) {
        if (!player.isAlive()) {
            player.getCheatTracker().registerOffense(CheatingOffense.ATTACKING_WHILE_DEAD);
            return;
        }
        final int skillId = attack.skill();
        final List<AttackPair> allDamage = attack.allDamage();
        final boolean adminOnly = player.getClient().getChannelServer().isAdminOnly();
        final PlayerStats stats = player.getStat();

        // Pre-load venom skills/effects if relevant (used later in skill-specific case)
        final ISkill venomSkill1 = SkillFactory.getSkill(4120005);
        final ISkill venomSkill2 = SkillFactory.getSkill(4220005);
        final ISkill venomSkill3 = SkillFactory.getSkill(4340001);
        final int venomLevel1 = player.getSkillLevel(venomSkill1);
        final int venomLevel2 = player.getSkillLevel(venomSkill2);
        final int venomLevel3 = player.getSkillLevel(venomSkill3);
        final MapleStatEffect venomEffect1 =
                venomLevel1 > 0 ? venomSkill1.getEffect(venomLevel1) : null;
        final MapleStatEffect venomEffect2 =
                venomLevel2 > 0 ? venomSkill2.getEffect(venomLevel2) : null;
        final MapleStatEffect venomEffect3 =
                venomLevel3 > 0 ? venomSkill3.getEffect(venomLevel3) : null;

        if (attack.real()) {
            player.getCheatTracker().checkAttack(skillId, attack.lastAttackTickCount());
        }
        if (skillId != 0) {
            if (effect == null) {
                player.getClient().sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            if (GameConstants.isMulungSkill(skillId)) {
                if (player.getMapId() / 10000 != 92502) {
                    return;
                } else {
                    player.mulung_EnergyModify(false);
                }
            }
            if (GameConstants.isPyramidSkill(skillId)) {
                if (player.getMapId() / 1000000 != 926) {
                    return;
                } else {
                    if (player.getPyramidSubway() == null
                            || !player.getPyramidSubway().onSkillUse(player)) {
                        return;
                    }
                }
            }
            if (attack.targets() > effect.getMobCount()) {
                player.getCheatTracker().registerOffense(CheatingOffense.MISMATCHING_BULLETCOUNT);
                return;
            }
        }
        if (attack.hits() > attackCount) {
            if (skillId != 4211006) {
                player.getCheatTracker().registerOffense(CheatingOffense.MISMATCHING_BULLETCOUNT);
                return;
            }
        }
        if (attack.hits() > 0 && attack.targets() > 0) {
            if (!stats.checkEquipDurabilitys(player, -1)) {
                player.dropMessage(
                        5, "An item has run out of durability but has no inventory room to go to.");
                return;
            }
        }
        int totDamage = 0;
        final MapleMap map = player.getMap();

        if (skillId == 4211006) { // meso explosion
            for (AttackPair oned : allDamage) {
                if (oned.attack != null) {
                    continue;
                }
                final MapleMapObject mapobject =
                        map.getMapObject(oned.objectid, MapleMapObjectType.ITEM);
                if (mapobject != null) {
                    final MapleMapItem mapitem = (MapleMapItem) mapobject;
                    mapitem.getLock().lock();
                    try {
                        if (mapitem.getMeso() > 0) {
                            if (mapitem.isPickedUp()) {
                                return;
                            }
                            map.removeMapObject(mapitem);
                            map.broadcastMessage(
                                    MaplePacketCreator.explodeDrop(mapitem.getObjectId()));
                            mapitem.setPickedUp(true);
                        } else {
                            player.getCheatTracker().registerOffense(CheatingOffense.ETC_EXPLOSION);
                            return;
                        }
                    } finally {
                        mapitem.getLock().unlock();
                    }
                } else {
                    player.getCheatTracker().registerOffense(CheatingOffense.EXPLODING_NONEXISTANT);
                    return;
                }
            }
        }
        int fixeddmg, totDamageToOneMonster = 0;
        long hpMob = 0;
        int CriticalDamage = stats.passive_sharpeye_percent();
        byte ShdowPartnerAttackPercentage = 0;
        if (attack_type == AttackType.RANGED_WITH_SHADOWPARTNER
                || attack_type == AttackType.NON_RANGED_WITH_MIRROR) {
            final MapleStatEffect shadowPartnerEffect;
            if (attack_type == AttackType.NON_RANGED_WITH_MIRROR) {
                shadowPartnerEffect = player.getStatForBuff(MapleBuffStat.MIRROR_IMAGE);
            } else {
                shadowPartnerEffect = player.getStatForBuff(MapleBuffStat.SHADOWPARTNER);
            }
            if (shadowPartnerEffect != null) {
                if (skillId != 0 && attack_type != AttackType.NON_RANGED_WITH_MIRROR) {
                    ShdowPartnerAttackPercentage = (byte) shadowPartnerEffect.getY();
                } else {
                    ShdowPartnerAttackPercentage = (byte) shadowPartnerEffect.getX();
                }
            }
            attackCount /= 2;
        }
        switch (skillId) {
            case 21100004:
            case 21110004:
            case 21120006:
                player.setLastCombo(System.currentTimeMillis());
                player.setCombo((short) 0);
                break;
        }

        byte overallAttackCount;
        double maxDamagePerHit = 0;
        MapleMonster monster;
        MapleMonsterStats monsterstats;
        boolean Tempest;
        int d = 0;

        for (final AttackPair oned : allDamage) {
            monster = map.getMonsterByOid(oned.objectid);
            if (monster != null) {
                totDamageToOneMonster = 0;
                hpMob = monster.getHp();
                monsterstats = monster.getStats();
                fixeddmg = monsterstats.getFixedDamage();
                Tempest = monster.getStatusSourceID(MonsterStatus.FREEZE) == 21120006;

                if (!Tempest && !player.isGM()) {
                    if (!monster.isBuffed(MonsterStatus.DAMAGE_IMMUNITY)
                            && !monster.isBuffed(MonsterStatus.WEAPON_IMMUNITY)
                            && !monster.isBuffed(MonsterStatus.WEAPON_DAMAGE_REFLECT)) {
                        maxDamagePerHit =
                                CalculateMaxWeaponDamagePerHit(
                                        player,
                                        monster,
                                        attack,
                                        theSkill,
                                        effect,
                                        maxDamagePerMonster,
                                        CriticalDamage);
                    } else {
                        maxDamagePerHit = 1;
                    }
                }
                overallAttackCount = 0;
                int eachd;
                for (Pair<Integer, Boolean> eachde : oned.attack) {
                    eachd = eachde.left();
                    overallAttackCount++;

                    if (overallAttackCount - 1 == attackCount) {
                        maxDamagePerHit = (maxDamagePerHit / 100) * ShdowPartnerAttackPercentage;
                    }
                    if (fixeddmg != -1) {
                        if (monsterstats.getOnlyNoramlAttack()) {
                            eachd = skillId != 0 ? 0 : fixeddmg;
                        } else {
                            eachd = fixeddmg;
                        }
                    } else {
                        if (monsterstats.getOnlyNoramlAttack()) {
                            eachd = skillId != 0 ? 0 : Math.min(eachd, (int) maxDamagePerHit);
                        } else if (!player.isGM()) {
                            if (Tempest) {
                                if (eachd > monster.getMobMaxHp()) {
                                    eachd =
                                            (int)
                                                    Math.min(
                                                            monster.getMobMaxHp(),
                                                            Integer.MAX_VALUE);
                                }
                            } else {
                                if (eachd > maxDamagePerHit) {
                                    eachd = (int) maxDamagePerHit;
                                }
                            }
                        }
                    }
                    if (adminOnly) {
                        player.dropMessage(-1, "Damage: " + eachd);
                    }
                    totDamageToOneMonster += eachd;
                    if (monster.getId() == 9300021 && player.getPyramidSubway() != null) {
                        player.getPyramidSubway().onMiss(player);
                    }
                }
                totDamage += totDamageToOneMonster;
                player.checkMonsterAggro(monster);

                if (player.getPosition().distanceSq(monster.getPosition()) > 700000.0) {
                    player.getCheatTracker()
                            .registerOffense(CheatingOffense.ATTACK_FARAWAY_MONSTER);
                }
                final MapleStatEffect pickpocketBuff =
                        player.getStatForBuff(MapleBuffStat.PICKPOCKET);
                if (pickpocketBuff != null) {
                    switch (skillId) {
                        case 0:
                        case 4001334:
                        case 4201005:
                        case 4211002:
                        case 4211004:
                        case 4221003:
                        case 4221007:
                            handlePickPocket(player, monster, oned);
                            break;
                    }
                }
                final MapleStatEffect darkSight = player.getStatForBuff(MapleBuffStat.DARKSIGHT);
                if (darkSight != null && !player.isGM()) {
                    if (darkSight.getSourceId() != 4330001 || !darkSight.makeChanceResult()) {
                        player.cancelEffectFromBuffStat(MapleBuffStat.DARKSIGHT);
                    }
                }

                if (totDamageToOneMonster > 0) {
                    if (skillId != 1221011) {
                        monster.damage(player, totDamageToOneMonster, true, skillId);
                    } else {
                        monster.damage(
                                player,
                                (monster.getStats().isBoss() ? 199999 : (monster.getHp() - 1)),
                                true,
                                skillId);
                    }
                    if (monster.isBuffed(MonsterStatus.WEAPON_DAMAGE_REFLECT)) {
                        player.addHP(-(7000 + Randomizer.nextInt(8000)));
                    }
                    if (stats.hpRecoverProp > 0) {
                        if (Randomizer.nextInt(100) <= stats.hpRecoverProp) {
                            player.healHP(stats.hpRecover);
                        }
                    }
                    if (stats.mpRecoverProp > 0) {
                        if (Randomizer.nextInt(100) <= stats.mpRecoverProp) {
                            player.healMP(stats.mpRecover);
                        }
                    }
                    final MapleStatEffect comboDrain =
                            player.getStatForBuff(MapleBuffStat.COMBO_DRAIN);
                    if (comboDrain != null) {
                        stats.setHp(
                                (stats.getHp()
                                        + ((int)
                                                Math.min(
                                                        monster.getMobMaxHp(),
                                                        Math.min(
                                                                ((int)
                                                                        ((double) totDamage
                                                                                * (double)
                                                                                        comboDrain
                                                                                                .getX()
                                                                                / 100.0)),
                                                                stats.getMaxHp() / 2)))),
                                true);
                    }
                    switch (skillId) {
                        case 4101005:
                        case 5111004:
                        case 14101006:
                            {
                                final MapleStatEffect drainEffect =
                                        theSkill.getEffect(player.getSkillLevel(theSkill));
                                int drainX = drainEffect.getX();
                                stats.setHp(
                                        (stats.getHp()
                                                + ((int)
                                                        Math.min(
                                                                monster.getMobMaxHp(),
                                                                Math.min(
                                                                        ((int)
                                                                                ((double) totDamage
                                                                                        * (double)
                                                                                                drainX
                                                                                        / 100.0)),
                                                                        stats.getMaxHp() / 2)))),
                                        true);
                                break;
                            }
                        case 5211006:
                        case 22151002:
                        case 5220011:
                            {
                                player.setLinkMid(monster.getObjectId());
                                break;
                            }
                        case 1311005:
                            {
                                final int remainingHP =
                                        stats.getHp() - totDamage * effect.getX() / 100;
                                stats.setHp(remainingHP < 1 ? 1 : remainingHP);
                                break;
                            }
                        case 4301001:
                        case 4311002:
                        case 4311003:
                        case 4331000:
                        case 4331004:
                        case 4331005:
                        case 4341005:
                        case 4221007:
                        case 4221001:
                        case 4211002:
                        case 4201005:
                        case 4001002:
                        case 4001334:
                        case 4121007:
                        case 4111005:
                        case 4001344:
                            {
                                if (venomEffect1 != null) {
                                    for (int i = 0; i < attackCount; i++) {
                                        if (venomEffect1.makeChanceResult()) {
                                            if (monster.getVenomMulti() < 3) {
                                                monster.setVenomMulti(
                                                        (byte) (monster.getVenomMulti() + 1));
                                                MonsterStatusEffect monsterStatusEffect =
                                                        new MonsterStatusEffect(
                                                                MonsterStatus.POISON,
                                                                1,
                                                                4120005,
                                                                null,
                                                                false);
                                                monster.applyStatus(
                                                        player,
                                                        monsterStatusEffect,
                                                        false,
                                                        venomEffect1.getDuration() + d,
                                                        true);
                                            }
                                        }
                                    }
                                } else if (venomEffect2 != null) {
                                    for (int i = 0; i < attackCount; i++) {
                                        if (venomEffect2.makeChanceResult()) {
                                            if (monster.getVenomMulti() < 3) {
                                                monster.setVenomMulti(
                                                        (byte) (monster.getVenomMulti() + 1));
                                                MonsterStatusEffect monsterStatusEffect =
                                                        new MonsterStatusEffect(
                                                                MonsterStatus.POISON,
                                                                1,
                                                                4220005,
                                                                null,
                                                                false);
                                                monster.applyStatus(
                                                        player,
                                                        monsterStatusEffect,
                                                        false,
                                                        venomEffect2.getDuration() + d,
                                                        true);
                                            }
                                        }
                                    }
                                } else if (venomEffect3 != null) {
                                    for (int i = 0; i < attackCount; i++) {
                                        if (venomEffect3.makeChanceResult()) {
                                            if (monster.getVenomMulti() < 3) {
                                                monster.setVenomMulti(
                                                        (byte) (monster.getVenomMulti() + 1));
                                                MonsterStatusEffect monsterStatusEffect =
                                                        new MonsterStatusEffect(
                                                                MonsterStatus.POISON,
                                                                1,
                                                                4340001,
                                                                null,
                                                                false);
                                                monster.applyStatus(
                                                        player,
                                                        monsterStatusEffect,
                                                        false,
                                                        venomEffect3.getDuration() + d,
                                                        true);
                                            }
                                        }
                                    }
                                }
                                break;
                            }
                        case 4201004:
                            {
                                break;
                            }
                        case 21000002:
                        case 21100001:
                        case 21100002:
                        case 21100004:
                        case 21110002:
                        case 21110003:
                        case 21110004:
                        case 21110006:
                        case 21110007:
                        case 21110008:
                        case 21120002:
                        case 21120005:
                        case 21120006:
                        case 21120009:
                        case 21120010:
                            {
                                if (player.getBuffedValue(MapleBuffStat.WK_CHARGE) != null
                                        && !monster.getStats().isBoss()) {
                                    final MapleStatEffect eff =
                                            player.getStatForBuff(MapleBuffStat.WK_CHARGE);
                                    if (eff != null && eff.getSourceId() == 21111005) {
                                        monster.applyStatus(
                                                player,
                                                new MonsterStatusEffect(
                                                        MonsterStatus.SPEED,
                                                        eff.getX(),
                                                        eff.getSourceId(),
                                                        null,
                                                        false),
                                                false,
                                                eff.getY() * 1000 + d,
                                                false);
                                    }
                                }
                                if (player.getBuffedValue(MapleBuffStat.BODY_PRESSURE) != null
                                        && !monster.getStats().isBoss()) {
                                    final MapleStatEffect eff =
                                            player.getStatForBuff(MapleBuffStat.BODY_PRESSURE);
                                    if (eff != null
                                            && eff.makeChanceResult()
                                            && !monster.isBuffed(MonsterStatus.NEUTRALISE)) {
                                        monster.applyStatus(
                                                player,
                                                new MonsterStatusEffect(
                                                        MonsterStatus.NEUTRALISE,
                                                        1,
                                                        eff.getSourceId(),
                                                        null,
                                                        false),
                                                false,
                                                eff.getX() * 1000 + d,
                                                false);
                                    }
                                }
                                break;
                            }
                        default:
                            break;
                    }
                    if (totDamageToOneMonster > 0) {
                        IItem weapon_ =
                                player.getInventory(MapleInventoryType.EQUIPPED)
                                        .getItem((byte) -11);
                        if (weapon_ != null) {
                            MonsterStatus stat =
                                    GameConstants.getStatFromWeapon(weapon_.getItemId());
                            if (stat != null
                                    && Randomizer.nextInt(100) < GameConstants.getStatChance()) {
                                final MonsterStatusEffect monsterStatusEffect =
                                        new MonsterStatusEffect(
                                                stat,
                                                GameConstants.getXForStat(stat),
                                                GameConstants.getSkillForStat(stat),
                                                null,
                                                false);
                                monster.applyStatus(
                                        player,
                                        monsterStatusEffect,
                                        false,
                                        10000 + d,
                                        false,
                                        false);
                            }
                        }
                        if (player.getBuffedValue(MapleBuffStat.BLIND) != null) {
                            final MapleStatEffect eff = player.getStatForBuff(MapleBuffStat.BLIND);
                            if (eff.makeChanceResult()) {
                                final MonsterStatusEffect monsterStatusEffect =
                                        new MonsterStatusEffect(
                                                MonsterStatus.ACC,
                                                eff.getX(),
                                                eff.getSourceId(),
                                                null,
                                                false);
                                monster.applyStatus(
                                        player,
                                        monsterStatusEffect,
                                        false,
                                        eff.getY() * 1000 + d,
                                        false);
                            }
                        }
                        if (player.getBuffedValue(MapleBuffStat.HAMSTRING) != null) {
                            final ISkill hamstringSkill = SkillFactory.getSkill(3121007);
                            final MapleStatEffect eff =
                                    hamstringSkill.getEffect(player.getSkillLevel(hamstringSkill));
                            if (eff.makeChanceResult()) {
                                final MonsterStatusEffect monsterStatusEffect =
                                        new MonsterStatusEffect(
                                                MonsterStatus.SPEED,
                                                eff.getX(),
                                                3121007,
                                                null,
                                                false);
                                monster.applyStatus(
                                        player,
                                        monsterStatusEffect,
                                        false,
                                        eff.getY() * 1000 + d,
                                        false);
                            }
                        }
                        if (player.getJob() == 121) {
                            for (int charge : charges) {
                                final ISkill chargeSkill = SkillFactory.getSkill(charge);
                                if (player.isBuffFrom(MapleBuffStat.WK_CHARGE, chargeSkill)) {
                                    final MonsterStatusEffect monsterStatusEffect =
                                            new MonsterStatusEffect(
                                                    MonsterStatus.FREEZE, 1, charge, null, false);
                                    monster.applyStatus(
                                            player,
                                            monsterStatusEffect,
                                            false,
                                            chargeSkill
                                                                    .getEffect(
                                                                            player.getSkillLevel(
                                                                                    chargeSkill))
                                                                    .getY()
                                                            * 2000
                                                    + d,
                                            false);
                                    break;
                                }
                            }
                        }
                    }
                    if (effect != null && effect.getMonsterStati().size() > 0) {
                        if (effect.makeChanceResult()) {
                            final long duration = effect.getDuration();
                            final boolean isPoison = effect.isPoison();
                            for (Map.Entry<MonsterStatus, Integer> z :
                                    effect.getMonsterStati().entrySet()) {
                                monster.applyStatus(
                                        player,
                                        new MonsterStatusEffect(
                                                z.getKey(),
                                                z.getValue(),
                                                theSkill.getId(),
                                                null,
                                                false),
                                        isPoison,
                                        duration + d,
                                        false,
                                        d);
                            }
                        }
                    }
                }
            }
            d += 50;
        }

        if (skillId == 4331003 && totDamageToOneMonster < hpMob) {
            return;
        }
        if (skillId != 0
                && (attack.targets() > 0 || (skillId != 4331003 && skillId != 4341002))
                && skillId != 21101003
                && skillId != 5110001
                && skillId != 15100004
                && skillId != 11101002
                && skillId != 13101002) {
            effect.applyTo(player, attack.position());
        }
        if (totDamage > 1) {
            final CheatTracker tracker = player.getCheatTracker();
            tracker.setAttacksWithoutHit(true);
            if (tracker.getAttacksWithoutHit() > 1000) {
                tracker.registerOffense(
                        CheatingOffense.ATTACK_WITHOUT_GETTING_HIT,
                        Integer.toString(tracker.getAttacksWithoutHit()));
            }
        }
    }

    public static final void applyAttackMagic(
            final AttackInfo attack,
            final ISkill theSkill,
            final MapleCharacter player,
            final MapleStatEffect effect) {
        if (!player.isAlive()) {
            player.getCheatTracker().registerOffense(CheatingOffense.ATTACKING_WHILE_DEAD);
            return;
        }
        final int skillId = attack.skill();
        final boolean isGM = player.isGM();
        final CheatTracker cheatTracker = player.getCheatTracker();
        if (attack.real()) {
            cheatTracker.checkAttack(skillId, attack.lastAttackTickCount());
        }
        if (attack.hits() > effect.getAttackCount() || attack.targets() > effect.getMobCount()) {
            cheatTracker.registerOffense(CheatingOffense.MISMATCHING_BULLETCOUNT);
            return;
        }
        if (attack.hits() > 0 && attack.targets() > 0) {
            if (!player.getStat().checkEquipDurabilitys(player, -1)) {
                player.dropMessage(
                        5, "An item has run out of durability but has no inventory room to go to.");
                return;
            }
        }
        if (GameConstants.isMulungSkill(skillId)) {
            if (player.getMapId() / 10000 != 92502) {
                return;
            } else {
                player.mulung_EnergyModify(false);
            }
        }
        if (GameConstants.isPyramidSkill(skillId)) {
            if (player.getMapId() / 1000000 != 926) {
                return;
            } else {
                if (player.getPyramidSubway() == null
                        || !player.getPyramidSubway().onSkillUse(player)) {
                    return;
                }
            }
        }
        final PlayerStats stats = player.getStat();
        final int skillLevel = player.getSkillLevel(theSkill);
        double maxDamagePerHit;
        if (skillId == 2301002) {
            maxDamagePerHit = 30000;
        } else if (skillId == 1000
                || skillId == 10001000
                || skillId == 20001000
                || skillId == 20011000
                || skillId == 30001000) {
            maxDamagePerHit = 40;
        } else if (GameConstants.isPyramidSkill(skillId)) {
            maxDamagePerHit = 1;
        } else {
            final double v75 = (effect.getMatk() * 0.058);
            maxDamagePerHit =
                    stats.getTotalMagic()
                            * (stats.getInt() * 0.5 + (v75 * v75) + effect.getMatk() * 3.3)
                            / 100;
        }
        maxDamagePerHit *= 1.04;

        final Element element =
                player.getBuffedValue(MapleBuffStat.ELEMENT_RESET) != null
                        ? Element.NEUTRAL
                        : theSkill.getElement();

        int totDamage = 0;
        final ISkill eaterSkill =
                SkillFactory.getSkill(GameConstants.getMPEaterForJob(player.getJob()));
        final int eaterLevel = player.getSkillLevel(eaterSkill);

        final MapleMap map = player.getMap();
        final int theSkillId = theSkill.getId();
        final boolean hasMonsterStatus = !effect.getMonsterStati().isEmpty();
        final boolean isPoison = effect.isPoison();
        final int effectDuration = effect.getDuration();

        int d = 0;
        for (final AttackPair oned : attack.allDamage()) {
            final MapleMonster monster = map.getMonsterByOid(oned.objectid);
            if (monster != null) {
                final boolean tempest =
                        monster.getStatusSourceID(MonsterStatus.FREEZE) == 21120006
                                && !monster.getStats().isBoss();
                int totDamageToOneMonster = 0;
                final MapleMonsterStats monsterstats = monster.getStats();
                final int fixeddmg = monsterstats.getFixedDamage();
                final boolean onlyNormalAttack = monsterstats.getOnlyNoramlAttack();
                for (Pair<Integer, Boolean> eachde : oned.attack) {
                    int eachd = eachde.left();
                    if (fixeddmg != -1) {
                        eachd = onlyNormalAttack ? 0 : fixeddmg;
                    } else {
                        if (onlyNormalAttack) {
                            eachd = 0;
                        } else if (!isGM) {
                            if (tempest) {
                                if (eachd > monster.getMobMaxHp()) {
                                    eachd =
                                            (int)
                                                    Math.min(
                                                            monster.getMobMaxHp(),
                                                            Integer.MAX_VALUE);
                                }
                            } else {
                                if (eachd > maxDamagePerHit) {
                                    eachd = (int) (maxDamagePerHit);
                                }
                            }
                        }
                    }
                    totDamageToOneMonster += eachd;
                }
                totDamage += totDamageToOneMonster;
                player.checkMonsterAggro(monster);

                if (player.getPosition().distanceSq(monster.getPosition()) > 700000.0) {
                    cheatTracker.registerOffense(CheatingOffense.ATTACK_FARAWAY_MONSTER);
                }
                if (skillId == 2301002 && !monsterstats.getUndead()) {
                    cheatTracker.registerOffense(CheatingOffense.HEAL_ATTACKING_UNDEAD);
                    return;
                }

                if (totDamageToOneMonster > 0) {
                    monster.damage(player, totDamageToOneMonster, true, skillId);
                    if (monster.isBuffed(MonsterStatus.MAGIC_DAMAGE_REFLECT)) {
                        player.addHP(-(7000 + Randomizer.nextInt(8000)));
                    }
                    switch (skillId) {
                        case 2221003:
                            monster.setTempEffectiveness(
                                    Element.FIRE, theSkill.getEffect(skillLevel).getDuration() + d);
                            break;
                        case 2121003:
                            monster.setTempEffectiveness(
                                    Element.ICE, theSkill.getEffect(skillLevel).getDuration() + d);
                            break;
                    }
                    if (hasMonsterStatus && effect.makeChanceResult()) {
                        for (Map.Entry<MonsterStatus, Integer> z :
                                effect.getMonsterStati().entrySet()) {
                            monster.applyStatus(
                                    player,
                                    new MonsterStatusEffect(
                                            z.getKey(), z.getValue(), theSkillId, null, false),
                                    isPoison,
                                    effectDuration + d,
                                    false,
                                    d);
                        }
                    }
                    if (eaterLevel > 0) {
                        eaterSkill.getEffect(eaterLevel).applyPassive(player, monster);
                    }
                }
            }
            d += 50;
        }
        if (skillId != 2301002) {
            effect.applyTo(player);
        }

        if (totDamage > 1) {
            cheatTracker.setAttacksWithoutHit(true);
            if (cheatTracker.getAttacksWithoutHit() > 1000) {
                cheatTracker.registerOffense(
                        CheatingOffense.ATTACK_WITHOUT_GETTING_HIT,
                        Integer.toString(cheatTracker.getAttacksWithoutHit()));
            }
        }
    }

    private static final double CalculateMaxMagicDamagePerHit(
            final MapleCharacter chr,
            final ISkill skill,
            final MapleMonster monster,
            final MapleMonsterStats mobstats,
            final PlayerStats stats,
            final Element elem,
            final Integer sharpEye,
            final double maxDamagePerMonster) {
        final int dLevel = Math.max(mobstats.getLevel() - chr.getLevel(), 0);
        final int Accuracy =
                (int)
                        (Math.floor((stats.getTotalInt() / 10.0))
                                + Math.floor((stats.getTotalLuk() / 10.0)));
        final int MinAccuracy = mobstats.getEva() * (dLevel * 2 + 51) / 120;
        // FullAccuracy = Avoid * (dLevel * 2 + 51) / 50

        final int skillId = skill.getId();
        if (MinAccuracy > Accuracy
                && skillId != 1000
                && skillId != 10001000
                && skillId != 20001000
                && skillId != 20011000
                && skillId != 30001000
                && !GameConstants.isPyramidSkill(skillId)) { // miss :P or HACK :O
            return 0;
        }
        double elemMaxDamagePerMob;

        switch (monster.getEffectiveness(elem)) {
            case IMMUNE:
                elemMaxDamagePerMob = 1;
                break;
            case NORMAL:
                elemMaxDamagePerMob = ElementalStaffAttackBonus(elem, maxDamagePerMonster, stats);
                break;
            case WEAK:
                elemMaxDamagePerMob =
                        ElementalStaffAttackBonus(elem, maxDamagePerMonster * 1.5, stats);
                break;
            case STRONG:
                elemMaxDamagePerMob =
                        ElementalStaffAttackBonus(elem, maxDamagePerMonster * 0.5, stats);
                break;
            default:
                throw new RuntimeException("Unknown enum constant");
        }
        // Calculate monster magic def
        // Min damage = (MIN before defense) - MDEF*.6
        // Max damage = (MAX before defense) - MDEF*.5
        elemMaxDamagePerMob -= mobstats.getMagicDefense() * 0.5;
        // Calculate Sharp eye bonus
        elemMaxDamagePerMob += ((double) elemMaxDamagePerMob / 100) * sharpEye;
        //	if (skill.isChargeSkill()) {
        //	    elemMaxDamagePerMob = (float) ((90 * ((System.currentTimeMillis() -
        // chr.getKeyDownSkill_Time()) / 1000) + 10) * elemMaxDamagePerMob * 0.01);
        //	}
        //      if (skill.isChargeSkill() && chr.getKeyDownSkill_Time() == 0) {
        //          return 1;
        //      }
        elemMaxDamagePerMob +=
                (elemMaxDamagePerMob * (mobstats.isBoss() ? stats.bossdam_r : stats.dam_r)) / 100;
        switch (skillId) {
            case 1000:
            case 10001000:
            case 20001000:
            case 20011000:
            case 30001000:
                elemMaxDamagePerMob = 40;
                break;
            case 1020:
            case 10001020:
            case 20001020:
            case 20011020:
            case 30001020:
                elemMaxDamagePerMob = 1;
                break;
        }
        if (elemMaxDamagePerMob > 199999) {
            elemMaxDamagePerMob = 199999;
        } else if (elemMaxDamagePerMob < 0) {
            elemMaxDamagePerMob = 1;
        }
        return elemMaxDamagePerMob;
    }

    private static final double ElementalStaffAttackBonus(
            final Element elem, double elemMaxDamagePerMob, final PlayerStats stats) {
        return switch (elem) {
            case FIRE -> (elemMaxDamagePerMob / 100) * stats.element_fire;
            case ICE -> (elemMaxDamagePerMob / 100) * stats.element_ice;
            case LIGHTING -> (elemMaxDamagePerMob / 100) * stats.element_light;
            case POISON -> (elemMaxDamagePerMob / 100) * stats.element_psn;
            default -> (elemMaxDamagePerMob / 100) * stats.def;
        };
    }

    private static void handlePickPocket(
            final MapleCharacter player, final MapleMonster mob, AttackPair oned) {
        final int maxmeso = player.getBuffedValue(MapleBuffStat.PICKPOCKET).intValue();
        final ISkill skill = SkillFactory.getSkill(4211003);
        final MapleStatEffect s = skill.getEffect(player.getSkillLevel(skill));

        // 預先取出怪物座標，避免在迴圈與延遲任務中重複呼叫 getPosition()
        final Point mobPos = mob.getPosition();
        final int mobX = mobPos.x;
        final int mobY = mobPos.y;
        final MapleMap map = player.getMap();

        for (final Pair<Integer, Boolean> eachde : oned.attack) {
            final Integer eachd = eachde.left();
            if (s.makeChanceResult()) {
                // 預先計算掉落金幣數量，避免在延遲任務中捕獲 eachd 造成額外物件保留
                final int meso =
                        Math.min(
                                (int)
                                        Math.max(
                                                ((double) eachd / (double) 20000)
                                                        * (double) maxmeso,
                                                (double) 1),
                                maxmeso);
                MapTimer.getInstance()
                        .schedule(
                                new Runnable() {

                                    @Override
                                    public void run() {
                                        map.spawnMesoDrop(
                                                meso,
                                                new Point(
                                                        mobX + Randomizer.nextInt(100) - 50, mobY),
                                                mob,
                                                player,
                                                true,
                                                (byte) 0);
                                    }
                                },
                                100);
            }
        }
    }

    private static double CalculateMaxWeaponDamagePerHit(
            final MapleCharacter player,
            final MapleMonster monster,
            final AttackInfo attack,
            final ISkill theSkill,
            final MapleStatEffect attackEffect,
            double maximumDamageToMonster,
            final Integer CriticalDamagePercent) {
        if (player.getMapId() / 1000000 == 914) { // aran
            return 199999;
        }
        List<Element> elements = new ArrayList<>(2);
        boolean defined = false;
        if (theSkill != null) {
            elements.add(theSkill.getElement());

            switch (theSkill.getId()) {
                case 3001004:
                case 33101001:
                    defined = true; // can go past 199999
                    break;
                case 1000:
                case 10001000:
                case 20001000:
                case 20011000:
                case 30001000:
                    maximumDamageToMonster = 40;
                    defined = true;
                    break;
                case 1020:
                case 10001020:
                case 20001020:
                case 20011020:
                case 30001020:
                    maximumDamageToMonster = 1;
                    defined = true;
                    break;
                case 4331003: // Owl Spirit
                    maximumDamageToMonster =
                            (monster.getStats().isBoss() ? 199999 : monster.getHp());
                    defined = true;
                    break;
                case 3221007: // Sniping
                    maximumDamageToMonster =
                            (monster.getStats().isBoss() ? 199999 : monster.getMobMaxHp());
                    defined = true;
                    break;
                case 1221011: // Heavens Hammer
                    maximumDamageToMonster =
                            (monster.getStats().isBoss() ? 199999 : monster.getHp() - 1);
                    defined = true;
                    break;
                case 4211006: // Meso Explosion
                    maximumDamageToMonster = 750000;
                    defined = true;
                    break;
                case 1009: // Bamboo Trust
                case 10001009:
                case 20001009:
                case 20011009:
                case 30001009:
                    defined = true;
                    maximumDamageToMonster =
                            (monster.getStats().isBoss()
                                    ? monster.getMobMaxHp() / 30 * 100
                                    : monster.getMobMaxHp());
                    break;
                case 3211006: // Sniper Strafe
                    if (monster.getStatusSourceID(MonsterStatus.FREEZE)
                            == 3211003) { // blizzard in effect
                        defined = true;
                        maximumDamageToMonster = monster.getHp();
                    }
                    break;
            }
        }
        if (player.getBuffedValue(MapleBuffStat.WK_CHARGE) != null) {
            int chargeSkillId = player.getBuffSource(MapleBuffStat.WK_CHARGE);

            switch (chargeSkillId) {
                case 1211003:
                case 1211004:
                    elements.add(Element.FIRE);
                    break;
                case 1211005:
                case 1211006:
                case 21111005:
                    elements.add(Element.ICE);
                    break;
                case 1211007:
                case 1211008:
                case 15101006:
                    elements.add(Element.LIGHTING);
                    break;
                case 1221003:
                case 1221004:
                case 11111007:
                    elements.add(Element.HOLY);
                    break;
                case 12101005:
                    elements.clear(); // neutral
                    break;
            }
        }
        if (player.getBuffedValue(MapleBuffStat.LIGHTNING_CHARGE) != null) {
            elements.add(Element.LIGHTING);
        }
        double elementalMaxDamagePerMonster = maximumDamageToMonster;
        if (!elements.isEmpty()) {
            double elementalEffect =
                    switch (attack.skill()) {
                        case 3211003:
                        case 3111003: // inferno and blizzard
                            yield attackEffect.getX() / 200.0;
                        default:
                            yield 0.5;
                    };
            for (Element element : elements) {
                switch (monster.getEffectiveness(element)) {
                    case IMMUNE:
                        elementalMaxDamagePerMonster = 1;
                        break;
                    case WEAK:
                        elementalMaxDamagePerMonster *= (1.0 + elementalEffect);
                        break;
                    case STRONG:
                        elementalMaxDamagePerMonster *= (1.0 - elementalEffect);
                        break;
                    default:
                        break; // normal nothing
                }
            }
        }
        // Calculate mob def
        final short moblevel = monster.getStats().getLevel();
        final short d = moblevel > player.getLevel() ? (short) (moblevel - player.getLevel()) : 0;
        elementalMaxDamagePerMonster =
                elementalMaxDamagePerMonster * (1 - 0.01 * d)
                        - monster.getStats().getPhysicalDefense() * 0.5;

        // Calculate passive bonuses + Sharp Eye
        elementalMaxDamagePerMonster +=
                (elementalMaxDamagePerMonster / 100.0) * CriticalDamagePercent;

        if (theSkill != null && theSkill.isChargeSkill() && player.getKeyDownSkill_Time() == 0) {
            return 0;
        }
        final MapleStatEffect homing = player.getStatForBuff(MapleBuffStat.HOMING_BEACON);
        if (homing != null
                && player.getLinkMid() == monster.getObjectId()
                && homing.getSourceId() == 5220011) { // bullseye
            elementalMaxDamagePerMonster += (elementalMaxDamagePerMonster * homing.getX());
        }
        final PlayerStats stat = player.getStat();
        elementalMaxDamagePerMonster +=
                (elementalMaxDamagePerMonster
                                * (monster.getStats().isBoss() ? stat.bossdam_r : stat.dam_r))
                        / 100.0;

        if (elementalMaxDamagePerMonster > 199999) {
            if (!defined) {
                elementalMaxDamagePerMonster = 199999;
            }
        } else if (elementalMaxDamagePerMonster < 0) {
            elementalMaxDamagePerMonster = 1;
        }
        return elementalMaxDamagePerMonster;
    }

    public static final AttackInfo DivideAttack(final AttackInfo attack, final int rate) {
        attack.setReal(false);
        if (rate <= 1) {
            return attack; // lol
        }
        for (AttackPair p : attack.allDamage()) {
            if (p.attack != null) {
                p.attack.replaceAll(
                        eachd -> new Pair<>(eachd.left() / rate, eachd.right())); // too ex.
            }
        }
        return attack;
    }

    public static final AttackInfo Modify_AttackCrit(
            final AttackInfo attack, final MapleCharacter chr, final int type) {
        final int CriticalRate = chr.getStat().passive_sharpeye_rate();
        final boolean shadow =
                (type == 2 && chr.getBuffedValue(MapleBuffStat.SHADOWPARTNER) != null)
                        || (type == 1 && chr.getBuffedValue(MapleBuffStat.MIRROR_IMAGE) != null);
        final int skill = attack.skill();
        if (skill != 4211006
                && skill != 3211003
                && skill != 4111004
                && (CriticalRate > 0
                        || skill == 4221001
                        || skill == 3221007)) { // blizz + shadow meso + m.e no crits
            final boolean assassinate = (skill == 4221001);
            final boolean snipe = (skill == 3221007);
            for (AttackPair p : attack.allDamage()) {
                final List<Pair<Integer, Boolean>> attacks = p.attack;
                if (attacks == null || attacks.isEmpty()) {
                    continue;
                }
                final int size = attacks.size();
                final int mid_att = size / 2;
                // 僅在需要 shadow 複製時才建立副本，避免無謂記憶體配置
                final List<Pair<Integer, Boolean>> eachd_copy =
                        shadow ? new ArrayList<>(attacks) : null;
                for (int i = 0; i < size; i++) {
                    final Pair<Integer, Boolean> eachd = attacks.get(i);
                    if (eachd.right()) {
                        continue;
                    }
                    final int hit = i + 1;
                    final boolean crit;
                    if (assassinate) { // assassinate never crit first 3, always crit last
                        crit = (hit == 4 && Randomizer.nextInt(100) < 90);
                    } else if (snipe || eachd.left() > 199999) { // snipe always crit
                        crit = true;
                    } else if (shadow
                            && hit > mid_att) { // shadowpartner copies second half to first half
                        crit = eachd_copy.get(hit - 1 - mid_att).right();
                    } else {
                        // rough calculation
                        crit = Randomizer.nextInt(100) < CriticalRate;
                    }
                    attacks.set(i, new Pair<>(eachd.left(), crit));
                }
            }
        }
        return attack;
    }

    public static final AttackInfo parseDmgMa(final LittleEndianAccessor lea) {
        final AttackInfo ret = new AttackInfo();

        lea.skip(1);
        lea.skip(8);
        ret.setTbyte(lea.readByte());
        ret.setTargets((byte) ((ret.tbyte() >>> 4) & 0xF));
        ret.setHits((byte) (ret.tbyte() & 0xF));
        lea.skip(8); // ?
        ret.setSkill(lea.readInt());
        lea.skip(12); // ORDER [4] bytes on v.79, [4] bytes on v.80, [1] byte on v.82
        switch (ret.skill()) {
            case 2121001: // Big Bang
            case 2221001:
            case 2321001:
            case 22121000: // breath
            case 22151001:
                ret.setCharge(lea.readInt());
                break;
            default:
                ret.setCharge(-1);
                break;
        }
        lea.skip(1);
        ret.setUnk((byte) 0);
        ret.setDisplay(lea.readByte()); // Always zero?
        ret.setAnimation(lea.readByte());
        lea.skip(1); // Weapon subclass
        ret.setSpeed(lea.readByte()); // Confirmed
        ret.setLastAttackTickCount(lea.readInt()); // Ticks

        final int targets = ret.targets();
        final int hits = ret.hits();
        final List<AttackPair> allDamage = new ArrayList<>(targets);
        ret.setAllDamage(allDamage);

        for (int i = 0; i < targets; i++) {
            final int oid = lea.readInt();
            lea.skip(
                    14); // [1] Always 6?, [3] unk, [4] Pos1, [4] Pos2, [2] seems to change randomly
            // for some
            // attack

            final List<Pair<Integer, Boolean>> allDamageNumbers = new ArrayList<>(hits);

            for (int j = 0; j < hits; j++) {
                final int damage = lea.readInt();
                allDamageNumbers.add(new Pair<>(Integer.valueOf(damage), Boolean.FALSE));
            }
            lea.skip(4); // CRC of monster [Wz Editing]
            allDamage.add(new AttackPair(Integer.valueOf(oid), allDamageNumbers));
        }
        ret.setPosition(lea.readPos());

        return ret;
    }

    public static final AttackInfo parseDmgM(final LittleEndianAccessor lea) {
        final AttackInfo ret = new AttackInfo();

        lea.skip(1);
        lea.skip(8);
        ret.setTbyte(lea.readByte());
        ret.setTargets((byte) ((ret.tbyte() >>> 4) & 0xF));
        ret.setHits((byte) (ret.tbyte() & 0xF));
        lea.skip(8);
        ret.setSkill(lea.readInt());
        lea.skip(12); // ORDER [4] bytes on v.79, [4] bytes on v.80, [1] byte on v.82
        switch (ret.skill()) {
            case 5101004: // Corkscrew
            case 15101003: // Cygnus corkscrew
            case 5201002: // Gernard
            case 14111006: // Poison bomb
            case 4341002:
            case 4341003:
                ret.setCharge(lea.readInt());
                break;
            default:
                ret.setCharge(0);
                break;
        }
        ret.setUnk(lea.readByte());
        ret.setDisplay(lea.readByte()); // Always zero?
        ret.setAnimation(lea.readByte());
        lea.skip(1); // Weapon class
        ret.setSpeed(lea.readByte()); // Confirmed
        ret.setLastAttackTickCount(lea.readInt()); // Ticks

        ret.setAllDamage(new ArrayList<>());

        if (ret.skill() == 4211006) { // Meso Explosion
            return parseMesoExplosion(lea, ret);
        }

        final int targets = ret.targets();
        final int hits = ret.hits();
        final List<AttackPair> allDamage = ret.allDamage();

        for (int i = 0; i < targets; i++) {
            final int oid = lea.readInt();
            lea.skip(
                    14); // [1] Always 6?, [3] unk, [4] Pos1, [4] Pos2, [2] seems to change randomly
            // for some
            // attack

            final List<Pair<Integer, Boolean>> allDamageNumbers = new ArrayList<>(hits);

            for (int j = 0; j < hits; j++) {
                final int damage = lea.readInt();
                allDamageNumbers.add(new Pair<>(Integer.valueOf(damage), Boolean.FALSE));
            }
            lea.skip(4); // CRC of monster [Wz Editing]
            allDamage.add(new AttackPair(Integer.valueOf(oid), allDamageNumbers));
        }
        if (ret.skill() == 14111006) {
            lea.skip(4);
        }
        ret.setPosition(lea.readPos());
        return ret;
    }

    public static final AttackInfo parseDmgR(final LittleEndianAccessor lea) {
        final AttackInfo ret = new AttackInfo();

        lea.skip(1);
        lea.skip(8);
        ret.setTbyte(lea.readByte());
        ret.setTargets((byte) ((ret.tbyte() >>> 4) & 0xF));
        ret.setHits((byte) (ret.tbyte() & 0xF));
        lea.skip(8);
        ret.setSkill(lea.readInt());

        lea.skip(12); // ORDER [4] bytes on v.79, [4] bytes on v.80, [1] byte on v.82

        switch (ret.skill()) {
            case 3121004: // Hurricane
            case 3221001: // Pierce
            case 5221004: // Rapidfire
            case 13111002: // Cygnus Hurricane
            case 33121009:
                lea.skip(4); // extra 4 bytes
                break;
        }
        ret.setCharge(-1);
        lea.skip(1);
        ret.setUnk((byte) 0);
        ret.setDisplay(lea.readByte()); // Always zero?
        ret.setAnimation(lea.readByte());
        lea.skip(1); // Weapon class
        ret.setSpeed(lea.readByte()); // Confirmed
        ret.setLastAttackTickCount(lea.readInt()); // Ticks
        ret.setSlot((byte) lea.readShort());
        ret.setCsstar((byte) lea.readShort());
        ret.setAOE(lea.readByte()); // is AOE or not, TT/ Avenger = 41, Showdown = 0

        final int targets = ret.targets();
        final int hits = ret.hits();
        final List<AttackPair> allDamage = new ArrayList<>(targets);
        ret.setAllDamage(allDamage);

        for (int i = 0; i < targets; i++) {
            final int oid = lea.readInt();
            lea.skip(
                    14); // [1] Always 6?, [3] unk, [4] Pos1, [4] Pos2, [2] seems to change randomly
            // for some
            // attack

            final List<Pair<Integer, Boolean>> allDamageNumbers = new ArrayList<>(hits);
            for (int j = 0; j < hits; j++) {
                final int damage = lea.readInt();
                allDamageNumbers.add(new Pair<>(Integer.valueOf(damage), Boolean.FALSE));
            }
            lea.skip(4); // CRC of monster [Wz Editing]

            allDamage.add(new AttackPair(Integer.valueOf(oid), allDamageNumbers));
        }
        lea.skip(4);
        ret.setPosition(lea.readPos());

        return ret;
    }

    public static final AttackInfo parseMesoExplosion(
            final LittleEndianAccessor lea, final AttackInfo ret) {
        byte bullets;
        if (ret.hits() == 0) {
            lea.skip(4);
            bullets = lea.readByte();
            for (int j = 0; j < bullets; j++) {
                ret.allDamage().add(new AttackPair(Integer.valueOf(lea.readInt()), null));
                lea.skip(1);
            }
            lea.skip(2); // 8F 02
            return ret;
        }

        int oid;
        List<Pair<Integer, Boolean>> allDamageNumbers;

        for (int i = 0; i < ret.targets(); i++) {
            oid = lea.readInt();
            lea.skip(12);
            bullets = lea.readByte();
            allDamageNumbers = new ArrayList<>(bullets > 0 ? bullets : 1);
            for (int j = 0; j < bullets; j++) {
                allDamageNumbers.add(
                        new Pair<>(
                                Integer.valueOf(lea.readInt()), Boolean.FALSE)); // m.e. never crits
            }
            ret.allDamage().add(new AttackPair(Integer.valueOf(oid), allDamageNumbers));
            lea.skip(4); // C3 8F 41 94, 51 04 5B 01
        }
        lea.skip(4);
        bullets = lea.readByte();

        for (int j = 0; j < bullets; j++) {
            ret.allDamage().add(new AttackPair(Integer.valueOf(lea.readInt()), null));
            lea.skip(1);
        }
        lea.skip(2); // 8F 02/ 63 02

        return ret;
    }
}
