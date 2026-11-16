package client.inventory;

import constants.ItemConstants;
import java.io.Serializable;

public class Item implements IItem, Serializable {

    private final int id;
    private short position;
    private short quantity;
    private byte flag;
    private int exp;
    private long expiration = -1;
    private MaplePet pet = null;
    private int uniqueid = -1;
    private String owner = "";
    private String GameMaster_log = null;
    private String giftFrom = "";
    protected MapleRing ring = null;

    public Item(
            final int id,
            final short position,
            final short quantity,
            final byte flag,
            final int uniqueid) {
        super();
        this.id = id;
        this.position = position;
        this.quantity = quantity;
        this.flag = flag;
        this.uniqueid = uniqueid;
    }

    public Item(final int id, final short position, final short quantity, final byte flag) {
        super();
        this.id = id;
        this.position = position;
        this.quantity = quantity;
        this.flag = flag;
    }

    public Item(int id, byte position, short quantity) {
        super();
        this.id = id;
        this.position = position;
        this.quantity = quantity;
    }

    public IItem copy() {
        final Item ret = new Item(id, position, quantity, flag, uniqueid);
        ret.pet = pet;
        ret.owner = owner;
        ret.GameMaster_log = GameMaster_log;
        ret.expiration = expiration;
        ret.giftFrom = giftFrom;
        return ret;
    }

    public final void setPosition(final short position) {
        this.position = position;

        if (pet != null) {
            pet.setInventoryPosition(position);
        }
    }

    public void setQuantity(final short quantity) {
        this.quantity = quantity;
    }

    @Override
    public final int getItemId() {
        return id;
    }

    @Override
    public final short getPosition() {
        return position;
    }

    @Override
    public final byte getFlag() {
        return flag;
    }

    @Override
    public final short getQuantity() {
        return quantity;
    }

    @Override
    public byte getType() {
        return 2; // An Item
    }

    @Override
    public final String getOwner() {
        return owner;
    }

    public final void setOwner(final String owner) {
        this.owner = owner;
    }

    public final void setFlag(final byte flag) {
        this.flag = flag;
    }

    @Override
    public final long getExpiration() {
        return expiration;
    }

    public final void setExpiration(final long expire) {
        this.expiration = expire;
    }

    @Override
    public final String getGMLog() {
        return GameMaster_log;
    }

    @Override
    public void setGMLog(final String GameMaster_log) {
        this.GameMaster_log = GameMaster_log;
    }

    @Override
    public final int getUniqueId() {
        return uniqueid;
    }

    @Override
    public final void setUniqueId(final int id) {
        this.uniqueid = id;
    }

    public final MaplePet getPet() {
        return pet;
    }

    public final void setPet(final MaplePet pet) {
        this.pet = pet;
    }

    @Override
    public void setGiftFrom(String gf) {
        this.giftFrom = gf;
    }

    @Override
    public String getGiftFrom() {
        return giftFrom;
    }

    @Override
    public int getExp() {
        return exp;
    }

    @Override
    public void setExp(int exp) {
        this.exp = exp;
    }

    @Override
    public int compareTo(IItem other) {
        if (Math.abs(position) < Math.abs(other.getPosition())) {
            return -1;
        } else if (Math.abs(position) == Math.abs(other.getPosition())) {
            return 0;
        } else {
            return 1;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof IItem)) {
            return false;
        }
        final IItem ite = (IItem) obj;
        return uniqueid == ite.getUniqueId()
                && id == ite.getItemId()
                && quantity == ite.getQuantity()
                && Math.abs(position) == Math.abs(ite.getPosition());
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + uniqueid;
        result = 31 * result + id;
        result = 31 * result + quantity;
        result = 31 * result + Math.abs(position);
        return result;
    }

    @Override
    public String toString() {
        return "Item: " + id + " quantity: " + quantity;
    }

    @Override
    public MapleRing getRing() {
        if (!ItemConstants.ItemTypes.isEffectRing(id) || getUniqueId() <= 0) {
            return null;
        }
        if (ring == null) {
            ring = MapleRing.loadFromDb(getUniqueId(), position < 0);
        }
        return ring;
    }

    public void setRing(MapleRing ring) {
        this.ring = ring;
    }
}
