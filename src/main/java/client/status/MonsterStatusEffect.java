package client.status;

import java.util.concurrent.ScheduledFuture;
import server.life.MobSkill;

public class MonsterStatusEffect {

    private MonsterStatus stati;
    private final int skill;
    private final MobSkill mobskill;
    private final boolean monsterSkill;
    private Integer x;
    private ScheduledFuture<?> cancelTask;
    private ScheduledFuture<?> poisonSchedule;
    private ScheduledFuture<?> poisonStatusSchedule;

    public MonsterStatusEffect(
            final MonsterStatus stat,
            final Integer x,
            final int skillId,
            final MobSkill mobskill,
            final boolean monsterSkill) {
        this.stati = stat;
        this.skill = skillId;
        this.monsterSkill = monsterSkill;
        // 防禦性拷貝：避免外部可變引用洩漏 (EI_EXPOSE_REP2)
        this.mobskill = mobskill == null ? null : new MobSkill(mobskill);
        this.x = x;
    }

    public final MonsterStatus getStati() {
        return stati;
    }

    public final Integer getX() {
        return x;
    }

    public final void setValue(final MonsterStatus status, final Integer newVal) {
        stati = status;
        x = newVal;
    }

    public final int getSkill() {
        return skill;
    }

    public final MobSkill getMobSkill() {
        // 防禦性拷貝：避免內部可變引用洩漏 (EI_EXPOSE_REP)
        return mobskill == null ? null : new MobSkill(mobskill);
    }

    public final boolean isMonsterSkill() {
        return monsterSkill;
    }

    public final void setCancelTask(final ScheduledFuture<?> cancelTask) {
        this.cancelTask = cancelTask;
    }

    public final void setPoisonSchedule(final ScheduledFuture<?> poisonSchedule) {
        this.poisonSchedule = poisonSchedule;
    }

    public final void setPoisonStatusSchedule(final ScheduledFuture<?> poisonStatusSchedule) {
        this.poisonStatusSchedule = poisonStatusSchedule;
    }

    public final void cancelTask() {
        if (this.cancelTask != null) {
            this.cancelTask.cancel(false);
        }
        this.cancelTask = null;
    }

    public final void cancelPoisonSchedule() {
        if (this.poisonSchedule != null) {
            try {
                this.poisonSchedule.cancel(false);
            } catch (NullPointerException e) {
            } // set to null anyway.
        }
        this.poisonSchedule = null;

        if (this.poisonStatusSchedule != null) {
            try {
                this.poisonStatusSchedule.cancel(false);
            } catch (NullPointerException e) {
            } // set to null anyway.
        }
        this.poisonStatusSchedule = null;
    }
}
