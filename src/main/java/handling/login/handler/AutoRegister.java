package handling.login.handler;

import client.LoginCrypto;
import database.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AutoRegister {
    private static final Logger log = LoggerFactory.getLogger(AutoRegister.class);

    private static final int ACCOUNTS_PER_IP = 5;
    private static volatile boolean autoRegister = true;
    private static volatile boolean success = false;

    private AutoRegister() {}

    public static boolean isAutoRegister() {
        return autoRegister;
    }

    public static void setAutoRegister(boolean value) {
        autoRegister = value;
    }

    public static boolean isSuccess() {
        return success;
    }

    public static void setSuccess(boolean value) {
        success = value;
    }

    public static boolean getAccountExists(String login) {
        if (login == null) {
            return false;
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return false;
            }
            try (PreparedStatement ps =
                    con.prepareStatement("SELECT name FROM accounts WHERE name = ?")) {
                ps.setString(1, login);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException ex) {
            log.error("查詢帳號是否存在失敗", ex);
        }
        return false;
    }

    public static void createAccount(String login, String pwd, String eip, String macData) {
        if (login == null || pwd == null || eip == null) {
            return;
        }
        String sockAddr = eip;

        int colonIdx = sockAddr.lastIndexOf(':');
        if (colonIdx <= 1) {
            return;
        }
        String ip = sockAddr.substring(1, colonIdx);

        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }

            // 1. 取得此 IP 已註冊的帳號數量
            int count = 0;
            try (PreparedStatement ipc =
                    con.prepareStatement("SELECT COUNT(*) FROM accounts WHERE SessionIP = ?")) {
                ipc.setString(1, ip);
                try (ResultSet rs = ipc.executeQuery()) {
                    if (rs.next()) {
                        count = rs.getInt(1);
                    }
                }
            }

            // 2. 未達上限才建立帳號
            if (count < ACCOUNTS_PER_IP) {
                try (PreparedStatement ps =
                        con.prepareStatement(
                                "INSERT INTO accounts (name, password, email, birthday, macs, SessionIP) VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, login);
                    ps.setString(2, LoginCrypto.hexSha1(pwd));
                    ps.setString(3, "autoregister@mail.com");
                    ps.setString(4, java.time.LocalDate.now().toString()); // yyyy-MM-dd，Created day
                    ps.setString(5, macData == null ? "" : macData);
                    ps.setString(6, ip);
                    ps.executeUpdate();
                }
            }

            success = true;
        } catch (Exception ex) {
            log.error("建立帳號失敗", ex);
        }
    }
}
