package handling.channel.handler;

import client.MapleCharacter;
import client.MapleCharacterUtil;
import client.MapleClient;
import handling.world.MaplePartyCharacter;
import handling.world.World;
import handling.world.family.MapleFamily;
import handling.world.family.MapleFamilyBuff;
import handling.world.family.MapleFamilyBuff.MapleFamilyBuffEntry;
import handling.world.family.MapleFamilyCharacter;
import java.util.List;
import server.maps.FieldLimitType;
import tools.MaplePacketCreator;
import tools.data.input.SeekableLittleEndianAccessor;
import tools.packet.FamilyPacket;

public class FamilyHandler {

    public static final void RequestFamily(final SeekableLittleEndianAccessor slea, MapleClient c) {
        final MapleCharacter chr =
                c.getChannelServer()
                        .getPlayerStorage()
                        .getCharacterByName(slea.readMapleAsciiString());
        if (chr != null) {
            c.sendPacket(FamilyPacket.getFamilyPedigree(chr));
        }
    }

    public static final void OpenFamily(final SeekableLittleEndianAccessor slea, MapleClient c) {
        c.sendPacket(FamilyPacket.getFamilyInfo(c.getPlayer()));
    }

    public static final void UseFamily(final SeekableLittleEndianAccessor slea, MapleClient c) {
        final int type = slea.readInt();
        final MapleFamilyBuffEntry entry = MapleFamilyBuff.getBuffEntry(type);
        if (entry == null) {
            return;
        }
        final MapleCharacter player = c.getPlayer();
        if (player == null) {
            return;
        }
        boolean success =
                player.getFamilyId() > 0
                        && player.canUseFamilyBuff(entry)
                        && player.getCurrentRep() > entry.rep;
        if (!success) {
            return;
        }
        MapleCharacter victim = null;
        switch (type) {
            case 0: // teleport: need add check for if not a safe place
                victim =
                        c.getChannelServer()
                                .getPlayerStorage()
                                .getCharacterByName(slea.readMapleAsciiString());
                if (FieldLimitType.VipRock.check(player.getMap().getFieldLimit())
                        || !player.isAlive()) {
                    player.dropMessage(5, "召喚失敗，你現在的地圖或狀態無法允許召喚");
                    success = false;
                } else if (victim == null || (victim.isGM() && !player.isGM())) {
                    player.dropMessage(1, "錯誤的名稱(不在相同頻道?)");
                    success = false;
                } else if (victim.getFamilyId() == player.getFamilyId()
                        && !FieldLimitType.VipRock.check(victim.getMap().getFieldLimit())
                        && victim.getId() != player.getId()) {
                    player.changeMap(victim.getMap(), victim.getMap().getPortal(0));
                } else {
                    player.dropMessage(5, "召喚失敗，你現在的地圖或狀態無法允許召喚");
                    success = false;
                }
                break;
            case 1: // TODO give a check to the player being forced somewhere else..
                victim =
                        c.getChannelServer()
                                .getPlayerStorage()
                                .getCharacterByName(slea.readMapleAsciiString());
                if (FieldLimitType.VipRock.check(player.getMap().getFieldLimit())
                        || !player.isAlive()) {
                    player.dropMessage(5, "召喚失敗，你現在的地圖或狀態無法允許召喚");
                } else if (victim == null || (victim.isGM() && !player.isGM())) {
                    player.dropMessage(1, "錯誤的名稱(不在相同頻道?)");
                } else if (victim.getTeleportName().length() > 0) {
                    player.dropMessage(1, "已經有其他玩家請求傳送");
                } else if (victim.getFamilyId() == player.getFamilyId()
                        && !FieldLimitType.VipRock.check(victim.getMap().getFieldLimit())
                        && victim.getId() != player.getId()) {
                    victim.getClient()
                            .sendPacket(
                                    FamilyPacket.familySummonRequest(
                                            player.getName(), player.getMap().getMapName()));
                    victim.setTeleportName(player.getName());
                } else {
                    player.dropMessage(5, "召喚失敗，你現在的地圖或狀態無法允許召喚");
                }
                return; // RETURN not break
            case 4: // 6 family members in pedigree online Drop Rate & Exp Rate + 100% 30 minutes
                final MapleFamily fam = World.Family.getFamily(player.getFamilyId());
                if (fam == null) {
                    success = false;
                    break;
                }
                final MapleFamilyCharacter selfMfc = fam.getMFC(player.getId());
                if (selfMfc == null) {
                    success = false;
                    break;
                }
                final List<MapleFamilyCharacter> chrs = selfMfc.getOnlineJuniors(fam);
                if (chrs.size() < 7) {
                    success = false;
                } else {
                    for (MapleFamilyCharacter chrz : chrs) {
                        final int chrChannel = World.Find.findChannel(chrz.getId());
                        if (chrChannel == -1) {
                            continue; // STOP WTF?! take reps though..
                        }
                        final MapleCharacter chrr =
                                World.getStorage(chrChannel).getCharacterById(chrz.getId());
                        if (chrr != null) {
                            entry.applyTo(chrr);
                        }
                        // chrr.getClient().sendPacket(FamilyPacket.familyBuff(entry.type, type,
                        // entry.effect,
                        // entry.duration*60000));
                    }
                }
                break;

            case 2: // drop rate + 50% 15 min
            case 3: // exp rate + 50% 15 min
            case 5: // drop rate + 100% 15 min
            case 6: // exp rate + 100% 15 min
            case 7: // drop rate + 100% 30 min
            case 8: // exp rate + 100% 30 min
                // c.sendPacket(FamilyPacket.familyBuff(entry.type, type, entry.effect,
                // entry.duration*60000));
                entry.applyTo(player);
                break;
            case 9: // drop rate + 100% party 30 min
            case 10: // exp rate + 100% party 30 min
                entry.applyTo(player);
                // c.sendPacket(FamilyPacket.familyBuff(entry.type, type, entry.effect,
                // entry.duration*60000));
                if (player.getParty() != null) {
                    for (MaplePartyCharacter mpc : player.getParty().getMembers()) {
                        if (mpc.getId() != player.getId()) {
                            final MapleCharacter chr =
                                    player.getMap().getCharacterById(mpc.getId());
                            if (chr != null) {
                                entry.applyTo(chr);
                                // chr.getClient().sendPacket(FamilyPacket.familyBuff(entry.type,
                                // type,
                                // entry.effect, entry.duration*60000));
                            }
                        }
                    }
                }
                break;
        }
        if (success) { // again
            player.setCurrentRep(player.getCurrentRep() - entry.rep);
            c.sendPacket(FamilyPacket.changeRep(-entry.rep));
            player.useFamilyBuff(entry);
        } else {
            player.dropMessage(5, "An error occured.");
        }
    }

