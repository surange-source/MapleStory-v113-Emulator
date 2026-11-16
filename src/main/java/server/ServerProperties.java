package server;

import database.DatabaseConnection;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Emilyx3
 */
public final class ServerProperties {
    private static final Logger LOG = LoggerFactory.getLogger(ServerProperties.class);

    private static final Properties props = new Properties();

    private static final String[] toLoad = {"Settings.ini"};

    private ServerProperties() {
    }

    static {
        for (String s : toLoad) {
            try (InputStreamReader fr
                    = new InputStreamReader(new FileInputStream(s), StandardCharsets.UTF_8)) {
                props.load(fr);
            } catch (IOException ex) {
                LOG.error("Settings.ini ERROR: ", ex);
                LOG.error("ServerProperties.ServerProperties 發生例外", ex);
            }
        }
    }

    public static void loadChannelIpsFromDatabase() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM auth_server_channel_ip"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    props.put(rs.getString("name") + rs.getInt("channelid"), rs.getString("value"));
                }
            }
        } catch (SQLException ex) {
            LOG.error("ServerProperties.loadChannelIpsFromDatabase 發生例外", ex);
            throw new RuntimeException("載入 auth_server_channel_ip 失敗", ex);
        }
    }

    public static String getProperty(String s) {
        return props.getProperty(s);
    }

    public static void setProperty(String prop, String newInf) {
        props.setProperty(prop, newInf);
    }

    public static String getProperty(String s, String def) {
        return props.getProperty(s, def);
    }
}
