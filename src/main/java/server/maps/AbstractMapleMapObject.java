package server.maps;

import java.awt.Point;

public abstract class AbstractMapleMapObject implements MapleMapObject {

    private volatile Point position = new Point();
    private volatile int objectId;

    public abstract MapleMapObjectType getType();

    @Override
    public Point getPosition() {
        Point p = this.position;
        return new Point(p.x, p.y);
    }

    @Override
    public void setPosition(Point position) {
        if (position == null) {
            return;
        }
        Point p = this.position;
        p.x = position.x;
        p.y = position.y;
    }

    @Override
    public int getObjectId() {
        return objectId;
    }

    @Override
    public void setObjectId(int id) {
        this.objectId = id;
    }
}
