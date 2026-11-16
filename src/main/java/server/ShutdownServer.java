package server;

import database.DatabaseConnection;
import handling.channel.ChannelServer;
import handling.login.LoginServer;
import handling.world.World;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.Timer.*;

public class ShutdownServer implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(ShutdownServer.class);

    private static final ShutdownServer instance = new ShutdownServer();
    static boolean running = false;

    private ShutdownServer() {
        // singleton
    }

    public static ShutdownServer getInstance() {
        return instance;
    }

    @Override
    public void run() {
        synchronized (this) {
            if (running) {
                return;
            }
            running = true;
        }
        World.isShutDown = true;
        try {
            for (ChannelServer cs : ChannelServer.getAllInstances()) {
                cs.setShutdown();
            }
            LoginServer.shutdown();
            Integer[] chs = ChannelServer.getAllInstance().toArray(new Integer[0]);

            for (int i : chs) {
                try {
                    ChannelServer cs = ChannelServer.getInstance(i);
                    synchronized (this) {
                        cs.shutdown(this);
                    }
                } catch (Exception e) {
                    LOG.error("ShutdownServer.run 發生例外", e);
                }
            }
            // CashShopServer.shutdown();
            World.Guild.save();
            World.Alliance.save();
            World.Family.save();
        } catch (Exception e) {
            LOG.error("關機儲存資料發生異常，原因: ", e);
            LOG.error("ShutdownServer.run 發生例外", e);
        }

        // 1. 先停止所有定時排程器，確保不會再有背景任務呼叫資料庫
        WorldTimer.getInstance().stop();
        MapTimer.getInstance().stop();
        MobTimer.getInstance().stop();
        BuffTimer.getInstance().stop();
        CloneTimer.getInstance().stop();
        EventTimer.getInstance().stop();
        EtcTimer.getInstance().stop();
        CheatTimer.getInstance().stop();
        PingTimer.getInstance().stop();

        try {
            LOG.info("關機中，等待五秒讓非同步存檔落盤...");
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            LOG.error("關機等待中斷，原因: ", e);
        }

        // 2. 最後才關閉 HikariCP 連線池，確保所有存檔都已完成
        try {
            DatabaseConnection.closeAll();
            LOG.info("HikariCP 資料庫連線池已安全關閉。");
        } catch (SQLException e) {
            LOG.error("關閉資料庫連線池失敗，原因: ", e);
        }

        Runtime.getRuntime().halt(0);
    }
}
