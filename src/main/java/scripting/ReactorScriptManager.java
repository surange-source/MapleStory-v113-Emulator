package scripting;

import client.MapleClient;
import database.DatabaseConnection;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import server.maps.MapleReactor;
import server.maps.ReactorDropEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ReactorScriptManager extends AbstractScriptManager {
    private static final Logger LOG = LoggerFactory.getLogger(ReactorScriptManager.class);

    private final Map<Integer, List<ReactorDropEntry>> drops = new HashMap<>();

    private ReactorScriptManager() {}

    private static final class Holder {

        private static final ReactorScriptManager INSTANCE = new ReactorScriptManager();

        private Holder() {}
    }

    /**
     * 取得 {@link ReactorScriptManager} 單例。
     *
     * <p>此方法刻意回傳共享的單例實例，呼叫端需要操作同一份 drops 快取， 因此不應回傳拷貝。單例本身為 {@code final} 類別且建構子為 private，
     * 外部無法替換或繼承，故抑制 SpotBugs 的 MS_EXPOSE_REP 警告。
     *
     * @return 全域唯一的 {@link ReactorScriptManager} 實例
     */
    @SuppressFBWarnings(
            value = "MS_EXPOSE_REP",
            justification = "Singleton by design; callers must share the same drops cache.")
    public static final ReactorScriptManager getInstance() {
        return Holder.INSTANCE;
    }

    public final void act(final MapleClient c, final MapleReactor reactor) {
        try {
            final Invocable iv = getInvocable("reactor/" + reactor.getReactorId() + ".js", c);

            if (iv == null) {
                return;
            }
            final ScriptEngine scriptengine = (ScriptEngine) iv;
            ReactorActionManager rm = new ReactorActionManager(c, reactor);

            scriptengine.put("rm", rm);
            iv.invokeFunction("act");
        } catch (NoSuchMethodException | ScriptException e) {
            LOG.error("Error executing reactor script. ReactorID: {}, ReactorName: {}:", reactor.getReactorId(), reactor.getName(), e);
        }
    }

    public final List<ReactorDropEntry> getDrops(final int rid) {
        List<ReactorDropEntry> ret = drops.get(rid);
        if (ret != null) {
            return ret;
        }
        ret = new ArrayList<>();

        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return ret;
            }
            try (PreparedStatement ps =
                    con.prepareStatement(
                            "SELECT itemid, chance, questid FROM reactordrops WHERE reactorid = ?")) {
                ps.setInt(1, rid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ret.add(
                                new ReactorDropEntry(
                                        rs.getInt("itemid"),
                                        rs.getInt("chance"),
                                        rs.getInt("questid")));
                    }
                }
            }
        } catch (final SQLException e) {
            LOG.error("Could not retrieve drops for reactor {}", rid, e);
            return ret;
        }
        drops.put(rid, ret);
        return ret;
    }

    public final void clearDrops() {
        drops.clear();
    }
}
