package client.inventory;

import database.DatabaseConnection;
import java.io.Serial;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MapleInventoryIdentifier implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(MapleInventoryIdentifier.class);

    @Serial private static final long serialVersionUID = 21830921831301L;
    private final AtomicInteger runningUID;
    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock readLock = rwl.readLock(), writeLock = rwl.writeLock();

    private MapleInventoryIdentifier() {
        this.runningUID = new AtomicInteger(0);
        getNextUniqueId();
    }

    private static final class Holder {

        private static final MapleInventoryIdentifier INSTANCE = new MapleInventoryIdentifier();
    }

    public static int getInstance() {
        return Holder.INSTANCE.getNextUniqueId();
    }

    public int getNextUniqueId() {
        if (grabRunningUID() <= 0) {
            setRunningUID(initUID());
        }
        incrementRunningUID();
        return grabRunningUID();
    }

    public int grabRunningUID() {
        readLock.lock();
        try {
            return runningUID.get(); // maybe we should really synchronize/do more here..
        } finally {
            readLock.unlock();
        }
    }

    public void incrementRunningUID() {
        setRunningUID(grabRunningUID() + 1);
    }

    public void setRunningUID(int rUID) {
        if (rUID < grabRunningUID()) { // dont go backwards.
            return;
        }
        writeLock.lock();
        try {
            runningUID.set(rUID); // maybe we should really synchronize/do more here..
        } finally {
            writeLock.unlock();
        }
    }

    public int initUID() {
        int ret = 0;
        if (grabRunningUID() > 0) {
            return grabRunningUID();
        }
        final int[] ids = new int[4];
        ids[0] = queryMaxId("SELECT MAX(uniqueid) FROM inventoryitems");
        ids[1] = queryMaxId("SELECT MAX(petid) FROM pets");
        ids[2] = queryMaxId("SELECT MAX(ringid) FROM rings");
        ids[3] = queryMaxId("SELECT MAX(partnerringid) FROM rings");

        for (int i = 0; i < 4; i++) {
            if (ids[i] > ret) {
                ret = ids[i];
            }
        }
        return ret;
    }

    private static int queryMaxId(final String sql) {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return 0;
            }
            try (PreparedStatement ps = con.prepareStatement(sql);
                    ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) + 1;
                }
            }
        } catch (SQLException e) {
            LOG.error("MapleInventoryIdentifier.queryMaxId 發生例外", e);
        }
        return 0;
    }
}
