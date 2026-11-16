package handling.channel.handler;

import client.MapleClient;
import handling.world.World;
import handling.world.guild.MapleBBSThread;
import java.util.List;
import tools.MaplePacketCreator;
import tools.data.input.SeekableLittleEndianAccessor;

public class BBSHandler {

    private static final String correctLength(final String in, final int maxSize) {
        if (in == null) {
            return "";
        }
        if (in.length() > maxSize) {
            return in.substring(0, maxSize);
        }
        return in;
    }

    public static final void BBSOperatopn(
            final SeekableLittleEndianAccessor slea, final MapleClient c) {
        if (c == null || c.getPlayer() == null) {
            return;
        }
        final int guildId = c.getPlayer().getGuildId();
        if (guildId <= 0) {
            return; // expelled while viewing bbs or hax
        }
        int localthreadid = 0;
        final byte action = slea.readByte();
        switch (action) {
            case 0: // start a new post
                final boolean bEdit = slea.readByte() > 0;
                if (bEdit) {
                    localthreadid = slea.readInt();
                }
                final boolean bNotice = slea.readByte() > 0;
                final String title = correctLength(slea.readMapleAsciiString(), 25);
                String text = correctLength(slea.readMapleAsciiString(), 600);
                final int icon = slea.readInt();
                if (icon >= 0x64 && icon <= 0x6a) {
                    if (!c.getPlayer().haveItem(5290000 + icon - 0x64, 1, false, true)) {
                        return; // hax, using an nx icon that s/he doesn't have
                    }
                } else if (icon < 0 || icon > 2) {
                    return; // hax, using an invalid icon
                }
                if (!bEdit) {
                    newBBSThread(c, title, text, icon, bNotice);
                } else {
                    editBBSThread(c, title, text, icon, localthreadid);
                }
                break;
            case 1: // delete a thread
                localthreadid = slea.readInt();
                deleteBBSThread(c, localthreadid);
                break;
            case 2: // list threads
                int start = slea.readInt();
                listBBSThreads(c, start * 10);
                break;
            case 3: // list thread + reply, followed by id (int)
                localthreadid = slea.readInt();
                displayThread(c, localthreadid);
                break;
            case 4: // reply
                localthreadid = slea.readInt();
                text = correctLength(slea.readMapleAsciiString(), 25);
                newBBSReply(c, localthreadid, text);
                break;
            case 5: // delete reply
                localthreadid = slea.readInt();
                int replyid = slea.readInt();
                deleteBBSReply(c, localthreadid, replyid);
                break;
            default:
                break;
        }
    }

    private static void listBBSThreads(final MapleClient c, final int start) {
        if (c == null || c.getPlayer() == null) {
            return;
        }
        final int guildId = c.getPlayer().getGuildId();
        if (guildId <= 0) {
            return;
        }
        c.sendPacket(MaplePacketCreator.BBSThreadList(World.Guild.getBBS(guildId), start));
    }

    private static final void newBBSReply(
            final MapleClient c, final int localthreadid, final String text) {
        if (c == null || c.getPlayer() == null) {
            return;
        }
        final int guildId = c.getPlayer().getGuildId();
        if (guildId <= 0) {
            return;
        }
        World.Guild.addBBSReply(guildId, localthreadid, text, c.getPlayer().getId());
        displayThread(c, localthreadid);
    }

    private static final void editBBSThread(
            final MapleClient c,
            final String title,
            final String text,
            final int icon,
            final int localthreadid) {
        if (c == null || c.getPlayer() == null) {
            return;
        }
        final int guildId = c.getPlayer().getGuildId();
        if (guildId <= 0) {
            return; // expelled while viewing?
        }
        World.Guild.editBBSThread(
                guildId,
                localthreadid,
                title,
                text,
                icon,
                c.getPlayer().getId(),
                c.getPlayer().getGuildRank());
        displayThread(c, localthreadid);
    }

    private static final void newBBSThread(
            final MapleClient c,
            final String title,
            final String text,
            final int icon,
            final boolean bNotice) {
        if (c == null || c.getPlayer() == null) {
            return;
        }
        final int guildId = c.getPlayer().getGuildId();
        if (guildId <= 0) {
            return; // expelled while viewing?
        }
        displayThread(
                c,
                World.Guild.addBBSThread(
                        guildId, title, text, icon, bNotice, c.getPlayer().getId()));
    }

    private static final void deleteBBSThread(final MapleClient c, final int localthreadid) {
        if (c == null || c.getPlayer() == null) {
            return;
        }
        final int guildId = c.getPlayer().getGuildId();
        if (guildId <= 0) {
            return;
        }
        World.Guild.deleteBBSThread(
                guildId, localthreadid, c.getPlayer().getId(), (int) c.getPlayer().getGuildRank());
    }

    private static final void deleteBBSReply(
            final MapleClient c, final int localthreadid, final int replyid) {
        if (c == null || c.getPlayer() == null) {
            return;
        }
        final int guildId = c.getPlayer().getGuildId();
        if (guildId <= 0) {
            return;
        }

        World.Guild.deleteBBSReply(
                guildId,
                localthreadid,
                replyid,
                c.getPlayer().getId(),
                (int) c.getPlayer().getGuildRank());
        displayThread(c, localthreadid);
    }

    private static final void displayThread(final MapleClient c, final int localthreadid) {
        if (c == null || c.getPlayer() == null) {
            return;
        }
        final int guildId = c.getPlayer().getGuildId();
        if (guildId <= 0) {
            return;
        }
        final List<MapleBBSThread> bbsList = World.Guild.getBBS(guildId);
        if (bbsList != null) {
            for (MapleBBSThread t : bbsList) {
                if (t != null && t.localthreadID == localthreadid) {
                    c.sendPacket(MaplePacketCreator.showThread(t));
                }
            }
        }
    }
}
