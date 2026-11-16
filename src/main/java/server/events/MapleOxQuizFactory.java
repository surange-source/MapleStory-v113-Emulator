package server.events;

import database.DatabaseConnection;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.Randomizer;
import tools.Pair;

public final class MapleOxQuizFactory {
    private static final Logger LOG = LoggerFactory.getLogger(MapleOxQuizFactory.class);

    private static final String SELECT_ALL_OXDATA = "SELECT * FROM wz_oxdata";
    private static final String SELECT_OXDATA_BY_ID =
            "SELECT * FROM wz_oxdata WHERE questionset = ? AND questionid = ?";

    private boolean initialized = false;
    private final Map<Pair<Integer, Integer>, MapleOxQuizEntry> questionCache;

    @SuppressFBWarnings(
            value = "SING_SINGLETON_HAS_NONPRIVATE_CONSTRUCTOR",
            justification = "Constructor is private; singleton is exposed via static holder.")
    private MapleOxQuizFactory() {
        questionCache = new HashMap<>();
    }

    private static final class SingletonHolder {

        private static final MapleOxQuizFactory INSTANCE = new MapleOxQuizFactory();
    }

    public static MapleOxQuizFactory getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public boolean hasInitialized() {
        return initialized;
    }

    public Entry<Pair<Integer, Integer>, MapleOxQuizEntry> grabRandomQuestion() {
        final int size = questionCache.size();
        if (size <= 0) {
            return null;
        }
        while (true) {
            for (Entry<Pair<Integer, Integer>, MapleOxQuizEntry> oxquiz :
                    questionCache.entrySet()) {
                if (Randomizer.nextInt(size) == 0) {
                    return oxquiz;
                }
            }
        }
    }

    public void initialize() {
        if (initialized) {
            return;
        }
        LOG.info("Loading OX Quiz :::");
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }
            try (PreparedStatement ps = con.prepareStatement(SELECT_ALL_OXDATA);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    questionCache.put(
                            new Pair<>(rs.getInt("questionset"), rs.getInt("questionid")), get(rs));
                }
            }
        } catch (Exception e) {
            LOG.error("MapleOxQuizFactory.initialize 發生例外", e);
        }
        LOG.info(" Loaded");
        initialized = true;
    }

    public MapleOxQuizEntry getFromSQL(int questionSet, int questionId) {
        MapleOxQuizEntry ret = null;
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return ret;
            }
            try (PreparedStatement ps = con.prepareStatement(SELECT_OXDATA_BY_ID)) {
                ps.setInt(1, questionSet);
                ps.setInt(2, questionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        ret = get(rs);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("MapleOxQuizFactory.getFromSQL 發生例外", e);
        }
        return ret;
    }

    public static MapleOxQuizEntry getOxEntry(int questionSet, int questionId) {
        return getInstance().getOxQuizEntry(new Pair<>(questionSet, questionId));
    }

    public static MapleOxQuizEntry getOxEntry(Pair<Integer, Integer> pair) {
        return getInstance().getOxQuizEntry(pair);
    }

    public MapleOxQuizEntry getOxQuizEntry(Pair<Integer, Integer> pair) {
        if (pair == null) {
            return null;
        }
        MapleOxQuizEntry mooe = questionCache.get(pair);
        if (mooe == null) {
            if (initialized) {
                return null;
            }
            mooe = getFromSQL(pair.left(), pair.right());
            if (mooe != null) {
                questionCache.put(pair, mooe);
            }
        }
        return mooe;
    }

    private MapleOxQuizEntry get(ResultSet rs) throws SQLException {
        return new MapleOxQuizEntry(
                rs.getString("question"),
                rs.getString("display"),
                getAnswerByText(rs.getString("answer")),
                rs.getInt("questionset"),
                rs.getInt("questionid"));
    }

    private int getAnswerByText(String text) {
        if (text == null) {
            return -1;
        }
        if (text.equalsIgnoreCase("x")) {
            return 0;
        } else if (text.equalsIgnoreCase("o")) {
            return 1;
        } else {
            return -1;
        }
    }

    public static class MapleOxQuizEntry {

        private String question, answerText;
        private int answer, questionset, questionid;

        public MapleOxQuizEntry(
                String question, String answerText, int answer, int questionset, int questionid) {
            this.question = question;
            this.answerText = answerText;
            this.answer = answer;
            this.questionset = questionset;
            this.questionid = questionid;
        }

        public String getQuestion() {
            return question;
        }

        public String getAnswerText() {
            return answerText;
        }

        public int getAnswer() {
            return answer;
        }

        public int getQuestionSet() {
            return questionset;
        }

        public int getQuestionId() {
            return questionid;
        }
    }
}
