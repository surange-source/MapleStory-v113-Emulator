package handling.channel.handler;

import client.ISkill;
import client.MapleCharacter;
import client.MapleClient;
import client.MapleStat;
import client.PlayerStats;
import client.SkillFactory;
import constants.GameConstants;
import java.util.ArrayList;
import java.util.List;
import server.Randomizer;
import tools.MaplePacketCreator;
import tools.Pair;
import tools.data.input.SeekableLittleEndianAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatsHandling {
    private static final Logger LOG = LoggerFactory.getLogger(StatsHandling.class);

    public static final void DistributeAP(
            final SeekableLittleEndianAccessor slea,
            final MapleClient c,
            final MapleCharacter chr) {
        final List<Pair<MapleStat, Integer>> statupdate = new ArrayList<>(2);
        c.sendPacket(MaplePacketCreator.updatePlayerStats(statupdate, true));
        chr.updateTick(slea.readInt());

        final PlayerStats stat = chr.getStat();
        final int job = chr.getJob();
        if (chr.getRemainingAp() > 0) {
            switch (slea.readInt()) {
                case 64: // Str
                    if (stat.getStr() >= 999) {
                        return;
                    }
                    stat.setStr((short) (stat.getStr() + 1));
                    statupdate.add(new Pair<>(MapleStat.STR, (int) stat.getStr()));
                    break;
                case 128: // Dex
                    if (stat.getDex() >= 999) {
                        return;
                    }
                    stat.setDex((short) (stat.getDex() + 1));
                    statupdate.add(new Pair<>(MapleStat.DEX, (int) stat.getDex()));
                    break;
                case 256: // Int
                    if (stat.getInt() >= 999) {
                        return;
                    }
                    stat.setInt((short) (stat.getInt() + 1));
                    statupdate.add(new Pair<>(MapleStat.INT, (int) stat.getInt()));
                    break;
                case 512: // Luk
                    if (stat.getLuk() >= 999) {
                        return;
                    }
                    stat.setLuk((short) (stat.getLuk() + 1));
                    statupdate.add(new Pair<>(MapleStat.LUK, (int) stat.getLuk()));
                    break;
                case 2048: // HP
                    short maxhp = stat.getMaxHp();
                    if (chr.getHpApUsed() >= 10000 || maxhp >= 30000) {
                        return;
                    }
                    if (job == 0) { // Beginner
                        maxhp += Randomizer.rand(8, 12);
                    } else if ((job >= 100 && job <= 132)
                            || (job >= 3200 && job <= 3212)) { // Warrior
                        ISkill improvingMaxHP = SkillFactory.getSkill(1000001);
                        int improvingMaxHPLevel = c.getPlayer().getSkillLevel(improvingMaxHP);
                        maxhp += Randomizer.rand(20, 25);
                        if (improvingMaxHPLevel >= 1) {
                            maxhp += improvingMaxHP.getEffect(improvingMaxHPLevel).getX();
                        }
                    } else if ((job >= 200 && job <= 232)) { // Magician
                        maxhp += Randomizer.rand(10, 20);
                    } else if ((job >= 300 && job <= 322)
                            || (job >= 400 && job <= 434)
                            || (job >= 1300 && job <= 1312)
                            || (job >= 1400 && job <= 1412)) { // Bowman
                        maxhp += Randomizer.rand(16, 20);
                    } else if ((job >= 500 && job <= 522)
                            || (job >= 3500 && job <= 3512)) { // Pirate
                        ISkill improvingMaxHP = SkillFactory.getSkill(5100000);
                        int improvingMaxHPLevel = c.getPlayer().getSkillLevel(improvingMaxHP);
                        maxhp += Randomizer.rand(18, 22);
                        if (improvingMaxHPLevel >= 1) {
                            maxhp += improvingMaxHP.getEffect(improvingMaxHPLevel).getY();
                        }
                    } else if (job >= 1500 && job <= 1512) { // Pirate
                        ISkill improvingMaxHP = SkillFactory.getSkill(15100000);
                        int improvingMaxHPLevel = c.getPlayer().getSkillLevel(improvingMaxHP);
                        maxhp += Randomizer.rand(18, 22);
                        if (improvingMaxHPLevel >= 1) {
                            maxhp += improvingMaxHP.getEffect(improvingMaxHPLevel).getY();
                        }
                    } else if (job >= 1100 && job <= 1112) { // Soul Master
                        ISkill improvingMaxHP = SkillFactory.getSkill(11000000);
                        int improvingMaxHPLevel = c.getPlayer().getSkillLevel(improvingMaxHP);
                        maxhp += Randomizer.rand(36, 42);
                        if (improvingMaxHPLevel >= 1) {
                            maxhp += improvingMaxHP.getEffect(improvingMaxHPLevel).getY();
                        }
                    } else if (job >= 1200 && job <= 1212) { // Flame Wizard
                        maxhp += Randomizer.rand(15, 21);
                    } else if (job >= 2000 && job <= 2112) { // Aran
                        maxhp += Randomizer.rand(40, 50);
                    } else { // GameMaster
                        maxhp += Randomizer.rand(50, 100);
                    }
                    maxhp = (short) Math.min(30000, Math.abs(maxhp));
                    chr.setHpApUsed((short) (chr.getHpApUsed() + 1));
                    stat.setMaxHp(maxhp);
                    statupdate.add(new Pair<>(MapleStat.MAXHP, (int) maxhp));
                    break;
                case 8192: // MP
                    short maxmp = stat.getMaxMp();
                    if (chr.getHpApUsed() >= 10000 || stat.getMaxMp() >= 30000) {
                        return;
                    }
                    if (job == 0) { // Beginner
                        maxmp += Randomizer.rand(6, 8);
                    } else if (job >= 100 && job <= 132) { // Warrior
                        maxmp += Randomizer.rand(2, 4);
                    } else if ((job >= 200 && job <= 232)) { // Magician
                        ISkill improvingMaxMP = SkillFactory.getSkill(2000001);
                        int improvingMaxMPLevel = c.getPlayer().getSkillLevel(improvingMaxMP);
                        maxmp += Randomizer.rand(18, 20);
                        if (improvingMaxMPLevel >= 1) {
                            maxmp += improvingMaxMP.getEffect(improvingMaxMPLevel).getY() * 2;
                        }
                    } else if ((job >= 300 && job <= 322)
                            || (job >= 400 && job <= 434)
                            || (job >= 500 && job <= 522)
                            || (job >= 1300 && job <= 1312)
                            || (job >= 1400 && job <= 1412)
                            || (job >= 1500 && job <= 1512)) { // Bowman
                        maxmp += Randomizer.rand(10, 12);
                    } else if (job >= 1100 && job <= 1112) { // Soul Master
                        maxmp += Randomizer.rand(6, 9);
                    } else if (job >= 1200 && job <= 1212) { // Flame Wizard
                        ISkill improvingMaxMP = SkillFactory.getSkill(12000000);
                        int improvingMaxMPLevel = c.getPlayer().getSkillLevel(improvingMaxMP);
                        maxmp += Randomizer.rand(18, 20);
                        if (improvingMaxMPLevel >= 1) {
                            maxmp += improvingMaxMP.getEffect(improvingMaxMPLevel).getY() * 2;
                        }
                    } else if (job >= 2000 && job <= 2112) { // Aran
                        maxmp += Randomizer.rand(6, 9);
                    } else { // GameMaster
                        maxmp += Randomizer.rand(50, 100);
                    }
                    maxmp += ((int) (stat.getTotalInt() / 15) + (int) (stat.getTotalInt() / 120));
                    maxmp = (short) Math.min(30000, Math.abs(maxmp));
                    chr.setHpApUsed((short) (chr.getHpApUsed() + 1));
                    stat.setMaxMp(maxmp);
                    statupdate.add(new Pair<>(MapleStat.MAXMP, (int) maxmp));
                    break;
                default:
                    c.sendPacket(
                            MaplePacketCreator.updatePlayerStats(
                                    MaplePacketCreator.EMPTY_STATUPDATE, true));
                    return;
            }
            chr.setRemainingAp((short) (chr.getRemainingAp() - 1));
            statupdate.add(new Pair<>(MapleStat.AVAILABLEAP, (int) chr.getRemainingAp()));
            c.sendPacket(MaplePacketCreator.updatePlayerStats(statupdate, true));
        }
    }

    public static final void DistributeSP(
            final int skillid, final MapleClient c, final MapleCharacter chr) {
        boolean isBeginnerSkill = false;
        final int remainingSp;

        switch (skillid) {
            case 1000:
            case 1001:
            case 1002:
                {
                    final int snailsLevel = chr.getSkillLevel(SkillFactory.getSkill(1000));
                    final int recoveryLevel = chr.getSkillLevel(SkillFactory.getSkill(1001));
                    final int nimbleFeetLevel = chr.getSkillLevel(SkillFactory.getSkill(1002));
                    remainingSp =
                            Math.min((chr.getLevel() - 1), 6)
                                    - snailsLevel
                                    - recoveryLevel
                                    - nimbleFeetLevel;
                    isBeginnerSkill = true;
                    break;
                }
            case 10001000:
            case 10001001:
            case 10001002:
                {
                    final int snailsLevel = chr.getSkillLevel(SkillFactory.getSkill(10001000));
                    final int recoveryLevel = chr.getSkillLevel(SkillFactory.getSkill(10001001));
                    final int nimbleFeetLevel = chr.getSkillLevel(SkillFactory.getSkill(10001002));
                    remainingSp =
                            Math.min((chr.getLevel() - 1), 6)
                                    - snailsLevel
                                    - recoveryLevel
                                    - nimbleFeetLevel;
                    isBeginnerSkill = true;
                    break;
                }
            case 20001000:
            case 20001001:
            case 20001002:
                {
                    final int snailsLevel = chr.getSkillLevel(SkillFactory.getSkill(20001000));
                    final int recoveryLevel = chr.getSkillLevel(SkillFactory.getSkill(20001001));
                    final int nimbleFeetLevel = chr.getSkillLevel(SkillFactory.getSkill(20001002));
                    remainingSp =
                            Math.min((chr.getLevel() - 1), 6)
                                    - snailsLevel
                                    - recoveryLevel
                                    - nimbleFeetLevel;
                    isBeginnerSkill = true;
                    break;
                }
            case 20011000:
            case 20011001:
            case 20011002:
                {
                    final int snailsLevel = chr.getSkillLevel(SkillFactory.getSkill(20011000));
                    final int recoveryLevel = chr.getSkillLevel(SkillFactory.getSkill(20011001));
                    final int nimbleFeetLevel = chr.getSkillLevel(SkillFactory.getSkill(20011002));
                    remainingSp =
                            Math.min((chr.getLevel() - 1), 6)
                                    - snailsLevel
                                    - recoveryLevel
                                    - nimbleFeetLevel;
                    isBeginnerSkill = true;
                    break;
                }
            case 30001000:
            case 30001001:
            case 30000002:
                {
                    final int snailsLevel = chr.getSkillLevel(SkillFactory.getSkill(30001000));
                    final int recoveryLevel = chr.getSkillLevel(SkillFactory.getSkill(30001001));
                    final int nimbleFeetLevel = chr.getSkillLevel(SkillFactory.getSkill(30000002));
                    remainingSp =
                            Math.min((chr.getLevel() - 1), 9)
                                    - snailsLevel
                                    - recoveryLevel
                                    - nimbleFeetLevel;
                    isBeginnerSkill = true; // resist can max ALL THREE
                    break;
                }
            default:
                {
                    remainingSp = chr.getRemainingSp();
                    break;
                }
        }
        final ISkill skill = SkillFactory.getSkill(skillid);

        if (skill.hasRequiredSkill()) {
            if (chr.getSkillLevel(SkillFactory.getSkill(skill.getRequiredSkillId()))
                    < skill.getRequiredSkillLevel()) {
                return;
            }
        }
        final int maxlevel = skill.isFourthJob() ? chr.getMasterLevel(skill) : skill.getMaxLevel();
        final int curLevel = chr.getSkillLevel(skill);

        if (skill.isInvisible() && curLevel == 0) {
            if ((skill.isFourthJob() && chr.getMasterLevel(skill) == 0)
                    || (!skill.isFourthJob() && maxlevel < 10 && !isBeginnerSkill)) {
                return;
            }
        }

        final int[] blocked = GameConstants.blockedSkills;
        if (blocked != null) {
            final int sid = skill.getId();
            for (int i = 0; i < blocked.length; i++) {
                if (blocked[i] == sid) {
                    chr.dropMessage(1, "You may not add this skill.");
                    return;
                }
            }
        }

        if ((remainingSp > 0 && curLevel + 1 <= maxlevel) && skill.canBeLearnedBy(chr.getJob())) {
            if (!isBeginnerSkill) {
                chr.setRemainingSp((short) (chr.getRemainingSp() - 1));
            }
            c.sendPacket(MaplePacketCreator.updateSp(chr, false));
            chr.changeSkillLevel(skill, (byte) (curLevel + 1), chr.getMasterLevel(skill));
        } else if (!skill.canBeLearnedBy(chr.getJob())) {
            return;
        }
    }

    public static final void AutoAssignAP(
            final SeekableLittleEndianAccessor slea,
            final MapleClient c,
            final MapleCharacter chr) {
        chr.updateTick(slea.readInt());
        slea.skip(4);
        if (slea.available() < 16) {
            LOG.debug("AutoAssignAP : \n{}", slea.toString(true));
            return;
        }
        final int PrimaryStat = slea.readInt();
        final int amount = slea.readInt();
        final int SecondaryStat = slea.readInt();
        final int amount2 = slea.readInt();
        if (amount < 0 || amount2 < 0) {
            return;
        }

        final PlayerStats playerst = chr.getStat();

        // 預先配置容量 3（主屬性 + 副屬性 + AVAILABLEAP），避免擴容
        final List<Pair<MapleStat, Integer>> statupdate = new ArrayList<>(3);
        c.sendPacket(MaplePacketCreator.updatePlayerStats(statupdate, true));

        if (chr.getRemainingAp() != amount + amount2) {
            return;
        }

        // 主屬性處理：先驗證上限，再寫入，避免中途 return 造成狀態不一致
        switch (PrimaryStat) {
            case 64: // Str
                if (playerst.getStr() + amount > 999) {
                    return;
                }
                playerst.setStr((short) (playerst.getStr() + amount));
                statupdate.add(new Pair<>(MapleStat.STR, (int) playerst.getStr()));
                break;
            case 128: // Dex
                if (playerst.getDex() + amount > 999) {
                    return;
                }
                playerst.setDex((short) (playerst.getDex() + amount));
                statupdate.add(new Pair<>(MapleStat.DEX, (int) playerst.getDex()));
                break;
            case 256: // Int
                if (playerst.getInt() + amount > 999) {
                    return;
                }
                playerst.setInt((short) (playerst.getInt() + amount));
                statupdate.add(new Pair<>(MapleStat.INT, (int) playerst.getInt()));
                break;
            case 512: // Luk
                if (playerst.getLuk() + amount > 999) {
                    return;
                }
                playerst.setLuk((short) (playerst.getLuk() + amount));
                statupdate.add(new Pair<>(MapleStat.LUK, (int) playerst.getLuk()));
                break;
            default:
                c.sendPacket(
                        MaplePacketCreator.updatePlayerStats(
                                MaplePacketCreator.EMPTY_STATUPDATE, true));
                return;
        }

        // 副屬性處理
        switch (SecondaryStat) {
            case 64: // Str
                if (playerst.getStr() + amount2 > 999) {
                    return;
                }
                playerst.setStr((short) (playerst.getStr() + amount2));
                statupdate.add(new Pair<>(MapleStat.STR, (int) playerst.getStr()));
                break;
            case 128: // Dex
                if (playerst.getDex() + amount2 > 999) {
                    return;
                }
                playerst.setDex((short) (playerst.getDex() + amount2));
                statupdate.add(new Pair<>(MapleStat.DEX, (int) playerst.getDex()));
                break;
            case 256: // Int
                if (playerst.getInt() + amount2 > 999) {
                    return;
                }
                playerst.setInt((short) (playerst.getInt() + amount2));
                statupdate.add(new Pair<>(MapleStat.INT, (int) playerst.getInt()));
                break;
            case 512: // Luk
                if (playerst.getLuk() + amount2 > 999) {
                    return;
                }
                playerst.setLuk((short) (playerst.getLuk() + amount2));
                statupdate.add(new Pair<>(MapleStat.LUK, (int) playerst.getLuk()));
                break;
            default:
                c.sendPacket(
                        MaplePacketCreator.updatePlayerStats(
                                MaplePacketCreator.EMPTY_STATUPDATE, true));
                return;
        }

        chr.setRemainingAp((short) (chr.getRemainingAp() - (amount + amount2)));
        statupdate.add(new Pair<>(MapleStat.AVAILABLEAP, (int) chr.getRemainingAp()));
        c.sendPacket(MaplePacketCreator.updatePlayerStats(statupdate, true));
    }
}
