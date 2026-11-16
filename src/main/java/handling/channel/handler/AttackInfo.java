package handling.channel.handler;

import client.ISkill;
import client.MapleCharacter;
import client.SkillFactory;
import constants.GameConstants;
import java.awt.Point;
import java.util.List;
import server.AutobanManager;
import server.MapleStatEffect;
import tools.AttackPair;

public record AttackInfo(State state) {

    /**
     * 可變狀態容器，讓 record 元件保持 final 的同時仍能支援既有 setter 語意。 此物件為單一攻擊封包的解析結果，生命週期短暫且僅由單一執行緒（該連線的 handler
     * thread）存取， 因此欄位使用 volatile 以確保跨執行緒可見性，避免不必要的 synchronized 競爭。
     */
    public static final class State {

        public volatile int skill;
        public volatile int charge;
        public volatile int lastAttackTickCount;
        public volatile List<AttackPair> allDamage;
        public volatile Point position;
        public volatile byte hits;
        public volatile byte targets;
        public volatile byte tbyte;
        public volatile byte display;
        public volatile byte animation;
        public volatile byte speed;
        public volatile byte csstar;
        public volatile byte AOE;
        public volatile byte slot;
        public volatile byte unk;
        public volatile boolean real = true;
    }

    public AttackInfo() {
        this(new State());
    }

    public int skill() {
        return state.skill;
    }

    public int charge() {
        return state.charge;
    }

    public int lastAttackTickCount() {
        return state.lastAttackTickCount;
    }

    public List<AttackPair> allDamage() {
        return state.allDamage;
    }

    public Point position() {
        return state.position;
    }

    public byte hits() {
        return state.hits;
    }

    public byte targets() {
        return state.targets;
    }

    public byte tbyte() {
        return state.tbyte;
    }

    public byte display() {
        return state.display;
    }

    public byte animation() {
        return state.animation;
    }

    public byte speed() {
        return state.speed;
    }

    public byte csstar() {
        return state.csstar;
    }

    public byte AOE() {
        return state.AOE;
    }

    public byte slot() {
        return state.slot;
    }

    public byte unk() {
        return state.unk;
    }

    public boolean real() {
        return state.real;
    }

    public void setSkill(int skill) {
        state.skill = skill;
    }

    public void setCharge(int charge) {
        state.charge = charge;
    }

    public void setLastAttackTickCount(int lastAttackTickCount) {
        state.lastAttackTickCount = lastAttackTickCount;
    }

    public void setAllDamage(List<AttackPair> allDamage) {
        state.allDamage = allDamage;
    }

    public void setPosition(Point position) {
        state.position = position;
    }

    public void setHits(byte hits) {
        state.hits = hits;
    }

    public void setTargets(byte targets) {
        state.targets = targets;
    }

    public void setTbyte(byte tbyte) {
        state.tbyte = tbyte;
    }

    public void setDisplay(byte display) {
        state.display = display;
    }

    public void setAnimation(byte animation) {
        state.animation = animation;
    }

    public void setSpeed(byte speed) {
        state.speed = speed;
    }

    public void setCsstar(byte csstar) {
        state.csstar = csstar;
    }

    public void setAOE(byte AOE) {
        state.AOE = AOE;
    }

    public void setSlot(byte slot) {
        state.slot = slot;
    }

    public void setUnk(byte unk) {
        state.unk = unk;
    }

    public void setReal(boolean real) {
        state.real = real;
    }

    public final MapleStatEffect getAttackEffect(
            final MapleCharacter chr, int skillLevel, final ISkill skill_) {
        final int skillId = skill();
        if (GameConstants.isMulungSkill(skillId) || GameConstants.isPyramidSkill(skillId)) {
            skillLevel = 1;
        } else if (skillLevel <= 0) {
            return null;
        }
        if (GameConstants.isLinkedAranSkill(skillId)) {
            final ISkill skillLink = SkillFactory.getSkill(skillId);
            if (skillLink == null) {
                return null;
            }
            if (display() > 80) {
                if (!skillLink.getAction()) {
                    AutobanManager.getInstance()
                            .autoban(
                                    chr.getClient(),
                                    "No delay hack, SkillID : "
                                            + skillId); // 2 of the same autobans? wtf...
                    return null;
                }
            }
            return skillLink.getEffect(skillLevel);
        }
        if (skill_ == null) {
            return null;
        }
        if (display() > 80) {
            if (!skill_.getAction()) {
                AutobanManager.getInstance()
                        .autoban(chr.getClient(), "No delay hack, SkillID : " + skillId);
                return null;
            }
        }
        return skill_.getEffect(skillLevel);
    }
}
