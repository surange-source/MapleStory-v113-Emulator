package handling.world;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class CheaterData implements Serializable, Comparable<CheaterData> {

    @Serial private static final long serialVersionUID = -8733673311051249885L;
    private int points;
    private String info;

    public CheaterData(int points, String info) {
        this.points = points;
        this.info = info;
    }

    public String getInfo() {
        return info;
    }

    public int getPoints() {
        return points;
    }

    public int compareTo(CheaterData o) {
        int thisVal = getPoints();
        int anotherVal = o.getPoints();
        return (thisVal < anotherVal ? 1 : (thisVal == anotherVal ? 0 : -1));
    }

    @Override
    public boolean equals(Object oth) {
        if (this == oth) {
            return true;
        }
        if (!(oth instanceof CheaterData)) {
            return false;
        }
        final CheaterData obj = (CheaterData) oth;
        return obj.points == this.points && Objects.equals(obj.info, this.info);
    }

    @Override
    public int hashCode() {
        return Objects.hash(points, info);
    }
}
