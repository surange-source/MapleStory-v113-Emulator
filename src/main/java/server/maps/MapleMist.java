package server.maps;

import client.ISkill;
import client.MapleCharacter;
import client.MapleClient;
import client.SkillFactory;
import handling.MaplePacket;
import java.awt.Point;
import java.awt.Rectangle;
import server.MapleStatEffect;
import server.life.MapleMonster;
import server.life.MobSkill;
import tools.MaplePacketCreator;

public class MapleMist extends AbstractMapleMapObject {

    private final Rectangle mistPosition;
    private final MapleStatEffect source;
    private final MobSkill skill;
    private final boolean isMobMist;
    private final int skillDelay, skilllevel, isPoisonMist, ownerId;

    public MapleMist(Rectangle mistPosition, MapleMonster mob, MobSkill skill) {
        this.mistPosition = mistPosition;
        this.ownerId = mob.getId();
        this.skill = skill;
        this.skilllevel = skill.getSkillLevel();

        isMobMist = true;
        isPoisonMist = 0;
        skillDelay = 0;
        this.source = null;
    }

    public MapleMist(Rectangle mistPosition, MapleCharacter owner, MapleStatEffect source) {
        this.mistPosition = mistPosition;
        this.ownerId = owner.getId();
        this.source = source;
        this.skillDelay = 8;
        this.isMobMist = false;
        this.skilllevel = owner.getSkillLevel(SkillFactory.getSkill(source.getSourceId()));
        this.skill = null;

        int poison = 0;
        switch (source.getSourceId()) {
            case 4221006: // Smoke Screen
                poison = 0;
                break;
            case 14111006:
            case 2111003: // FP mist
            case 12111005: // Flame wizard, [Flame Gear]
                poison = 1;
                break;
            case 22161003: // Recovery Aura
                poison = 2;
                break;
        }
        this.isPoisonMist = poison;
    }

    // fake
    public MapleMist(Rectangle mistPosition, MapleCharacter owner) {
        this.mistPosition = mistPosition;
        this.ownerId = owner.getId();
        MapleStatEffect eff = new MapleStatEffect();
        eff.setSourceId(2111003);
        this.source = eff;
        this.skilllevel = 30;
        isMobMist = false;
        isPoisonMist = 0;
        skillDelay = 8;
        this.skill = null;
    }

    @Override
    public MapleMapObjectType getType() {
        return MapleMapObjectType.MIST;
    }

    @Override
    public Point getPosition() {
        return mistPosition.getLocation();
    }

    public ISkill getSourceSkill() {
        return SkillFactory.getSkill(source.getSourceId());
    }

    public boolean isMobMist() {
        return isMobMist;
    }

    public int isPoisonMist() {
        return isPoisonMist;
    }

    public int getSkillDelay() {
        return skillDelay;
    }

    public int getSkillLevel() {
        return skilllevel;
    }

    public int getOwnerId() {
        return ownerId;
    }

    public MobSkill getMobSkill() {
        return this.skill;
    }

    public Rectangle getBox() {
        return mistPosition;
    }

    public MapleStatEffect getSource() {
        return source;
    }

    @Override
    public void setPosition(Point position) {}

    public MaplePacket fakeSpawnData(int level) {
        return MaplePacketCreator.spawnMist(this);
    }

    @Override
    public void sendSpawnData(final MapleClient c) {
        c.sendPacket(MaplePacketCreator.spawnMist(this));
    }

    @Override
    public void sendDestroyData(final MapleClient c) {
        c.sendPacket(MaplePacketCreator.removeMist(getObjectId(), false));
    }

    public boolean makeChanceResult() {
        return source.makeChanceResult();
    }
}
