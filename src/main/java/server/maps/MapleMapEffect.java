package server.maps;

import client.MapleClient;
import handling.MaplePacket;
import tools.MaplePacketCreator;
import tools.packet.MTSCSPacket;

public class MapleMapEffect {

    private volatile String msg = "";
    private volatile int itemId = 0;
    private volatile boolean active = true;
    private volatile boolean jukebox = false;

    public MapleMapEffect(String msg, int itemId) {
        this.msg = msg;
        this.itemId = itemId;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setJukebox(boolean actie) {
        this.jukebox = actie;
    }

    public boolean isJukebox() {
        return this.jukebox;
    }

    public MaplePacket
            makeDestroyData() { // jukebox doesn't REALLY have a destroy, but 0 stops all music
        return jukebox ? MTSCSPacket.playCashSong(0, "") : MaplePacketCreator.removeMapEffect();
    }

    public MaplePacket makeStartData() {
        return jukebox
                ? MTSCSPacket.playCashSong(itemId, msg)
                : MaplePacketCreator.startMapEffect(msg, itemId, active);
    }

    public void sendStartData(MapleClient c) {
        c.sendPacket(makeStartData());
    }
}
