package tools.wztosql;

import database.DatabaseConnection;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import provider.MapleData;
import provider.MapleDataProvider;
import provider.MapleDataProviderFactory;
import provider.MapleDataTool;
import server.quest.MapleQuestActionType;
import server.quest.MapleQuestRequirementType;
import tools.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DumpQuests {
    private static final Logger log = LoggerFactory.getLogger(DumpQuests.class);

    /** 允許被 delete() 清空的資料表白名單，避免動態拼接 SQL 造成注入風險。 */
    private static final Set<String> DELETABLE_TABLES =
            Set.of(
                    "wz_questdata",
                    "wz_questactdata",
                    "wz_questactitemdata",
                    "wz_questactskilldata",
                    "wz_questactquestdata",
                    "wz_questreqdata",
                    "wz_questpartydata");

    private final MapleDataProvider quest;
    protected boolean hadError = false;
    protected boolean update = false;
    protected int id = 0;
    protected String rootpath = new java.io.File("wz").getAbsolutePath();

    public DumpQuests(boolean update) throws Exception {
        this.update = update;
        this.quest = MapleDataProviderFactory.getDataProvider(new File(rootpath + "/Quest.wz"));
        if (quest == null) {
            hadError = true;
        }
    }

    public boolean isHadError() {
        return hadError;
    }

    public void dumpQuests() throws Exception {
        if (hadError) {
            return;
        }
        try (Connection con = DatabaseConnection.getConnection();
                PreparedStatement psai =
                        con.prepareStatement(
                                "INSERT INTO wz_questactitemdata(uniqueid, itemid, count, period, gender, job, jobEx, prop) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
                PreparedStatement psas =
                        con.prepareStatement(
                                "INSERT INTO wz_questactskilldata(uniqueid, skillid, skillLevel, masterLevel) VALUES (?, ?, ?, ?)");
                PreparedStatement psaq =
                        con.prepareStatement(
                                "INSERT INTO wz_questactquestdata(uniqueid, quest, state) VALUES (?, ?, ?)");
                PreparedStatement ps =
                        con.prepareStatement(
                                "INSERT INTO wz_questdata(questid, name, autoStart, autoPreComplete, viewMedalItem, selectedSkillID, blocked, autoAccept, autoComplete) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
                PreparedStatement psr =
                        con.prepareStatement(
                                "INSERT INTO wz_questreqdata(questid, type, name, stringStore, intStoresFirst, intStoresSecond) VALUES (?, ?, ?, ?, ?, ?)");
                PreparedStatement psq =
                        con.prepareStatement(
                                "INSERT INTO wz_questpartydata(questid, `rank`, mode, property, value) VALUES(?,?,?,?,?)");
                PreparedStatement psa =
                        con.prepareStatement(
                                "INSERT INTO wz_questactdata(questid, type, name, intStore, applicableJobs, uniqueid) VALUES (?, ?, ?, ?, ?, ?)")) {
            try {
                dumpQuests(con, psai, psas, psaq, ps, psr, psq, psa);
            } catch (Exception e) {
                log.error("{} quest.", id, e);
                hadError = true;
            }
            // 批次寫入：即使 dumpQuests 拋出例外，仍嘗試 flush 已累積的批次
            psai.executeBatch();
            psas.executeBatch();
            psaq.executeBatch();
            psa.executeBatch();
            psr.executeBatch();
            psq.executeBatch();
            ps.executeBatch();
        }
    }

    /**
     * 依白名單清空指定資料表。表名必須存在於 {@link #DELETABLE_TABLES}， 避免動態拼接 SQL 造成注入。
     *
     * @param con 已借出的資料庫連線（由呼叫端管理生命週期）
     * @param table 要清空的資料表名稱
     * @throws IllegalArgumentException 若表名不在白名單內
     */
    public void delete(Connection con, String table) throws Exception {
        if (table == null || !DELETABLE_TABLES.contains(table)) {
            throw new IllegalArgumentException("不允許清空的資料表: " + table);
        }
        // 表名已通過白名單驗證，可安全拼接；其餘皆為固定 SQL
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + table)) {
            ps.executeUpdate();
        }
    }

    /**
     * 檢查指定任務 ID 是否已存在於 wz_questdata。
     *
     * @param con 已借出的資料庫連線（由呼叫端管理生命週期）
     * @param questId 任務 ID
     * @return 若存在則回傳 true
     */
    public boolean doesExist(Connection con, int questId) throws Exception {
        try (PreparedStatement ps =
                con.prepareStatement("SELECT 1 FROM wz_questdata WHERE questid = ?")) {
            ps.setInt(1, questId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void dumpQuests(
            Connection con,
            PreparedStatement psai,
            PreparedStatement psas,
            PreparedStatement psaq,
            PreparedStatement ps,
            PreparedStatement psr,
            PreparedStatement psq,
            PreparedStatement psa)
            throws Exception {
        if (!update) {
            log.info("清理數據庫...");
            delete(con, "wz_questdata");
            delete(con, "wz_questactdata");
            delete(con, "wz_questactitemdata");
            delete(con, "wz_questactskilldata");
            delete(con, "wz_questactquestdata");
            delete(con, "wz_questreqdata");
            delete(con, "wz_questpartydata");
            log.info("Deleted wz_questdata successfully.");
        }
        final MapleData checkz = quest.getData("Check.img");
        final MapleData actz = quest.getData("Act.img");
        final MapleData infoz = quest.getData("QuestInfo.img");
        final MapleData pinfoz = quest.getData("PQuest.img");
        log.info("Adding into wz_questdata.....");
        int uniqueid = 0;
        for (MapleData qz : checkz.getChildren()) { // requirements first
            this.id = Integer.parseInt(qz.getName());
            if (update && doesExist(con, id)) {
                continue;
            }
            ps.setInt(1, id);
            for (int i = 0; i < 2; i++) {
                MapleData reqData = qz.getChildByPath(String.valueOf(i));
                if (reqData != null) {
                    psr.setInt(1, id);
                    psr.setInt(2, i); // 0 = start
                    for (MapleData req : reqData.getChildren()) {
                        if (MapleQuestRequirementType.getByWZName(req.getName())
                                == MapleQuestRequirementType.UNDEFINED) {
                            continue; // un-needed
                        }
                        psr.setString(3, req.getName());
                        switch (req.getName()) {
                            case "fieldEnter":
                                // diff
                                psr.setString(
                                        4,
                                        String.valueOf(MapleDataTool.getIntConvert("0", req, 0)));
                                break;
                            case "end":
                            case "startscript":
                            case "endscript":
                                psr.setString(4, MapleDataTool.getString(req, ""));
                                break;
                            default:
                                psr.setString(4, String.valueOf(MapleDataTool.getInt(req, 0)));
                                break;
                        }
                        StringBuilder intStore1 = new StringBuilder();
                        StringBuilder intStore2 = new StringBuilder();
                        List<Pair<Integer, Integer>> dataStore = new LinkedList<>();
                        switch (req.getName()) {
                            case "job":
                            case "job_TW":
                                {
                                    final List<MapleData> child = req.getChildren();
                                    for (int x = 0; x < child.size(); x++) {
                                        dataStore.add(
                                                new Pair<>(
                                                        i, MapleDataTool.getInt(child.get(x), -1)));
                                    }
                                    break;
                                }
                            case "skill":
                                {
                                    final List<MapleData> child = req.getChildren();
                                    for (int x = 0; x < child.size(); x++) {
                                        final MapleData childdata = child.get(x);
                                        if (childdata == null) {
                                            continue;
                                        }
                                        dataStore.add(
                                                new Pair<>(
                                                        MapleDataTool.getInt(
                                                                childdata.getChildByPath("id"), 0),
                                                        MapleDataTool.getInt(
                                                                childdata.getChildByPath("acquire"),
                                                                0)));
                                    }
                                    break;
                                }
                            case "quest":
                                {
                                    final List<MapleData> child = req.getChildren();
                                    for (int x = 0; x < child.size(); x++) {
                                        final MapleData childdata = child.get(x);
                                        if (childdata == null) {
                                            continue;
                                        }
                                        dataStore.add(
                                                new Pair<>(
                                                        MapleDataTool.getInt(
                                                                childdata.getChildByPath("id"), 0),
                                                        MapleDataTool.getInt(
                                                                childdata.getChildByPath("state"),
                                                                0)));
                                    }
                                    break;
                                }
                            case "item":
                            case "mob":
                                {
                                    final List<MapleData> child = req.getChildren();
                                    for (int x = 0; x < child.size(); x++) {
                                        final MapleData childdata = child.get(x);
                                        if (childdata == null) {
                                            continue;
                                        }
                                        dataStore.add(
                                                new Pair<>(
                                                        MapleDataTool.getInt(
                                                                childdata.getChildByPath("id"), 0),
                                                        MapleDataTool.getInt(
                                                                childdata.getChildByPath("count"),
                                                                0)));
                                    }
                                    break;
                                }
                            case "mbcard":
                                {
                                    final List<MapleData> child = req.getChildren();
                                    for (int x = 0; x < child.size(); x++) {
                                        final MapleData childdata = child.get(x);
                                        if (childdata == null) {
                                            continue;
                                        }
                                        dataStore.add(
                                                new Pair<>(
                                                        MapleDataTool.getInt(
                                                                childdata.getChildByPath("id"), 0),
                                                        MapleDataTool.getInt(
                                                                childdata.getChildByPath("min"),
                                                                0)));
                                    }
                                    break;
                                }
                            case "pet":
                                {
                                    final List<MapleData> child = req.getChildren();
                                    for (int x = 0; x < child.size(); x++) {
                                        final MapleData childdata = child.get(x);
                                        if (childdata == null) {
                                            continue;
                                        }
                                        dataStore.add(
                                                new Pair<>(
                                                        i,
                                                        MapleDataTool.getInt(
                                                                childdata.getChildByPath("id"),
                                                                0)));
                                    }
                                    break;
                                }
                            default:
                                break;
                        }
                        for (Pair<Integer, Integer> data : dataStore) {
                            if (intStore1.length() > 0) {
                                intStore1.append(", ");
                                intStore2.append(", ");
                            }
                            intStore1.append(data.left());
                            intStore2.append(data.right());
                        }
                        psr.setString(5, intStore1.toString());
                        psr.setString(6, intStore2.toString());
                        psr.addBatch();
                    }
                }
                MapleData actData = actz.getChildByPath(id + "/" + i);
                if (actData != null) {
                    psa.setInt(1, id);
                    psa.setInt(2, i); // 0 = start
                    for (MapleData act : actData.getChildren()) {
                        if (MapleQuestActionType.getByWZName(act.getName())
                                == MapleQuestActionType.UNDEFINED) {
                            continue; // un-needed
                        }
                        psa.setString(3, act.getName());
                        if (act.getName().equals("sp")) {
                            psa.setInt(4, MapleDataTool.getIntConvert("0/sp_value", act, 0));
                        } else {
                            psa.setInt(4, MapleDataTool.getInt(act, 0));
                        }
                        StringBuilder applicableJobs = new StringBuilder();
                        if (act.getName().equals("sp") || act.getName().equals("skill")) {
                            int index = 0;
                            while (true) {
                                if (act.getChildByPath(index + "/job") != null) {
                                    for (MapleData d : act.getChildByPath(index + "/job")) {
                                        if (applicableJobs.length() > 0) {
                                            applicableJobs.append(", ");
                                        }
                                        applicableJobs.append(MapleDataTool.getInt(d, 0));
                                    }
                                    index++;
                                } else {
                                    break;
                                }
                            }
                        } else if (act.getChildByPath("job") != null) {
                            for (MapleData d : act.getChildByPath("job")) {
                                if (applicableJobs.length() > 0) {
                                    applicableJobs.append(", ");
                                }
                                applicableJobs.append(MapleDataTool.getInt(d, 0));
                            }
                        }
                        psa.setString(5, applicableJobs.toString());
                        psa.setInt(6, -1);
                        switch (act.getName()) {
                            case "item":
                                // prop, job, gender, id, count
                                uniqueid++;
                                psa.setInt(6, uniqueid);
                                psai.setInt(1, uniqueid);
                                for (MapleData iEntry : act.getChildren()) {
                                    psai.setInt(2, MapleDataTool.getInt("id", iEntry, 0));
                                    psai.setInt(3, MapleDataTool.getInt("count", iEntry, 0));
                                    psai.setInt(4, MapleDataTool.getInt("period", iEntry, 0));
                                    psai.setInt(5, MapleDataTool.getInt("gender", iEntry, 2));
                                    psai.setInt(6, MapleDataTool.getInt("job", iEntry, -1));
                                    psai.setInt(7, MapleDataTool.getInt("jobEx", iEntry, -1));

                                    if (iEntry.getChildByPath("prop") == null) {
                                        psai.setInt(8, -2);
                                    } else {
                                        psai.setInt(8, MapleDataTool.getInt("prop", iEntry, -1));
                                    }
                                    psai.addBatch();
                                }
                                break;
                            case "skill":
                                uniqueid++;
                                psa.setInt(6, uniqueid);
                                psas.setInt(1, uniqueid);
                                for (MapleData sEntry : act) {
                                    psas.setInt(2, MapleDataTool.getInt("id", sEntry, 0));
                                    psas.setInt(3, MapleDataTool.getInt("skillLevel", sEntry, 0));
                                    psas.setInt(4, MapleDataTool.getInt("masterLevel", sEntry, 0));
                                    psas.addBatch();
                                }
                                break;
                            case "quest":
                                uniqueid++;
                                psa.setInt(6, uniqueid);
                                psaq.setInt(1, uniqueid);
                                for (MapleData sEntry : act) {
                                    psaq.setInt(2, MapleDataTool.getInt("id", sEntry, 0));
                                    psaq.setInt(3, MapleDataTool.getInt("state", sEntry, 0));
                                    psaq.addBatch();
                                }
                                break;
                            default:
                                break;
                        }
                        psa.addBatch();
                    }
                }
            }
            MapleData infoData = infoz.getChildByPath(String.valueOf(id));
            if (infoData != null) {
                ps.setString(2, MapleDataTool.getString("name", infoData, ""));
                int autoStart = MapleDataTool.getInt("autoStart", infoData, -1);
                if (autoStart == -1) {
                    autoStart = MapleDataTool.getInt("selfStart", infoData, 0);
                }
                ps.setInt(3, autoStart > 0 ? 1 : 0);
                int autoPreComplete = MapleDataTool.getInt("autoPreComplete", infoData, -1);
                if (autoPreComplete == -1) {
                    autoPreComplete = MapleDataTool.getInt("selfComplete", infoData, 0);
                }
                ps.setInt(4, autoPreComplete > 0 ? 1 : 0);
                ps.setInt(5, MapleDataTool.getInt("viewMedalItem", infoData, 0));
                ps.setInt(6, MapleDataTool.getInt("selectedSkillID", infoData, 0));
                ps.setInt(7, MapleDataTool.getInt("blocked", infoData, 0));
                ps.setInt(8, MapleDataTool.getInt("autoAccept", infoData, 0));
                ps.setInt(9, MapleDataTool.getInt("autoComplete", infoData, 0));
            } else {
                ps.setString(2, "");
                ps.setInt(3, 0);
                ps.setInt(4, 0);
                ps.setInt(5, 0);
                ps.setInt(6, 0);
                ps.setInt(7, 0);
                ps.setInt(8, 0);
                ps.setInt(9, 0);
            }
            ps.addBatch();

            MapleData pinfoData = pinfoz.getChildByPath(String.valueOf(id));
            if (pinfoData != null && pinfoData.getChildByPath("rank") != null) {
                psq.setInt(1, id);
                for (MapleData d : pinfoData.getChildByPath("rank")) {
                    psq.setString(2, d.getName());
                    for (MapleData c : d) {
                        psq.setString(3, c.getName());
                        for (MapleData b : c) {
                            psq.setString(4, b.getName());
                            psq.setInt(5, MapleDataTool.getInt(b, 0));
                            psq.addBatch();
                        }
                    }
                }
            }
            log.debug("轉存任務[{}]({})]...", infoData == null ? "NO-NAME" : MapleDataTool.getString("name", infoData, ""), this.id);
            //            System.out.println("Added quest: " + id);
        }
        log.info("Done wz_questdata...");
    }

    public int currentId() {
        return id;
    }

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new java.io.PrintStream(System.err, true, StandardCharsets.UTF_8));
        log.debug("【除錯提示】目前程式運作的絕對路徑是: {}", new java.io.File("wz").getAbsolutePath());
        log.info("轉存任務...");
        boolean hadError = false;
        boolean update = false;
        long startTime = System.currentTimeMillis();
        for (String file : args) {
            if (file.equalsIgnoreCase("-update")) {
                update = true;
            }
        }
        int currentQuest = 0;
        try {
            final DumpQuests dq = new DumpQuests(update);
            log.info("Dumping quests");
            dq.dumpQuests();
            hadError |= dq.isHadError();
            currentQuest = dq.currentId();
        } catch (Exception e) {
            hadError = true;
            log.error("DumpQuest ERROR", e);
        }
        long endTime = System.currentTimeMillis();
        double elapsedSeconds = (endTime - startTime) / 1000.0;
        int elapsedSecs = (((int) elapsedSeconds) % 60);
        int elapsedMinutes = (int) (elapsedSeconds / 60.0);

        String withErrors = "";
        if (hadError) {
            withErrors = " with errors";
        }
        log.info("Finished{} in {} minutes {} seconds", withErrors, elapsedMinutes, elapsedSecs);
    }
}
