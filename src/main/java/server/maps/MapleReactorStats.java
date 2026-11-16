package server.maps;

import java.awt.Point;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tools.Pair;

public class MapleReactorStats {

    private volatile byte facingDirection;
    private volatile Point tl;
    private volatile Point br;
    private final Map<Byte, StateData> stateInfo = new ConcurrentHashMap<>();

    public final void setFacingDirection(final byte facingDirection) {
        this.facingDirection = facingDirection;
    }

    public final byte getFacingDirection() {
        return facingDirection;
    }

    public void setTL(Point tl) {
        this.tl = tl;
    }

    public void setBR(Point br) {
        this.br = br;
    }

    public Point getTL() {
        return tl;
    }

    public Point getBR() {
        return br;
    }

    public void addState(
            byte state, int type, Pair<Integer, Integer> reactItem, byte nextState, int timeOut) {
        StateData newState = new StateData(type, reactItem, nextState, timeOut);
        stateInfo.put(state, newState);
    }

    public byte getNextState(byte state) {
        StateData nextState = stateInfo.get(state);
        if (nextState != null) {
            return nextState.getNextState();
        } else {
            return -1;
        }
    }

    public int getType(byte state) {
        StateData nextState = stateInfo.get(state);
        if (nextState != null) {
            return nextState.getType();
        } else {
            return -1;
        }
    }

    public Pair<Integer, Integer> getReactItem(byte state) {
        StateData nextState = stateInfo.get(state);
        if (nextState != null) {
            return nextState.getReactItem();
        } else {
            return null;
        }
    }

    public int getTimeOut(byte state) {
        StateData nextState = stateInfo.get(state);
        if (nextState != null) {
            return nextState.getTimeOut();
        } else {
            return -1;
        }
    }

    private static class StateData {

        private final int type;
        private final int timeOut;
        private final Pair<Integer, Integer> reactItem;
        private final byte nextState;

        private StateData(int type, Pair<Integer, Integer> reactItem, byte nextState, int timeOut) {
            this.type = type;
            this.reactItem = reactItem;
            this.nextState = nextState;
            this.timeOut = timeOut;
        }

        private int getType() {
            return type;
        }

        private byte getNextState() {
            return nextState;
        }

        private Pair<Integer, Integer> getReactItem() {
            return reactItem;
        }

        private int getTimeOut() {
            return timeOut;
        }
    }
}
