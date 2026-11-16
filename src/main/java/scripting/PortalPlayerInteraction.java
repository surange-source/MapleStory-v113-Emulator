package scripting;

import client.MapleClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.MaplePortal;
import server.life.MapleLifeFactory;

public class PortalPlayerInteraction extends AbstractPlayerInteraction {
    private static final Logger LOG = LoggerFactory.getLogger(PortalPlayerInteraction.class);

    private final MaplePortal portal;

    public PortalPlayerInteraction(final MapleClient c, final MaplePortal portal) {
        super(c);
        this.portal = portal;
    }

    public final MaplePortal getPortal() {
        return portal;
    }

    public final void inFreeMarket() {
        //        if (getMapId() != 910000000) {
        if (getPlayer().getLevel() >= 10) {
            saveLocation("FREE_MARKET");
            playPortalSE();
            warp(910000000, "st00");
        } else {
            playerMessage(5, "你需要10級才可以進入自由市場");
        }
        //        }
    }

    // summon one monster on reactor location
    @Override
    public void spawnMonster(int id) {
        if (MapleLifeFactory.getMonster(id) != null) {
            spawnMonster(id, 1, portal.getPosition());
        } else {
            if (getPlayer() != null) {
                if (getPlayer().isStaff()) {
                    getPlayer().dropMessage("怪物代碼[" + id + "] 不存在");
                    LOG.error("含有 怪物代碼[" + id + "] 不存在 之漏洞\r\n");
                }
            }
        }
    }

    // summon monsters on reactor location
    @Override
    public void spawnMonster(int id, int qty) {
        if (MapleLifeFactory.getMonster(id) != null) {
            spawnMonster(id, qty, portal.getPosition());
        } else {
            if (getPlayer() != null) {
                if (getPlayer().isStaff()) {
                    getPlayer().dropMessage("怪物代碼[" + id + "] 不存在");
                    LOG.error("含有 怪物代碼[" + id + "] 不存在 之漏洞\r\n");
                }
            }
        }
    }
}
