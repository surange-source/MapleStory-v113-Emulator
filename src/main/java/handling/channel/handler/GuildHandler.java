package handling.channel.handler;

import client.MapleCharacter;
import client.MapleClient;
import handling.MaplePacket;
import handling.world.World;
import handling.world.guild.MapleGuild;
import handling.world.guild.MapleGuildResponse;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import tools.MaplePacketCreator;
import tools.data.input.SeekableLittleEndianAccessor;

public class GuildHandler {

    public static final void DenyGuildRequest(final String from, final MapleClient c) {
        final MapleCharacter cfrom =
                c.getChannelServer().getPlayerStorage().getCharacterByName(from);
        if (cfrom != null) {
            cfrom.getClient()
                    .sendPacket(MaplePacketCreator.denyGuildInvitation(c.getPlayer().getName()));
        }
    }

    private static final boolean isGuildNameAcceptable(final String name) {
        if (name.length() > 15) {
            return false;
        }
        if (name.length() < 3) {
            return false;
        }
        return true;
    }

    private static final void respawnPlayer(final MapleCharacter mc) {
        mc.getMap().broadcastMessage(mc, MaplePacketCreator.removePlayerFromMap(mc.getId()), false);
        mc.getMap().broadcastMessage(mc, MaplePacketCreator.spawnPlayerMapobject(mc), false);
    }

    private static final class Invited {

        public final String name;
        public final int gid;
        public final long expiration;

        public Invited(final String n, final int id) {
            name = n.toLowerCase();
            gid = id;
            expiration = System.currentTimeMillis() + 60 * 60 * 1000; // 1 hr expiration
        }

