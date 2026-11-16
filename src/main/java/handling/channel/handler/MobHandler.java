package handling.channel.handler;

import client.MapleCharacter;
import client.MapleClient;
import client.inventory.MapleInventoryType;
import java.awt.Point;
import java.util.List;
import server.MapleInventoryManipulator;
import server.Randomizer;
import server.life.MapleMonster;
import server.life.MobSkill;
import server.life.MobSkillFactory;
import server.maps.MapleMap;
import server.maps.MapleNodes.MapleNodeInfo;
import server.movement.LifeMovementFragment;
import tools.MaplePacketCreator;
import tools.Pair;
import tools.data.input.SeekableLittleEndianAccessor;
import tools.packet.MobPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MobHandler {
    private static final Logger log = LoggerFactory.getLogger(MobHandler.class);

    public static final void MoveMonster(
            final SeekableLittleEndianAccessor slea,
            final MapleClient c,
            final MapleCharacter chr) {
        if (chr == null || chr.getMap() == null) {
            return;
        }
        final int oid = slea.readInt();
        final MapleMap map = chr.getMap();
        final MapleMonster monster = map.getMonsterByOid(oid);

        if (monster == null) {
            chr.addMoveMob(oid);
            return;
        }
        final short moveid = slea.readShort();
        final boolean useSkill = slea.readByte() > 0;
        final byte skill = slea.readByte();
        final int skill1 = slea.readByte() & 0xFF;
        final int skill2 = slea.readByte();
        final int skill3 = slea.readByte();
        final int skill4 = slea.readByte();
        int realskill = 0;
        int level = 0;

        if (useSkill) {
            final byte size = monster.getNoSkills();
            boolean used = false;

            if (size > 0) {
                final Pair<Integer, Integer> skillToUse =
                        monster.getSkills().get((byte) Randomizer.nextInt(size));
                realskill = skillToUse.left();
                level = skillToUse.right();
                final MobSkill mobSkill = MobSkillFactory.getMobSkill(realskill, level);

                if (mobSkill != null && !mobSkill.checkCurrentBuff(chr, monster)) {
                    final long now = System.currentTimeMillis();
                    final long ls = monster.getLastSkillUsed(realskill);

                    if (ls == 0 || ((now - ls) > mobSkill.getCoolTime())) {
                        monster.setLastSkillUsed(realskill, now, mobSkill.getCoolTime());

                        final int reqHp =
                                (int) (((float) monster.getHp() / monster.getMobMaxHp()) * 100);
                        if (reqHp <= mobSkill.getHP()) {
                            used = true;
                            mobSkill.applyEffect(chr, monster, true);
                        }
                    }
                }
            }
            if (!used) {
                realskill = 0;
                level = 0;
            }
        }

        slea.read(13);
        final Point startPos = slea.readPos();
        final List<LifeMovementFragment> res = MovementParse.parseMovement(slea, 2);

        c.sendPacket(
                MobPacket.moveMonsterResponse(
                        monster.getObjectId(),
                        moveid,
                        monster.getMp(),
                        monster.isControllerHasAggro(),
                        realskill,
                        level));

        if (res != null) {
            final long available = slea.available();
            if (available < 9 || available > 17) {
                log.error("slea.available != 17 (movement parsing error)");
                log.debug("{}", slea.toString(true));
                c.getSession().close();
                return;
            }

            MovementParse.updatePosition(res, monster, -1);
            final Point endPos = monster.getPosition();
            map.moveMonster(monster, endPos);
            map.broadcastMessage(
                    chr,
                    MobPacket.moveMonster(
                            useSkill,
                            skill,
                            skill1,
                            skill2,
                            skill3,
                            skill4,
                            monster.getObjectId(),
                            startPos,
                            endPos,
                            res),
                    endPos);
        }
    }

    public static final void FriendlyDamage(
            final SeekableLittleEndianAccessor slea, final MapleCharacter chr) {
        final MapleMap map = chr.getMap();
        if (map == null) {
            return;
        }
        final int fromOid = slea.readInt();
        slea.skip(4); // Player ID
        final int toOid = slea.readInt();

        final MapleMonster mobfrom = map.getMonsterByOid(fromOid);
        if (mobfrom == null) {
            return;
        }
        final MapleMonster mobto = map.getMonsterByOid(toOid);
        if (mobto == null) {
            return;
        }
        if (!mobto.getStats().isFriendly()) {
            return;
        }
        final int damage =
                (mobto.getStats().getLevel() * Randomizer.nextInt(99))
                        / 2; // Temp for now until I figure out something more effective
        mobto.damage(chr, damage, true);
        checkShammos(chr, mobto, map);
    }

    public static final void checkShammos(
            final MapleCharacter chr, final MapleMonster mobto, final MapleMap map) {
        if (!mobto.isAlive() && mobto.getId() == 9300275) { // shammos
            final List<MapleCharacter> chars = map.getCharactersThreadsafe();
            for (MapleCharacter chrz : chars) { // check for 2022698
                if (chrz.getParty() != null
                        && chrz.getParty().getLeader().getId() == chrz.getId()) {
                    // leader
                    if (chrz.haveItem(2022698)) {
                        MapleInventoryManipulator.removeById(
                                chrz.getClient(), MapleInventoryType.USE, 2022698, 1, false, true);
                        mobto.heal((int) mobto.getMobMaxHp(), mobto.getMobMaxMp(), true);
                        return;
                    }
                    break;
                }
            }
            map.broadcastMessage(
                    MaplePacketCreator.serverNotice(
                            6, "Your party has failed to protect the monster."));
            final MapleMap mapp =
                    chr.getClient().getChannelServer().getMapFactory().getMap(921120001);
            for (MapleCharacter chrz : chars) {
                chrz.changeMap(mapp, mapp.getPortal(0));
            }
        } else if (mobto.getId() == 9300275 && mobto.getEventInstance() != null) {
            mobto.getEventInstance().setProperty("HP", String.valueOf(mobto.getHp()));
        }
    }

    public static final void MonsterBomb(final int oid, final MapleCharacter chr) {
        final MapleMonster monster = chr.getMap().getMonsterByOid(oid);

        if (monster == null || !chr.isAlive() || chr.isHidden()) {
            return;
        }
        final byte selfd = monster.getStats().getSelfD();
        if (selfd != -1) {
            chr.getMap().killMonster(monster, chr, false, false, selfd);
        }
    }

    public static final void AutoAggro(final int monsteroid, final MapleCharacter chr) {
        if (chr == null) {
            return;
        }
        final MapleMap map = chr.getMap();
        if (map == null || chr.isHidden()) { // no evidence :)
            return;
        }
        final MapleMonster monster = map.getMonsterByOid(monsteroid);
        if (monster == null) {
            return;
        }
        if (chr.getPosition().distanceSq(monster.getPosition()) >= 200000) {
            return;
        }
        final MapleCharacter controller = monster.getController();
        if (controller == null) {
            monster.switchController(chr, true);
            return;
        }
        if (map.getCharacterById(controller.getId()) == null) {
            monster.switchController(chr, true);
        } else {
            monster.switchController(controller, true);
        }
    }

    public static final void HypnotizeDmg(
            final SeekableLittleEndianAccessor slea, final MapleCharacter chr) {
        final MapleMap map = chr.getMap();
        final MapleMonster mob_from = map.getMonsterByOid(slea.readInt()); // From
        slea.skip(4); // Player ID
        final int to = slea.readInt(); // mobto
        slea.skip(1); // Same as player damage, -1 = bump, integer = skill ID
        final int damage = slea.readInt();
        //	slea.skip(1); // Facing direction
        //	slea.skip(4); // Some type of pos, damage display, I think

        if (mob_from == null) {
            return;
        }

        final MapleMonster mob_to = map.getMonsterByOid(to);
        if (mob_to == null || !mob_to.getStats().isFriendly()) { // temp for now
            return;
        }

        if (damage > 30000) {
            return;
        }

        mob_to.damage(chr, damage, true);
        checkShammos(chr, mob_to, map);
    }

    public static final void DisplayNode(
            final SeekableLittleEndianAccessor slea, final MapleCharacter chr) {
        final MapleMonster mob_from = chr.getMap().getMonsterByOid(slea.readInt()); // From
        if (mob_from != null) {
            chr.getClient()
                    .sendPacket(MaplePacketCreator.getNodeProperties(mob_from, chr.getMap()));
        }
    }

    public static final void MobNode(
            final SeekableLittleEndianAccessor slea, final MapleCharacter chr) {
        final MapleMap map = chr.getMap();
        final MapleMonster mob_from = map.getMonsterByOid(slea.readInt()); // From
        final int newNode = slea.readInt();
        final int nodeSize = map.getNodes().size();
        if (mob_from == null || nodeSize <= 0 || nodeSize < newNode) {
            return;
        }
        final MapleNodeInfo mni = map.getNode(newNode);
        if (mni == null) {
            return;
        }
        if (mni.attr == 2) { // talk
            map.talkMonster(
                    "Please escort me carefully.",
                    5120035,
                    mob_from.getObjectId()); // temporary for now. itemID is located in WZ file
        }
        if (mob_from.getLastNode() >= newNode) {
            return;
        }
        mob_from.setLastNode(newNode);
        if (nodeSize != newNode) { // not the last node on the map
            return;
        }
        int newMap = -1;
        switch (chr.getMapId() / 100) {
            case 9211200:
                newMap = 921120100;
                break;
            case 9211201:
                newMap = 921120200;
                break;
            case 9211202:
                newMap = 921120300;
                break;
            case 9211203:
                newMap = 921120400;
                break;
            case 9211204:
                map.removeMonster(mob_from);
                break;
        }
        if (newMap > 0) {
            map.broadcastMessage(MaplePacketCreator.serverNotice(5, "Proceed to the next stage."));
            map.removeMonster(mob_from);
        }
    }
}
