package server;

import database.DatabaseConnection;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import server.maps.SpeedRunType;
import tools.Pair;
import tools.StringUtil;

public final class SpeedRunner {

    private final Map<SpeedRunType, Pair<String, Map<Integer, String>>> speedRunData;

    private SpeedRunner() {
        speedRunData = new EnumMap<>(SpeedRunType.class);
    }

    private static final class Holder {

        private static final SpeedRunner INSTANCE = new SpeedRunner();
    }

    /**
     * 取得 {@link SpeedRunner} 單例。
     *
     * <p>
     * 此單例刻意共享可變狀態（speedrun 排行榜資料），呼叫端會透過
     * {@link #addSpeedRunData} / {@link #removeSpeedRunData} 修改內容，
     * 因此不可回傳拷貝，否則會破壞業務行為。
     *
     * @return 全域唯一的 {@link SpeedRunner} 實例
     */
    @SuppressFBWarnings(
            value = "MS_EXPOSE_REP",
            justification
            = "Singleton by design; callers intentionally mutate shared speedrun data via add/remove methods.")
    public static final SpeedRunner getInstance() {
        return Holder.INSTANCE;
    }

    public final Pair<String, Map<Integer, String>> getSpeedRunData(SpeedRunType type) {
        if (type == null) {
            return null;
        }
        return speedRunData.get(type);
    }

    public final void addSpeedRunData(
            SpeedRunType type, Pair<StringBuilder, Map<Integer, String>> mib) {
        if (type == null || mib == null || mib.left() == null) {
            return;
        }
        speedRunData.put(type, new Pair<>(mib.left().toString(), mib.right()));
    }

    public final void removeSpeedRunData(SpeedRunType type) {
        if (type == null) {
            return;
        }
        speedRunData.remove(type);
    }

    public final void loadSpeedRuns() throws SQLException {
        if (speedRunData.size() > 0) {
            return;
        }
        for (SpeedRunType type : SpeedRunType.values()) {
            loadSpeedRunData(type);
        }
    }

    public final void loadSpeedRunData(SpeedRunType type) throws SQLException {
        if (type == null) {
            return;
        }
        final String sql
                = "SELECT * FROM speedruns WHERE type = ? ORDER BY time LIMIT 25"; // or should we do
        // less
        StringBuilder ret
                = new StringBuilder(
                        "#rThese are the speedrun times for "
                        + StringUtil.makeEnumHumanReadable(type.name())
                        + ".#k\r\n\r\n");
        Map<Integer, String> rett = new LinkedHashMap<>();
        boolean changed = false;
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, type.name());
                try (ResultSet rs = ps.executeQuery()) {
                    int rank = 1;
                    boolean cont = rs.next();
                    changed = cont;
                    while (cont) {
                        addSpeedRunData(
                                ret,
                                rett,
                                rs.getString("members"),
                                rs.getString("leader"),
                                rank,
                                rs.getString("timestring"));
                        rank++;
                        cont = rs.next();
                    }
                }
            }
        }
        if (changed) {
            speedRunData.put(type, new Pair<>(ret.toString(), rett));
        }
    }

    public final Pair<StringBuilder, Map<Integer, String>> addSpeedRunData(
            StringBuilder ret,
            Map<Integer, String> rett,
            String members,
            String leader,
            int rank,
            String timestring) {
        if (ret == null || rett == null) {
            return new Pair<>(ret, rett);
        }
        StringBuilder rettt = new StringBuilder();

        String[] membrz
                = (members == null || members.isEmpty()) ? new String[0] : members.split(",");
        rettt.append("#b該遠征隊 " + leader + "'成功挑戰排名為 " + rank + ".#k\r\n\r\n");
        for (int i = 0; i < membrz.length; i++) {
            rettt.append("#r#e");
            rettt.append(i + 1);
            rettt.append(".#n ");
            rettt.append(membrz[i]);
            rettt.append("#k\r\n");
        }
        rett.put(rank, rettt.toString());
        ret.append("#b");
        if (membrz.length > 1) {
            ret.append("#L");
            ret.append(rank);
            ret.append("#");
        }
        ret.append("Rank #e");
        ret.append(rank);
        ret.append("#n#k : ");
        ret.append(leader == null ? "" : leader);
        ret.append(", in ");
        ret.append(timestring == null ? "" : timestring);
        if (membrz.length > 1) {
            ret.append("#l");
        }
        ret.append("\r\n");
        return new Pair<>(ret, rett);
    }
}
