package handling.netty;

import client.MapleClient;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.AttributeKey;
import java.util.List;
import tools.MapleAESOFB;

public class MaplePacketDecoder extends ByteToMessageDecoder {

    public static final AttributeKey<DecoderState> DECODER_STATE_KEY =
            AttributeKey.valueOf(MaplePacketDecoder.class.getName() + ".STATE");

    public static class DecoderState {

        public int packetlength = -1;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> message)
            throws Exception {
        final MapleClient client = (MapleClient) ctx.channel().attr(MapleClient.CLIENT_KEY).get();
        if (client == null) {
            in.skipBytes(in.readableBytes());
            return;
        }
        DecoderState decoderState = (DecoderState) ctx.channel().attr(DECODER_STATE_KEY).get();
        if (decoderState == null) {
            decoderState = new DecoderState();
            ctx.channel().attr(DECODER_STATE_KEY).set(decoderState);
        }
        if (in.readableBytes() >= 4 && decoderState.packetlength == -1) {
            int packetHeader = in.readInt();
            if (!client.getReceiveCrypto().checkPacket(packetHeader)) {
                ctx.channel().disconnect();
                return;
            }
            decoderState.packetlength = MapleAESOFB.getPacketLength(packetHeader);
        } else if (in.readableBytes() < 4 && decoderState.packetlength == -1) {
            return;
        }
        if (in.readableBytes() >= decoderState.packetlength) {
            byte decryptedPacket[] = new byte[decoderState.packetlength];
            in.readBytes(decryptedPacket);
            decoderState.packetlength = -1;
            client.getReceiveCrypto().crypt(decryptedPacket);
            // MapleCustomEncryption.decryptData(decryptedPacket);
            message.add(decryptedPacket);
        }
    }
}
