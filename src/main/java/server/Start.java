package server;

import client.SkillFactory;
import database.DatabaseConnection;
import handling.cashshop.CashShopServer;
import handling.channel.ChannelServer;
import handling.channel.MapleGuildRanking;
import handling.login.LoginInformationProvider;
import handling.login.LoginServer;
import handling.world.World;
import handling.world.family.MapleFamilyBuff;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.Timer.*;
import server.events.MapleOxQuizFactory;
import server.life.MapleLifeFactory;
import server.quest.MapleQuest;

public class Start {
    private static final Logger LOG = LoggerFactory.getLogger(Start.class);

    public static final void main(final String args[]) {
        if (Boolean.parseBoolean(ServerProperties.getProperty("tms.Admin"))) {
            LOG.info("[!!! 管理員模式 !!!]");
        }
        if (Boolean.parseBoolean(ServerProperties.getProperty("tms.AutoRegister"))) {
            LOG.info("開啟註冊模式 :::");
        }
        if (Boolean.parseBoolean(ServerProperties.getProperty("tms.MTSload"))) {
            LOG.info("開啟拍賣，關閉萬能NPC :::");
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("UPDATE accounts SET loggedin = 0")) {
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new RuntimeException("[EXCEPTION] Please check if the SQL server is active.", ex);
        }

        ServerProperties.loadChannelIpsFromDatabase();

        World.init();
        LOG.info("目前使用的WZPath: .\\{}", ServerProperties.getProperty("tms.Wzpath"));
        WorldTimer.getInstance().start();
        EtcTimer.getInstance().start();
        MapTimer.getInstance().start();
        MobTimer.getInstance().start();
        CloneTimer.getInstance().start();
        EventTimer.getInstance().start();
        BuffTimer.getInstance().start();
        CheatTimer.getInstance().start();
        PingTimer.getInstance().start();
        //        System.out.println(ServerProperties.getProperty("tms.Wzpath"));
        LoginInformationProvider.getInstance();
        MapleQuest.initQuests(false);
        MapleLifeFactory.loadQuestCounts();
        //        ItemMakerFactory.getInstance();
        MapleItemInformationProvider.getInstance().loadEtc();
        MapleItemInformationProvider.getInstance().loadItems();
        //        System.out.println("[載入髮型臉部物件]");
        MapleItemInformationProvider.getInstance().loadStyles(false);
        RandomRewards.getInstance();
        SkillFactory.getSkill(99999999);
        MapleOxQuizFactory.getInstance().initialize();
        MapleCarnivalFactory.getInstance();
        MapleGuildRanking.getInstance().getRank();
        MapleFamilyBuff.getBuffEntry();
        // MapleServerHandler.registerMBean();
        RankingWorker.getInstance().run();
        if (Boolean.parseBoolean(ServerProperties.getProperty("tms.MTSload"))) {
            MTSStorage.load();
        } else {
            LOG.info("[!拍賣商場已停用!]");
        }
        CashItemFactory.getInstance().initialize();
        LoginServer.run_startup_configurations();
        ChannelServer.startChannel_Main();
        if (Boolean.parseBoolean(ServerProperties.getProperty("tms.CSload"))) {
            LOG.info("[購物商場伺服器啟動中]");
            CashShopServer.run_startup_configurations();
            LOG.info("[購物商場伺服器啟動完成]");
        } else {
            LOG.info("[!購物商場已停用!]");
        }
        CheatTimer.getInstance().register(AutobanManager.getInstance(), 60000);
        Runtime.getRuntime().addShutdownHook(new Thread(ShutdownServer.getInstance()));
        try {
            SpeedRunner.getInstance().loadSpeedRuns();
        } catch (SQLException e) {
            LOG.error("Start.main 發生例外", e);
        }
        World.registerRespawn();
        LoginServer.setOn();
        LOG.info("加載完成 :::");
        // System.gc();
        // PingTimer.getInstance().register(System::gc, 1800000);
    }

    public class Shutdown implements Runnable {

        @Override
        public void run() {
            new Thread(ShutdownServer.getInstance()).start();
        }
    }
}