    public static final void FamilyOperation(
            final SeekableLittleEndianAccessor slea, MapleClient c) {
        final MapleCharacter player = c.getPlayer();
        if (player == null) {
            return;
        }
        final MapleCharacter addChr =
                c.getChannelServer()
                        .getPlayerStorage()
                        .getCharacterByName(slea.readMapleAsciiString());
        if (addChr == null) {
            player.dropMessage(1, "該玩家不存在或者不再線上");
        } else if (addChr.getFamilyId() == player.getFamilyId() && addChr.getFamilyId() > 0) {
            player.dropMessage(1, "你們已經是同一個家族了");
        } else if (addChr.getMapId() != player.getMapId()) {
            player.dropMessage(1, "跟隨者必須在同地圖");
        } else if (addChr.getSeniorId() != 0) {
            player.dropMessage(1, "該玩家已經有家族");
        } else if (addChr.getLevel() >= player.getLevel()) {
            player.dropMessage(1, "該玩家等級高過你");
        } else if (addChr.getLevel() < player.getLevel() - 20) {
            player.dropMessage(1, "雙方等級差異超過二十");
            // } else if (player.getFamilyId() != 0 && player.getFamily().getGens() >= 1000) {
            //	player.dropMessage(5, "Your family cannot extend more than 1000 generations from
            // above and
            // below.");
        } else if (addChr.getLevel() < 10) {
            player.dropMessage(1, "跟隨者等級必須在10級以上");
        } else if (player.getJunior1() > 0 && player.getJunior2() > 0) {
            player.dropMessage(1, "你已經有兩個跟隨者");
        } else if (player.isGM() || !addChr.isGM()) {
            addChr.getClient()
                    .sendPacket(
                            FamilyPacket.sendFamilyInvite(
                                    player.getId(),
                                    player.getLevel(),
                                    player.getJob(),
                                    player.getName()));
        }
        c.sendPacket(MaplePacketCreator.enableActions());
    }

    public static final void FamilyPrecept(final SeekableLittleEndianAccessor slea, MapleClient c) {
        final MapleCharacter player = c.getPlayer();
        if (player == null) {
            return;
        }
        final MapleFamily fam = World.Family.getFamily(player.getFamilyId());
        if (fam == null || fam.getLeaderId() != player.getId()) {
            return;
        }
        // fam.setNotice(slea.readMapleAsciiString());
        fam.setNotice(slea.readMapleAsciiString());
        player.dropMessage(1, "重開家族視窗即可套用.");
    }