        @Override
        public final boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Invited)) {
                return false;
            }
            Invited oth = (Invited) other;
            return (gid == oth.gid && name.equals(oth.name));
        }

        @Override
        public final int hashCode() {
            return Objects.hash(gid, name);
        }
    }

    // 使用 ConcurrentMap 取代 LinkedList + contains 的 O(n) 掃描，並消除外部同步需求。
    // key 為 "gid:name" 複合鍵，確保 (gid, name) 唯一性與 O(1) 查找/移除。
    private static final ConcurrentMap<String, Invited> invited = new ConcurrentHashMap<>();
    private static final AtomicLong nextPruneTime =
            new AtomicLong(System.currentTimeMillis() + 20 * 60 * 1000);

    private static String inviteKey(final int gid, final String name) {
        return gid + ":" + name;
    }

    private static void pruneInvites() {
        final long now = System.currentTimeMillis();
        final long next = nextPruneTime.get();
        if (now >= next) {
            // CAS 避免多執行緒同時進入 prune 區塊
            if (nextPruneTime.compareAndSet(next, now + 20 * 60 * 1000)) {
                final Iterator<Invited> itr = invited.values().iterator();
                while (itr.hasNext()) {
                    final Invited inv = itr.next();
                    if (now >= inv.expiration) {
                        itr.remove();
                    }
                }
            }
        }
    }

    public static final void Guild(final SeekableLittleEndianAccessor slea, final MapleClient c) {
        pruneInvites();

        switch (slea.readByte()) {
            case 0x02:
                { // Create guild
                    if (c.getPlayer().getGuildId() > 0 || c.getPlayer().getMapId() != 200000301) {
                        c.getPlayer().dropMessage(1, "你不能在創建一個新的公會.");
                        return;
                    } else if (c.getPlayer().getMeso() < 500000) {
                        c.getPlayer().dropMessage(1, "你的楓幣不夠,無法創建公會");
                        return;
                    }
                    final String guildName = slea.readMapleAsciiString();

                    if (!isGuildNameAcceptable(guildName)) {
                        c.getPlayer().dropMessage(1, "這個公會名稱是不被准許的.");
                        return;
                    }
                    final int guildId = World.Guild.createGuild(c.getPlayer().getId(), guildName);
                    if (guildId == 0) {
                        c.sendPacket(MaplePacketCreator.genericGuildMessage((byte) 0x1c));
                        return;
                    }
                    c.getPlayer().gainMeso(-500000, true, false, true);
                    c.getPlayer().setGuildId(guildId);
                    c.getPlayer().setGuildRank((byte) 1);
                    c.getPlayer().saveGuildStatus();
                    c.sendPacket(MaplePacketCreator.showGuildInfo(c.getPlayer()));
                    World.Guild.setGuildMemberOnline(c.getPlayer().getMGC(), true, c.getChannel());
                    c.getPlayer().dropMessage(1, "恭喜你成功創建一個公會.");
                    respawnPlayer(c.getPlayer());
                    break;
                }
            case 0x05:
                { // invitation
                    if (c.getPlayer().getGuildId() <= 0
                            || c.getPlayer().getGuildRank() > 2) { // 1 == guild master, 2 == jr
                        return;
                    }
                    final String name = slea.readMapleAsciiString();
                    final MapleGuildResponse mgr = MapleGuild.sendInvite(c, name);

                    if (mgr != null) {
                        c.sendPacket(mgr.getPacket());
                    } else {
                        final Invited inv = new Invited(name, c.getPlayer().getGuildId());
                        invited.putIfAbsent(inviteKey(inv.gid, inv.name), inv);
                    }
                    break;
                }
            case 0x06:
                { // accepted guild invitation
                    if (c.getPlayer().getGuildId() > 0) {
                        return;
                    }
                    final int guildId = slea.readInt();
                    final int cid = slea.readInt();

                    if (cid != c.getPlayer().getId()) {
                        return;
                    }
                    final String name = c.getPlayer().getName().toLowerCase();
                    final String key = inviteKey(guildId, name);
                    final Invited inv = invited.get(key);
                    if (inv != null) {
                        // 原子移除，避免重複接受
                        if (invited.remove(key, inv)) {
                            c.getPlayer().setGuildId(guildId);
                            c.getPlayer().setGuildRank((byte) 5);

                            final int s = World.Guild.addGuildMember(c.getPlayer().getMGC());
                            if (s == 0) {
                                c.getPlayer().dropMessage(1, "你想要加入的公會已經滿了.");
                                c.getPlayer().setGuildId(0);
                                return;
                            }
                            c.sendPacket(MaplePacketCreator.showGuildInfo(c.getPlayer()));
                            final MapleGuild gs = World.Guild.getGuild(guildId);
                            if (gs != null) {
                                final List<MaplePacket> alliancePacks =
                                        World.Alliance.getAllianceInfo(gs.getAllianceId(), true);
                                if (alliancePacks != null) {
                                    for (MaplePacket pack : alliancePacks) {
                                        if (pack != null) {
                                            c.sendPacket(pack);
                                        }
                                    }
                                }
                            }
                            c.getPlayer().saveGuildStatus();
                            respawnPlayer(c.getPlayer());
                        }
                    }
                    break;
                }
            case 0x07:
                { // leaving
                    final int cid = slea.readInt();
                    final String name = slea.readMapleAsciiString();

                    if (cid != c.getPlayer().getId()
                            || !name.equals(c.getPlayer().getName())
                            || c.getPlayer().getGuildId() <= 0) {
                        return;
                    }
                    World.Guild.leaveGuild(c.getPlayer().getMGC());
                    c.sendPacket(MaplePacketCreator.showGuildInfo(null));
                    c.sendPacket(MaplePacketCreator.fuckGuildInfo(c.getPlayer()));
                    break;
                }
            case 0x08:
                { // Expel
                    final int cid = slea.readInt();
                    final String name = slea.readMapleAsciiString();

                    if (c.getPlayer().getGuildRank() > 2 || c.getPlayer().getGuildId() <= 0) {
                        return;
                    }
                    World.Guild.expelMember(c.getPlayer().getMGC(), name, cid);
                    break;
                }
            case 0x0d:
                { // Guild rank titles change
                    if (c.getPlayer().getGuildId() <= 0 || c.getPlayer().getGuildRank() != 1) {
                        return;
                    }
                    final String ranks[] = new String[5];
                    for (int i = 0; i < 5; i++) {
                        ranks[i] = slea.readMapleAsciiString();
                    }

                    World.Guild.changeRankTitle(c.getPlayer().getGuildId(), ranks);
                    break;
                }
            case 0x0e:
                { // Rank change
                    final int cid = slea.readInt();
                    final byte newRank = slea.readByte();

                    if ((newRank <= 1 || newRank > 5)
                            || c.getPlayer().getGuildRank() > 2
                            || (newRank <= 2 && c.getPlayer().getGuildRank() != 1)
                            || c.getPlayer().getGuildId() <= 0) {
                        return;
                    }

                    World.Guild.changeRank(c.getPlayer().getGuildId(), cid, newRank);
                    break;
                }
            case 0x0f:
                { // guild emblem change
                    if (c.getPlayer().getGuildId() <= 0
                            || c.getPlayer().getGuildRank() != 1
                            || c.getPlayer().getMapId() != 200000301) {
                        return;
                    }

                    if (c.getPlayer().getMeso() < 1000000) {
                        c.getPlayer().dropMessage(1, "你的楓幣不夠,無法創建公會徽章");
                        return;
                    }
                    final short bg = slea.readShort();
                    final byte bgcolor = slea.readByte();
                    final short logo = slea.readShort();
                    final byte logocolor = slea.readByte();

                    World.Guild.setGuildEmblem(
                            c.getPlayer().getGuildId(), bg, bgcolor, logo, logocolor);

                    c.getPlayer().gainMeso(-1000000, true, false, true);
                    respawnPlayer(c.getPlayer());
                    break;
                }
            case 0x10:
                { // guild notice change
                    final String notice = slea.readMapleAsciiString();
                    if (notice.length() > 100
                            || c.getPlayer().getGuildId() <= 0
                            || c.getPlayer().getGuildRank() > 2) {
                        return;
                    }
                    World.Guild.setGuildNotice(c.getPlayer().getGuildId(), notice);
                    break;
                }
        }
    }
}
