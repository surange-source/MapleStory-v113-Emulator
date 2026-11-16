package tools.wztosql;

import database.DatabaseConnection;
import java.awt.Point;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import provider.MapleData;
import provider.MapleDataProvider;
import provider.MapleDataProviderFactory;
import provider.MapleDataTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DumpMobSkills {
    private static final Logger log = LoggerFactory.getLogger(DumpMobSkills.class);

    private MapleDataProvider skill;
    protected boolean hadError = false;
    protected boolean update = false;
    protected int id = 0;
    protected String rootpath = new java.io.File("wz").getAbsolutePath();

    public DumpMobSkills(boolean update) throws Exception {
        this.update = update;
        this.skill = MapleDataProviderFactory.getDataProvider(new File(rootpath + "/Skill.wz"));
        if (skill == null) {
            hadError = true;
        }
    }

    public boolean isHadError() {
        return hadError;
    }

    public void dumpMobSkills() throws Exception {
        if (hadError) {
            return;
        }
        try (Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps =
                        con.prepareStatement(
                                "INSERT INTO wz_mobskilldata(skillid, `level`, hp, mpcon, x, y, time, prop, `limit`, spawneffect,`interval`, summons, ltx, lty, rbx, rby, once) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            try {
                dumpMobSkills(con, ps);
            } catch (Exception e) {
                log.error("{} skill.", id);
                log.error("未處理的例外", e);
                hadError = true;
            }
            ps.executeBatch();
        }
    }

    public void delete(Connection con, String sql) throws Exception {
        if (sql == null) {
            return;
        }
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    public boolean doesExist(Connection con, String sql) throws Exception {
        if (sql == null) {
            return false;
        }
        try (PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    // kinda inefficient
    public void dumpMobSkills(Connection con, PreparedStatement ps) throws Exception {
        if (!update) {
            delete(con, "DELETE FROM wz_mobskilldata");
            log.info("Deleted wz_mobskilldata successfully.");
        }
        final MapleData skillz = skill.getData("MobSkill.img");
        if (skillz == null) {
            log.info("MobSkill.img not found, skipping.");
            return;
        }
        log.info("Adding into wz_mobskilldata.....");

        for (MapleData ids : skillz.getChildren()) {
            final MapleData levels = ids.getChildByPath("level");
            if (levels == null) {
                continue;
            }
            for (MapleData lvlz : levels.getChildren()) {
                this.id = Integer.parseInt(ids.getName());
                int lvl = Integer.parseInt(lvlz.getName());
                if (update
                        && doesExist(
                                con,
                                "SELECT * FROM wz_mobskilldata WHERE skillid = "
                                        + id
                                        + " AND level = "
                                        + lvl)) {
                    continue;
                }
                ps.setInt(1, id);
                ps.setInt(2, lvl);
                ps.setInt(3, MapleDataTool.getInt("hp", lvlz, 100));
                ps.setInt(4, MapleDataTool.getInt("mpCon", lvlz, 0));
                ps.setInt(5, MapleDataTool.getInt("x", lvlz, 1));
                ps.setInt(6, MapleDataTool.getInt("y", lvlz, 1));
                ps.setInt(7, MapleDataTool.getInt("time", lvlz, 0)); // * 1000
                ps.setInt(8, MapleDataTool.getInt("prop", lvlz, 100)); // / 100.0
                ps.setInt(9, MapleDataTool.getInt("limit", lvlz, 0));
                ps.setInt(10, MapleDataTool.getInt("summonEffect", lvlz, 0));
                ps.setInt(11, MapleDataTool.getInt("interval", lvlz, 0)); // * 1000

                StringBuilder summ = new StringBuilder();
                List<Integer> toSummon = new ArrayList<>();
                for (int i = 0; i > -1; i++) {
                    if (lvlz.getChildByPath(String.valueOf(i)) == null) {
                        break;
                    }
                    toSummon.add(
                            Integer.valueOf(
                                    MapleDataTool.getInt(
                                            lvlz.getChildByPath(String.valueOf(i)), 0)));
                }
                for (Integer summon : toSummon) {
                    if (summ.length() > 0) {
                        summ.append(", ");
                    }
                    summ.append(String.valueOf(summon));
                }
                ps.setString(12, summ.toString());
                if (lvlz.getChildByPath("lt") != null) {
                    Point lt = (Point) lvlz.getChildByPath("lt").getData();
                    ps.setInt(13, lt.x);
                    ps.setInt(14, lt.y);
                } else {
                    ps.setInt(13, 0);
                    ps.setInt(14, 0);
                }
                if (lvlz.getChildByPath("rb") != null) {
                    Point rb = (Point) lvlz.getChildByPath("rb").getData();
                    ps.setInt(15, rb.x);
                    ps.setInt(16, rb.y);
                } else {
                    ps.setInt(15, 0);
                    ps.setInt(16, 0);
                }
                ps.setByte(17, (byte) (MapleDataTool.getInt("summonOnce", lvlz, 0) > 0 ? 1 : 0));
                log.info("Added skill: {} level {}", id, lvl);
                ps.addBatch();
            }
        }
        log.info("Done wz_mobskilldata...");
    }

    public int currentId() {
        return id;
    }

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new java.io.PrintStream(System.err, true, StandardCharsets.UTF_8));
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
            final DumpMobSkills dq = new DumpMobSkills(update);
            log.info("Dumping mobskills");
            dq.dumpMobSkills();
            hadError |= dq.isHadError();
            currentQuest = dq.currentId();
        } catch (Exception e) {
            hadError = true;
            log.error("未處理的例外", e);
            log.error("{} skill.", currentQuest);
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
