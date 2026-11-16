package handling.channel;

import database.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.Timer;

public final class MapleGuildRanking {
    private static final Logger LOG = LoggerFactory.getLogger(MapleGuildRanking.class);

    private static final MapleGuildRanking instance = new MapleGuildRanking();
    private final List<GuildRankingInfo> ranks = new LinkedList<>();
    private final List<levelRankingInfo> LevelRank = new LinkedList<>();
    private final List<mesoRankingInfo> MesoRank = new LinkedList<>();
    private final List<mesoRankingInfo> FameRank = new LinkedList<>();
    private final Map<Integer, List<JobRankingInfo>> JobRanks = new HashMap<>();

    private MapleGuildRanking() {}

    public void RankingUpdate() {
        Timer.WorldTimer.getInstance()
                .register(
                        () -> {
                            try {
                                reload();
                                showLevelRank();
                                showMesoRank();
                                //   System.out.println("Ranking update");
                            } catch (Exception ex) {
                                LOG.error("MapleGuildRanking.RankingUpdate 發生例外", ex);
                                LOG.error("Could not update rankings");
                            }
                        },
                        1 * 60 * 60 * 1000,
                        1 * 60 * 60 * 1000);
    }

    public static synchronized MapleGuildRanking getInstance() {
        return instance;
    }

    public List<GuildRankingInfo> getRank() {
        if (ranks.isEmpty()) {
            reload();
        }
        return Collections.unmodifiableList(ranks);
    }

    public List<levelRankingInfo> getLevelRank() {
        if (LevelRank.isEmpty()) {
            showLevelRank();
        }
        return Collections.unmodifiableList(LevelRank);
    }

    public List<mesoRankingInfo> getMesoRank() {
        if (MesoRank.isEmpty()) {
            showMesoRank();
        }
        return Collections.unmodifiableList(MesoRank);
    }

