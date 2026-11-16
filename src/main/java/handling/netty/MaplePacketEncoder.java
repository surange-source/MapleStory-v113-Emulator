package handling.netty;

import client.MapleClient;
import handling.MaplePacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.util.concurrent.locks.Lock;
import tools.MapleAESOFB;

public class MaplePacketEncoder extends MessageToByteEncoder<Object> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Object message, ByteBuf buffer)
            throws Exception {
        final MapleClient client = (MapleClient) ctx.channel().attr(MapleClient.CLIENT_KEY).get();
        if (client != null) {
            final MapleAESOFB send_crypto = client.getSendCrypto();
            final byte[] input = ((MaplePacket) message).getBytes();
            final byte[] unencrypted = new byte[input.length];
            System.arraycopy(input, 0, unencrypted, 0, input.length);
            final byte[] ret = new byte[unencrypted.length + 4];
            // MapleCustomEncryption.encryptData(unencrypted);

            final Lock mutex = client.getLock();
            mutex.lock();
            try {
                final byte[] header = send_crypto.getPacketHeader(unencrypted.length);
                send_crypto.crypt(unencrypted);
                System.arraycopy(header, 0, ret, 0, 4);
                System.arraycopy(unencrypted, 0, ret, 4, unencrypted.length);
                buffer.writeBytes(ret);
            } finally {
                mutex.unlock();
            }
            //            System.arraycopy(unencrypted, 0, ret, 4, unencrypted.length);
            //            out.write(ByteBuffer.wrap(ret));
        } else {
            buffer.writeBytes((((MaplePacket) message).getBytes()));
        }
    }
}
