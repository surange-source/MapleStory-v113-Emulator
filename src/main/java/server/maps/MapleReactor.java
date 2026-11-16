package server.maps;

import client.MapleClient;
import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicBoolean;
import scripting.ReactorScriptManager;
import server.Timer.MapTimer;
import tools.MaplePacketCreator;
import tools.Pair;

public class MapleReactor extends AbstractMapleMapObject {

    private final int rid;
    private final MapleReactorStats stats;
    private volatile byte state;
    private volatile int delay;
    private volatile MapleMap map;
    private volatile String name = "";
    private final AtomicBoolean timerActive = new AtomicBoolean(false);
    private final AtomicBoolean alive = new AtomicBoolean(true);
    private final Object stateLock = new Object();

    public MapleReactor(MapleReactorStats stats, int rid) {
        this.stats = stats;
        this.rid = rid;
        alive.set(true);
    }

    public final byte getFacingDirection() {
        return stats.getFacingDirection();
    }

    public void setTimerActive(boolean active) {
        this.timerActive.set(active);
    }

    public boolean isTimerActive() {
        return timerActive.get();
    }

    public int getReactorId() {
        return rid;
    }

    public void setState(byte state) {
        this.state = state;
    }

    public byte getState() {
        return state;
    }

    public boolean isAlive() {
        return alive.get();
    }

    public void setAlive(boolean alive) {
        this.alive.set(alive);
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    public int getDelay() {
        return delay;
    }

    @Override
    public MapleMapObjectType getType() {
        return MapleMapObjectType.REACTOR;
    }

    public int getReactorType() {
        return stats.getType(state);
    }

    public void setMap(MapleMap map) {
        this.map = map;
    }

    public MapleMap getMap() {
        return map;
    }

    public Pair<Integer, Integer> getReactItem() {
        return stats.getReactItem(state);
    }

    @Override
    public void sendDestroyData(MapleClient client) {
        client.sendPacket(MaplePacketCreator.destroyReactor(this));
    }

    @Override
    public void sendSpawnData(MapleClient client) {
        client.sendPacket(MaplePacketCreator.spawnReactor(this));
    }

    public void forceStartReactor(MapleClient c) {
        ReactorScriptManager.getInstance().act(c, this);
    }

    public void forceHitReactor(final byte newState) {
        setState((byte) newState);
        setTimerActive(false);
        final MapleMap m = this.map;
        if (m != null) {
            m.broadcastMessage(MaplePacketCreator.triggerReactor(this, (short) 0));
        }
    }

    // hitReactor command for item-triggered reactors
    public void hitReactor(MapleClient c) {
        hitReactor(0, (short) 0, c);
    }

    public void forceTrigger() {
        final MapleMap m = this.map;
        if (m != null) {
            m.broadcastMessage(MaplePacketCreator.triggerReactor(this, (short) 0));
        }
    }

    public void delayedDestroyReactor(long delay) {
        MapTimer.getInstance()
                .schedule(
                        new Runnable() {

                            @Override
                            public void run() {
                                final MapleMap m = MapleReactor.this.map;
                                if (m != null) {
                                    m.destroyReactor(getObjectId());
                                }
                            }
                        },
                        delay);
    }

    public void hitReactor(int charPos, short stance, MapleClient c) {
        synchronized (stateLock) {
            final byte currentState = state;
            if (stats.getType(currentState) < 999 && stats.getType(currentState) != -1) {
                // type 2 = only hit from right (kerning swamp plants), 00 is air left 02 is ground
                // left
                final byte oldState = currentState;
                if (!(stats.getType(currentState) == 2
                        && (charPos == 0 || charPos == 2))) { // next state
                    final byte nextState = stats.getNextState(currentState);
                    state = nextState;

                    if (stats.getNextState(nextState) == -1
                            || stats.getType(nextState) == 999) { // end of reactor
                        if ((stats.getType(nextState) < 100 || stats.getType(nextState) == 999)
                                && delay > 0) { // reactor broken
                            final MapleMap m = this.map;
                            if (m != null) {
                                m.destroyReactor(getObjectId());
                            }
                        } else { // item-triggered on final step
                            final MapleMap m = this.map;
                            if (m != null) {
                                m.broadcastMessage(MaplePacketCreator.triggerReactor(this, stance));
                            }
                        }
                        ReactorScriptManager.getInstance().act(c, this);
                    } else { // reactor not broken yet
                        boolean done = false;
                        final MapleMap m = this.map;
                        if (m != null) {
                            m.broadcastMessage(
                                    MaplePacketCreator.triggerReactor(
                                            this,
                                            stance)); // magatia is weird cause full beaker can be
                            // activated
                        }
                        // by gm hat o.o
                        if (nextState == stats.getNextState(nextState)
                                || rid == 2618000
                                || rid == 2309000) { // current state = next state, looping reactor
                            ReactorScriptManager.getInstance().act(c, this);
                            done = true;
                        }
                        if (stats.getTimeOut(nextState) > 0) {
                            if (!done) {
                                ReactorScriptManager.getInstance().act(c, this);
                            }
                            scheduleSetState(nextState, oldState, stats.getTimeOut(nextState));
                        }
                    }
                }
            }
        }
    }

    public Rectangle getArea() {
        int height = stats.getBR().y - stats.getTL().y;
        int width = stats.getBR().x - stats.getTL().x;
        int origX = getPosition().x + stats.getTL().x;
        int origY = getPosition().y + stats.getTL().y;

        return new Rectangle(origX, origY, width, height);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Reactor "
                + getObjectId()
                + " of id "
                + rid
                + " at position "
                + getPosition().toString()
                + " state"
                + state
                + " type "
                + stats.getType(state);
    }

    public void delayedHitReactor(final MapleClient c, long delay) {
        MapTimer.getInstance()
                .schedule(
                        new Runnable() {

                            @Override
                            public void run() {
                                hitReactor(c);
                            }
                        },
                        delay);
    }

    public void scheduleSetState(final byte oldState, final byte newState, long delay) {
        MapTimer.getInstance()
                .schedule(
                        new Runnable() {

                            @Override
                            public void run() {
                                synchronized (stateLock) {
                                    if (MapleReactor.this.state == oldState) {
                                        forceHitReactor(newState);
                                    }
                                }
                            }
                        },
                        delay);
    }
}
