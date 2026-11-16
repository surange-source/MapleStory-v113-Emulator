package server;

import client.MapleDisease;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import provider.MapleData;
import provider.MapleDataProvider;
import provider.MapleDataProviderFactory;
import provider.MapleDataTool;
import server.life.MobSkill;
import server.life.MobSkillFactory;

public final class MapleCarnivalFactory {

    private final Map<Integer, MCSkill> skills = new HashMap<>();
    private final Map<Integer, MCSkill> guardians = new HashMap<>();
    private final MapleDataProvider dataRoot =
            MapleDataProviderFactory.getDataProvider(
                    new File(ServerProperties.getProperty("tms.Wzpath") + "/Skill.wz"));

    private MapleCarnivalFactory() {
        // whoosh
        initialize();
    }

    private static final class Holder {
        private static final MapleCarnivalFactory INSTANCE = new MapleCarnivalFactory();
    }

    public static final MapleCarnivalFactory getInstance() {
        return Holder.INSTANCE;
    }

    private void initialize() {
        if (skills.size() != 0) {
            return;
        }
        for (MapleData z : dataRoot.getData("MCSkill.img")) {
            skills.put(
                    Integer.parseInt(z.getName()),
                    new MCSkill(
                            MapleDataTool.getInt("spendCP", z, 0),
                            MapleDataTool.getInt("mobSkillID", z, 0),
                            MapleDataTool.getInt("level", z, 0),
                            MapleDataTool.getInt("target", z, 1) > 1));
        }
        for (MapleData z : dataRoot.getData("MCGuardian.img")) {
            guardians.put(
                    Integer.parseInt(z.getName()),
                    new MCSkill(
                            MapleDataTool.getInt("spendCP", z, 0),
                            MapleDataTool.getInt("mobSkillID", z, 0),
                            MapleDataTool.getInt("level", z, 0),
                            true));
        }
    }

    public MCSkill getSkill(final int id) {
        return skills.get(id);
    }

    public MCSkill getGuardian(final int id) {
        return guardians.get(id);
    }

    public static class MCSkill {

        public int cpLoss, mobSkillId, level;
        public boolean targetsAll;

        public MCSkill(int _cpLoss, int _skillid, int _level, boolean _targetsAll) {
            cpLoss = _cpLoss;
            mobSkillId = _skillid;
            level = _level;
            targetsAll = _targetsAll;
        }

        public MobSkill getMobSkill() {
            return MobSkillFactory.getMobSkill(mobSkillId, level);
        }

        public MapleDisease getDisease() {
            if (mobSkillId <= 0) {
                return MapleDisease.getRandom();
            }
            return MapleDisease.getBySkill(mobSkillId);
        }
    }
}