    public static final void FamilySummon(final SeekableLittleEndianAccessor slea, MapleClient c) {
        final int TYPE = 1; // the type of the summon request.
        final MapleFamilyBuffEntry cost = MapleFamilyBuff.getBuffEntry(TYPE);
        final MapleCharacter player = c.getPlayer();
        if (player == null) {
            return;
        }
        final MapleCharacter tt =
                c.getChannelServer()
                        .getPlayerStorage()
                        .getCharacterByName(slea.readMapleAsciiString());
        if (player.getFamilyId() > 0
                && tt != null
                && tt.getFamilyId() == player.getFamilyId()
                && !FieldLimitType.VipRock.check(tt.getMap().getFieldLimit())
                && !FieldLimitType.VipRock.check(player.getMap().getFieldLimit())
                && player.isAlive()
                && tt.isAlive()
                && tt.canUseFamilyBuff(cost)
                && player.getTeleportName().equals(tt.getName())
                && tt.getCurrentRep() > cost.rep
                && player.getEventInstance() == null
                && tt.getEventInstance() == null) {
            // whew lots of checks
            final boolean accepted = slea.readByte() > 0;
            if (accepted) {
                player.changeMap(tt.getMap(), tt.getMap().getPortal(0));
                tt.setCurrentRep(tt.getCurrentRep() - cost.rep);
                tt.getClient().sendPacket(FamilyPacket.changeRep(-cost.rep));
                tt.useFamilyBuff(cost);
            } else {
                tt.dropMessage(5, "召喚失敗，你現在的地圖或狀態無法允許召喚");
            }
        } else {
            player.dropMessage(5, "召喚失敗，你現在的地圖或狀態無法允許召喚");
        }
        player.setTeleportName("");
    }

    public static final void DeleteJunior(final SeekableLittleEndianAccessor slea, MapleClient c) {
        final int juniorid = slea.readInt();
        final MapleCharacter player = c.getPlayer();
        if (player == null) {
            return;
        }
        if (player.getFamilyId() <= 0
                || juniorid <= 0
                || (player.getJunior1() != juniorid && player.getJunior2() != juniorid)) {
            return;
        }
        // junior is not required to be online.
        final MapleFamily fam = World.Family.getFamily(player.getFamilyId());
        if (fam == null) {
            return;
        }
        final MapleFamilyCharacter other = fam.getMFC(juniorid);
        if (other == null) {
            return;
        }
        final MapleFamilyCharacter oth = player.getMFC();
        if (oth == null) {
            return;
        }
        final boolean junior2 = oth.getJunior2() == juniorid;
        if (junior2) {
            oth.setJunior2(0);
        } else {
            oth.setJunior1(0);
        }
        player.saveFamilyStatus();
        other.setSeniorId(0);
        // if (!other.isOnline()) {
        MapleFamily.setOfflineFamilyStatus(
                other.getFamilyId(),
                other.getSeniorId(),
                other.getJunior1(),
                other.getJunior2(),
                other.getCurrentRep(),
                other.getTotalRep(),
                other.getId());
        // }
        MapleCharacterUtil.sendNote(
                other.getName(), player.getName(), player.getName() + " 解散了家族", 0);
        if (!fam.splitFamily(
                juniorid,
                other)) { // juniorid splits to make their own family. function should handle the
            // rest
            if (!junior2) {
                fam.resetDescendants();
            }
            fam.resetPedigree();
        }
        player.dropMessage(1, "踢出了 (" + other.getName() + ").");
        c.sendPacket(MaplePacketCreator.enableActions());
    }

    public static final void DeleteSenior(final SeekableLittleEndianAccessor slea, MapleClient c) {
        final MapleCharacter player = c.getPlayer();
        if (player == null) {
            return;
        }
        if (player.getFamilyId() <= 0 || player.getSeniorId() <= 0) {
            return;
        }
        // not required to be online
        final MapleFamily fam = World.Family.getFamily(player.getFamilyId()); // this is old family
        if (fam == null) {
            return;
        }
        final MapleFamilyCharacter mgc = fam.getMFC(player.getSeniorId());
        if (mgc == null) {
            return;
        }
        final MapleFamilyCharacter mgc_ = player.getMFC();
        if (mgc_ == null) {
            return;
        }
        mgc_.setSeniorId(0);
        final boolean junior2 = mgc.getJunior2() == player.getId();
        if (junior2) {
            mgc.setJunior2(0);
        } else {
            mgc.setJunior1(0);
        }
        // if (!mgc.isOnline()) {
        MapleFamily.setOfflineFamilyStatus(
                mgc.getFamilyId(),
                mgc.getSeniorId(),
                mgc.getJunior1(),
                mgc.getJunior2(),
                mgc.getCurrentRep(),
                mgc.getTotalRep(),
                mgc.getId());
        // }
        player.saveFamilyStatus();
        MapleCharacterUtil.sendNote(
                mgc.getName(), player.getName(), player.getName() + " 離開你的家族", 0);
        if (!fam.splitFamily(player.getId(), mgc_)) { // now, we're the family leader
            if (!junior2) {
                fam.resetDescendants();
            }
            fam.resetPedigree();
        }
        player.dropMessage(1, "退出了 (" + mgc.getName() + ") 的家族.");
        c.sendPacket(MaplePacketCreator.enableActions());
    }

