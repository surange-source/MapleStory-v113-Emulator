package handling.cashshop;

import handling.channel.PlayerStorage;
import handling.netty.ServerConnection;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.MTSStorage;
import server.ServerProperties;

public class CashShopServer {
    private static final Logger LOG = LoggerFactory.getLogger(CashShopServer.class);

    private static ServerConnection init;
    private static String ip;
    private static InetSocketAddress InetSocketadd;
    private static int PORT = 8605;
    private static PlayerStorage players, playersMTS;
    private static boolean finishedShutdown = false;

    public static final void run_startup_configurations() {
        PORT = Integer.parseInt(ServerProperties.getProperty("tms.CPort", "8605"));
        ip = ServerProperties.getProperty("tms.IP") + ":" + PORT;

        try {
            init = new ServerConnection(PORT, 1, -10); // could code world here to seperate them
            init.run();
            players = new PlayerStorage(-10);
            playersMTS = new PlayerStorage(-20);
            LOG.info("Shop    1: Listening on port {}", PORT);
        } catch (final Exception e) {
            LOG.error("Binding to port {} failed", PORT);
            LOG.error("CashShopServer.run_startup_configurations 發生例外", e);
            throw new RuntimeException("Binding failed.", e);
        }
    }

    public static final String getIP() {
        return ip;
    }

    public static final PlayerStorage getPlayerStorage() {
        return players;
    }

    public static final PlayerStorage getPlayerStorageMTS() {
        return playersMTS;
    }

    public static final void shutdown() {
        if (finishedShutdown) {
            return;
        }
        LOG.info("Saving all connected clients (CS)...");
        players.disconnectAll();
        playersMTS.disconnectAll();
        MTSStorage.getInstance().saveBuyNow(true);
        LOG.info("Shutting down CS...");
        // acceptor.unbindAll();
        finishedShutdown = true;
    }

    public static boolean isShutdown() {
        return finishedShutdown;
    }
}
