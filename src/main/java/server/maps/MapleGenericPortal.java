package server.maps;

import client.MapleClient;
import client.anticheat.CheatingOffense;
import handling.channel.ChannelServer;
import java.awt.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripting.PortalScriptManager;
import server.MaplePortal;
import tools.MaplePacketCreator;

public class MapleGenericPortal implements MaplePortal {
    private static final Logger LOG = LoggerFactory.getLogger(MapleGenericPortal.class);

    private volatile String name;
    private volatile String target;
    private volatile String scriptName;
    private volatile Point position;
    private volatile int targetmap;
    private final int type;
    private volatile int id;
    private volatile boolean portalState = true;

    public MapleGenericPortal(final int type) {
        this.type = type;
    }

    @Override
    public final int getId() {
        return id;
    }

    public final void setId(int id) {
        this.id = id;
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final Point getPosition() {
        return position;
    }

    @Override
    public final String getTarget() {
        return target;
    }

    @Override
    public final int getTargetMapId() {
        return targetmap;
    }

    @Override
    public final int getType() {
        return type;
    }

    @Override
    public final String getScriptName() {
        return scriptName;
    }

    public final void setName(final String name) {
        this.name = name;
    }

    public final void setPosition(final Point position) {
        this.position = position;
    }

    public final void setTarget(final String target) {
        this.target = target;
    }

    public final void setTargetMapId(final int targetmapid) {
        this.targetmap = targetmapid;
    }

    @Override
    public final void setScriptName(final String scriptName) {
        this.scriptName = scriptName;
    }

    @Override
    public final void enterPortal(final MapleClient c) {
        if (c == null || c.getPlayer() == null) {
            return;
        }
        // Snapshot volatile fields once to avoid torn reads across the method body.
        final Point portalPos = this.position;
        final String localScriptName = this.scriptName;
        final int localTargetMapId = this.targetmap;
        final String localTarget = this.target;
        final boolean localPortalState = this.portalState;

        if (portalPos != null && portalPos.distanceSq(c.getPlayer().getPosition()) > 22500) {
            c.getPlayer().getCheatTracker().registerOffense(CheatingOffense.USING_FARAWAY_PORTAL);
        }
        final MapleMap currentmap = c.getPlayer().getMap();
        if (localPortalState || c.getPlayer().isGM()) {
            if (localScriptName != null) {
                c.getPlayer().checkFollow();
                try {
                    PortalScriptManager.getInstance().executePortalScript(this, c);
                } catch (final Exception e) {
                    LOG.error("MapleGenericPortal.enterPortal 發生例外", e);
                }
            } else if (localTargetMapId != 999999999) {
                final MapleMap to =
                        ChannelServer.getInstance(c.getChannel())
                                .getMapFactory()
                                .getMap(localTargetMapId);
                if (!c.getPlayer().isGM()) {
                    if (to.getLevelLimit() > 0 && to.getLevelLimit() > c.getPlayer().getLevel()) {
                        c.getPlayer()
                                .dropMessage(-1, "You are too low of a level to enter this place.");
                        c.sendPacket(MaplePacketCreator.enableActions());
                        return;
                    }
                    // if (to.getForceMove() > 0 && to.getForceMove() < c.getPlayer().getLevel()) {
                    //    c.getPlayer().dropMessage(-1, "You are too high of a level to enter this
                    // place.");
                    //    c.sendPacket(MaplePacketCreator.enableActions());
                    //    return;
                    // }
                }
                c.getPlayer()
                        .changeMapPortal(
                                to,
                                to.getPortal(localTarget) == null
                                        ? to.getPortal(0)
                                        : to.getPortal(
                                                localTarget)); // late resolving makes this harder
                // but prevents us from
                // loading the whole world at once
            }
        }
        if (c.getPlayer().getMap() == currentmap) { // Character is still on the same map.
            c.sendPacket(MaplePacketCreator.enableActions());
        }
    }

    @Override
    public boolean getPortalState() {
        return portalState;
    }

    @Override
    public void setPortalState(boolean ps) {
        this.portalState = ps;
    }
}
