package handling.channel.handler;

import client.BuddylistEntry;
import client.CharacterNameAndId;
import client.MapleCharacter;
import client.MapleClient;
import client.MapleQuestStatus;
import client.SkillFactory;
import handling.MaplePacket;
import handling.cashshop.CashShopServer;
import handling.channel.ChannelServer;
import handling.world.CharacterIdChannelPair;
import handling.world.CharacterTransfer;
import handling.world.MapleMessenger;
import handling.world.MapleMessengerCharacter;
import handling.world.MaplePartyCharacter;
import handling.world.PartyOperation;
import handling.world.PlayerBuffStorage;
import handling.world.World;
import handling.world.guild.MapleGuild;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.maps.FieldLimitType;
import server.shops.HiredFishing;
import tools.MaplePacketCreator;
import tools.data.input.SeekableLittleEndianAccessor;
import tools.packet.FamilyPacket;

public class InterServerHandler {
    private static final Logger LOG = LoggerFactory.getLogger(InterServerHandler.class);

    public static final void EnterCS(
            final MapleClient c, final MapleCharacter chr, final boolean mts) {
        if (chr == null || !chr.isAlive() || chr.getEventInstance() != null || c.getChannelServer() == null) {
            c.sendPacket(MaplePacketCreator.serverBlocked(2));
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        final ChannelServer ch = ChannelServer.getInstance(c.getChannel());

        chr.changeRemoval();

        if (chr.getMessenger() != null) {
            MapleMessengerCharacter messengerplayer = new MapleMessengerCharacter(chr);
            World.Messenger.leaveMessenger(chr.getMessenger().getId(), messengerplayer);
        }
        PlayerBuffStorage.addBuffsToStorage(chr.getId(), chr.getAllBuffs());
        PlayerBuffStorage.addCooldownsToStorage(chr.getId(), chr.getCooldowns());
        PlayerBuffStorage.addDiseaseToStorage(chr.getId(), chr.getAllDiseases());
        World.ChannelChange_Data(new CharacterTransfer(chr), chr.getId(), mts ? -20 : -10);
        ch.removePlayer(chr);
        c.updateLoginState(MapleClient.CHANGE_CHANNEL, c.getSessionIPAddress());

        c.sendPacket(
                MaplePacketCreator.getChannelChange(
                        Integer.parseInt(CashShopServer.getIP().split(":")[1])));
        chr.saveToDB(false, false);
        chr.getMap().removePlayer(chr);
        c.setPlayer(null);
        c.setReceiving(false);
    }

    public static final void Loggedin(final int playerid, final MapleClient c) {
        final ChannelServer channelServer = c.getChannelServer();
        MapleCharacter player;
        final CharacterTransfer transfer =
                channelServer.getPlayerStorage().getPendingCharacter(playerid);

        if (transfer == null) {
            player = MapleCharacter.loadCharFromDB(playerid, c, true);
        } else {
            player = MapleCharacter.ReconstructChr(transfer, c, true);
        }
        if (player == null) {
            c.getSession().close();
            return;
        }
        c.setPlayer(player);
        c.setAccID(player.getAccountID());

        if (!c.CheckIPAddress()) {
            c.setPlayer(null);
            c.getSession().close();
            return;
        }

        final int state = c.getLoginState();
        boolean allowLogin = false;

        if (state == MapleClient.LOGIN_SERVER_TRANSITION || state == MapleClient.CHANGE_CHANNEL) {
            allowLogin = true;
        } else if (!World.isCharacterListConnected(c.loadCharacterNames(c.getWorld()))) {
            allowLogin = true;
        }

        if (!allowLogin) {
            c.setPlayer(null);
            c.getSession().close();
            return;
        }
        c.updateLoginState(MapleClient.LOGIN_LOGGEDIN, c.getSessionIPAddress());
        channelServer.addPlayer(player);

        c.sendPacket(MaplePacketCreator.getCharInfo(player));
        if (player.isGM()) {
            SkillFactory.getSkill(9001004).getEffect(1).applyTo(player);
        }
        c.sendPacket(MaplePacketCreator.temporaryStats_Reset());
        player.getMap().addPlayer(player);

        try {
            player.silentGiveBuffs(PlayerBuffStorage.getBuffsFromStorage(player.getId()));
            player.giveCoolDowns(PlayerBuffStorage.getCooldownsFromStorage(player.getId()));
            player.giveSilentDebuff(PlayerBuffStorage.getDiseaseFromStorage(player.getId()));

            // Start of buddylist
            final int buddyIds[] = player.getBuddylist().getBuddyIds();
            World.Buddy.loggedOn(
                    player.getName(),
                    player.getId(),
                    c.getChannel(),
                    buddyIds,
                    player.getGMLevel(),
                    player.isHidden());
            if (player.getParty() != null) {
                World.Party.updateParty(
                        player.getParty().getId(),
                        PartyOperation.LOG_ONOFF,
                        new MaplePartyCharacter(player));
            }
            final CharacterIdChannelPair[] onlineBuddies =
                    World.Find.multiBuddyFind(player.getId(), buddyIds);
            for (CharacterIdChannelPair onlineBuddy : onlineBuddies) {
                final BuddylistEntry ble = player.getBuddylist().get(onlineBuddy.getCharacterId());
                if (ble != null) {
                    ble.setChannel(onlineBuddy.getChannel());
                    player.getBuddylist().put(ble);
                }
            }
            c.sendPacket(MaplePacketCreator.updateBuddylist(player.getBuddylist().getBuddies()));

            // Start of Messenger
            final MapleMessenger messenger = player.getMessenger();
            if (messenger != null) {
                World.Messenger.silentJoinMessenger(
                        messenger.getId(), new MapleMessengerCharacter(c.getPlayer()));
                World.Messenger.updateMessenger(
                        messenger.getId(), c.getPlayer().getName(), c.getChannel());
            }

            // Start of Guild and alliance
            if (player.getGuildId() <= 0) {
                c.sendPacket(MaplePacketCreator.fuckGuildInfo(player));
            }
            if (player.getGuildId() > 0) {
                World.Guild.setGuildMemberOnline(player.getMGC(), true, c.getChannel());
                c.sendPacket(MaplePacketCreator.showGuildInfo(player));
                final MapleGuild gs = World.Guild.getGuild(player.getGuildId());
                if (gs != null) {
                    final List<MaplePacket> packetList =
                            World.Alliance.getAllianceInfo(gs.getAllianceId(), true);
                    if (packetList != null) {
                        for (MaplePacket pack : packetList) {
                            if (pack != null) {
                                c.sendPacket(pack);
                            }
                        }
                    }
                } else {
                    player.setGuildId(0);
                    player.setGuildRank((byte) 5);
                    player.setAllianceRank((byte) 5);
                    player.saveGuildStatus();
                }
            }
            if (player.getFamilyId() > 0) {
                World.Family.setFamilyMemberOnline(player.getMFC(), true, c.getChannel());
            }
            c.sendPacket(FamilyPacket.getFamilyInfo(player));
        } catch (Exception e) {
            LOG.error("Login_Error:", e);
        }
        c.sendPacket(FamilyPacket.getFamilyData());
        player.sendMacros();
        player.showNote();
        player.updatePartyMemberHP();
        player.startFairySchedule(false);

        c.sendPacket(MaplePacketCreator.getKeymap(player.getKeyLayout()));

        for (MapleQuestStatus status : player.getStartedQuests()) {
            if (status.hasMobKills()) {
                c.sendPacket(MaplePacketCreator.updateQuestMobKills(status));
            }
        }
        final CharacterNameAndId pendingBuddyRequest = player.getBuddylist().pollPendingRequest();
        if (pendingBuddyRequest != null) {
            player.getBuddylist()
                    .put(
                            new BuddylistEntry(
                                    pendingBuddyRequest.getName(),
                                    pendingBuddyRequest.getId(),
                                    "ETC",
                                    -1,
                                    false,
                                    pendingBuddyRequest.getLevel(),
                                    pendingBuddyRequest.getJob()));
            c.sendPacket(
                    MaplePacketCreator.requestBuddylistAdd(
                            pendingBuddyRequest.getId(),
                            pendingBuddyRequest.getName(),
                            pendingBuddyRequest.getLevel(),
                            pendingBuddyRequest.getJob()));
        }
        player.expirationTask();
        if (player.getJob() == 132) { // DARKKNIGHT
            player.checkBerserk();
        }
        player.spawnSavedPets();

        final HiredFishing fishing = World.hasFishing(player.getAccountID());
        if (fishing != null) {
            player.setPlayerFishing(fishing);
            player.startFishingTask(true, true);
        }
    }

    public static final void ChangeChannel(
            final SeekableLittleEndianAccessor slea,
            final MapleClient c,
            final MapleCharacter chr) {
        if (chr == null
                || !chr.isAlive()
                || chr.getEventInstance() != null
                || chr.getMap() == null
                || FieldLimitType.ChannelSwitch.check(chr.getMap().getFieldLimit())) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        chr.changeChannel(slea.readByte() + 1);
    }
}