    public List<JobRankingInfo> getJobRank(int type) {
        List<JobRankingInfo> list = JobRanks.get(type);
        if (list == null || list.isEmpty()) {
            loadJobRank(type);
            list = JobRanks.get(type);
        }
        if (list == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(list);
    }

    private void reload() {
        ranks.clear();
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }
            try (PreparedStatement ps =
                            con.prepareStatement(
                                    "SELECT * FROM guilds ORDER BY `GP` DESC LIMIT 50");
                    ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    final GuildRankingInfo rank =
                            new GuildRankingInfo(
                                    rs.getString("name"),
                                    rs.getInt("GP"),
                                    rs.getInt("logo"),
                                    rs.getInt("logoColor"),
                                    rs.getInt("logoBG"),
                                    rs.getInt("logoBGColor"));

                    ranks.add(rank);
                }
            }
        } catch (SQLException e) {
            LOG.error("Error handling guildRanking");
            LOG.error("MapleGuildRanking.reload 發生例外", e);
        }
    }

    private void showLevelRank() {
        LevelRank.clear();
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }
            try (PreparedStatement ps =
                            con.prepareStatement(
                                    "SELECT * FROM characters WHERE gm < 1 and accountid > 0 ORDER BY `level` DESC LIMIT 100");
                    ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    final levelRankingInfo rank1 =
                            new levelRankingInfo(
                                    rs.getString("name"),
                                    rs.getInt("level"),
                                    rs.getInt("str"),
                                    rs.getInt("dex"),
                                    rs.getInt("int"),
                                    rs.getInt("luk"));
                    LevelRank.add(rank1);
                }
            }
        } catch (SQLException e) {
            LOG.error("排行錯誤");
        }
    }

    private void showMesoRank() {
        MesoRank.clear();

        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }
            try (PreparedStatement ps =
                            con.prepareStatement(
                                    "SELECT *, ( chr.meso + s.meso ) as money FROM `characters` as chr , `storages` as s WHERE chr.gm < 1  AND s.accountid = chr.accountid ORDER BY money DESC LIMIT 20");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    final mesoRankingInfo rank2 =
                            new mesoRankingInfo(
                                    rs.getString("name"),
                                    rs.getLong("money"),
                                    rs.getInt("str"),
                                    rs.getInt("dex"),
                                    rs.getInt("int"),
                                    rs.getInt("luk"));
                    MesoRank.add(rank2);
                }
            }
        } catch (SQLException e) {
            LOG.error("富豪排行錯誤");
        }
    }

    public static class mesoRankingInfo {

        private final String name;
        private final long meso;
        private final int str, dex, _int, luk;

        public mesoRankingInfo(String name, long meso, int str, int dex, int intt, int luk) {
            this.name = name;
            this.meso = meso;
            this.str = str;
            this.dex = dex;
            this._int = intt;
            this.luk = luk;
        }

        public String getName() {
            return name;
        }

        public long getMeso() {
            return meso;
        }

        public int getStr() {
            return str;
        }

        public int getDex() {
            return dex;
        }

        public int getInt() {
            return _int;
        }

        public int getLuk() {
            return luk;
        }
    }

    public static class JobRankingInfo {

        private final String name;
        private final int level, str, dex, _int, luk, job;

        public JobRankingInfo(
                String name, int level, int job, int str, int dex, int intt, int luk) {
            this.name = name;
            this.level = level;
            this.job = job;
            this.str = str;
            this.dex = dex;
            this._int = intt;
            this.luk = luk;
        }

        public String getName() {
            return name;
        }

        public int getLevel() {
            return level;
        }

        public int getStr() {
            return str;
        }

        public int getDex() {
            return dex;
        }

        public int getInt() {
            return _int;
        }

        public int getLuk() {
            return luk;
        }

        public int getJob() {
            return job;
        }
    }

    private void loadJobRank(int type) {
        List<JobRankingInfo> existing = JobRanks.get(type);
        if (existing != null) {
            existing.clear();
        }

        int jobMin;
        int jobMax;
        switch (type) {
            case 1:
                jobMin = 100;
                jobMax = 132;
                break;
            case 2:
                jobMin = 200;
                jobMax = 232;
                break;
            case 3:
                jobMin = 300;
                jobMax = 322;
                break;
            case 4:
                jobMin = 400;
                jobMax = 422;
                break;
            case 5:
                jobMin = 500;
                jobMax = 522;
                break;
            case 6:
                jobMin = 2000;
                jobMax = 2112;
                break;
            default:
                return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }
            try (PreparedStatement ps =
                    con.prepareStatement(
                            "SELECT * FROM characters WHERE gm = 0 and job >= ? and job <= ? "
                                    + "and accountid in (select id from accounts where banned= '0') "
                                    + "ORDER BY `level` DESC LIMIT 10")) {
                ps.setInt(1, jobMin);
                ps.setInt(2, jobMax);
                try (ResultSet rs = ps.executeQuery()) {
                    LinkedList<JobRankingInfo> JobRankList = new LinkedList<>();
                    while (rs.next()) {
                        final JobRankingInfo JobRank =
                                new JobRankingInfo(
                                        rs.getString("name"),
                                        rs.getInt("level"),
                                        rs.getInt("job"),
                                        rs.getInt("str"),
                                        rs.getInt("dex"),
                                        rs.getInt("int"),
                                        rs.getInt("luk"));
                        JobRankList.add(JobRank);
                    }
                    JobRanks.put(type, JobRankList);
                }
            }
        } catch (SQLException e) {
            LOG.error("未能顯示職業{}排行", type);
        }
    }

    public static class levelRankingInfo {

        private final String name;
        private final int level, str, dex, _int, luk;

        public levelRankingInfo(String name, int level, int str, int dex, int intt, int luk) {
            this.name = name;
            this.level = level;
            this.str = str;
            this.dex = dex;
            this._int = intt;
            this.luk = luk;
        }

        public String getName() {
            return name;
        }

        public int getLevel() {
            return level;
        }

        public int getStr() {
            return str;
        }

        public int getDex() {
            return dex;
        }

        public int getInt() {
            return _int;
        }

        public int getLuk() {
            return luk;
        }
    }

    public static class GuildRankingInfo {

        private String name;
        private int gp, logo, logocolor, logobg, logobgcolor;

        public GuildRankingInfo(
                String name, int gp, int logo, int logocolor, int logobg, int logobgcolor) {
            this.name = name;
            this.gp = gp;
            this.logo = logo;
            this.logocolor = logocolor;
            this.logobg = logobg;
            this.logobgcolor = logobgcolor;
        }

        public String getName() {
            return name;
        }

        public int getGP() {
            return gp;
        }

        public int getLogo() {
            return logo;
        }

        public int getLogoColor() {
            return logocolor;
        }

        public int getLogoBg() {
            return logobg;
        }

        public int getLogoBgColor() {
            return logobgcolor;
        }
    }
}
