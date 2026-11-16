package handling;

import client.MapleClient;
import constants.ServerConstants;
import handling.cashshop.CashShopServer;
import handling.cashshop.handler.CashShopOperation;
import handling.cashshop.handler.MTSOperation;
import handling.channel.ChannelServer;
import handling.channel.handler.*;
import handling.login.LoginServer;
import handling.login.handler.CharLoginHandler;
import handling.netty.MaplePacketDecoder;
import handling.world.World;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripting.NPCScriptManager;
import server.Randomizer;
import server.ServerProperties;
import tools.MapleAESOFB;
import tools.Pair;
import tools.data.input.ByteArrayByteStream;
import tools.data.input.GenericSeekableLittleEndianAccessor;
import tools.data.input.SeekableLittleEndianAccessor;
import tools.packet.LoginPacket;

public class MapleServerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(MapleServerHandler.class);

    public static final boolean Log_Packets =
            Boolean.parseBoolean(ServerProperties.getProperty("tms.LogPackets", "false"));
    private int world = -1, channel = -1;
    private boolean cs;
    private final Map<String, Long> blockedIPs = new ConcurrentHashMap<>();
    private final Map<String, Pair<Long, Byte>> tracker = new ConcurrentHashMap<>();
    private static final long TRACKER_EXPIRE_MS = 20_000L;
    private static final long BLOCK_DURATION_MS = 300_000L; // 封鎖 5 分鐘後自動過期，避免記憶體無限膨脹
    private static final String nl = System.getProperty("line.separator");
    private static final File loggedIPs = new File("Logs/LogIPs.txt");
    private static final Map<String, FileWriter> logIPMap = new ConcurrentHashMap<>();
    private static boolean debugMode =
            Boolean.parseBoolean(ServerProperties.getProperty("tms.Debug", "false"));
    private static final EnumSet<RecvPacketOpcode> blocked = EnumSet.noneOf(RecvPacketOpcode.class);

    static {
        try {
            File parentDir = loggedIPs.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            if (!loggedIPs.exists()) {
                loggedIPs.createNewFile();
            }
        } catch (IOException e) {
            LOG.error("自動建立日誌檔案失敗: {}", e);
        }
        reloadLoggedIPs();
        RecvPacketOpcode[] block =
                new RecvPacketOpcode[] {
                    RecvPacketOpcode.NPC_ACTION,
                    RecvPacketOpcode.MOVE_PLAYER,
                    RecvPacketOpcode.MOVE_PET,
                    RecvPacketOpcode.MOVE_SUMMON,
                    RecvPacketOpcode.MOVE_DRAGON,
                    RecvPacketOpcode.MOVE_LIFE,
                    RecvPacketOpcode.HEAL_OVER_TIME,
                    RecvPacketOpcode.STRANGE_DATA
                };
        blocked.addAll(Arrays.asList(block));
    }

    public static void reloadLoggedIPs() {
        for (FileWriter fw : logIPMap.values()) {
            if (fw != null) {
                try {
                    fw.write("=== Closing Log ===");
                    fw.write(nl);
                    fw.flush();
                    fw.close();
                } catch (IOException ex) {
                    LOG.error("Error closing Packet Log.");
                    LOG.error("Error closing Packet Log.", ex);
                }
            }
        }
        logIPMap.clear();
        try (Scanner sc = new Scanner(loggedIPs, StandardCharsets.UTF_8)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (!line.isEmpty()) {
                    FileWriter fw =
                            new FileWriter(
                                    new File("PacketLog_" + line + ".txt"),
                                    StandardCharsets.UTF_8,
                                    true);
                    fw.write("=== Creating Log ===");
                    fw.write(nl);
                    fw.flush();
                    logIPMap.put(line, fw);
                }
            }
        } catch (Exception e) {
            LOG.error("Could not reload packet logged IPs.");
            LOG.error("Could not reload packet logged IPs.", e);
        }
    }

    private static String extractRemoteAddress(ChannelHandlerContext ctx) {
        if (ctx == null || ctx.channel() == null) {
            return "";
        }
        SocketAddress remote = ctx.channel().remoteAddress();
        if (remote == null) {
            return "";
        }
        String raw = remote.toString();
        int colonIdx = raw.indexOf(':');
        return colonIdx > -1 ? raw.substring(0, colonIdx) : raw;
    }

    private static FileWriter isLoggedIP(ChannelHandlerContext ctx) {
        if (logIPMap.isEmpty()) {
            return null;
        }
        String addr = extractRemoteAddress(ctx);
        if (addr.isEmpty()) {
            return null;
        }
        int slashIdx = addr.indexOf('/');
        String realIP = slashIdx > -1 ? addr.substring(slashIdx + 1) : addr;
        return logIPMap.get(realIP);
    }

    // <editor-fold defaultstate="collapsed" desc="Packet Log Implementation">
    private static final int Log_Size = 2000;
    private static final Deque<LoggedPacket> Packet_Log = new ArrayDeque<>(Log_Size);
    private static final ReentrantReadWriteLock Packet_Log_Lock = new ReentrantReadWriteLock();
    private static final File Packet_Log_Output = new File("PacketLog.txt");

    public static void log(
            SeekableLittleEndianAccessor packet,
            RecvPacketOpcode op,
            MapleClient c,
            ChannelHandlerContext ctx) {
        if (blocked.contains(op)) {
            return;
        }
        final String remoteAddr =
                ctx.channel().remoteAddress() == null
                        ? "[Unknown]"
                        : ctx.channel().remoteAddress().toString();
        final int accId = c == null ? -1 : c.getAccID();
        final String accName =
                c == null || c.getAccountName() == null ? "[Null]" : c.getAccountName();
        final String chrName =
                c == null || c.getPlayer() == null || c.getPlayer().getName() == null
                        ? "[Null]"
                        : c.getPlayer().getName();
        // 僅保存文字摘要，避免將原始 byte[] 緩衝區長期鎖死在靜態集合中
        final String packetData = packet.toString();

        Packet_Log_Lock.writeLock().lock();
        try {
            LoggedPacket logged = null;
            if (Packet_Log.size() >= Log_Size) {
                logged = Packet_Log.pollFirst();
            }
            if (logged == null) {
                logged = new LoggedPacket(packetData, op, remoteAddr, accId, accName, chrName);
            } else {
                logged.setInfo(packetData, op, remoteAddr, accId, accName, chrName);
            }
            Packet_Log.addLast(logged);
        } finally {
            Packet_Log_Lock.writeLock().unlock();
        }
    }

    private static class LoggedPacket {

        private static final String nl = System.getProperty("line.separator");
        private String ip, accName, accId, chrName, packetData;
        private long timestamp;
        private RecvPacketOpcode op;

        public LoggedPacket(
                String packetData,
                RecvPacketOpcode op,
                String ip,
                int id,
                String accName,
                String chrName) {
            setInfo(packetData, op, ip, id, accName, chrName);
        }

        public final void setInfo(
                String packetData,
                RecvPacketOpcode op,
                String ip,
                int id,
                String accName,
                String chrName) {
            this.ip = ip;
            this.op = op;
            this.packetData = packetData;
            this.accId = String.valueOf(id);
            this.accName = accName;
            this.chrName = chrName;
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[IP: ")
                    .append(ip)
                    .append("] [")
                    .append(accId)
                    .append('|')
                    .append(accName)
                    .append('|')
                    .append(chrName)
                    .append("] [Time: ")
                    .append(timestamp)
                    .append(']');
            sb.append(nl);
            sb.append("[Op: ").append(op).append(']');
            sb.append(" [Data: ").append(packetData).append(']');
            return sb.toString();
        }
    }

    public static void registerMBean() {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        try {
            MapleServerHandler mbean = new MapleServerHandler();
            mBeanServer.registerMBean(mbean, new ObjectName("handling:type=MapleServerHandler"));
        } catch (Exception e) {
            LOG.error("Error registering PacketLog MBean");
            LOG.error("MapleServerHandler.registerMBean 發生例外", e);
        }
    }

    public void writeLog() {
        try (Writer fw =
                new OutputStreamWriter(
                        new FileOutputStream(Packet_Log_Output, true), StandardCharsets.UTF_8)) {
            Packet_Log_Lock.writeLock().lock();
            try {
                for (LoggedPacket loggedPacket : Packet_Log) {
                    fw.write(loggedPacket.toString());
                    fw.write(nl);
                }
                fw.flush();
                Packet_Log.clear();
            } finally {
                Packet_Log_Lock.writeLock().unlock();
            }
        } catch (IOException ex) {
            LOG.error("Error writing log to file.");
        }
    }

    public MapleServerHandler() {
        // MBean
    }

    public MapleServerHandler(final int channel, final boolean cs) {
        this.channel = channel;
        this.cs = cs;
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause)
            throws Exception {
        if (cause instanceof IOException || cause instanceof ClassCastException) {
            return;
        }
        MapleClient client = ctx.channel().attr(MapleClient.CLIENT_KEY).get();
        if (client != null && client.getPlayer() != null) {
            LOG.error("ServerException.txt", "Exception caught by: " + client.getPlayer());
        }
    }

    private void cleanupExpiredConnectionTrackers(long now) {
        if (tracker.size() > 512) {
            tracker.entrySet().removeIf(e -> now - e.getValue().left() > TRACKER_EXPIRE_MS);
        }
        if (!blockedIPs.isEmpty()) {
            blockedIPs.entrySet().removeIf(e -> now > e.getValue());
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        final String address = extractRemoteAddress(ctx);
        final long now = System.currentTimeMillis();
        cleanupExpiredConnectionTrackers(now);

        if (!address.isEmpty()) {
            Long blockedUntil = blockedIPs.get(address);
            if (blockedUntil != null) {
                if (now < blockedUntil) {
                    ctx.channel().close();
                    return;
                }
                blockedIPs.remove(address);
            }

            final Pair<Long, Byte> track = tracker.get(address);
            byte count;
            if (track == null) {
                count = 1;
            } else {
                count = track.right();
                final long difference = now - track.left();
                if (difference < 2000) {
                    count++;
                } else if (difference > TRACKER_EXPIRE_MS) {
                    count = 1;
                }
                if (count >= 10) {
                    blockedIPs.put(address, now + BLOCK_DURATION_MS);
                    tracker.remove(address);
                    ctx.channel().close();
                    return;
                }
            }
            tracker.put(address, new Pair<>(now, count));
        }

        if (channel > -1) {
            if (ChannelServer.getInstance(channel).isShutdown()) {
                ctx.channel().close();
                return;
            }
        } else if (cs) {
            if (CashShopServer.isShutdown()) {
                ctx.channel().close();
                return;
            }
        } else {
            if (LoginServer.isShutdown()) {
                ctx.channel().close();
                return;
            }
        }
        final byte serverRecv[] = new byte[] {70, 114, 122, (byte) Randomizer.nextInt(255)};
        final byte serverSend[] = new byte[] {82, 48, 120, (byte) Randomizer.nextInt(255)};
        final byte ivRecv[] =
                ServerConstants.Use_Fixed_IV ? new byte[] {9, 0, 0x5, 0x5F} : serverRecv;
        final byte ivSend[] =
                ServerConstants.Use_Fixed_IV ? new byte[] {1, 0x5F, 4, 0x3F} : serverSend;

        final MapleClient client =
                new MapleClient(
                        new MapleAESOFB(
                                ivSend,
                                (short) (0xFFFF - ServerConstants.MAPLE_VERSION)),
                        new MapleAESOFB(ivRecv, ServerConstants.MAPLE_VERSION),
                        ctx.channel());
        client.setChannel(channel);

        MaplePacketDecoder.DecoderState decoderState = new MaplePacketDecoder.DecoderState();
        ctx.channel().attr(MaplePacketDecoder.DECODER_STATE_KEY).set(decoderState);

        ctx.channel()
                .writeAndFlush(LoginPacket.getHello(ServerConstants.MAPLE_VERSION, ivSend, ivRecv));
        ctx.channel().attr(MapleClient.CLIENT_KEY).set(client);

        StringBuilder sb = new StringBuilder();
        if (channel > -1) {
            sb.append("[Channel Server] Channel ").append(channel).append(" : ");
        } else if (cs) {
            sb.append("[Cash Server]");
        } else {
            sb.append("[Login Server]");
        }
        sb.append("已建立連線，src: ").append(address);
        LOG.info(sb.toString());
        World.Client.addClient(client);

        FileWriter fw = isLoggedIP(ctx);
        if (fw != null) {
            if (channel > -1) {
                fw.write("=== Logged Into Channel " + channel + " ===");
                fw.write(nl);
            } else if (cs) {
                fw.write("=== Logged Into CashShop Server ===");
                fw.write(nl);
            } else {
                fw.write("=== Logged Into Login Server ===");
                fw.write(nl);
            }
            fw.flush();
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        MapleClient client = ctx.channel().attr(MapleClient.CLIENT_KEY).getAndSet(null);
        ctx.channel().attr(MaplePacketDecoder.DECODER_STATE_KEY).set(null);

        if (client != null) {
            try {
                FileWriter fw = isLoggedIP(ctx);
                if (fw != null) {
                    fw.write("=== Session Closed ===");
                    fw.write(nl);
                    fw.flush();
                }
                client.disconnect(true, cs);
            } finally {
                World.Client.removeClient(client);
            }
        }
        super.channelInactive(ctx);
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt)
            throws Exception {
        MapleClient client = ctx.channel().attr(MapleClient.CLIENT_KEY).get();
        if (client != null) {
            client.sendPing();
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object message) {
        try {
            byte[] in = (byte[]) message;
            final SeekableLittleEndianAccessor slea =
                    new GenericSeekableLittleEndianAccessor(new ByteArrayByteStream(in));
            if (slea.available() < 2) {
                return;
            }
            final short header_num = slea.readShort();

            for (final RecvPacketOpcode recv : RecvPacketOpcode.values()) {
                if (recv.getValue() == header_num) {

                    if (debugMode) {
                        final StringBuilder sb =
                                new StringBuilder(
                                        "Received data 已處理 :" + String.valueOf(recv) + "\n");
                        sb.append(tools.HexTool.toString(in))
                                .append("\n")
                                .append(tools.HexTool.toStringFromAscii(in));
                        LOG.debug(sb.toString());
                    }
                    final MapleClient c = ctx.channel().attr(MapleClient.CLIENT_KEY).get();
                    if (c == null || !c.isReceiving()) {
                        return;
                    }
                    if (recv.NeedsChecking()) {
                        if (!c.isLoggedIn()) {
                            return;
                        }
                    }
                    if (c.getPlayer() != null && c.isMonitored()) {
                        if (!blocked.contains(recv)) {
                            try (FileWriter fw =
                                    new FileWriter(
                                            new File(
                                                    "Logs/MonitorLogs/"
                                                            + c.getPlayer().getName()
                                                            + "_log.txt"),
                                            StandardCharsets.UTF_8,
                                            true)) {
                                fw.write(
                                        String.valueOf(recv)
                                                + " ("
                                                + Integer.toHexString(header_num)
                                                + ") Handled: \r\n"
                                                + slea.toString()
                                                + "\r\n");
                                fw.flush();
                            }
                        }
                    }
                    if (Log_Packets) {
                        log(slea, recv, c, ctx);
                    }
                    handlePacket(recv, slea, c, cs);

                    FileWriter fw = isLoggedIP(ctx);
                    if (fw != null && !blocked.contains(recv)) {
                        try {
                            if (recv == RecvPacketOpcode.PLAYER_LOGGEDIN && c != null) {
                                fw.write(
                                        ">> [AccountName: "
                                                + (c.getAccountName() == null
                                                        ? "null"
                                                        : c.getAccountName())
                                                + "] | [IGN: "
                                                + (c.getPlayer() == null
                                                                || c.getPlayer().getName() == null
                                                        ? "null"
                                                        : c.getPlayer().getName())
                                                + "] | [Time: "
                                                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                                + "]");
                                fw.write(nl);
                            }
                            fw.write("[" + recv.toString() + "]" + slea.toString(true));
                            fw.write(nl);
                            fw.flush();
                        } catch (IOException ioe) {
                            LOG.error("MapleServerHandler.channelRead ERROR", ioe);
                        }
                    }
                    return;
                }
            }
            if (debugMode) {
                final StringBuilder sb = new StringBuilder("Received data 未處理 : ");
                sb.append(tools.HexTool.toString(in))
                        .append("\n")
                        .append(tools.HexTool.toStringFromAscii(in));
                LOG.debug(sb.toString());
            }
        } catch (Exception e) {
            LOG.error("MapleServerHandler.channelread ERROR", e);
        }
    }

    public static final void handlePacket(
            final RecvPacketOpcode header,
            final SeekableLittleEndianAccessor slea,
            final MapleClient c,
            final boolean cs)
            throws Exception {
        switch (header) {
            case PONG:
                c.pongReceived();
                break;
            case STRANGE_DATA:
                break;
            case HELLO_LOGIN:
                CharLoginHandler.Welcome(c);
                break;
            case HELLO_CHANNEL:
                CharLoginHandler.Welcome(c);
                break;
            case LOGIN_PASSWORD:
                CharLoginHandler.login(slea, c);
                break;
            case SERVERLIST_REQUEST:
                CharLoginHandler.ServerListRequest(c);
                break;
            case CHARLIST_REQUEST:
                CharLoginHandler.CharlistRequest(slea, c);
                break;
            case SERVERSTATUS_REQUEST:
                CharLoginHandler.ServerStatusRequest(c);
                break;
            case CHECK_CHAR_NAME:
                CharLoginHandler.CheckCharName(slea.readMapleAsciiString(), c);
                break;
            case CREATE_CHAR:
                CharLoginHandler.CreateChar(slea, c);
                break;
            case DELETE_CHAR:
                CharLoginHandler.DeleteChar(slea, c);
                break;
            case CHAR_SELECT:
                CharLoginHandler.Character_WithoutSecondPassword(slea, c);
                break;
            case AUTH_SECOND_PASSWORD:
                CharLoginHandler.Character_WithSecondPassword(slea, c);
                break;
            case SET_GENDER:
                CharLoginHandler.SetGenderRequest(slea, c);
                break;
            case RSA_KEY:
                break;
            case CHANGE_CHANNEL:
                InterServerHandler.ChangeChannel(slea, c, c.getPlayer());
                break;
            case PLAYER_LOGGEDIN:
                final int playerid = slea.readInt();
                if (cs) {
                    CashShopOperation.EnterCS(playerid, c);
                } else {
                    InterServerHandler.Loggedin(playerid, c);
                }
                break;
            case ENTER_CASH_SHOP:
                if (Boolean.parseBoolean(ServerProperties.getProperty("tms.CSload"))) {
                    InterServerHandler.EnterCS(c, c.getPlayer(), false);
                } else {
                    c.getPlayer().dropMessage(1, "目前暫停使用.");
                }
                break;
            case ENTER_MTS:
                if (Boolean.parseBoolean(ServerProperties.getProperty("tms.MTSload"))) {
                    InterServerHandler.EnterCS(c, c.getPlayer(), true);
                } else {
                    NPCScriptManager.getInstance().dispose(c);
                    c.getSession().writeAndFlush(tools.MaplePacketCreator.enableActions());
                    NPCScriptManager.getInstance().start(c, 9010000, "聚合功能");
                }
                break;
            case MOVE_PLAYER:
                PlayerHandler.MovePlayer(slea, c, c.getPlayer());
                break;
            case CHAR_INFO_REQUEST:
                c.getPlayer().updateTick(slea.readInt());
                PlayerHandler.CharInfoRequest(slea.readInt(), c, c.getPlayer());
                break;
            case CLOSE_RANGE_ATTACK:
                PlayerHandler.closeRangeAttack(slea, c, c.getPlayer(), false);
                break;
            case RANGED_ATTACK:
                PlayerHandler.rangedAttack(slea, c, c.getPlayer());
                break;
            case MAGIC_ATTACK:
                PlayerHandler.MagicDamage(slea, c, c.getPlayer());
                break;
            case SPECIAL_MOVE:
                PlayerHandler.SpecialMove(slea, c, c.getPlayer());
                break;
            case PASSIVE_ENERGY:
                PlayerHandler.closeRangeAttack(slea, c, c.getPlayer(), true);
                break;
            case FACE_EXPRESSION:
                PlayerHandler.ChangeEmotion(slea.readInt(), c.getPlayer());
                break;
            case TAKE_DAMAGE:
                PlayerHandler.TakeDamage(slea, c, c.getPlayer());
                break;
            case HEAL_OVER_TIME:
                PlayerHandler.Heal(slea, c.getPlayer());
                break;
            case CANCEL_BUFF:
                PlayerHandler.CancelBuffHandler(slea.readInt(), c.getPlayer());
                break;
            case CANCEL_ITEM_EFFECT:
                PlayerHandler.CancelItemEffect(slea.readInt(), c.getPlayer());
                break;
            case USE_CHAIR:
                PlayerHandler.UseChair(slea.readInt(), c, c.getPlayer());
                break;
            case CANCEL_CHAIR:
                PlayerHandler.CancelChair(slea.readShort(), c, c.getPlayer());
                break;
            case USE_ITEMEFFECT:
            case WHEEL_OF_FORTUNE:
                PlayerHandler.UseItemEffect(slea.readInt(), c, c.getPlayer());
                break;
            case SKILL_EFFECT:
                PlayerHandler.SkillEffect(slea, c.getPlayer());
                break;
            case MESO_DROP:
                c.getPlayer().updateTick(slea.readInt());
                PlayerHandler.DropMeso(slea.readInt(), c.getPlayer());
                break;
            case MONSTER_BOOK_COVER:
                PlayerHandler.ChangeMonsterBookCover(slea.readInt(), c, c.getPlayer());
                break;
            case CHANGE_KEYMAP:
                PlayerHandler.ChangeKeymap(slea, c.getPlayer());
                break;
            case CHANGE_MAP:
                if (cs) {
                    CashShopOperation.LeaveCS(slea, c, c.getPlayer());
                } else {
                    PlayerHandler.ChangeMap(slea, c, c.getPlayer());
                }
                break;
            case CHANGE_MAP_SPECIAL:
                slea.skip(1);
                PlayerHandler.ChangeMapSpecial(slea.readMapleAsciiString(), c, c.getPlayer());
                break;
            case USE_INNER_PORTAL:
                slea.skip(1);
                PlayerHandler.InnerPortal(slea, c, c.getPlayer());
                break;
            case TROCK_ADD_MAP:
                PlayerHandler.TrockAddMap(slea, c, c.getPlayer());
                break;
            case ARAN_COMBO:
                PlayerHandler.AranCombo(c, c.getPlayer());
                break;
            case SKILL_MACRO:
                PlayerHandler.ChangeSkillMacro(slea, c.getPlayer());
                break;
            case GIVE_FAME:
                PlayersHandler.GiveFame(slea, c, c.getPlayer());
                break;
            case TRANSFORM_PLAYER:
                PlayersHandler.TransformPlayer(slea, c, c.getPlayer());
                break;
            case NOTE_ACTION:
                PlayersHandler.Note(slea, c.getPlayer());
                break;
            case USE_DOOR:
                PlayersHandler.UseDoor(slea, c.getPlayer());
                break;
            case DAMAGE_REACTOR:
                PlayersHandler.HitReactor(slea, c);
                break;
            case TOUCH_REACTOR:
                PlayersHandler.TouchReactor(slea, c);
                break;
            case CLOSE_CHALKBOARD:
                c.getPlayer().setChalkboard(null);
                break;
            case ITEM_MAKER:
                ItemMakerHandler.ItemMaker(slea, c);
                break;
            case ITEM_SORT:
                InventoryHandler.ItemSort(slea, c);
                break;
            case ITEM_GATHER:
                InventoryHandler.ItemGather(slea, c);
                break;
            case ITEM_MOVE:
                InventoryHandler.ItemMove(slea, c);
                break;
            case ITEM_PICKUP:
                InventoryHandler.Pickup_Player(slea, c, c.getPlayer());
                break;
            case USE_CASH_ITEM:
                InventoryHandler.UseCashItem(slea, c);
                break;
            case USE_ITEM:
                InventoryHandler.UseItem(slea, c, c.getPlayer());
                break;
            case USE_MAGNIFY_GLASS:
                InventoryHandler.UseMagnify(slea, c);
                break;
            case USE_SCRIPTED_NPC_ITEM:
                InventoryHandler.UseScriptedNPCItem(slea, c, c.getPlayer());
                break;
            case USE_RETURN_SCROLL:
                InventoryHandler.UseReturnScroll(slea, c, c.getPlayer());
                break;
            case USE_UPGRADE_SCROLL:
                c.getPlayer().updateTick(slea.readInt());
                InventoryHandler.UseUpgradeScroll(
                        (byte) slea.readShort(),
                        (byte) slea.readShort(),
                        (byte) slea.readShort(),
                        c,
                        c.getPlayer());
                break;
            case USE_POTENTIAL_SCROLL:
                c.getPlayer().updateTick(slea.readInt());
                InventoryHandler.UseUpgradeScroll(
                        (byte) slea.readShort(),
                        (byte) slea.readShort(),
                        (byte) 0,
                        c,
                        c.getPlayer());
                break;
            case USE_EQUIP_SCROLL:
                c.getPlayer().updateTick(slea.readInt());
                InventoryHandler.UseUpgradeScroll(
                        (byte) slea.readShort(),
                        (byte) slea.readShort(),
                        (byte) 0,
                        c,
                        c.getPlayer());
                break;
            case USE_SUMMON_BAG:
                InventoryHandler.UseSummonBag(slea, c, c.getPlayer());
                break;
            case USE_TREASUER_CHEST:
                InventoryHandler.UseTreasureChest(slea, c, c.getPlayer());
                break;
            case USE_SKILL_BOOK:
                InventoryHandler.UseSkillBook(slea, c, c.getPlayer());
                break;
            case USE_CATCH_ITEM:
                InventoryHandler.UseCatchItem(slea, c, c.getPlayer());
                break;
            case USE_MOUNT_FOOD:
                InventoryHandler.UseMountFood(slea, c, c.getPlayer());
                break;
            case REWARD_ITEM:
                InventoryHandler.UseRewardItem(
                        (byte) slea.readShort(), slea.readInt(), c, c.getPlayer());
                break;
            case HYPNOTIZE_DMG:
                MobHandler.HypnotizeDmg(slea, c.getPlayer());
                break;
            case MOB_NODE:
                MobHandler.MobNode(slea, c.getPlayer());
                break;
            case DISPLAY_NODE:
                MobHandler.DisplayNode(slea, c.getPlayer());
                break;
            case MOVE_LIFE:
                MobHandler.MoveMonster(slea, c, c.getPlayer());
                break;
            case AUTO_AGGRO:
                MobHandler.AutoAggro(slea.readInt(), c.getPlayer());
                break;
            case FRIENDLY_DAMAGE:
                MobHandler.FriendlyDamage(slea, c.getPlayer());
                break;
            case MONSTER_BOMB:
                MobHandler.MonsterBomb(slea.readInt(), c.getPlayer());
                break;
            case NPC_SHOP:
                NPCHandler.NPCShop(slea, c, c.getPlayer());
                break;
            case NPC_TALK:
                NPCHandler.NPCTalk(slea, c, c.getPlayer());
                break;
            case NPC_TALK_MORE:
                NPCHandler.NPCMoreTalk(slea, c);
                break;
            case NPC_ACTION:
                NPCHandler.NPCAnimation(slea, c);
                break;
            case QUEST_ACTION:
                NPCHandler.QuestAction(slea, c, c.getPlayer());
                break;
            case STORAGE:
                NPCHandler.Storage(slea, c, c.getPlayer());
                break;
            case GENERAL_CHAT:
                ChatHandler.GeneralChat(
                        slea.readMapleAsciiString(), slea.readByte(), c, c.getPlayer());
                break;
            case PARTYCHAT:
                ChatHandler.Others(slea, c, c.getPlayer());
                break;
            case WHISPER:
                ChatHandler.Whisper_Find(slea, c);
                break;
            case MESSENGER:
                ChatHandler.Messenger(slea, c);
                break;
            case AUTO_ASSIGN_AP:
                StatsHandling.AutoAssignAP(slea, c, c.getPlayer());
                break;
            case DISTRIBUTE_AP:
                StatsHandling.DistributeAP(slea, c, c.getPlayer());
                break;
            case DISTRIBUTE_SP:
                c.getPlayer().updateTick(slea.readInt());
                StatsHandling.DistributeSP(slea.readInt(), c, c.getPlayer());
                break;
            case PLAYER_INTERACTION:
                PlayerInteractionHandler.PlayerInteraction(slea, c, c.getPlayer());
                break;
            case GUILD_OPERATION:
                GuildHandler.Guild(slea, c);
                break;
            case UPDATE_CHAR_INFO:
                PlayersHandler.UpdateCharInfo(slea, c, c.getPlayer());
                break;
            case DENY_GUILD_REQUEST:
                slea.skip(1);
                GuildHandler.DenyGuildRequest(slea.readMapleAsciiString(), c);
                break;
            case ALLIANCE_OPERATION:
                AllianceHandler.HandleAlliance(slea, c, false);
                break;
            case DENY_ALLIANCE_REQUEST:
                AllianceHandler.HandleAlliance(slea, c, true);
                break;
            case BBS_OPERATION:
                BBSHandler.BBSOperatopn(slea, c);
                break;
            case PARTY_OPERATION:
                PartyHandler.PartyOperatopn(slea, c);
                break;
            case DENY_PARTY_REQUEST:
                PartyHandler.DenyPartyRequest(slea, c);
                break;
            case BUDDYLIST_MODIFY:
                BuddyListHandler.BuddyOperation(slea, c);
                break;
            case CYGNUS_SUMMON:
                UserInterfaceHandler.CygnusSummon_NPCRequest(c);
                break;
            case SHIP_OBJECT:
                UserInterfaceHandler.ShipObjectRequest(slea.readInt(), c);
                break;
            case BUY_CS_ITEM:
                CashShopOperation.BuyCashItem(slea, c, c.getPlayer());
                break;
            case COUPON_CODE:
                LOG.warn("MapleServerHandler.handlePacket ERROR Coupon : ", slea.toString(true));
                //LOG.debug(slea.toString());
                slea.skip(2);
                CashShopOperation.CouponCode(slea.readMapleAsciiString(), c);
                break;
            case CS_UPDATE:
                CashShopOperation.CSUpdate(c);
                break;
            case TOUCHING_MTS:
            case MTS_TAB:
                MTSOperation.MTSOperation(slea, c);
                break;
            case DAMAGE_SUMMON:
                slea.skip(4);
                SummonHandler.DamageSummon(slea, c.getPlayer());
                break;
            case MOVE_SUMMON:
                SummonHandler.MoveSummon(slea, c.getPlayer());
                break;
            case SUMMON_ATTACK:
                SummonHandler.SummonAttack(slea, c, c.getPlayer());
                break;
            case SPAWN_PET:
                PetHandler.SpawnPet(slea, c, c.getPlayer());
                break;
            case MOVE_PET:
                PetHandler.MovePet(slea, c.getPlayer());
                break;
            case PET_CHAT:
                if (slea.available() < 12) {
                    break;
                }
                PetHandler.PetChat(
                        (int) slea.readLong(),
                        slea.readShort(),
                        slea.readMapleAsciiString(),
                        c.getPlayer());
                break;
            case PET_COMMAND:
                PetHandler.PetCommand(slea, c, c.getPlayer());
                break;
            case PET_FOOD:
                PetHandler.PetFood(slea, c, c.getPlayer());
                break;
            case PET_LOOT:
                InventoryHandler.Pickup_Pet(slea, c, c.getPlayer());
                break;
            case PET_AUTO_POT:
                PetHandler.Pet_AutoPotion(slea, c, c.getPlayer());
                break;
            case MONSTER_CARNIVAL:
                MonsterCarnivalHandler.MonsterCarnival(slea, c);
                break;
            case DUEY_ACTION:
                DueyHandler.DueyOperation(slea, c);
                break;
            case USE_HIRED_MERCHANT:
                HiredMerchantHandler.UseHiredMerchant(slea, c);
                break;
            case USE_HIRED_FISHING:
                HiredFishingHandler.UseHiredFishing(slea, c);
                break;
            case MERCH_ITEM_STORE:
                final int conv = c.getPlayer().getConversation();
                if (conv == 3) {
                    HiredMerchantHandler.MerchantItemStore(slea, c);
                } else {
                    HiredFishingHandler.FishingItemStore(slea, c);
                }
                break;
            case CANCEL_DEBUFF:
                break;
            case LEFT_KNOCK_BACK:
                PlayerHandler.leftKnockBack(slea, c);
                break;
            case SNOWBALL:
                PlayerHandler.snowBall(slea, c);
                break;
            case COCONUT:
                PlayersHandler.hitCoconut(slea, c);
                break;
            case REPAIR:
                NPCHandler.repair(slea, c);
                break;
            case REPAIR_ALL:
                NPCHandler.repairAll(c);
                break;
            case GAME_POLL:
                UserInterfaceHandler.InGame_Poll(slea, c);
                break;
            case XMAS_SURPRISE:
                CashShopOperation.UseXmaxsSurprise(slea, c);
                break;
            case OWL:
                InventoryHandler.Owl(slea, c);
                break;
            case OWL_WARP:
                InventoryHandler.OwlWarp(slea, c);
                break;
            case USE_OWL_MINERVA:
                InventoryHandler.OwlMinerva(slea, c);
                break;
            case RPS_GAME:
                NPCHandler.RPSGame(slea, c);
                break;
            case UPDATE_QUEST:
                NPCHandler.UpdateQuest(slea, c);
                break;
            case USE_ITEM_QUEST:
                NPCHandler.UseItemQuest(slea, c);
                break;
            case FOLLOW_REQUEST:
                PlayersHandler.FollowRequest(slea, c);
                break;
            case FOLLOW_REPLY:
                PlayersHandler.FollowReply(slea, c);
                break;
            case RING_ACTION:
                PlayersHandler.RingAction(slea, c);
                break;
            case REQUEST_FAMILY:
                FamilyHandler.RequestFamily(slea, c);
                break;
            case OPEN_FAMILY:
                FamilyHandler.OpenFamily(slea, c);
                break;
            case FAMILY_OPERATION:
                FamilyHandler.FamilyOperation(slea, c);
                break;
            case DELETE_JUNIOR:
                FamilyHandler.DeleteJunior(slea, c);
                break;
            case DELETE_SENIOR:
                FamilyHandler.DeleteSenior(slea, c);
                break;
            case USE_FAMILY:
                FamilyHandler.UseFamily(slea, c);
                break;
            case FAMILY_PRECEPT:
                FamilyHandler.FamilyPrecept(slea, c);
                break;
            case FAMILY_SUMMON:
                FamilyHandler.FamilySummon(slea, c);
                break;
            case ACCEPT_FAMILY:
                FamilyHandler.AcceptFamily(slea, c);
                break;
            case SOLOMON:
                PlayersHandler.Solomon(slea, c);
                break;
            case GACH_EXP:
                PlayersHandler.GachExp(slea, c);
                break;
            default:
                LOG.debug("[UNHANDLED] Recv [{}] found", header.toString());
                break;
        }
    }
}