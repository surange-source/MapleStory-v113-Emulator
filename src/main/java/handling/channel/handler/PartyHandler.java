package handling.channel.handler;

import client.MapleCharacter;
import client.MapleClient;
import handling.world.MapleParty;
import handling.world.MaplePartyCharacter;
import handling.world.PartyOperation;
import handling.world.World;
import tools.MaplePacketCreator;
import tools.data.input.SeekableLittleEndianAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PartyHandler {
    private static final Logger log = LoggerFactory.getLogger(PartyHandler.class);

    public static final void DenyPartyRequest(
            final SeekableLittleEndianAccessor slea, final MapleClient c) {
        final int action = slea.readByte();
        final int partyid = slea.readInt();
        final MapleCharacter player = c.getPlayer();
        if (player == null) {
            return;
        }
        if (player.getParty() == null) {
            final MapleParty party = World.Party.getParty(partyid);
            if (party != null) {
                if (action == 0x1B) { // accept
                    if (party.getMembers().size() < 6) {
                        World.Party.updateParty(
                                partyid, PartyOperation.JOIN, new MaplePartyCharacter(player));
                        player.receivePartyMemberHP();
                        player.updatePartyMemberHP();
                    } else {
                        c.sendPacket(MaplePacketCreator.partyStatusMessage(17));
                    }
                } else if (action != 0x16) {
                    final MapleCharacter cfrom =
                            c.getChannelServer()
                                    .getPlayerStorage()
                                    .getCharacterById(party.getLeader().getId());
                    if (cfrom != null) {
                        cfrom.getClient()
                                .sendPacket(
                                        MaplePacketCreator.partyStatusMessage(
                                                23, player.getName()));
                    }
                }
            } else {
                player.dropMessage(5, "該隊伍不存在");
            }
        } else {
            player.dropMessage(5, "你無法重複加入隊伍");
        }
    }

    public static final void PartyOperatopn(
            final SeekableLittleEndianAccessor slea, final MapleClient c) {
        final int operation = slea.readByte();
        final MapleCharacter player = c.getPlayer();
        if (player == null) {
            return;
        }
        MapleParty party = player.getParty();
        final MaplePartyCharacter partyplayer = new MaplePartyCharacter(player);

        switch (operation) {
            case 1: // create
                if (player.getParty() == null) {
                    party = World.Party.createParty(partyplayer);
                    player.setParty(party);
                    c.sendPacket(MaplePacketCreator.partyCreated(party.getId()));
                } else {
                    if (partyplayer.equals(party.getLeader())
                            && party.getMembers().size() == 1) { // only one, reupdate
                        c.sendPacket(MaplePacketCreator.partyCreated(party.getId()));
                    } else {
                        player.dropMessage(5, "你無法重複創建隊伍");
                    }
                }
                break;
            case 2: // leave
                if (party != null) { // are we in a party? o.O"
                    if (partyplayer.equals(party.getLeader())) { // disband
                        World.Party.updateParty(party.getId(), PartyOperation.DISBAND, partyplayer);
                        if (player.getEventInstance() != null) {
                            player.getEventInstance().disbandParty();
                        }
                        if (player.getPyramidSubway() != null) {
                            player.getPyramidSubway().fail(player);
                        }
                    } else {
                        World.Party.updateParty(party.getId(), PartyOperation.LEAVE, partyplayer);
                        if (player.getEventInstance() != null) {
                            player.getEventInstance().leftParty(player);
                        }
                        if (player.getPyramidSubway() != null) {
                            player.getPyramidSubway().fail(player);
                        }
                    }
                    player.setParty(null);
                }
                break;
            case 3: // accept invitation
                final int partyid = slea.readInt();
                if (player.getParty() == null) {
                    party = World.Party.getParty(partyid);
                    if (party != null) {
                        if (party.getMembers().size() < 6) {
                            World.Party.updateParty(
                                    party.getId(), PartyOperation.JOIN, partyplayer);
                            player.receivePartyMemberHP();
                            player.updatePartyMemberHP();
                        } else {
                            c.sendPacket(MaplePacketCreator.partyStatusMessage(17));
                        }
                    } else {
                        player.dropMessage(5, "該隊伍不存在");
                    }
                } else {
                    player.dropMessage(5, "你無法重複加入隊伍");
                }
                break;
            case 4: // invite
                final MapleCharacter invited =
                        c.getChannelServer()
                                .getPlayerStorage()
                                .getCharacterByName(slea.readMapleAsciiString());
                if (invited != null) {
                    if (invited.getParty() == null && party != null) {
                        if (party.getMembers().size() < 6) {
                            c.sendPacket(
                                    MaplePacketCreator.serverNotice(
                                            5, "發出隊伍邀請給'" + invited.getName() + "'"));
                            invited.getClient().sendPacket(MaplePacketCreator.partyInvite(player));
                        } else {
                            c.sendPacket(MaplePacketCreator.partyStatusMessage(16));
                        }
                    } else {
                        c.sendPacket(MaplePacketCreator.partyStatusMessage(17));
                    }
                } else {
                    c.sendPacket(MaplePacketCreator.partyStatusMessage(19));
                }
                break;
            case 5: // expel
                if (party != null && partyplayer.equals(party.getLeader())) {
                    final MaplePartyCharacter expelled = party.getMemberById(slea.readInt());
                    if (expelled != null) {
                        World.Party.updateParty(party.getId(), PartyOperation.EXPEL, expelled);
                        if (player.getEventInstance() != null) {
                            if (expelled.isOnline()) {
                                player.getEventInstance().disbandParty();
                            }
                        }
                        if (player.getPyramidSubway() != null && expelled.isOnline()) {
                            player.getPyramidSubway().fail(player);
                        }
                    }
                }
                break;
            case 6: // change leader
                if (party != null) {
                    final MaplePartyCharacter newleader = party.getMemberById(slea.readInt());
                    if (newleader != null && partyplayer.equals(party.getLeader())) {
                        World.Party.updateParty(
                                party.getId(), PartyOperation.CHANGE_LEADER, newleader);
                    }
                }
                break;
            default:
                log.warn("Unhandled Party function.{}", operation);
                break;
        }
    }
}
