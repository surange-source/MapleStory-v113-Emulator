package handling.world;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class CharacterIdChannelPair implements Externalizable, Comparable<CharacterIdChannelPair> {

    private int charid = 0;
    private int channel = 1;

    /** only for externalisation */
    public CharacterIdChannelPair() {}

    public CharacterIdChannelPair(int charid, int channel) {
        super();
        this.charid = charid;
        this.channel = channel;
    }

    public int getCharacterId() {
        return charid;
    }

    public int getChannel() {
        return channel;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        charid = in.readInt();
        channel = in.readByte();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(charid);
        out.writeByte(channel);
    }

    @Override
    public int compareTo(CharacterIdChannelPair o) {
        int cmp = Integer.compare(channel, o.channel);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(charid, o.charid);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CharacterIdChannelPair)) {
            return false;
        }
        CharacterIdChannelPair other = (CharacterIdChannelPair) obj;
        return charid == other.charid && channel == other.channel;
    }

    @Override
    public int hashCode() {
        return 31 * charid + channel;
    }
}
