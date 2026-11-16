package server;

import database.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RankingWorker {
    private static final Logger LOG = LoggerFactory.getLogger(RankingWorker.class);

    private final Map<Integer, List<RankingInformation>> rankings = new HashMap<>();
    private final Map<String, Integer> jobCommands = new HashMap<>();

    private RankingWorker() {}

    private static final class Holder {

        private static final RankingWorker INSTANCE = new RankingWorker();
    }

    public static final RankingWorker getInstance() {
        return Holder.INSTANCE;
    }

    public final Integer getJobCommand(final String job) {
        return jobCommands.get(job);
    }

    public final Map<String, Integer> getJobCommands() {
        return jobCommands;
    }

    public final List<RankingInformation> getRankingInfo(final int job) {
        return rankings.get(job);
    }

    public final void run() {
        LOG.info("Loading Ranking Server :::");
        loadJobCommands();
        try {
            updateRanking();
        } catch (Exception ex) {
            LOG.error("RankingWorker.run 發生例外", ex);
            LOG.error("Could not update rankings");
        }
        LOG.info(" Loaded");
    }

    private void updateRanking() throws Exception {
        final String selectSql =
                "SELECT c.id, c.job, c.exp, c.level, c.name, c.jobRank, c.jobRankMove, c.`rank`, c.rankMove"
                        + ", a.lastlogin AS lastlogin, a.loggedin FROM characters AS c LEFT JOIN accounts AS a ON c.accountid = a.id WHERE c.gm = 0 AND a.banned = 0 "
                        + "ORDER BY c.level DESC , c.exp DESC , c.fame DESC , c.meso DESC , c.rank ASC";
        final String updateSql =
                "UPDATE characters SET jobRank = ?, jobRankMove = ?, `rank` = ?, rankMove = ? WHERE id = ?";

        int rank = 0; // for "all"
        final Map<Integer, Integer> rankMap = new LinkedHashMap<>();
        for (int i : jobCommands.values()) {
            rankMap.put(i, 0); // job to rank
            rankings.put(i, new ArrayList<>());
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                LOG.error("Could not update rankings: no database connection");
                return;
            }
            final boolean prevAutoCommit = con.getAutoCommit();
            con.setAutoCommit(false);
            try (PreparedStatement charSelect = con.prepareStatement(selectSql);
                    ResultSet rs = charSelect.executeQuery();
                    PreparedStatement ps = con.prepareStatement(updateSql)) {
                while (rs.next()) {
                    int job = rs.getInt("job");
                    if (!rankMap.containsKey(job / 100)) { // not supported.
                        continue;
                    }
                    int jobRank = rankMap.get(job / 100) + 1;
                    rankMap.put(job / 100, jobRank);
                    rank++;
                    rankings.get(-1)
                            .add(
                                    new RankingInformation(
                                            rs.getString("name"),
                                            job,
                                            rs.getInt("level"),
                                            rs.getInt("exp"),
                                            rank));
                    rankings.get(job / 100)
                            .add(
                                    new RankingInformation(
                                            rs.getString("name"),
                                            job,
                                            rs.getInt("level"),
                                            rs.getInt("exp"),
                                            jobRank));
                    ps.setInt(1, jobRank);
                    ps.setInt(2, rs.getInt("jobRank") - jobRank);
                    ps.setInt(3, rank);
                    ps.setInt(4, rs.getInt("rank") - rank);
                    ps.setInt(5, rs.getInt("id"));
                    ps.addBatch();
                }
                ps.executeBatch();
                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(prevAutoCommit);
            }
        }
    }

    public final void loadJobCommands() {
        // messy, cleanup
        jobCommands.put("all", -1);
        jobCommands.put("beginner", 0);
        jobCommands.put("warrior", 1);
        jobCommands.put("magician", 2);
        jobCommands.put("bowman", 3);
        jobCommands.put("thief", 4);
        jobCommands.put("pirate", 5);
        //        jobCommands.put("nobless", 10);
        //        jobCommands.put("soulmaster", 11);
        //        jobCommands.put("flamewizard", 12);
        //        jobCommands.put("windbreaker", 13);
        //        jobCommands.put("nightwalker", 14);
        //        jobCommands.put("striker", 15);
        //        jobCommands.put("legend", 20);
        //        jobCommands.put("aran", 21);
        //        jobCommands.put("evan", 22);
        // taking out resistance from here for now
    }

    public static class RankingInformation {

        public int job, level, exp, rank;
        public String name, toString;

        public RankingInformation(String name, int job, int level, int exp, int rank) {
            this.name = name;
            this.job = job;
            this.level = level;
            this.exp = exp;
            this.rank = rank;
            loadToString();
        }

        public void loadToString() {
            final StringBuilder builder = new StringBuilder("Rank ");
            builder.append(rank);
            builder.append(" : ");
            builder.append(name);
            builder.append(" - Level ");
            builder.append(level);
            builder.append(" ");
            builder.append(MapleCarnivalChallenge.getJobNameById(job));
            builder.append(" | ");
            builder.append(exp);
            builder.append(" EXP");
            this.toString = builder.toString(); // Rank 1 : KiDALex - Level 200 Blade Master | 0 EXP
        }

        @Override
        public String toString() {
            return toString;
        }
    }
}
