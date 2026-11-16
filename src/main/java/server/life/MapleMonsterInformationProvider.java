package server.life;

import database.DatabaseConnection;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import provider.MapleData;
import provider.MapleDataProvider;
import provider.MapleDataProviderFactory;
import provider.MapleDataTool;

public final class MapleMonsterInformationProvider {
    private static final Logger LOG = LoggerFactory.getLogger(MapleMonsterInformationProvider.class);

    private static final class SingletonHolder {

        private static final MapleMonsterInformationProvider INSTANCE =
                new MapleMonsterInformationProvider();

        private SingletonHolder() {}
    }

    private final Map<Integer, String> mobCache = new HashMap<>();
    private final Map<Integer, List<MonsterDropEntry>> drops = new HashMap<>();
    private final List<MonsterGlobalDropEntry> globaldrops = new ArrayList<>();

    /**
     * Private constructor to enforce singleton pattern. Initialization failures are handled inside
     * {@link #retrieveGlobal()}.
     */
    private MapleMonsterInformationProvider() {
        retrieveGlobal();
    }

    /**
     * Returns the shared singleton instance.
     *
     * <p>The returned object is intentionally the shared singleton; callers are expected to use it
     * directly. This is a false positive for {@code MS_EXPOSE_REP} because the singleton is
     * designed to be shared and is not a mutable value object whose internal state should be
     * copied.
     *
     * @return the singleton instance
     */
    @SuppressFBWarnings(
            value = "MS_EXPOSE_REP",
            justification =
                    "Singleton accessor intentionally returns the shared instance; "
                            + "copying would break existing callers and business behavior.")
    public static MapleMonsterInformationProvider getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public final List<MonsterGlobalDropEntry> getGlobalDrop() {
        return globaldrops;
    }

    public Map<Integer, String> getAllMonsters() {
        if (mobCache.isEmpty()) {
            final MapleDataProvider stringData =
                    MapleDataProviderFactory.getDataProvider("String.wz");
            MapleData mobsData = stringData.getData("Mob.img");
            for (MapleData itemFolder : mobsData.getChildren()) {
                mobCache.put(
                        Integer.parseInt(itemFolder.getName()),
                        MapleDataTool.getString("name", itemFolder, "NO-NAME"));
            }
        }
        return mobCache;
    }

    private void retrieveGlobal() {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }
            try (PreparedStatement ps =
                            con.prepareStatement(
                                    "SELECT * FROM drop_data_global WHERE chance > 0");
                    ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    globaldrops.add(
                            new MonsterGlobalDropEntry(
                                    rs.getInt("itemid"),
                                    rs.getInt("chance"),
                                    rs.getInt("continent"),
                                    rs.getByte("dropType"),
                                    rs.getInt("minimum_quantity"),
                                    rs.getInt("maximum_quantity"),
                                    rs.getShort("questid")));
                }
            }
        } catch (SQLException e) {
            LOG.error("Error retrieving drop", e);
        }
    }

    public List<MonsterDropEntry> retrieveDrop(final int monsterId) {
        if (drops.containsKey(monsterId)) {
            return drops.get(monsterId);
        }
        final List<MonsterDropEntry> ret = new LinkedList<>();

        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return ret;
            }
            try (PreparedStatement ps =
                    con.prepareStatement("SELECT * FROM drop_data WHERE dropperid = ?")) {
                ps.setInt(1, monsterId);
                try (ResultSet rs = ps.executeQuery()) {
                    int itemid;
                    int chance;
                    while (rs.next()) {
                        itemid = rs.getInt("itemid");
                        chance = rs.getInt("chance");
                        /*
                        if (GameConstants.getInventoryType(itemid) == MapleInventoryType.EQUIP) {
                            chance *= 10; //in GMS/SEA it was raised
                        }
                         */
                        ret.add(
                                new MonsterDropEntry(
                                        itemid,
                                        chance,
                                        rs.getInt("minimum_quantity"),
                                        rs.getInt("maximum_quantity"),
                                        rs.getShort("questid")));
                    }
                }
            }
        } catch (SQLException e) {
            return ret;
        }
        drops.put(monsterId, ret);
        return ret;
    }

    public void clearDrops() {
        drops.clear();
        globaldrops.clear();
        retrieveGlobal();
    }

    public int getDropQuest(int monsterId) {
        int quest = 0;
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return quest;
            }
            try (PreparedStatement ps =
                    con.prepareStatement("SELECT questid FROM drop_data WHERE dropperid = ?")) {
                ps.setInt(1, monsterId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        quest = rs.getInt("questid");
                    }
                }
            }
        } catch (SQLException e) {
            LOG.error("[ERR]MonsterInfoProv.getDropQuest: ", e);
        }
        return quest;
    }

    public int getDropChance(int monsterId) {
        int chance = 0;
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return chance;
            }
            try (PreparedStatement ps =
                    con.prepareStatement("SELECT chance FROM drop_data WHERE dropperid = ?")) {
                ps.setInt(1, monsterId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        chance = rs.getInt("chance");
                    }
                }
            }
        } catch (SQLException e) {
            LOG.error("[ERR]MonsterInfoProv.getDropChance: ", e);
        }
        return chance;
    }

    public List<Integer> getMobByItem(final int itemId) {
        final List<Integer> mobs = new LinkedList<>();
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return mobs;
            }
            try (PreparedStatement ps =
                    con.prepareStatement("SELECT * FROM drop_data WHERE itemid = ?")) {
                ps.setInt(1, itemId);
                try (ResultSet rs = ps.executeQuery()) {
                    int mobid;
                    while (rs.next()) {
                        mobid = rs.getInt("dropperid");
                        if (!mobs.contains(mobid)) {
                            mobs.add(mobid);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOG.error("[ERR]MonsterInfoProv.getMobByItem: ", e);
            LOG.error("MapleMonsterInformationProvider.getMobByItem 發生例外", e);
        }
        return mobs;
    }
}
