package server.quest;

import client.MapleCharacter;
import client.MapleQuestStatus;
import constants.GameConstants;
import database.DatabaseConnection;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripting.NPCScriptManager;
import tools.MaplePacketCreator;
import tools.Pair;

public final class MapleQuest implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(MapleQuest.class);

    private static final long serialVersionUID = 9179541993413738569L;
    private static final Map<Integer, MapleQuest> quests = new LinkedHashMap<>();
    protected int id;
    protected List<MapleQuestRequirement> startReqs = new LinkedList<>();
    protected List<MapleQuestRequirement> completeReqs = new LinkedList<>();
    protected List<MapleQuestAction> startActs = new LinkedList<>();
    protected List<MapleQuestAction> completeActs = new LinkedList<>();
    protected Map<String, List<Pair<String, Pair<String, Integer>>>> partyQuestInfo =
            new LinkedHashMap<>(); // [rank, [more/less/equal, [property, value]]]
    protected Map<Integer, Integer> relevantMobs = new LinkedHashMap<>();
    private boolean autoStart = false,
            autoPreComplete = false,
            repeatable = false,
            customend = false,
            blocked = false,
            autoAccept = false,
            autoComplete = false,
            scriptedStart = false;
    private int viewMedalItem = 0, selectedSkillID = 0;
    protected String name = "";

    private MapleQuest(final int id) {
        this.id = id;
    }

    private static List<Map<String, Object>> readAllRows(Connection con, String sql)
            throws SQLException {
        final List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            final ResultSetMetaData meta = rs.getMetaData();
            final int colCount = meta.getColumnCount();
            final String[] colNames = new String[colCount + 1];
            for (int i = 1; i <= colCount; i++) {
                colNames[i] = meta.getColumnLabel(i).toLowerCase();
            }
            while (rs.next()) {
                final Map<String, Object> row = new HashMap<>(colCount * 2);
                for (int i = 1; i <= colCount; i++) {
                    row.put(colNames[i], rs.getObject(i));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private static Map<Integer, List<Map<String, Object>>> groupByIntKey(
            List<Map<String, Object>> rows, String keyCol) {
        final Map<Integer, List<Map<String, Object>>> grouped = new HashMap<>();
        final String k = keyCol.toLowerCase();
        for (Map<String, Object> row : rows) {
            final Object val = row.get(k);
            if (val instanceof Number num) {
                grouped.computeIfAbsent(num.intValue(), unused -> new ArrayList<>()).add(row);
            }
        }
        return grouped;
    }

    private static int getIntVal(Map<String, Object> row, String col) {
        final Object v = row.get(col.toLowerCase());
        if (v instanceof Number num) {
            return num.intValue();
        }
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (v != null) {
            try {
                return Integer.parseInt(v.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private static String getStrVal(Map<String, Object> row, String col) {
        final Object v = row.get(col.toLowerCase());
        return v != null ? v.toString() : null;
    }

    /** 離線記憶體 ResultSet，供既有的 MapleQuestRequirement 與 MapleQuestAction 無縫讀取，完全不佔用資料庫連線 */
    private static ResultSet createMemoryResultSet(List<Map<String, Object>> rows) {
        final List<Map<String, Object>> data =
                rows != null ? rows : Collections.emptyList();
        return (ResultSet)
                Proxy.newProxyInstance(
                        MapleQuest.class.getClassLoader(),
                        new Class<?>[] {ResultSet.class},
                        new InvocationHandler() {
                            private int index = -1;
                            private boolean wasNull = false;

                            private Object getRaw(Object colArg) {
                                final Map<String, Object> row = data.get(index);
                                if (colArg instanceof String colName) {
                                    final Object val = row.get(colName.toLowerCase());
                                    wasNull = (val == null);
                                    return val;
                                }
                                if (colArg instanceof Integer colIdx) {
                                    int i = 1;
                                    for (Object val : row.values()) {
                                        if (i == colIdx) {
                                            wasNull = (val == null);
                                            return val;
                                        }
                                        i++;
                                    }
                                }
                                wasNull = true;
                                return null;
                            }

                            @Override
                            public Object invoke(Object proxy, Method method, Object[] args)
                                    throws Throwable {
                                final String m = method.getName();
                                if ("next".equals(m)) {
                                    index++;
                                    return index < data.size();
                                }
                                if ("close".equals(m)) {
                                    return null;
                                }
                                if ("wasNull".equals(m)) {
                                    return wasNull;
                                }
                                if ("isClosed".equals(m)) {
                                    return false;
                                }
                                if ("getString".equals(m)) {
                                    final Object v = getRaw(args[0]);
                                    return v != null ? v.toString() : null;
                                }
                                if ("getInt".equals(m)) {
                                    final Object v = getRaw(args[0]);
                                    if (v instanceof Number n) {
                                        return n.intValue();
                                    }
                                    if (v instanceof Boolean b) {
                                        return b ? 1 : 0;
                                    }
                                    return v != null ? Integer.parseInt(v.toString()) : 0;
                                }
                                if ("getLong".equals(m)) {
                                    final Object v = getRaw(args[0]);
                                    if (v instanceof Number n) {
                                        return n.longValue();
                                    }
                                    return v != null ? Long.parseLong(v.toString()) : 0L;
                                }
                                if ("getShort".equals(m)) {
                                    final Object v = getRaw(args[0]);
                                    if (v instanceof Number n) {
                                        return n.shortValue();
                                    }
                                    return v != null ? Short.parseShort(v.toString()) : (short) 0;
                                }
                                if ("getByte".equals(m)) {
                                    final Object v = getRaw(args[0]);
                                    if (v instanceof Number n) {
                                        return n.byteValue();
                                    }
                                    return v != null ? Byte.parseByte(v.toString()) : (byte) 0;
                                }
                                if ("getBoolean".equals(m)) {
                                    final Object v = getRaw(args[0]);
                                    if (v instanceof Boolean b) {
                                        return b;
                                    }
                                    if (v instanceof Number n) {
                                        return n.intValue() != 0;
                                    }
                                    return v != null && Boolean.parseBoolean(v.toString());
                                }
                                if ("getFloat".equals(m)) {
                                    final Object v = getRaw(args[0]);
                                    return v instanceof Number n ? n.floatValue() : 0f;
                                }
                                if ("getDouble".equals(m)) {
                                    final Object v = getRaw(args[0]);
                                    return v instanceof Number n ? n.doubleValue() : 0d;
                                }
                                if ("getObject".equals(m)) {
                                    return getRaw(args[0]);
                                }
                                return null;
                            }
                        });
    }

    /** 離線記憶體 PreparedStatement，攔截 setInt(1, uniqueId) 後回傳對應的記憶體 ResultSet */
    private static PreparedStatement createMemoryPreparedStatement(
            Map<Integer, List<Map<String, Object>>> dataByUniqueId) {
        return (PreparedStatement)
                Proxy.newProxyInstance(
                        MapleQuest.class.getClassLoader(),
                        new Class<?>[] {PreparedStatement.class},
                        new InvocationHandler() {
                            private int boundId = 0;

                            @Override
                            public Object invoke(Object proxy, Method method, Object[] args) {
                                final String m = method.getName();
                                if ("setInt".equals(m) && args != null && args.length >= 2) {
                                    boundId = ((Number) args[1]).intValue();
                                    return null;
                                }
                                if ("executeQuery".equals(m)) {
                                    return createMemoryResultSet(dataByUniqueId.get(boundId));
                                }
                                if ("close".equals(m)) {
                                    return null;
                                }
                                return null;
                            }
                        });
    }

    private static MapleQuest buildQuestFromMemory(
            Map<String, Object> qRow,
            List<Map<String, Object>> reqRows,
            List<Map<String, Object>> actRows,
            List<Map<String, Object>> partyRows,
            PreparedStatement memPss,
            PreparedStatement memPsq,
            PreparedStatement memPsi)
            throws SQLException {
        final MapleQuest ret = new MapleQuest(getIntVal(qRow, "questid"));
        ret.name = getStrVal(qRow, "name");
        ret.autoStart = getIntVal(qRow, "autoStart") > 0;
        ret.autoPreComplete = getIntVal(qRow, "autoPreComplete") > 0;
        ret.autoAccept = getIntVal(qRow, "autoAccept") > 0;
        ret.autoComplete = getIntVal(qRow, "autoComplete") > 0;
        ret.viewMedalItem = getIntVal(qRow, "viewMedalItem");
        ret.selectedSkillID = getIntVal(qRow, "selectedSkillID");
        ret.blocked =
                getIntVal(qRow, "blocked")
                        > 0; // ult.explorer quests will dc as the item isn't there...

        try (ResultSet rse = createMemoryResultSet(reqRows)) {
            while (rse.next()) {
                final MapleQuestRequirementType type =
                        MapleQuestRequirementType.getByWZName(rse.getString("name"));
                final MapleQuestRequirement req = new MapleQuestRequirement(ret, type, rse);
                switch (type) {
                    case interval:
                        ret.repeatable = true;
                        break;
                    case normalAutoStart:
                        ret.repeatable = true;
                        ret.autoStart = true;
                        break;
                    case startscript:
                        ret.scriptedStart = true;
                        break;
                    case endscript:
                        ret.customend = true;
                        break;
                    case mob:
                        for (Pair<Integer, Integer> mob : req.getDataStore()) {
                            ret.relevantMobs.put(mob.left(), mob.right());
                        }
                        break;
                    default:
                        break;
                }
                if (rse.getInt("type") == 0) {
                    ret.startReqs.add(req);
                } else {
                    ret.completeReqs.add(req);
                }
            }
        }

        try (ResultSet rse = createMemoryResultSet(actRows)) {
            while (rse.next()) {
                final MapleQuestActionType ty =
                        MapleQuestActionType.getByWZName(rse.getString("name"));
                if (rse.getInt("type") == 0) { // pass it over so it will set ID + type once done
                    if (ty == MapleQuestActionType.item && ret.id == 7103) { // pap glitch
                        // continue;
                    }
                    ret.startActs.add(
                            new MapleQuestAction(ty, rse, ret, memPss, memPsq, memPsi));
                } else {
                    if (ty == MapleQuestActionType.item && ret.id == 7102) { // pap glitch
                        continue;
                    }
                    ret.completeActs.add(
                            new MapleQuestAction(ty, rse, ret, memPss, memPsq, memPsi));
                }
            }
        }

        if (partyRows != null) {
            for (Map<String, Object> pRow : partyRows) {
                final String rank = getStrVal(pRow, "rank");
                ret.partyQuestInfo
                        .computeIfAbsent(rank, k -> new ArrayList<>())
                        .add(
                                new Pair<>(
                                        getStrVal(pRow, "mode"),
                                        new Pair<>(
                                                getStrVal(pRow, "property"),
                                                getIntVal(pRow, "value"))));
            }
        }
        return ret;
    }

    public List<Pair<String, Pair<String, Integer>>> getInfoByRank(final String rank) {
        return partyQuestInfo.get(rank);
    }

    public final int getSkillID() {
        return selectedSkillID;
    }

    public final String getName() {
        return name;
    }

    public static void initQuests(boolean reload) {
        if (reload) {
            LOG.info("Loading MapleQuest(reload) :::");
            quests.clear();
        } else {
            LOG.info("Loading MapleQuest :::");
        }
        if (!quests.isEmpty()) {
            LOG.info("MapleQuest not EMPTY, exited.");
            return;
        }

        final List<Map<String, Object>> questRows;
        final Map<Integer, List<Map<String, Object>>> reqByQuest;
        final Map<Integer, List<Map<String, Object>>> actByQuest;
        final Map<Integer, List<Map<String, Object>>> skillByUnique;
        final Map<Integer, List<Map<String, Object>>> questActByUnique;
        final Map<Integer, List<Map<String, Object>>> itemByUnique;
        final Map<Integer, List<Map<String, Object>>> partyByQuest;

        // 階段一：僅用 7 次整表查詢將資料載入記憶體並立即歸還資料庫連線（耗時 < 50ms，杜絕 ProxyLeakTask 假警報）
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                LOG.info(" Loaded");
                return;
            }
            questRows = readAllRows(con, "SELECT * FROM wz_questdata");
            reqByQuest =
                    groupByIntKey(
                            readAllRows(con, "SELECT * FROM wz_questreqdata"), "questid");
            actByQuest =
                    groupByIntKey(
                            readAllRows(con, "SELECT * FROM wz_questactdata"), "questid");
            skillByUnique =
                    groupByIntKey(
                            readAllRows(con, "SELECT * FROM wz_questactskilldata"), "uniqueid");
            questActByUnique =
                    groupByIntKey(
                            readAllRows(con, "SELECT * FROM wz_questactquestdata"), "uniqueid");
            itemByUnique =
                    groupByIntKey(
                            readAllRows(con, "SELECT * FROM wz_questactitemdata"), "uniqueid");
            partyByQuest =
                    groupByIntKey(
                            readAllRows(con, "SELECT * FROM wz_questpartydata"), "questid");
        } catch (SQLException e) {
            LOG.error("MapleQuest ERROR: ", e);
            LOG.info(" Loaded");
            return;
        }

        // 階段二：在純記憶體中組裝所有任務物件（完全不佔用連線池）
        try (PreparedStatement memPss = createMemoryPreparedStatement(skillByUnique);
                PreparedStatement memPsq = createMemoryPreparedStatement(questActByUnique);
                PreparedStatement memPsi = createMemoryPreparedStatement(itemByUnique)) {
            for (Map<String, Object> qRow : questRows) {
                final int qid = getIntVal(qRow, "questid");
                quests.put(
                        qid,
                        buildQuestFromMemory(
                                qRow,
                                reqByQuest.get(qid),
                                actByQuest.get(qid),
                                partyByQuest.get(qid),
                                memPss,
                                memPsq,
                                memPsi));
            }
        } catch (SQLException e) {
            LOG.error("MapleQuest ERROR: ", e);
        }
        LOG.info(" Loaded");
    }

    public static void clearQuests() {
        initQuests(true); // test
    }

    public static Collection<MapleQuest> getAllInstances() {
        return quests.values();
    }

    public static MapleQuest getInstance(int id) {
        MapleQuest ret = quests.get(id);
        if (ret == null) {
            ret = new MapleQuest(id);
            try {
                quests.put(id, ret); // by this time we have already initialized
            } catch (Exception ex) {
                LOG.error("Caused by questID {}", id);
            }
        }
        return ret;
    }

    public boolean canStart(MapleCharacter c, Integer npcid) {
        if (c.getQuest(this).getStatus() != 0
                && !(c.getQuest(this).getStatus() == 2 && repeatable)) {
            return false;
        }
        for (MapleQuestRequirement r : startReqs) {
            if (!r.check(c, npcid)) {
                return false;
            }
        }
        return true;
    }

    public boolean canComplete(MapleCharacter c, Integer npcid) {
        if (c.getQuest(this).getStatus() != 1) {
            return false;
        }
        for (MapleQuestRequirement r : completeReqs) {
            if (!r.check(c, npcid)) {
                return false;
            }
        }
        return true;
    }

    public final void RestoreLostItem(final MapleCharacter c, final int itemid) {
        for (final MapleQuestAction a : startActs) {
            if (a.RestoreLostItem(c, itemid)) {
                break;
            }
        }
    }

    public void start(MapleCharacter c, int npc) {
        if ((autoStart || checkNPCOnMap(c, npc)) && canStart(c, npc)) {
            for (MapleQuestAction a : startActs) {
                if (!a.checkEnd(c, null)) { // just in case
                    return;
                }
            }
            for (MapleQuestAction a : startActs) {
                a.runStart(c, null);
            }
            if (!customend) {
                forceStart(c, npc, null);
            } else {
                NPCScriptManager.getInstance().endQuest(c.getClient(), npc, getId(), true);
            }
        } else if (GameConstants.CanAcceptQuest(id)) { // 修正任務沒辦法接
            for (MapleQuestAction a : startActs) {
                if (!a.checkEnd(c, null)) { // just in case
                    return;
                }
            }
            for (MapleQuestAction a : startActs) {
                a.runStart(c, null);
            }
            if (!customend) {
                forceStart(c, npc, null);
            } else {
                NPCScriptManager.getInstance().endQuest(c.getClient(), npc, getId(), true);
            }
        }
    }

    public void complete(MapleCharacter c, int npc) {
        complete(c, npc, null);
    }

    public void complete(MapleCharacter c, int npc, Integer selection) {
        if ((autoPreComplete || checkNPCOnMap(c, npc)) && canComplete(c, npc)) {
            for (MapleQuestAction a : completeActs) {
                if (!a.checkEnd(c, selection)) {
                    return;
                }
            }
            forceComplete(c, npc);
            for (MapleQuestAction a : completeActs) {
                a.runEnd(c, selection);
            }
            // we save forfeits only for logging purposes, they shouldn't matter anymore
            // completion time is set by the constructor

            c.getClient().sendPacket(MaplePacketCreator.showSpecialEffect(9)); // Quest completion
            c.getMap()
                    .broadcastMessage(c, MaplePacketCreator.showSpecialEffect(c.getId(), 9), false);
        } else if (id == 29507) { // 修正任務沒辦法完成
            for (MapleQuestAction a : completeActs) {
                if (!a.checkEnd(c, selection)) {
                    return;
                }
            }
            forceComplete(c, npc);
            for (MapleQuestAction a : completeActs) {
                a.runEnd(c, selection);
            }
            // we save forfeits only for logging purposes, they shouldn't matter anymore
            // completion time is set by the constructor

            c.getClient().sendPacket(MaplePacketCreator.showSpecialEffect(9)); // Quest completion
            c.getMap()
                    .broadcastMessage(c, MaplePacketCreator.showSpecialEffect(c.getId(), 9), false);
        }
    }

    public void forfeit(MapleCharacter c) {
        if (c.getQuest(this).getStatus() != (byte) 1) {
            return;
        }
        final MapleQuestStatus oldStatus = c.getQuest(this);
        final MapleQuestStatus newStatus = new MapleQuestStatus(this, (byte) 0);
        newStatus.setForfeited(oldStatus.getForfeited() + 1);
        newStatus.setCompletionTime(oldStatus.getCompletionTime());
        c.updateQuest(newStatus);
    }

    public void forceStart(MapleCharacter c, int npc, String customData) {
        final MapleQuestStatus newStatus = new MapleQuestStatus(this, (byte) 1, npc);
        newStatus.setForfeited(c.getQuest(this).getForfeited());
        newStatus.setCompletionTime(c.getQuest(this).getCompletionTime());
        newStatus.setCustomData(customData);
        c.updateQuest(newStatus);
    }

    public void forceComplete(MapleCharacter c, int npc) {
        final MapleQuestStatus newStatus = new MapleQuestStatus(this, (byte) 2, npc);
        newStatus.setForfeited(c.getQuest(this).getForfeited());
        c.updateQuest(newStatus);
    }

    public int getId() {
        return id;
    }

    public Map<Integer, Integer> getRelevantMobs() {
        return relevantMobs;
    }

    private boolean checkNPCOnMap(MapleCharacter player, int npcid) {
        boolean next = false;
        switch (npcid) {
            case 9000040:
            case 9000066:
                next = true;
                break;
        }
        if (player.getMap() != null) {
            if (player.getMap().containsNPC(npcid)) {
                next = true;
            }
        }
        return next;
    }

    public int getMedalItem() {
        return viewMedalItem;
    }

    public static enum MedalQuest {
        新手冒險家(
                29005,
                29015,
                15,
                new int[] {
                    104000000, 104010001, 100000006, 104020000, 100000000, 100010000, 100040000,
                    100040100, 101010103, 101020000, 101000000, 102000000, 101030104, 101030406,
                    102020300, 103000000, 102050000, 103010001, 103030200, 110000000
                }),
        冰原雪域山脈探險家(
                29006,
                29012,
                50,
                new int[] {
                    200000000, 200010100, 200010300, 200080000, 200080100, 211000000, 211030000,
                    211040300, 211041200, 211041800
                }),
        路德斯湖探險家(
                29007,
                29012,
                40,
                new int[] {
                    222000000, 222010400, 222020000, 220000000, 220020300, 220040200, 221020701,
                    221000000, 221030600, 221040400
                }),
        海底探險家(
                29008,
                29012,
                40,
                new int[] {
                    230000000, 230010400, 230010200, 230010201, 230020000, 230020201, 230030100,
                    230040000, 230040200, 230040400
                }),
        武陵桃園探險家(
                29009,
                29012,
                50,
                new int[] {
                    251000000, 251010200, 251010402, 251010500, 250010500, 250010504, 250000000,
                    250010300, 250010304, 250020300
                }),
        納希沙漠探險家(
                29010,
                29012,
                70,
                new int[] {
                    261030000, 261020401, 261020000, 261010100, 261000000, 260020700, 260020300,
                    260000000, 260010600, 260010300
                }),
        米納爾森林探險家(
                29011,
                29012,
                70,
                new int[] {
                    240000000, 240010200, 240010800, 240020401, 240020101, 240030000, 240040400,
                    240040511, 240040521, 240050000
                }),
        奇幻村探險家(
                29014,
                29015,
                50,
                new int[] {
                    105040300, 105070001, 105040305, 105090200, 105090300, 105090301, 105090312,
                    105090500, 105090900, 105080000
                });
        public int questid, level, lquestid;
        public int[] maps;

        private MedalQuest(int questid, int lquestid, int level, int[] maps) {
            this.questid = questid; // infoquest = questid -2005, customdata = questid -1995
            this.level = level;
            this.lquestid = lquestid;
            this.maps = maps; // note # of maps
        }
    }

    public boolean hasStartScript() {
        return this.scriptedStart;
    }
}
