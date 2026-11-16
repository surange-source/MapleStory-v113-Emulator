package handling;

import tools.HexTool;

public class ByteArrayMaplePacket implements MaplePacket {

    public static final long serialVersionUID = -7997681658570958848L;
    private final byte[] data;
    private transient Runnable onSend;

    public ByteArrayMaplePacket(final byte[] data) {
        this.data = (data == null) ? new byte[0] : data.clone();
    }

    @Override
    public final byte[] getBytes() {
        return data.clone();
    }

    @Override
    public final Runnable getOnSend() {
        return onSend;
    }

    @Override
    public void setOnSend(final Runnable onSend) {
        this.onSend = onSend;
    }

    @Override
    public String toString() {
        return HexTool.toString(data);
    }
}
