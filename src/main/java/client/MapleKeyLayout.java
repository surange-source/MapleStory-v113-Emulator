package client;

import database.DatabaseConnection;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.Serial;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import tools.Pair;
import tools.data.output.MaplePacketLittleEndianWriter;

public class MapleKeyLayout implements Serializable {

    @Serial private static final long serialVersionUID = 9179541993413738569L;
    private boolean changed = false;
    private Map<Integer, Pair<Byte, Integer>> keymap;

    public MapleKeyLayout() {
        keymap = new HashMap<>();
    }

    public MapleKeyLayout(Map<Integer, Pair<Byte, Integer>> keys) {
        keymap = (keys == null) ? new HashMap<>() : new HashMap<>(keys);
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "Layout() 的契約即為回傳可編輯的 keymap 供呼叫端修改，" + "回傳拷貝會使修改失效，破壞按鍵配置更新行為。")
    public final Map<Integer, Pair<Byte, Integer>> Layout() {
        changed = true;
        return keymap;
    }

    public final void writeData(final MaplePacketLittleEndianWriter mplew) {
        Pair<Byte, Integer> binding;
        for (int x = 0; x < 90; x++) {
            binding = keymap.get(Integer.valueOf(x));
            if (binding != null) {
                mplew.write(binding.left());
                mplew.writeInt(binding.right());
            } else {
                mplew.write(0);
                mplew.writeInt(0);
            }
        }
    }

    public final void saveKeys(final Connection con, final int charid) throws SQLException {
        if (!changed || keymap == null || keymap.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM keymap WHERE characterid = ?")) {
            ps.setInt(1, charid);
            ps.execute();
        }

        try (PreparedStatement ps =
                con.prepareStatement("INSERT INTO keymap VALUES (DEFAULT,?,?,?,?)")) {
            for (Entry<Integer, Pair<Byte, Integer>> keybinding : keymap.entrySet()) {
                if (keybinding == null
                        || keybinding.getKey() == null
                        || keybinding.getValue() == null) {
                    continue;
                }
                ps.setInt(1, charid);
                ps.setInt(2, keybinding.getKey().intValue());
                ps.setByte(3, keybinding.getValue().left().byteValue());
                ps.setInt(4, keybinding.getValue().right().intValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
        changed = false;
    }

    public final void saveKeys(final int charid) throws SQLException {
        if (!changed || keymap == null || keymap.isEmpty()) {
            return;
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }
            try {
                con.setAutoCommit(false);
                saveKeys(con, charid);
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }
}