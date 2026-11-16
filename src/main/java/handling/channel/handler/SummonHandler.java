package handling.channel.handler;

import client.ISkill;
import client.MapleBuffStat;
import client.MapleCharacter;
import client.MapleClient;
import client.SkillFactory;
import client.SummonSkillEntry;
import client.anticheat.CheatingOffense;
import client.status.MonsterStatus;
import client.status.MonsterStatusEffect;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import server.AutobanManager;
import server.MapleStatEffect;
import server.life.MapleMonster;
import server.life.SummonAttackEntry;
import server.maps.MapleMap;
import server.maps.MapleMapObject;
import server.maps.MapleMapObjectType;
import server.maps.MapleSummon;
import server.maps.SummonMovementType;
import server.movement.LifeMovementFragment;
import tools.MaplePacketCreator;
import tools.data.input.SeekableLittleEndianAccessor;
import tools.packet.MobPacket;

public class SummonHandler {

    public static final void MoveSummon(
            final SeekableLittleEndianAccessor slea, final MapleCharacter chr) {
        final int oid = slea.readInt();
        final Point startPos = new Point(slea.readShort(), slea.readShort());
        final List<LifeMovementFragment> res = MovementParse.parseMovement(slea, 4);
        if (chr == null) {
            return;
        }
        final Map<Integer, MapleSummon> summons = chr.getSummons();
        if (summons == null || summons.isEmpty()) {
            return;
        }
        final MapleSummon sum = summons.get(oid);
        if (sum == null || sum.getMovementType() == SummonMovementType.STATIONARY) {
            return;
        }
        MovementParse.updatePosition(res, sum, 0);
        if (!res.isEmpty()) {
            final MapleMap map = chr.getMap();
            if (map != null) {
                map.broadcastMessage(
                        chr,
                        MaplePacketCreator.moveSummon(chr.getId(), oid, startPos, res),
                        sum.getPosition());
            }
        }
    }

    public static final void DamageSummon(
            final SeekableLittleEndianAccessor slea, final MapleCharacter chr) {
        final int unkByte = slea.readByte();
        final int damage = slea.readInt();
        final int monsterIdFrom = slea.readInt();

        if (chr == null) {
            return;
        }
        final Map<Integer, MapleSummon> summons = chr.getSummons();
        if (summons == null || summons.isEmpty()) {
            return;
        }
        final Iterator<MapleSummon> iter = summons.values().iterator();
        while (iter.hasNext()) {
            final MapleSummon summon = iter.next();
            if (summon != null && summon.isPuppet() && summon.getOwnerId() == chr.getId()) {
                summon.addHP((short) -damage);
                if (summon.getHP() <= 0) {
                    chr.cancelEffectFromBuffStat(MapleBuffStat.PUPPET);
                }
                final MapleMap map = chr.getMap();
                if (map != null) {
                    map.broadcastMessage(
                            chr,
                            MaplePacketCreator.damageSummon(
                                    chr.getId(), summon.getSkill(), damage, unkByte, monsterIdFrom),
                            summon.getPosition());
                }
                break;
            }
        }
    }

    public static void SummonAttack(
            final SeekableLittleEndianAccessor slea,
            final MapleClient c,
            final MapleCharacter chr) {
        if (chr == null || !chr.isAlive()) {
            return;
        }
        final MapleMap map = chr.getMap();
        if (map == null) {
            return;
        }
        final MapleMapObject obj = map.getMapObject(slea.readInt(), MapleMapObjectType.SUMMON);
        if (obj == null) {
            return;
        }
        final MapleSummon summon = (MapleSummon) obj;
        if (summon.getOwnerId() != chr.getId() || summon.getSkillLevel() <= 0) {
            return;
        }
        final SummonSkillEntry sse = SkillFactory.getSummonData(summon.getSkill());
        if (sse == null) {
            return;
        }
        slea.skip(8);
        final int tick = slea.readInt();
        chr.updateTick(tick);
        summon.CheckSummonAttackFrequency(chr, tick);
        slea.skip(8);
        final byte animation = slea.readByte();
        slea.skip(8);
        final byte numAttacked = slea.readByte();
        if (numAttacked > sse.mobCount) {
            chr.getCheatTracker().registerOffense(CheatingOffense.SUMMON_HACK_MOBS);
            return;
        }
        slea.skip(8);
        final List<SummonAttackEntry> allDamage =
                new ArrayList<>(numAttacked > 0 ? numAttacked : 1);
        chr.getCheatTracker().checkSummonAttack();

        final Point chrPos = chr.getPosition();
        for (int i = 0; i < numAttacked; i++) {
            final MapleMonster mob = map.getMonsterByOid(slea.readInt());
            if (mob == null) {
                continue;
            }
            if (chrPos.distanceSq(mob.getPosition()) > 400000.0) {
                chr.getCheatTracker()
                        .registerOffense(CheatingOffense.ATTACK_FARAWAY_MONSTER_SUMMON);
            }
            slea.skip(18);
            final int damage = slea.readInt();
            allDamage.add(new SummonAttackEntry(mob, damage));
        }
        if (!summon.isChangedMap()) {
            map.broadcastMessage(
                    chr,
                    MaplePacketCreator.summonAttack(
                            summon.getOwnerId(),
                            summon.getObjectId(),
                            animation,
                            allDamage,
                            chr.getLevel()),
                    summon.getPosition());
        }
        final ISkill summonSkill = SkillFactory.getSkill(summon.getSkill());
        if (summonSkill == null) {
            return;
        }
        final MapleStatEffect summonEffect = summonSkill.getEffect(summon.getSkillLevel());
        if (summonEffect == null) {
            return;
        }
        final Map<MonsterStatus, Integer> stati = summonEffect.getMonsterStati();
        final boolean hasStati = stati != null && !stati.isEmpty();
        final boolean isGM = chr.isGM();
        final MapleClient client = chr.getClient();

        for (SummonAttackEntry attackEntry : allDamage) {
            final int toDamage = attackEntry.getDamage();
            final MapleMonster mob = attackEntry.getMonster();
            if (mob == null) {
                continue;
            }
            if (toDamage > 0 && hasStati) {
                if (summonEffect.makeChanceResult()) {
                    for (Map.Entry<MonsterStatus, Integer> z : stati.entrySet()) {
                        mob.applyStatus(
                                chr,
                                new MonsterStatusEffect(
                                        z.getKey(), z.getValue(), summonSkill.getId(), null, false),
                                summonEffect.isPoison(),
                                4000,
                                false);
                    }
                }
            }
            if (isGM || toDamage < 120000) {
                mob.damage(chr, toDamage, true);
                chr.checkMonsterAggro(mob);
                if (!mob.isAlive()) {
                    if (client != null) {
                        client.sendPacket(MobPacket.killMonster(mob.getObjectId(), 1));
                    }
                }
            } else {
                AutobanManager.getInstance()
                        .autoban(c, "High Summon Damage (" + toDamage + " to " + mob.getId() + ")");
            }
        }
        if (summon.isGaviota()) {
            map.broadcastMessage(MaplePacketCreator.removeSummon(summon, true));
            map.removeMapObject(summon);
            chr.removeVisibleMapObject(summon);
            chr.cancelEffectFromBuffStat(MapleBuffStat.SUMMON);
            chr.cancelEffectFromBuffStat(MapleBuffStat.REAPER);
        }
    }
}