    public static final void AcceptFamily(SeekableLittleEndianAccessor slea, MapleClient c) {
        final MapleCharacter player = c.getPlayer();
        if (player == null) {
            return;
        }
        final MapleCharacter inviter = player.getMap().getCharacterById(slea.readInt());
        if (inviter != null
                && player.getSeniorId() == 0
                && (player.isGM() || !inviter.isHidden())
                && inviter.getLevel() - 20 <= player.getLevel()
                && inviter.getLevel() >= 10
                && inviter.getName().equals(slea.readMapleAsciiString())
                && inviter.getNoJuniors() < 2 /*&& inviter.getFamily().getGens() < 1000*/
                && player.getLevel() >= 10) {
            final boolean accepted = slea.readByte() > 0;
            inviter.getClient()
                    .sendPacket(FamilyPacket.sendFamilyJoinResponse(accepted, player.getName()));
            if (accepted) {
                // c.sendPacket(FamilyPacket.sendFamilyMessage(0));
                c.sendPacket(FamilyPacket.getSeniorMessage(inviter.getName()));
                final int old = player.getMFC() == null ? 0 : player.getMFC().getFamilyId();
                final int oldj1 = player.getMFC() == null ? 0 : player.getMFC().getJunior1();
                final int oldj2 = player.getMFC() == null ? 0 : player.getMFC().getJunior2();
                final MapleFamily fam =
                        inviter.getFamilyId() > 0
                                ? World.Family.getFamily(inviter.getFamilyId())
                                : null;
                if (fam != null) {
                    // if old isn't null, don't set the familyid yet, mergeFamily will take care of
                    // it
                    player.setFamily(
                            old <= 0 ? inviter.getFamilyId() : old,
                            inviter.getId(),
                            oldj1 <= 0 ? 0 : oldj1,
                            oldj2 <= 0 ? 0 : oldj2);
                    final MapleFamilyCharacter mf = inviter.getMFC();
                    if (mf != null) {
                        if (mf.getJunior1() > 0) {
                            mf.setJunior2(player.getId());
                        } else {
                            mf.setJunior1(player.getId());
                        }
                        inviter.saveFamilyStatus();
                    }
                    if (old > 0 && World.Family.getFamily(old) != null) { // has junior
                        MapleFamily.mergeFamily(fam, World.Family.getFamily(old));
                    } else {
                        player.setFamily(
                                inviter.getFamilyId(),
                                inviter.getId(),
                                oldj1 <= 0 ? 0 : oldj1,
                                oldj2 <= 0 ? 0 : oldj2);
                        fam.setOnline(player.getId(), true, c.getChannel());
                        player.saveFamilyStatus();
                    }
                    if (inviter.getNoJuniors() == 1
                            || old > 0) { // just got their first junior whoopee
                        fam.resetDescendants();
                    }
                    fam.resetPedigree(); // is this necessary?
                } else {
                    final int id = MapleFamily.createFamily(inviter.getId());
                    if (id > 0) {
                        // before loading the family, set sql
                        MapleFamily.setOfflineFamilyStatus(
                                id,
                                0,
                                player.getId(),
                                0,
                                inviter.getCurrentRep(),
                                inviter.getTotalRep(),
                                inviter.getId());
                        MapleFamily.setOfflineFamilyStatus(
                                id,
                                inviter.getId(),
                                oldj1 <= 0 ? 0 : oldj1,
                                oldj2 <= 0 ? 0 : oldj2,
                                player.getCurrentRep(),
                                player.getTotalRep(),
                                player.getId());
                        inviter.setFamily(id, 0, player.getId(), 0); // load the family
                        player.setFamily(
                                id,
                                inviter.getId(),
                                oldj1 <= 0 ? 0 : oldj1,
                                oldj2 <= 0 ? 0 : oldj2);
                        final MapleFamily newFam = World.Family.getFamily(id);
                        if (newFam != null) {
                            newFam.setOnline(
                                    inviter.getId(), true, inviter.getClient().getChannel());
                            if (old > 0 && World.Family.getFamily(old) != null) { // has junior
                                MapleFamily.mergeFamily(newFam, World.Family.getFamily(old));
                            } else {
                                newFam.setOnline(player.getId(), true, c.getChannel());
                            }
                            newFam.resetDescendants();
                            newFam.resetPedigree();
                        }
                    }
                }
                c.sendPacket(FamilyPacket.getFamilyInfo(player));
            }
        }
    }
}
