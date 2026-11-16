package database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ServerProperties;

/**
 * MapleStory V113 Database Connection Pool (Phase B: Pure HikariCP Mode). All callers must acquire
 * connections via try-with-resources: try (Connection con = DatabaseConnection.getConnection()) {
 * ... }
 */
public class DatabaseConnection {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseConnection.class);

    private static volatile HikariDataSource ds;

    public static final int CLOSE_CURRENT_RESULT = 1;

    /**
     * The constant indicating that the current <code>ResultSet</code> object should not be closed
     * when calling <code>getMoreResults</code>.
     *
     * @since 1.4
     */
    public static final int KEEP_CURRENT_RESULT = 2;

    /**
     * The constant indicating that all <code>ResultSet</code> objects that have previously been
     * kept open should be closed when calling <code>getMoreResults</code>.
     *
     * @since 1.4
     */
    public static final int CLOSE_ALL_RESULTS = 3;

    /**
     * The constant indicating that a batch statement executed successfully but that no count of the
     * number of rows it affected is available.
     *
     * @since 1.4
     */
    public static final int SUCCESS_NO_INFO = -2;

    /**
     * The constant indicating that an error occured while executing a batch statement.
     *
     * @since 1.4
     */
    public static final int EXECUTE_FAILED = -3;

    /**
     * The constant indicating that generated keys should be made available for retrieval.
     *
     * @since 1.4
     */
    public static final int RETURN_GENERATED_KEYS = 1;

    /**
     * The constant indicating that generated keys should not be made available for retrieval.
     *
     * @since 1.4
     */
    public static final int NO_GENERATED_KEYS = 2;

    /** 從 HikariCP 連線池借出一條資料庫連線。 呼叫端必須使用 try-with-resources 確保用畢立即歸還至連線池。 */
    public static final Connection getConnection() {
        try {
            return dataSource().getConnection();
        } catch (SQLException e) {
            LOG.error("[DatabaseConnection.getConnection] 無法從連線池取得連線", e);
            throw new DatabaseException("無法從連線池取得連線", e);
        }
    }

    /** 兼容舊版呼叫介面，直接委派給標準連線池 getConnection()。 */
    public static final Connection NewConnection() {
        return getConnection();
    }

    public static final void closeAll() throws SQLException {
        final HikariDataSource local = ds;
        if (local != null) {
            ds = null;
            local.close();
        }
    }

    private static HikariDataSource dataSource() {
        HikariDataSource local = ds;
        if (local == null || local.isClosed()) {
            synchronized (DatabaseConnection.class) {
                local = ds;
                if (local == null || local.isClosed()) {
                    local = buildDataSource();
                    ds = local;
                }
            }
        }
        return local;
    }

    private static HikariDataSource buildDataSource() {
        final HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(
                "jdbc:mysql://"
                        + ServerProperties.getProperty("tms.dbIP")
                        + ":"
                        + ServerProperties.getProperty("tms.dbPort")
                        + "/"
                        + ServerProperties.getProperty("tms.dbName")
                        + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Taipei"
                        + "&cachePrepStmts=true&prepStmtCacheSize=250&prepStmtCacheSqlLimit=2048"
                        + "&useServerPrepStmts=true&rewriteBatchedStatements=true");
        cfg.setUsername(ServerProperties.getProperty("tms.dbUser"));
        cfg.setPassword(ServerProperties.getProperty("tms.dbPass"));
        cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // Phase B 標準連線池模式：連線用完即還，不再永久綁定執行緒
        final int maxPool = Math.max(24, Runtime.getRuntime().availableProcessors() * 4);
        cfg.setMaximumPoolSize(maxPool);
        cfg.setMinimumIdle(4);

        cfg.setConnectionTimeout(10_000);
        cfg.setIdleTimeout(300_000); // 閒置超過 5 分鐘自動釋放多餘連線
        cfg.setMaxLifetime(1_800_000); // 30 分鐘強制汰換，低於 MySQL 預設 wait_timeout
        cfg.setInitializationFailTimeout(-1);

        // 開啟 5 秒連線洩漏偵測：若有連線超過 5 秒未歸還，控制台會自動印出完整 Java 堆疊定位
        cfg.setLeakDetectionThreshold(5_000);

        cfg.setReadOnly(false);
        cfg.setAutoCommit(true);
        cfg.setPoolName("MapleDB");
        return new HikariDataSource(cfg);
    }
}
