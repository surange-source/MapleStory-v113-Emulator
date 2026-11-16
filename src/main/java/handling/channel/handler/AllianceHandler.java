package handling.channel.handler;

import client.MapleCharacter;
import client.MapleClient;
import handling.MaplePacket;
import handling.world.World;
import handling.world.guild.MapleGuild;
import tools.MaplePacketCreator;
import tools.data.input.SeekableLittleEndianAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AllianceHandler {
    private static final Logger log = LoggerFactory.getLogger(AllianceHandler.class);

    public static final void HandleAlliance(
            final SeekableLittleEndianAccessor slea, final MapleClient c, boolean denied) {
        final MapleCharacter player = c.getPlayer();
        if (player == null || player.getGuildId() <= 0) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        final MapleGuild gs = World.Guild.getGuild(player.getGuildId());
        if (gs == null) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        // System.out.println("Unhandled GuildAlliance \n" + slea.toString());
        final byte op = slea.readByte();
        if (player.getGuildRank() != 1 && op != 1) { // only updating doesn't need guild leader
            return;
        }
        if (op == 22) {
            denied = true;
        }
        int leaderid = 0;
        if (gs.getAllianceId() > 0) {
            leaderid = World.Alliance.getAllianceLeader(gs.getAllianceId());
        }
        // accept invite, and deny invite don't need allianceid.
        if (op != 4 && !denied) {
            if (gs.getAllianceId() <= 0 || leaderid <= 0) {
                return;
            }
        } else if (leaderid > 0
                || gs.getAllianceId() > 0) { // infact, if they have allianceid it's suspicious
            return;
        }
        if (denied) {
            DenyInvite(c, gs);
            return;
        }
        MapleCharacter chr;
        int inviteid;
        switch (op) {
            case 1: // load... must be in world op
                for (MaplePacket pack : World.Alliance.getAllianceInfo(gs.getAllianceId(), false)) {
                    if (pack != null) {
                        c.sendPacket(pack);
                    }
                }
                break;
            case 3: // invite
                final int newGuild = World.Guild.getGuildLeader(slea.readMapleAsciiString());
                if (newGuild > 0 && player.getAllianceRank() == 1 && leaderid == player.getId()) {
                    chr = c.getChannelServer().getPlayerStorage().getCharacterById(newGuild);
                    if (chr != null
                            && chr.getGuildId() > 0
                            && World.Alliance.canInvite(gs.getAllianceId())) {
                        chr.getClient()
                                .sendPacket(
                                        MaplePacketCreator.sendAllianceInvite(
                                                World.Alliance.getAlliance(gs.getAllianceId())
                                                        .getName(),
                                                player));
                        World.Guild.setInvitedId(chr.getGuildId(), gs.getAllianceId());
                    }
                }
                break;
            case 4: // accept invite... guildid that invited(int, a/b check) -> guildname that was
                // invited? but we dont care about that
                inviteid = World.Guild.getInvitedId(player.getGuildId());
                if (inviteid > 0) {
                    if (!World.Alliance.addGuildToAlliance(inviteid, player.getGuildId())) {
                        player.dropMessage(5, "An error occured when adding guild.");
                    }
                    World.Guild.setInvitedId(player.getGuildId(), 0);
                }
                break;
            case 2: // leave; nothing
            case 6: // expel, guildid(int) -> allianceid(don't care, a/b check)
                final int gid;
                if (op == 6 && slea.available() >= 4) {
                    gid = slea.readInt();
                    if (slea.available() >= 4 && gs.getAllianceId() != slea.readInt()) {
                        break;
                    }
                } else {
                    gid = player.getGuildId();
                }
                if (player.getAllianceRank() <= 2
                        && (player.getAllianceRank() == 1 || player.getGuildId() == gid)) {
                    if (!World.Alliance.removeGuildFromAlliance(
                            gs.getAllianceId(), gid, player.getGuildId() != gid)) {
                        player.dropMessage(5, "An error occured when removing guild.");
                    }
                }
                break;
            case 7: // change leader
                if (player.getAllianceRank() == 1 && leaderid == player.getId()) {
                    if (!World.Alliance.changeAllianceLeader(gs.getAllianceId(), slea.readInt())) {
                        player.dropMessage(5, "An error occured when changing leader.");
                    }
                }
                break;
            case 8: // title update
                if (player.getAllianceRank() == 1 && leaderid == player.getId()) {
                    String[] ranks = new String[5];
                    for (int i = 0; i < 5; i++) {
                        ranks[i] = slea.readMapleAsciiString();
                    }
                    World.Alliance.updateAllianceRanks(gs.getAllianceId(), ranks);
                }
                break;
            case 9:
                if (player.getAllianceRank() <= 2) {
                    if (!World.Alliance.changeAllianceRank(
                            gs.getAllianceId(), slea.readInt(), slea.readByte())) {
                        player.dropMessage(5, "An error occured when changing rank.");
                    }
                }
                break;
            case 10: // notice update
                if (player.getAllianceRank() <= 2) {
                    final String notice = slea.readMapleAsciiString();
                    if (notice.length() > 100) {
                        break;
                    }
                    World.Alliance.updateAllianceNotice(gs.getAllianceId(), notice);
                }
                break;
            default:
                log.debug("Unhandled GuildAlliance op: {}, \n{}", op, slea.toString());
                break;
        }
        // c.sendPacket(MaplePacketCreator.enableActions());
    }

    public static final void DenyInvite(
            MapleClient c,
            final MapleGuild
                    gs) { // playername that invited -> guildname that was invited but we also don't
        // care
        final MapleCharacter player = c.getPlayer();
        if (player == null) {
            return;
        }
        final int inviteid = World.Guild.getInvitedId(player.getGuildId());
        if (inviteid > 0) {
            final int newAlliance = World.Alliance.getAllianceLeader(inviteid);
            if (newAlliance > 0) {
                final MapleCharacter chr =
                        c.getChannelServer().getPlayerStorage().getCharacterById(newAlliance);
                if (chr != null) {
                    chr.dropMessage(
                            5, gs.getName() + " Guild has rejected the Guild Union invitation.");
                }
                World.Guild.setInvitedId(player.getGuildId(), 0);
            }
        }
        // c.sendPacket(MaplePacketCreator.enableActions());
    }
}
