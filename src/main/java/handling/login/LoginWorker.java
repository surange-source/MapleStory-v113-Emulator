package handling.login;

import client.MapleClient;
import handling.channel.ChannelServer;
import java.util.Map;
import java.util.Map.Entry;
import server.Timer.PingTimer;
import tools.MaplePacketCreator;
import tools.packet.LoginPacket;

public class LoginWorker {

    private static long lastUpdate = 0;

    public static void registerClient(final MapleClient c) {
        if (LoginServer.isAdminOnly() && !c.isGm()) {
            c.sendPacket(
                    MaplePacketCreator.serverNotice(
                            1,
                            "The server is currently set to Admin login only.\r\nWe are currently testing some issues.\r\nPlease try again later."));
            c.sendPacket(LoginPacket.getLoginFailed(7));
            return;
        }

        if (System.currentTimeMillis() - lastUpdate > 600000) { // Update once every 10 minutes
            lastUpdate = System.currentTimeMillis();
            final Map<Integer, Integer> load = ChannelServer.getChannelLoad();
            int usersOn = 0;
            if (load == null
                    || load.size()
                            <= 0) { // In an unfortunate event that client logged in before load
                lastUpdate = 0;
                c.sendPacket(LoginPacket.getLoginFailed(7));
                return;
            }
            final double loadFactor = 1200 / ((double) LoginServer.getUserLimit() / load.size());
            for (Entry<Integer, Integer> entry : load.entrySet()) {
                usersOn += entry.getValue();
                load.put(entry.getKey(), Math.min(1200, (int) (entry.getValue() * loadFactor)));
            }
            LoginServer.setLoad(load, usersOn);
            lastUpdate = System.currentTimeMillis();
        }

        if (c.finishLogin() == 0) {
            if (c.getSecondPassword() == null) {
                c.sendPacket(LoginPacket.getGenderNeeded(c));
            } else {
                c.sendPacket(LoginPacket.getAuthSuccessRequest(c));
                c.sendPacket(
                        LoginPacket.getServerList(
                                0, LoginServer.getServerName(), LoginServer.getLoad()));
                c.sendPacket(LoginPacket.getEndOfServerList());
            }
            c.setIdleTask(
                    PingTimer.getInstance()
                            .schedule(
                                    new Runnable() {

                                        public void run() {
                                            //                    c.getSession().close();
                                        }
                                    },
                                    10 * 60 * 10000));
        } else {
            c.sendPacket(LoginPacket.getLoginFailed(7));
            return;
        }
    }
}
