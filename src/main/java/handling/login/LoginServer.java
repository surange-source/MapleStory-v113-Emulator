package handling.login;

import handling.netty.ServerConnection;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import server.ServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginServer {
    private static final Logger log = LoggerFactory.getLogger(LoginServer.class);

    private static ServerConnection init;
    static int PORT = 8484;
    private static InetSocketAddress InetSocketadd;
    private static Map<Integer, Integer> load = new HashMap<>();
    private static String serverName, eventMessage, bubbleMessage, bubbleMessagePos;
    private static byte flag;
    private static int maxCharacters, userLimit, usersOn = 0;
    private static boolean finishedShutdown = true, adminOnly = false;

    public static final void addChannel(final int channel) {
        load.put(channel, 0);
    }

    public static final void removeChannel(final int channel) {
        load.remove(channel);
    }

    public static final void run_startup_configurations() {
        userLimit = Integer.parseInt(ServerProperties.getProperty("tms.UserLimit"));
        serverName = ServerProperties.getProperty("tms.ServerName");
        eventMessage = ServerProperties.getProperty("tms.EventMessage");
        bubbleMessage = ServerProperties.getProperty("tms.BubbleMessage");
        bubbleMessagePos = ServerProperties.getProperty("tms.BubbleMessagePos");
        flag = Byte.parseByte(ServerProperties.getProperty("tms.Flag"));
        adminOnly = Boolean.parseBoolean(ServerProperties.getProperty("tms.Admin", "false"));
        maxCharacters = Integer.parseInt(ServerProperties.getProperty("tms.MaxCharacters"));
        PORT = Integer.parseInt(ServerProperties.getProperty("tms.LPort", "8484"));

        try {
            init = new ServerConnection(PORT); // could code world here to seperate them
            init.run();
            log.info("Login   1: Listening on port {}", PORT);
        } catch (final Exception e) {
            log.error("Binding to port {} failed", PORT, e);
            throw new RuntimeException("Binding to port " + PORT + " failed", e);
        }
    }

    public static final void shutdown() {
        if (finishedShutdown) {
            return;
        }
        log.info("Shutting down login...");
        init.close();
        finishedShutdown = true; // nothing. lol
    }

    public static final String getServerName() {
        return serverName;
    }

    public static final String getEventMessage() {
        return eventMessage;
    }

    public static final String getBubbleMessage() {
        return bubbleMessage;
    }

    public static final String getBubbleMessagePos() {
        return bubbleMessagePos;
    }

    public static final byte getFlag() {
        return flag;
    }

    public static final int getMaxCharacters() {
        return maxCharacters;
    }

    public static final Map<Integer, Integer> getLoad() {
        return Map.copyOf(load);
    }

    public static void setLoad(final Map<Integer, Integer> load_, final int usersOn_) {
        load = (load_ == null) ? new HashMap<>() : new HashMap<>(load_);
        usersOn = usersOn_;
    }

    public static final int getPort() {
        return PORT;
    }

    public static final void setPort(final int port) {
        PORT = port;
    }

    public static final void setEventMessage(final String newMessage) {
        eventMessage = newMessage;
    }

    public static final void setFlag(final byte newflag) {
        flag = newflag;
    }

    public static final int getUserLimit() {
        return userLimit;
    }

    public static final int getUsersOn() {
        return usersOn;
    }

    public static final void setUserLimit(final int newLimit) {
        userLimit = newLimit;
    }

    public static final boolean isAdminOnly() {
        return adminOnly;
    }

    public static final boolean isShutdown() {
        return finishedShutdown;
    }

    public static final void setOn() {
        finishedShutdown = false;
    }
}
