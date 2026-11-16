package handling.channel.handler;

import client.MapleCharacter;
import client.MapleClient;
import client.MapleDisease;
import java.util.List;
import server.MapleCarnivalFactory;
import server.MapleCarnivalFactory.MCSkill;
import server.Randomizer;
import server.life.MapleLifeFactory;
import server.life.MapleMonster;
import tools.MaplePacketCreator;
import tools.Pair;
import tools.data.input.SeekableLittleEndianAccessor;
import tools.packet.MonsterCarnivalPacket;

public class MonsterCarnivalHandler {

    public static final void MonsterCarnival(
            final SeekableLittleEndianAccessor slea, final MapleClient c) {
        final MapleCharacter player = c.getPlayer();
        if (player == null || player.getCarnivalParty() == null) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        final int tab = slea.readByte();
        final int num = slea.readInt();

        if (tab == 0) {
            final List<Pair<Integer, Integer>> mobs = player.getMap().getMobsToSpawn();
            if (mobs == null
                    || num < 0
                    || num >= mobs.size()
                    || player.getAvailableCP() < mobs.get(num).right()) {
                player.dropMessage(5, "You do not have the CP.");
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            final MapleMonster mons = MapleLifeFactory.getMonster(mobs.get(num).left());
            if (mons != null
                    && player.getMap()
                            .makeCarnivalSpawn(player.getCarnivalParty().getTeam(), mons, num)) {
                player.getCarnivalParty().useCP(player, mobs.get(num).right());
                player.CPUpdate(false, player.getAvailableCP(), player.getTotalCP(), 0);
                final int partyAvail = player.getCarnivalParty().getAvailableCP();
                final int partyTotal = player.getCarnivalParty().getTotalCP();
                final int partyTeam = player.getCarnivalParty().getTeam();
                for (MapleCharacter chr : player.getMap().getCharactersThreadsafe()) {
                    chr.CPUpdate(true, partyAvail, partyTotal, partyTeam);
                }
                player.getMap()
                        .broadcastMessage(
                                MonsterCarnivalPacket.playerSummoned(player.getName(), tab, num));
                c.sendPacket(MaplePacketCreator.enableActions());
            } else {
                player.dropMessage(5, "You may no longer summon the monster.");
                c.sendPacket(MaplePacketCreator.enableActions());
            }

        } else if (tab == 1) { // debuff
            final List<Integer> skillid = player.getMap().getSkillIds();
            if (skillid == null || num < 0 || num >= skillid.size()) {
                player.dropMessage(5, "An error occurred.");
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            final MCSkill skil = MapleCarnivalFactory.getInstance().getSkill(skillid.get(num));
            if (skil == null || player.getAvailableCP() < skil.cpLoss) {
                player.dropMessage(5, "You do not have the CP.");
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            final MapleDisease dis = skil.getDisease();
            final int myPartyId = player.getParty() != null ? player.getParty().getId() : -1;
            boolean found = false;
            for (MapleCharacter chr : player.getMap().getCharactersThreadsafe()) {
                if (chr.getParty() == null
                        || (myPartyId != -1 && chr.getParty().getId() != myPartyId)) {
                    if (skil.targetsAll || Randomizer.nextBoolean()) {
                        found = true;
                        if (dis == null) {
                            chr.dispel();
                        } else if (skil.getMobSkill() == null) {
                            chr.giveDebuff(dis, 1, 30000, MapleDisease.getByDisease(dis), 1);
                        } else {
                            chr.giveDebuff(dis, skil.getMobSkill());
                        }
                        if (!skil.targetsAll) {
                            break;
                        }
                    }
                }
            }
            if (found) {
                player.getCarnivalParty().useCP(player, skil.cpLoss);
                player.CPUpdate(false, player.getAvailableCP(), player.getTotalCP(), 0);
                final int partyAvail = player.getCarnivalParty().getAvailableCP();
                final int partyTotal = player.getCarnivalParty().getTotalCP();
                final int partyTeam = player.getCarnivalParty().getTeam();
                for (MapleCharacter chr : player.getMap().getCharactersThreadsafe()) {
                    chr.CPUpdate(true, partyAvail, partyTotal, partyTeam);
                }
                player.getMap()
                        .broadcastMessage(
                                MonsterCarnivalPacket.playerSummoned(player.getName(), tab, num));
                c.sendPacket(MaplePacketCreator.enableActions());
            } else {
                player.dropMessage(5, "An error occurred.");
                c.sendPacket(MaplePacketCreator.enableActions());
            }
        } else if (tab == 2) { // skill
            final MCSkill skil = MapleCarnivalFactory.getInstance().getGuardian(num);
            if (skil == null || player.getAvailableCP() < skil.cpLoss) {
                player.dropMessage(5, "You do not have the CP.");
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            if (player.getMap().makeCarnivalReactor(player.getCarnivalParty().getTeam(), num)) {
                player.getCarnivalParty().useCP(player, skil.cpLoss);
                player.CPUpdate(false, player.getAvailableCP(), player.getTotalCP(), 0);
                final int partyAvail = player.getCarnivalParty().getAvailableCP();
                final int partyTotal = player.getCarnivalParty().getTotalCP();
                final int partyTeam = player.getCarnivalParty().getTeam();
                for (MapleCharacter chr : player.getMap().getCharactersThreadsafe()) {
                    chr.CPUpdate(true, partyAvail, partyTotal, partyTeam);
                }
                player.getMap()
                        .broadcastMessage(
                                MonsterCarnivalPacket.playerSummoned(player.getName(), tab, num));
                c.sendPacket(MaplePacketCreator.enableActions());
            } else {
                player.dropMessage(5, "You may no longer summon the being.");
                c.sendPacket(MaplePacketCreator.enableActions());
            }
        }
    }
}
