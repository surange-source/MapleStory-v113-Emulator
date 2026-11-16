package server.quest;

import client.ISkill;
import client.MapleCharacter;
import client.MapleQuestStatus;
import client.MapleStat;
import client.SkillFactory;
import client.inventory.InventoryException;
import client.inventory.MapleInventoryType;
import constants.GameConstants;
import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import server.MapleInventoryManipulator;
import server.MapleItemInformationProvider;
import server.RandomRewards;
import server.Randomizer;
import tools.MaplePacketCreator;
import tools.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MapleQuestAction implements Serializable {
    private static final Logger log = LoggerFactory.getLogger(MapleQuestAction.class);

    private static final long serialVersionUID = 9179541993413738569L;
    private MapleQuestActionType type;
    private MapleQuest quest;
    private int intStore = 0;
    private List<Integer> applicableJobs = new ArrayList();
    private transient List<QuestItem> items = null;

    public record SkillInfo(int skillId, int skillLevel, int masterLevel) implements Serializable {}

    private transient List<SkillInfo> skills = null;
    private List<Pair<Integer, Integer>> state = null;

    public MapleQuestAction(
            MapleQuestActionType type,
            ResultSet rse,
            MapleQuest quest,
            PreparedStatement pss,
            PreparedStatement psq,
            PreparedStatement psi)
            throws SQLException {
        this.type = type;
        this.quest = quest;

        this.intStore = rse.getInt("intStore");
        String[] jobs = rse.getString("applicableJobs").split(", ");
        if ((jobs.length <= 0) && (rse.getString("applicableJobs").length() > 0)) {
            this.applicableJobs.add(Integer.valueOf(rse.getString("applicableJobs")));
        }
        for (String j : jobs) {
            if (j.length() > 0) {
                this.applicableJobs.add(Integer.valueOf(j));
            }
        }
        switch (type) {
            case item:
                this.items = new ArrayList();
                psi.setInt(1, rse.getInt("uniqueid"));
                try (ResultSet rs = psi.executeQuery()) {
                    while (rs.next()) {
                        this.items.add(
                                new QuestItem(
                                        rs.getInt("itemid"),
                                        rs.getInt("count"),
                                        rs.getInt("period"),
                                        rs.getInt("gender"),
                                        rs.getInt("job"),
                                        rs.getInt("jobEx"),
                                        rs.getInt("prop")));
                    }
                }
                break;
            case quest:
                this.state = new ArrayList();
                psq.setInt(1, rse.getInt("uniqueid"));
                try (ResultSet rs = psq.executeQuery()) {
                    while (rs.next()) {
                        this.state.add(new Pair(rs.getInt("quest"), rs.getInt("state")));
                    }
                }
                break;
            case skill:
                this.skills = new ArrayList();
                pss.setInt(1, rse.getInt("uniqueid"));
                try (ResultSet rs = pss.executeQuery()) {
                    while (rs.next()) {
                        this.skills.add(
                                new SkillInfo(
                                        rs.getInt("skillid"),
                                        rs.getInt("skillLevel"),
                                        rs.getInt("masterLevel")));
                    }
                }
                break;
            default:
                break;
        }
    }

    private static boolean canGetItem(QuestItem item, MapleCharacter c) {
        if ((item.gender != 2) && (item.gender >= 0) && (item.gender != c.getGender())) {
            return false;
        }
        if (item.job > 0) {
            List code = getJobBy5ByteEncoding(item.job);
            boolean jobFound = false;
            for (Iterator i$ = code.iterator(); i$.hasNext(); ) {
                int codec = ((Integer) i$.next());
                if (codec / 100 == c.getJob() / 100) {
                    jobFound = true;
                    break;
                }
            }
            Iterator i$;
            if ((!jobFound) && (item.jobEx > 0)) {
                List codeEx = getJobBySimpleEncoding(item.jobEx);
                for (i$ = codeEx.iterator(); i$.hasNext(); ) {
                    int codec = ((Integer) i$.next());
                    if (codec / 100 % 10 == c.getJob() / 100 % 10) {
                        jobFound = true;
                        break;
                    }
                }
            }
            return jobFound;
        }
        return true;
    }

    public final boolean RestoreLostItem(final MapleCharacter c, final int itemid) {
        if (type == MapleQuestActionType.item) {
            for (QuestItem item : this.items) {
                if (item.itemid == itemid) {
                    if (!c.haveItem(item.itemid, item.count, true, false)) {
                        MapleInventoryManipulator.addById(
                                c.getClient(),
                                item.itemid,
                                (short) item.count,
                                "Obtained from quest (Restored) "
                                        + this.quest.getId()
                                        + " on "
                                        + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public void runStart(MapleCharacter c, Integer extSelection) {
        MapleQuestStatus status;
        int selection;
        int extNum;
        switch (type) {
            case exp:
                status = c.getQuest(quest);
                if (status.getForfeited() > 0) {
                    break;
                }
                c.gainExp(
                        this.intStore * GameConstants.getExpRate_Quest(c.getLevel()),
                        true,
                        true,
                        true);
                break;
            case item:
                Map props = new HashMap();
                for (QuestItem item : this.items) {
                    if ((item.prop > 0) && (canGetItem(item, c))) {
                        for (int i = 0; i < item.prop; i++) {
                            props.put(props.size(), item.itemid);
                        }
                    }
                }
                selection = 0;
                extNum = 0;
                if (!props.isEmpty()) {
                    selection = ((Integer) props.get(Randomizer.nextInt(props.size())));
                }
                for (QuestItem item : this.items) {
                    if (!canGetItem(item, c)) {
                        continue;
                    }
                    int id = item.itemid;
                    if ((item.prop == -2)
                            || (item.prop == -1
                                    ? (extSelection != null) && (extSelection == extNum++)
                                    : id == selection)) {
                        short count = (short) item.count;
                        if (count < 0) {
                            try {
                                MapleInventoryManipulator.removeById(
                                        c.getClient(),
                                        GameConstants.getInventoryType(id),
                                        id,
                                        count * -1,
                                        true,
                                        false);
                            } catch (InventoryException ie) {
                                log.error("[h4x] Completing a quest without meeting the requirements", ie);
                            }
                            c.getClient()
                                    .getSession()
                                    .writeAndFlush(
                                            MaplePacketCreator.getShowItemGain(id, count, true));
                        } else {
                            int period = item.period / 1440;
                            String name = MapleItemInformationProvider.getInstance().getName(id);
                            if ((id / 10000 == 114) && (name != null) && (name.length() > 0)) {
                                String msg = "你已獲得稱號 <" + name + ">";
                                c.dropMessage(-1, msg);
                                c.dropMessage(5, msg);
                            }
                            MapleInventoryManipulator.addById(
                                    c.getClient(), id, count, "", null, period);
                            c.getClient()
                                    .getSession()
                                    .writeAndFlush(
                                            MaplePacketCreator.getShowItemGain(id, count, true));
                        }
                    }
                }
                break;
            case nextQuest:
                status = c.getQuest(quest);
                if (status.getForfeited() > 0) {
                    break;
                }
                c.getClient()
                        .sendPacket(
                                MaplePacketCreator.updateQuestFinish(
                                        quest.getId(), status.getNpc(), this.intStore));
                break;
            case money:
                status = c.getQuest(quest);
                if (status.getForfeited() > 0) {
                    break;
                }
                c.gainMeso(this.intStore, true, false, true);
                break;
            case quest:
                for (Pair q : this.state) {
                    c.updateQuest(
                            new MapleQuestStatus(
                                    MapleQuest.getInstance(((Integer) q.left())),
                                    ((Integer) q.right()).byteValue()));
                }
                break;
            case skill:
                for (SkillInfo skills : this.skills) {
                    int skillid = ((Integer) skills.skillId);
                    int skillLevel = ((Integer) skills.skillLevel);
                    int masterLevel = ((Integer) skills.masterLevel);
                    ISkill skillObject = SkillFactory.getSkill(skillid);
                    boolean found = false;
                    for (Iterator i$ = this.applicableJobs.iterator(); i$.hasNext(); ) {
                        int applicableJob = ((Integer) i$.next());
                        if (c.getJob() == applicableJob) {
                            found = true;
                            break;
                        }
                    }
                    if ((skillObject.isBeginnerSkill()) || (found)) {
                        c.changeSkillLevel(
                                skillObject,
                                (byte) Math.max(skillLevel, c.getSkillLevel(skillObject)),
                                (byte) Math.max(masterLevel, c.getMasterLevel(skillObject)));
                    }
                }
                break;
            case pop:
                status = c.getQuest(quest);
                if (status.getForfeited() > 0) {
                    break;
                }
                final int fameGain = this.intStore;
                c.addFame(fameGain);
                c.updateSingleStat(MapleStat.FAME, c.getFame());
                c.getClient().sendPacket(MaplePacketCreator.getShowFameGain(fameGain));
                break;
            case buffItemID:
                status = c.getQuest(quest);
                if (status.getForfeited() > 0) {
                    break;
                }
                final int tobuff = this.intStore;
                if (tobuff == -1) {
                    break;
                }
                MapleItemInformationProvider.getInstance().getItemEffect(tobuff).applyTo(c);
                break;
            case infoNumber:
                {
                    //		System.out.println("quest : "+MapleDataTool.getInt(data, 0)+"");
                    //		MapleQuest.getInstance(MapleDataTool.getInt(data, 0)).forceComplete(c, 0);
                    break;
                }
            case sp:
                {
                    status = c.getQuest(quest);
                    if (status.getForfeited() > 0) {
                        break;
                    }
                    int sp_val = this.intStore;
                    if (!this.applicableJobs.isEmpty()) {
                        int finalJob = 0;
                        for (Iterator i$ = this.applicableJobs.iterator(); i$.hasNext(); ) {
                            int job_val = ((Integer) i$.next());
                            if ((c.getJob() >= job_val) && (job_val > finalJob)) {
                                finalJob = job_val;
                            }
                        }
                        if (finalJob == 0) {
                            c.gainSP(sp_val);
                        } else {
                            c.gainSP(sp_val);
                        }
                    } else {
                        c.gainSP(sp_val);
                    }
                    break;
                }
            default:
                break;
        }
    }

    public boolean checkEnd(MapleCharacter c, Integer extSelection) {
        switch (type) {
            case item:
                {
                    Map props = new HashMap();

                    for (QuestItem item : this.items) {
                        if ((item.prop > 0) && (canGetItem(item, c))) {
                            for (int i = 0; i < item.prop; i++) {
                                props.put(props.size(), item.itemid);
                            }
                        }
                    }
                    int selection = 0;
                    int extNum = 0;
                    if (!props.isEmpty()) {
                        selection = ((Integer) props.get(Randomizer.nextInt(props.size())));
                    }
                    byte eq = 0;
                    byte use = 0;
                    byte setup = 0;
                    byte etc = 0;
                    byte cash = 0;

                    for (QuestItem item : this.items) {
                        if (!canGetItem(item, c)) {
                            continue;
                        }
                        int id = item.itemid;
                        if ((item.prop == -2)
                                || (item.prop == -1
                                        ? (extSelection != null) && (extSelection == extNum++)
                                        : id == selection)) {
                            short count = (short) item.count;
                            if (count < 0) {
                                if (!c.haveItem(id, count, false, true)) {
                                    c.dropMessage(1, "您是短一些項目來完成任務。");
                                    return false;
                                }
                            } else {
                                if (MapleItemInformationProvider.getInstance()
                                                .isPickupRestricted(id)
                                        && c.haveItem(id, 1, true, false)) {
                                    c.dropMessage(
                                            1,
                                            "你已經有了這個道具: "
                                                    + MapleItemInformationProvider.getInstance()
                                                            .getName(id));
                                    return false;
                                }
                                switch (GameConstants.getInventoryType(id)) {
                                    case EQUIP:
                                        eq = (byte) (eq + 1);
                                        break;
                                    case USE:
                                        use = (byte) (use + 1);
                                        break;
                                    case SETUP:
                                        setup = (byte) (setup + 1);
                                        break;
                                    case ETC:
                                        etc = (byte) (etc + 1);
                                        break;
                                    case CASH:
                                        cash = (byte) (cash + 1);
                                }
                            }
                        }
                    }
                    if (c.getInventory(MapleInventoryType.EQUIP).getNumFreeSlot() < eq) {
                        c.dropMessage(1, "請確認裝備欄是否滿了。");
                        return false;
                    } else if (c.getInventory(MapleInventoryType.USE).getNumFreeSlot() < use) {
                        c.dropMessage(1, "請確認消耗欄是否滿了。");
                        return false;
                    } else if (c.getInventory(MapleInventoryType.SETUP).getNumFreeSlot() < setup) {
                        c.dropMessage(1, "請確認裝飾欄是否滿了。");
                        return false;
                    } else if (c.getInventory(MapleInventoryType.ETC).getNumFreeSlot() < etc) {
                        c.dropMessage(1, "請確認其他欄是否滿了。");
                        return false;
                    } else if (c.getInventory(MapleInventoryType.CASH).getNumFreeSlot() < cash) {
                        c.dropMessage(1, "請確認特殊欄是否滿了。");
                        return false;
                    }
                    return true;
                }
            case money:
                {
                    final int meso = this.intStore;
                    if (c.getMeso() + meso < 0) { // Giving, overflow
                        c.dropMessage(1, "楓幣不足.");
                        return false;
                    } else if (meso < 0 && c.getMeso() < Math.abs(meso)) { // remove meso
                        c.dropMessage(1, "楓幣不足.");
                        return false;
                    }
                    return true;
                }
        }
        return true;
    }

    public void runEnd(MapleCharacter c, Integer extSelection) {
        switch (type) {
            case exp:
                {
                    c.gainExp(
                            this.intStore * GameConstants.getExpRate_Quest(c.getLevel()),
                            true,
                            true,
                            true);
                    break;
                }
            case item:
                {
                    Map props = new HashMap();
                    for (QuestItem item : this.items) {
                        if ((item.prop > 0) && (canGetItem(item, c))) {
                            for (int i = 0; i < item.prop; i++) {
                                props.put(props.size(), item.itemid);
                            }
                        }
                    }
                    int selection = 0;
                    int extNum = 0;
                    if (!props.isEmpty()) {
                        selection = ((Integer) props.get(Randomizer.nextInt(props.size())));
                    }
                    for (QuestItem item : this.items) {
                        if (!canGetItem(item, c)) {
                            continue;
                        }
                        int id = item.itemid;
                        if ((item.prop == -2)
                                || (item.prop == -1
                                        ? (extSelection != null) && (extSelection == extNum++)
                                        : id == selection)) {
                            short count = (short) item.count;
                            if (count < 0) {
                                MapleInventoryManipulator.removeById(
                                        c.getClient(),
                                        GameConstants.getInventoryType(id),
                                        id,
                                        count * -1,
                                        true,
                                        false);
                                c.getClient()
                                        .getSession()
                                        .writeAndFlush(
                                                MaplePacketCreator.getShowItemGain(
                                                        id, count, true));
                            } else {
                                int period = item.period / 1440;
                                String name =
                                        MapleItemInformationProvider.getInstance().getName(id);
                                if ((id / 10000 == 114) && (name != null) && (name.length() > 0)) {
                                    String msg = "你獲得了勳章 <" + name + ">";
                                    c.dropMessage(-1, msg);
                                    c.dropMessage(5, msg);
                                }
                                MapleInventoryManipulator.addById(
                                        c.getClient(), id, count, "", null, period);
                                c.getClient()
                                        .getSession()
                                        .writeAndFlush(
                                                MaplePacketCreator.getShowItemGain(
                                                        id, count, true));
                            }
                        }
                    }
                    break;
                }
            case nextQuest:
                {
                    c.getClient()
                            .sendPacket(
                                    MaplePacketCreator.updateQuestFinish(
                                            quest.getId(),
                                            c.getQuest(quest).getNpc(),
                                            this.intStore));
                    break;
                }
            case money:
                {
                    c.gainMeso(this.intStore, true, false, true);
                    break;
                }
            case quest:
                {
                    for (Pair<Integer, Integer> q : this.state) {
                        c.updateQuest(
                                new MapleQuestStatus(
                                        MapleQuest.getInstance((q.left())),
                                        (q.right()).byteValue()));
                    }
                    break;
                }
            case skill:
                {
                    for (SkillInfo skills : this.skills) {
                        int skillid = ((Integer) skills.skillId);
                        int skillLevel = ((Integer) skills.skillLevel);
                        int masterLevel = ((Integer) skills.masterLevel);
                        ISkill skillObject = SkillFactory.getSkill(skillid);
                        boolean found = false;
                        for (Iterator i$ = this.applicableJobs.iterator(); i$.hasNext(); ) {
                            int applicableJob = ((Integer) i$.next());
                            if (c.getJob() == applicableJob) {
                                found = true;
                                break;
                            }
                        }
                        if ((skillObject.isBeginnerSkill()) || (found)) {
                            c.changeSkillLevel(
                                    skillObject,
                                    (byte) Math.max(skillLevel, c.getSkillLevel(skillObject)),
                                    (byte) Math.max(masterLevel, c.getMasterLevel(skillObject)));
                        }
                    }
                    break;
                }
            case pop:
                {
                    final int fameGain = this.intStore;
                    c.addFame(fameGain);
                    c.updateSingleStat(MapleStat.FAME, c.getFame());
                    c.getClient().sendPacket(MaplePacketCreator.getShowFameGain(fameGain));
                    break;
                }
            case buffItemID:
                {
                    final int tobuff = this.intStore;
                    if (tobuff == -1) {
                        break;
                    }
                    MapleItemInformationProvider.getInstance().getItemEffect(tobuff).applyTo(c);
                    break;
                }
            case infoNumber:
                {
                    //		System.out.println("quest : "+MapleDataTool.getInt(data, 0)+"");
                    //		MapleQuest.getInstance(MapleDataTool.getInt(data, 0)).forceComplete(c, 0);
                    break;
                }
            case sp:
                {
                    int sp_val = this.intStore;
                    if (!this.applicableJobs.isEmpty()) {
                        int finalJob = 0;
                        for (Iterator i$ = this.applicableJobs.iterator(); i$.hasNext(); ) {
                            int job_val = ((Integer) i$.next());
                            if ((c.getJob() >= job_val) && (job_val > finalJob)) {
                                finalJob = job_val;
                            }
                        }
                        if (finalJob == 0) {
                            c.gainSP(sp_val);
                        } else {
                            c.gainSP(sp_val);
                        }
                    } else {
                        c.gainSP(sp_val);
                    }
                    break;
                }
            default:
                break;
        }
    }

    private static List<Integer> getJobBy5ByteEncoding(int encoded) {
        List<Integer> ret = new ArrayList<>();
        if ((encoded & 0x1) != 0) {
            ret.add(0);
        }
        if ((encoded & 0x2) != 0) {
            ret.add(100);
        }
        if ((encoded & 0x4) != 0) {
            ret.add(200);
        }
        if ((encoded & 0x8) != 0) {
            ret.add(300);
        }
        if ((encoded & 0x10) != 0) {
            ret.add(400);
        }
        if ((encoded & 0x20) != 0) {
            ret.add(500);
        }
        if ((encoded & 0x400) != 0) {
            ret.add(1000);
        }
        if ((encoded & 0x800) != 0) {
            ret.add(1100);
        }
        if ((encoded & 0x1000) != 0) {
            ret.add(1200);
        }
        if ((encoded & 0x2000) != 0) {
            ret.add(1300);
        }
        if ((encoded & 0x4000) != 0) {
            ret.add(1400);
        }
        if ((encoded & 0x8000) != 0) {
            ret.add(1500);
        }
        if ((encoded & 0x20000) != 0) {
            ret.add(2001); // im not sure of this one
            ret.add(2200);
        }
        if ((encoded & 0x100000) != 0) {
            ret.add(2000);
            ret.add(2001); // ?
        }
        if ((encoded & 0x200000) != 0) {
            ret.add(2100);
        }
        if ((encoded & 0x400000) != 0) {
            ret.add(2001); // ?
            ret.add(2200);
        }

        if ((encoded & 0x40000000) != 0) { // i haven't seen any higher than this o.o
            ret.add(3000);
            ret.add(3200);
            ret.add(3300);
            ret.add(3500);
        }
        return ret;
    }

    private static List<Integer> getJobBySimpleEncoding(int encoded) {
        List ret = new ArrayList();
        if ((encoded & 0x1) != 0) {
            ret.add(200);
        }
        if ((encoded & 0x2) != 0) {
            ret.add(300);
        }
        if ((encoded & 0x4) != 0) {
            ret.add(400);
        }
        if ((encoded & 0x8) != 0) {
            ret.add(500);
        }
        return ret;
    }

    public MapleQuestActionType getType() {
        return type;
    }

    @Override
    public String toString() {
        //        return type + ": " + data;
        return this.type.toString();
    }

    public List<SkillInfo> getSkills() {
        return this.skills;
    }

    public List<QuestItem> getItems() {
        return this.items;
    }

    public static class QuestItem implements Serializable {

        private static final long serialVersionUID = 9179541993413738570L;

        public int itemid;
        public int count;
        public int period;
        public int gender;
        public int job;
        public int jobEx;
        public int prop;

        public QuestItem(
                int itemid, int count, int period, int gender, int job, int jobEx, int prop) {
            if (RandomRewards.getInstance().getTenPercent().contains(itemid)) {
                count += Randomizer.nextInt(3);
            }
            this.itemid = itemid;
            this.count = count;
            this.period = period;
            this.gender = gender;
            this.job = job;
            this.jobEx = jobEx;
            this.prop = prop;
        }
    }
}
