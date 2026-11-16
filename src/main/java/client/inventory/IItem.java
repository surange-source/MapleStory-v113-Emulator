package client.inventory;

public interface IItem extends Comparable<IItem> {

    byte getType();

    short getPosition();

    byte getFlag();

    short getQuantity();

    String getOwner();

    String getGMLog();

    int getItemId();

    MaplePet getPet();

    int getUniqueId();

    int getExp();

    void setExp(int exp);

    IItem copy();

    long getExpiration();

    void setFlag(byte flag);

    void setUniqueId(int id);

    void setPosition(short position);

    void setExpiration(long expire);

    void setOwner(String owner);

    void setGMLog(String GameMaster_log);

    void setQuantity(short quantity);

    void setGiftFrom(String gf);

    String getGiftFrom();

    MapleRing getRing();
}
