package client;

import constants.GameConstants;
import database.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.Pair;

public class MapleCharacterUtil {
    private static final Logger LOG = LoggerFactory.getLogger(MapleCharacterUtil.class);

    private static final Pattern namePattern = Pattern.compile("[a-zA-Z0-9_-]{3,12}");
    private static final Pattern petPattern = Pattern.compile("[a-zA-Z0-9_-]{4,12}");

    public static final boolean canCreateChar(final String name) {
        if (getIdByName(name) != -1 || !isEligibleCharName(name)) {
            return false;
        }
        return true;
    }

    public static final boolean isEligibleCharName(final String name) {
        if (name.length() > 15) {
            return false;
        }
        if (name.length() < 2) {
            return false;
        }
        for (String z : GameConstants.getReserved()) {
            if (name.indexOf(z) != -1) {
                return false;
            }
        }
        return true;
    }

    public static final boolean canChangePetName(final String name) {
        if (petPattern.matcher(name).matches()) {
            for (String z : GameConstants.getReserved()) {
                if (name.indexOf(z) != -1) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public static final String makeMapleReadable(final String in) {
        String wui = in.replace('I', 'i');
        wui = wui.replace('l', 'L');
        wui = wui.replace("rn", "Rn");
        wui = wui.replace("vv", "Vv");
        wui = wui.replace("VV", "Vv");
        return wui;
    }

    public static final int getIdByName(final String name) {
        if (name == null) {
            return -1;
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return -1;
            }
            try (PreparedStatement ps =
                    con.prepareStatement("SELECT id FROM characters WHERE name = ?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return -1;
                    }
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            LOG.error("error 'getIdByName'", e);
        }
        return -1;
    }

    public static final boolean PromptPoll(final int accountid) {
        boolean prompt = false;
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return prompt;
            }
            try (PreparedStatement ps =
                    con.prepareStatement("SELECT * from game_poll_reply where AccountId = ?")) {
                ps.setInt(1, accountid);
                try (ResultSet rs = ps.executeQuery()) {
                    prompt = !rs.next();
                }
            }
        } catch (SQLException e) {
        }
        return prompt;
    }

    public static final boolean SetPoll(final int accountid, final int selection) {
        if (!PromptPoll(accountid)) { // Hacking OR spamming the db.
            return false;
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return false;
            }
            try (PreparedStatement ps =
                    con.prepareStatement(
                            "INSERT INTO game_poll_reply (AccountId, SelectAns) VALUES (?, ?)")) {
                ps.setInt(1, accountid);
                ps.setInt(2, selection);

                ps.execute();
            }
        } catch (SQLException e) {
        }
        return true;
    }

    // -2 = An unknown error occured
    // -1 = Account not found on database
    // 0 = You do not have a second password set currently.
    // 1 = The password you have input is wrong
    // 2 = Password Changed successfully
    public static final int Change_SecondPassword(
            final int accid, final String password, final String newpassword) {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return -2;
            }
            String secondPassword;
            String salt2;
            try (PreparedStatement ps =
                    con.prepareStatement("SELECT * from accounts where id = ?")) {
                ps.setInt(1, accid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return -1;
                    }
                    secondPassword = rs.getString("2ndpassword");
                    salt2 = rs.getString("salt2");
                }
            }
            if (secondPassword != null && salt2 != null) {
                secondPassword = LoginCrypto.rand_r(secondPassword);
            } else if (secondPassword == null && salt2 == null) {
                return 0;
            }
            if (!check_ifPasswordEquals(secondPassword, password, salt2)) {
                return 1;
            }

            String SHA1hashedsecond;
            try {
                SHA1hashedsecond = LoginCryptoLegacy.encodeSHA1(newpassword);
            } catch (Exception e) {
                return -2;
            }
            try (PreparedStatement ps =
                    con.prepareStatement(
                            "UPDATE accounts set 2ndpassword = ?, salt2 = ? where id = ?")) {
                ps.setString(1, SHA1hashedsecond);
                ps.setString(2, null);
                ps.setInt(3, accid);

                if (!ps.execute()) {
                    return 2;
                }
            }
            return -2;
        } catch (SQLException e) {
            LOG.error("error 'getIdByName'", e);
            return -2;
        }
    }

    private static final boolean check_ifPasswordEquals(
            final String passhash, final String pwd, final String salt) {
        // Check if the passwords are correct here. :B
        if (passhash == null || pwd == null) {
            return false;
        }
        if (LoginCryptoLegacy.isLegacyPassword(passhash)
                && LoginCryptoLegacy.checkPassword(pwd, passhash)) {
            // Check if a password upgrade is needed.
            return true;
        } else if (salt == null && LoginCrypto.checkSha1Hash(passhash, pwd)) {
            return true;
        } else if (LoginCrypto.checkSaltedSha512Hash(passhash, pwd, salt)) {
            return true;
        }
        return false;
    }

    // id accountid gender
    public static Pair<Integer, Pair<Integer, Integer>> getInfoByName(String name, int world) {
        if (name == null) {
            return null;
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return null;
            }
            try (PreparedStatement ps =
                    con.prepareStatement("SELECT * FROM characters WHERE name = ? AND world = ?")) {
                ps.setString(1, name);
                ps.setInt(2, world);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    return new Pair<>(
                            rs.getInt("id"),
                            new Pair<>(rs.getInt("accountid"), rs.getInt("gender")));
                }
            }
        } catch (Exception e) {
            LOG.error("MapleCharacterUtil.check_ifPasswordEquals 發生例外", e);
        }
        return null;
    }

    public static void setNXCodeUsed(String name, String code) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }
            try (PreparedStatement ps =
                    con.prepareStatement(
                            "UPDATE nxcode SET `user` = ?, `valid` = 0 WHERE code = ?")) {
                ps.setString(1, name);
                ps.setString(2, code);
                ps.execute();
            }
        }
    }

    public static void sendNote(String to, String name, String msg, int fame) {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }
            try (PreparedStatement ps =
                    con.prepareStatement(
                            "INSERT INTO notes (`to`, `from`, `message`, `timestamp`, `gift`) VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, to);
                ps.setString(2, name);
                ps.setString(3, msg);
                ps.setLong(4, System.currentTimeMillis());
                ps.setInt(5, fame);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            LOG.error("Unable to send note", e);
        }
    }

    public static boolean getNXCodeValid(String code, boolean validcode) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return validcode;
            }
            try (PreparedStatement ps =
                    con.prepareStatement("SELECT `valid` FROM nxcode WHERE code = ?")) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        validcode = rs.getInt("valid") > 0;
                    }
                }
            }
        }
        return validcode;
    }

    public static int getNXCodeType(String code) throws SQLException {
        int type = -1;
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return type;
            }
            try (PreparedStatement ps =
                    con.prepareStatement("SELECT `type` FROM nxcode WHERE code = ?")) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        type = rs.getInt("type");
                    }
                }
            }
        }
        return type;
    }

    public static int getNXCodeItem(String code) throws SQLException {
        int item = -1;
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return item;
            }
            try (PreparedStatement ps =
                    con.prepareStatement("SELECT `item` FROM nxcode WHERE code = ?")) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        item = rs.getInt("item");
                    }
                }
            }
        }
        return item;
    }
}
