package server.maps;

import client.MapleCharacter;
import client.MapleClient;
import client.MapleQuestStatus;
import client.SkillFactory;
import java.awt.Point;
import scripting.EventManager;
import scripting.NPCScriptManager;
import server.MapleItemInformationProvider;
import server.Randomizer;
import server.life.MapleLifeFactory;
import server.life.MapleMonster;
import server.quest.MapleQuest;
import server.quest.MapleQuest.MedalQuest;
import tools.MaplePacketCreator;
import tools.packet.UIPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapScriptMethods {
    private static final Logger LOG = LoggerFactory.getLogger(MapScriptMethods.class);

    private static final Point witchTowerPos = new Point(-60, 184);
    private static final String[] mulungEffects = {
        "我等著你，只要你持之以恆，必能走向正確的道路",
        "何來的勇氣挑戰武陵!",
        "我會讓你後悔挑戰武陵道館!",
        "我喜歡你的直性格! 但不是你的魯莽衝動!",
        "如果你真的那麼想要失敗的話，就繼續往前吧!"
    };

    private static enum onFirstUserEnter {
        dojang_Eff,
        PinkBeen_before,
        onRewordMap,
        StageMsg_together,
        StageMsg_davy,
        party6weatherMsg,
        StageMsg_juliet,
        StageMsg_romio,
        moonrabbit_mapEnter,
        astaroth_summon,
        boss_Ravana,
        killing_BonusSetting,
        killing_MapSetting,
        metro_firstSetting,
        balog_bonusSetting,
        balog_summon,
        easy_balog_summon,
        Sky_TrapFEnter,
        shammos_Fenter,
        PRaid_D_Fenter,
        PRaid_B_Fenter,
        GhostF,
        NULL;

        private static onFirstUserEnter fromString(String Str) {
            try {
                return valueOf(Str);
            } catch (IllegalArgumentException ex) {
                return NULL;
            }
        }
    }

    private static enum onUserEnter {
        babyPigMap,
        crash_Dragon,
        evanleaveD,
        getDragonEgg,
        meetWithDragon,
        go1010100,
        go1010200,
        go1010300,
        go1010400,
        evanPromotion,
        PromiseDragon,
        evanTogether,
        incubation_dragon,
        TD_MC_Openning,
        TD_MC_gasi,
        TD_MC_title,
        cygnusJobTutorial,
        cygnusTest,
        startEreb,
        dojang_Msg,
        dojang_1st,
        reundodraco,
        undomorphdarco,
        explorationPoint,
        goAdventure,
        go10000,
        go20000,
        go30000,
        go40000,
        go50000,
        go1000000,
        go1010000,
        go1020000,
        go2000000,
        goArcher,
        goPirate,
        goRogue,
        goMagician,
        goSwordman,
        goLith,
        iceCave,
        mirrorCave,
        aranDirection,
        rienArrow,
        rien,
        check_count,
        Massacre_first,
        Massacre_result,
        aranTutorAlone,
        evanAlone,
        dojang_QcheckSet,
        Sky_StageEnter,
        outCase,
        balog_buff,
        balog_dateSet,
        Sky_BossEnter,
        Sky_GateMapEnter,
        shammos_Enter,
        shammos_Result,
        shammos_Base,
        dollCave00,
        dollCave01,
        Sky_Quest,
        enterBlackfrog,
        onSDI,
        blackSDI,
        summonIceWall,
        metro_firstSetting,
        start_itemTake,
        PRaid_D_Enter,
        PRaid_B_Enter,
        PRaid_Revive,
        PRaid_W_Enter,
        PRaid_WinEnter,
        PRaid_FailEnter,
        Ghost,
        NULL;

        private static onUserEnter fromString(String Str) {
            try {
                return valueOf(Str);
            } catch (IllegalArgumentException ex) {
                return NULL;
            }
        }
    }

    public static void startScript_FirstUser(MapleClient c, String scriptName) {
        final MapleCharacter player = c.getPlayer();
        if (player == null) {
            return;
        }
        final MapleMap map = player.getMap();
        final int mapId = player.getMapId();

        switch (onFirstUserEnter.fromString(scriptName)) {
            case dojang_Eff:
                {
                    int temp = (mapId - 925000000) / 100;
                    int stage = (int) (temp - ((temp / 100) * 100));

                    sendDojoClock(c, getTiming(stage) * 60);
                    sendDojoStart(c, stage - getDojoStageDec(stage));
                    break;
                }
            case PinkBeen_before:
                {
                    handlePinkBeanStart(c);
                    break;
                }
            case onRewordMap:
                {
                    reloadWitchTower(c);
                    break;
                }
            case GhostF:
                {
                    map.startMapEffect("這個地圖感覺陰森森的..有種莫名的奇怪感覺..", 5120025);
                    break;
                }
            // 5120018 = ludi(none), 5120019 = orbis(start_itemTake - onUser)
            case moonrabbit_mapEnter:
                {
                    map.startMapEffect("粥環繞月球的月見草種子和保護月球兔子！", 5120016);
                    break;
                }
            /*            case StageMsg_together: {
            switch (c.getPlayer().getMapId()) {
            case 103000800:
            c.getPlayer().getMap().startMapEffect("Solve the question and gather the amount of passes!", 5120017);
            break;
            case 103000801:
            c.getPlayer().getMap().startMapEffect("Get on the ropes and unveil the correct combination!", 5120017);
            break;
            case 103000802:
            c.getPlayer().getMap().startMapEffect("Get on the platforms and unveil the correct combination!", 5120017);
            break;
            case 103000803:
            c.getPlayer().getMap().startMapEffect("Get on the barrels and unveil the correct combination!", 5120017);
            break;
            case 103000804:
            c.getPlayer().getMap().startMapEffect("Defeat King Slime and his minions!", 5120017);
            break;
            }
            break;
            }*/
            case StageMsg_romio:
                {
                    switch (mapId) {
                        case 926100000:
                            map.startMapEffect(
                                    "Please find the hidden door by investigating the Lab!",
                                    5120021);
                            break;
                        case 926100001:
                            map.startMapEffect("Find  your way through this darkness!", 5120021);
                            break;
                        case 926100100:
                            map.startMapEffect("Fill the beakers to power the energy!", 5120021);
                            break;
                        case 926100200:
                            map.startMapEffect(
                                    "Get the files for the experiment through each door!", 5120021);
                            break;
                        case 926100203:
                            map.startMapEffect("Please defeat all the monsters!", 5120021);
                            break;
                        case 926100300:
                            map.startMapEffect("Find your way through the Lab!", 5120021);
                            break;
                        case 926100401:
                            map.startMapEffect("Please, protect my love!", 5120021);

                            break;
                    }
                    break;
                }
            case StageMsg_juliet:
                {
                    switch (mapId) {
                        case 926110000:
                            map.startMapEffect(
                                    "Please find the hidden door by investigating the Lab!",
                                    5120022);
                            break;
                        case 926110001:
                            map.startMapEffect("Find  your way through this darkness!", 5120022);
                            break;
                        case 926110100:
                            map.startMapEffect("Fill the beakers to power the energy!", 5120022);
                            break;
                        case 926110200:
                            map.startMapEffect(
                                    "Get the files for the experiment through each door!", 5120022);
                            break;
                        case 926110203:
                            map.startMapEffect("Please defeat all the monsters!", 5120022);
                            break;
                        case 926110300:
                            map.startMapEffect("Find your way through the Lab!", 5120022);
                            break;
                        case 926110401:
                            map.startMapEffect("Please, protect my love!", 5120022);
                            break;
                    }
                    break;
                }
            case party6weatherMsg:
                {
                    switch (mapId) {
                        case 930000000:
                            map.startMapEffect("進入傳送點，我要對你們施放變身魔法了！", 5120023);
                            break;
                        case 930000100:
                            map.startMapEffect("消滅所有怪物！", 5120023);
                            break;
                        case 930000200:
                            map.startMapEffect("對荊棘施放稀釋的毒液4個！", 5120023);
                            break;
                        case 930000300:
                            map.startMapEffect("我迷路了...", 5120023);
                            break;
                        case 930000400:
                            map.startMapEffect("拿淨化之珠後拿10個怪物株給我！", 5120023);
                            break;
                        case 930000500:
                            map.startMapEffect("從怪人書桌中尋找紫色魔力石！", 5120023);
                            break;
                        case 930000600:
                            map.startMapEffect("將紫色魔力石放在祭壇上！", 5120023);
                            break;
                    }
                    break;
                }
            case StageMsg_davy:
                {
                    switch (mapId) {
                        case 925100000:
                            map.startMapEffect(
                                    "Defeat the monsters outside of the ship to advance!", 5120020);
                            break;
                        case 925100100:
                            map.startMapEffect(
                                    "We must prove ourselves! Get me Pirate Medals!", 5120020);
                            break;
                        case 925100200:
                            map.startMapEffect("Defeat the guards here to pass!", 5120020);
                            break;
                        case 925100300:
                            map.startMapEffect("Eliminate the guards here to pass!", 5120020);
                            break;
                        case 925100400:
                            map.startMapEffect(
                                    "Lock the doors! Seal the root of the Ship's power!", 5120020);
                            break;
                        case 925100500:
                            map.startMapEffect("Destroy the Lord Pirate!", 5120020);
                            break;
                    }
                    final EventManager em =
                            c.getChannelServer().getEventSM().getEventManager("Pirate");
                    if (mapId == 925100500 && em != null && em.getProperty("stage5") != null) {
                        int mobId = Randomizer.nextBoolean() ? 9300119 : 9300119; // lord pirate
                        final int st = Integer.parseInt(em.getProperty("stage5"));
                        switch (st) {
                            case 1:
                                mobId = /*Randomizer.nextBoolean() ? 9300119 : */ 9300105; // angry
                                break;
                            case 2:
                                mobId = /*Randomizer.nextBoolean() ? */
                                        9300106 /* : 9300105*/; // enraged
                                break;
                        }
                        final MapleMonster shammos = MapleLifeFactory.getMonster(mobId);
                        if (player.getEventInstance() != null) {
                            player.getEventInstance().registerMonster(shammos);
                        }
                        map.spawnMonsterOnGroundBelow(shammos, new Point(411, 236));
                    }
                    break;
                }
            case astaroth_summon:
                {
                    map.resetFully();
                    map.spawnMonsterOnGroundBelow(
                            MapleLifeFactory.getMonster(9400633),
                            new Point(600, -26)); // rough estimate
                    break;
                }
            case boss_Ravana:
                { // event handles this so nothing for now until i find out something to do with it
                    map.broadcastMessage(
                            MaplePacketCreator.serverNotice(5, "Ravana has appeared!"));
                    break;
                }
            case killing_BonusSetting:
                { // spawns monsters according to mapid
                    // 910320010-910320029 = Train 999 bubblings.
                    // 926010010-926010029 = 30 Yetis
                    // 926010030-926010049 = 35 Yetis
                    // 926010050-926010069 = 40 Yetis
                    // 926010070-926010089 - 50 Yetis (specialized? immortality)
                    // TODO also find positions to spawn these at
                    map.resetFully();
                    c.sendPacket(MaplePacketCreator.showEffect("killing/bonus/bonus"));
                    c.sendPacket(MaplePacketCreator.showEffect("killing/bonus/stage"));
                    Point pos1 = null, pos2 = null, pos3 = null;
                    int spawnPer = 0;
                    int mobId = 0;
                    // 9700019, 9700029
                    // 9700021 = one thats invincible
                    if (mapId >= 910320010 && mapId <= 910320029) {
                        pos1 = new Point(121, 218);
                        pos2 = new Point(396, 43);
                        pos3 = new Point(-63, 43);
                        mobId = 9700020;
                        spawnPer = 10;
                    } else if (mapId >= 926010010 && mapId <= 926010029) {
                        pos1 = new Point(0, 88);
                        pos2 = new Point(-326, -115);
                        pos3 = new Point(361, -115);
                        mobId = 9700019;
                        spawnPer = 10;
                    } else if (mapId >= 926010030 && mapId <= 926010049) {
                        pos1 = new Point(0, 88);
                        pos2 = new Point(-326, -115);
                        pos3 = new Point(361, -115);
                        mobId = 9700019;
                        spawnPer = 15;
                    } else if (mapId >= 926010050 && mapId <= 926010069) {
                        pos1 = new Point(0, 88);
                        pos2 = new Point(-326, -115);
                        pos3 = new Point(361, -115);
                        mobId = 9700019;
                        spawnPer = 20;
                    } else if (mapId >= 926010070 && mapId <= 926010089) {
                        pos1 = new Point(0, 88);
                        pos2 = new Point(-326, -115);
                        pos3 = new Point(361, -115);
                        mobId = 9700029;
                        spawnPer = 20;
                    } else {
                        break;
                    }
                    for (int i = 0; i < spawnPer; i++) {
                        map.spawnMonsterOnGroundBelow(
                                MapleLifeFactory.getMonster(mobId), new Point(pos1));
                        map.spawnMonsterOnGroundBelow(
                                MapleLifeFactory.getMonster(mobId), new Point(pos2));
                        map.spawnMonsterOnGroundBelow(
                                MapleLifeFactory.getMonster(mobId), new Point(pos3));
                    }
                    player.startMapTimeLimitTask(120, map.getReturnMap());
                    break;
                }
            case shammos_Fenter:
                {
                    if (mapId >= 921120100 && mapId < 921120500) {
                        final MapleMonster shammos = MapleLifeFactory.getMonster(9300275);
                        if (player.getEventInstance() != null) {
                            player.getEventInstance().registerMonster(shammos);
                            if (player.getEventInstance().getProperty("HP") != null) {
                                shammos.setHp(
                                        Long.parseLong(
                                                player.getEventInstance().getProperty("HP")));
                            } else {
                                player.getEventInstance().setProperty("HP", "50000");
                            }
                        }
                        map.spawnMonsterWithEffectBelow(
                                shammos, new Point(map.getPortal(0).getPosition()), 12);
                        shammos.switchController(player, false);
                        c.sendPacket(MaplePacketCreator.getNodeProperties(shammos, map));
                    }
                    break;
                }
            case PRaid_D_Fenter:
                {
                    switch (mapId % 10) {
                        case 0:
                            map.startMapEffect("Eliminate all the monsters!", 5120033);
                            break;
                        case 1:
                            map.startMapEffect(
                                    "Break the boxes and eliminate the monsters!", 5120033);
                            break;
                        case 2:
                            map.startMapEffect("Eliminate the Officer!", 5120033);
                            break;
                        case 3:
                            map.startMapEffect("Eliminate all the monsters!", 5120033);
                            break;
                        case 4:
                            map.startMapEffect("Find the way to the other side!", 5120033);
                            break;
                    }
                    break;
                }
            case PRaid_B_Fenter:
                {
                    map.startMapEffect("Defeat the Ghost Ship Captain!", 5120033);
                    break;
                }
            case balog_summon:
            case easy_balog_summon:
                { // we dont want to reset
                    break;
                }
            case metro_firstSetting:
            case killing_MapSetting:
            case Sky_TrapFEnter:
            case balog_bonusSetting:
                { // not needed
                    map.resetFully();
                    break;
                }
            default:
                {
                    LOG.info("未處理的腳本 : {}, 型態 : onUserEnter - 地圖ID {}", scriptName, mapId);
                    break;
                }
        }
    }

    public static void startScript_User(MapleClient c, String scriptName) {
        if (c.getPlayer() == null) {
            return;
        } // o_O
        final MapleCharacter player = c.getPlayer(); // 快取玩家引用，避免後續 getPlayer() 可能返回 null 或重複查詢
        String data = "";
        switch (onUserEnter.fromString(scriptName)) {
            case cygnusTest:
            case cygnusJobTutorial:
                {
                    showIntro(
                            c,
                            "Effect/Direction.img/cygnusJobTutorial/Scene"
                                    + (player.getMapId() - 913040100));
                    break;
                }
            case shammos_Enter:
                { // nothing to go on inside the map
                    c.sendPacket(
                            MaplePacketCreator.sendPyramidEnergy(
                                    "shammos_LastStage",
                                    String.valueOf((player.getMapId() % 1000) / 100)));
                    if (player.getEventInstance() != null && player.getMapId() == 921120500) {
                        NPCScriptManager.getInstance().dispose(c); // only boss map.
                        NPCScriptManager.getInstance().start(c, 2022006);
                    }
                    break;
                }
            case start_itemTake:
                { // nothing to go on inside the map
                    final EventManager em =
                            c.getChannelServer().getEventSM().getEventManager("OrbisPQ");
                    if (em != null && em.getProperty("pre").equals("0")) {
                        NPCScriptManager.getInstance().dispose(c);
                        NPCScriptManager.getInstance().start(c, 2013001);
                    }
                    break;
                }
            case PRaid_W_Enter:
                {
                    c.sendPacket(MaplePacketCreator.sendPyramidEnergy("PRaid_expPenalty", "0"));
                    c.sendPacket(
                            MaplePacketCreator.sendPyramidEnergy("PRaid_ElapssedTimeAtField", "0"));
                    c.sendPacket(MaplePacketCreator.sendPyramidEnergy("PRaid_Point", "-1"));
                    c.sendPacket(MaplePacketCreator.sendPyramidEnergy("PRaid_Bonus", "-1"));
                    c.sendPacket(MaplePacketCreator.sendPyramidEnergy("PRaid_Total", "-1"));
                    c.sendPacket(MaplePacketCreator.sendPyramidEnergy("PRaid_Team", ""));
                    c.sendPacket(MaplePacketCreator.sendPyramidEnergy("PRaid_IsRevive", "0"));
                    player.writePoint("PRaid_Point", "-1");
                    player.writeStatus("Red_Stage", "1");
                    player.writeStatus("Blue_Stage", "1");
                    player.writeStatus("redTeamDamage", "0");
                    player.writeStatus("blueTeamDamage", "0");
                    break;
                }
            case PRaid_D_Enter:
            case PRaid_B_Enter:
            case PRaid_WinEnter: // handled by event
            case PRaid_FailEnter: // also
            case PRaid_Revive: // likely to subtract points or remove a life, but idc rly
            case metro_firstSetting:
            case blackSDI:
            case summonIceWall:
            case onSDI:
            case enterBlackfrog:
            case Sky_Quest: // forest that disappeared 240030102
            case dollCave00:
            case dollCave01:
            case shammos_Base:
            case shammos_Result:
            case Sky_BossEnter:
            case Sky_GateMapEnter:
            case balog_dateSet:
            case balog_buff:
            case outCase:
            case Sky_StageEnter:
            case dojang_QcheckSet:
            case evanTogether:
            case aranTutorAlone:
            case Ghost:
                {
                    player.getMap().startMapEffect("這裡感覺怪陰森的...", 5120025);
                    break;
                }
            case evanAlone:
                { // no idea
                    c.sendPacket(MaplePacketCreator.enableActions());
                    break;
                }
            case startEreb:
            case mirrorCave:
            case babyPigMap:
            case evanleaveD:
                {
                    c.sendPacket(UIPacket.IntroDisableUI(false));
                    c.sendPacket(UIPacket.IntroLock(false));
                    c.sendPacket(MaplePacketCreator.enableActions());
                    break;
                }
            case dojang_Msg:
                {
                    player.getMap()
                            .startMapEffect(
                                    mulungEffects[Randomizer.nextInt(mulungEffects.length)],
                                    5120024);
                    break;
                }
            case dojang_1st:
                {
                    player.writeMulungEnergy();
                    break;
                }
            case undomorphdarco:
            case reundodraco:
                {
                    player.cancelEffect(
                            MapleItemInformationProvider.getInstance().getItemEffect(2210016),
                            false,
                            -1);
                    break;
                }
            case goAdventure:
                {
                    // BUG in MSEA v.91, so let's skip this part.
                    showIntro(
                            c,
                            "Effect/Direction3.img/goAdventure/Scene"
                                    + (player.getGender() == 0 ? "0" : "1"));
                    break;
                }
            case crash_Dragon:
                showIntro(
                        c,
                        "Effect/Direction4.img/crash/Scene"
                                + (player.getGender() == 0 ? "0" : "1"));
                break;
            case getDragonEgg:
                showIntro(
                        c,
                        "Effect/Direction4.img/getDragonEgg/Scene"
                                + (player.getGender() == 0 ? "0" : "1"));
                break;
            case meetWithDragon:
                showIntro(
                        c,
                        "Effect/Direction4.img/meetWithDragon/Scene"
                                + (player.getGender() == 0 ? "0" : "1"));
                break;
            case PromiseDragon:
                showIntro(
                        c,
                        "Effect/Direction4.img/PromiseDragon/Scene"
                                + (player.getGender() == 0 ? "0" : "1"));
                break;
            case evanPromotion:
                switch (player.getMapId()) {
                    case 900090000:
                        data =
                                "Effect/Direction4.img/promotion/Scene0"
                                        + (player.getGender() == 0 ? "0" : "1");
                        break;
                    case 900090001:
                        data = "Effect/Direction4.img/promotion/Scene1";
                        break;
                    case 900090002:
                        data =
                                "Effect/Direction4.img/promotion/Scene2"
                                        + (player.getGender() == 0 ? "0" : "1");
                        break;
                    case 900090003:
                        data = "Effect/Direction4.img/promotion/Scene3";
                        break;
                    case 900090004:
                        c.sendPacket(UIPacket.IntroDisableUI(false));
                        c.sendPacket(UIPacket.IntroLock(false));
                        c.sendPacket(MaplePacketCreator.enableActions());
                        final MapleMap mapto =
                                c.getChannelServer().getMapFactory().getMap(900010000);
                        player.changeMap(mapto, mapto.getPortal(0));
                        return;
                }
                showIntro(c, data);
                break;
            case TD_MC_title:
                {
                    c.sendPacket(UIPacket.IntroDisableUI(false));
                    c.sendPacket(UIPacket.IntroLock(false));
                    c.sendPacket(MaplePacketCreator.enableActions());
                    c.sendPacket(UIPacket.MapEff("temaD/enter/mushCatle"));
                    break;
                }
            case explorationPoint:
                {
                    final int currentMapId = player.getMapId(); // 快取地圖ID，避免重複 getter
                    if (currentMapId == 104000000) {
                        c.sendPacket(UIPacket.IntroDisableUI(false));
                        c.sendPacket(UIPacket.IntroLock(false));
                        c.sendPacket(MaplePacketCreator.enableActions());
                        c.sendPacket(UIPacket.MapNameDisplay(currentMapId));
                    }
                    MedalQuest m = null;
                    for (MedalQuest mq : MedalQuest.values()) {
                        for (int i : mq.maps) {
                            if (currentMapId == i) {
                                m = mq;
                                break;
                            }
                        }
                    }
                    if (m != null
                            && player.getLevel() >= m.level
                            && player.getQuestStatus(m.questid) != 2) {
                        if (player.getQuestStatus(m.lquestid) != 1) {
                            MapleQuest.getInstance(m.lquestid).forceStart(player, 0, "0");
                        }
                        if (player.getQuestStatus(m.questid) != 1) {
                            MapleQuest.getInstance(m.questid).forceStart(player, 0, null);
                            final StringBuilder sb = new StringBuilder("enter=");
                            for (int i = 0; i < m.maps.length; i++) {
                                sb.append("0");
                            }
                            player.updateInfoQuest(m.questid - 2005, sb.toString());
                            MapleQuest.getInstance(m.questid - 1995).forceStart(player, 0, "0");
                        }
                        final String quest = player.getInfoQuest(m.questid - 2005);
                        final MapleQuestStatus stat =
                                player.getQuestNAdd(MapleQuest.getInstance(m.questid - 1995));
                        if (stat.getCustomData() == null) { // just a check.
                            stat.setCustomData("0");
                        }
                        int number = Integer.parseInt(stat.getCustomData());
                        final StringBuilder sb = new StringBuilder("enter=");
                        boolean changedd = false;
                        for (int i = 0; i < m.maps.length; i++) {
                            boolean changed = false;
                            if (currentMapId == m.maps[i]) {
                                if (quest.substring(i + 6, i + 7).equals("0")) {
                                    sb.append("1");
                                    changed = true;
                                    changedd = true;
                                }
                            }
                            if (!changed) {
                                sb.append(quest.substring(i + 6, i + 7));
                            }
                        }
                        if (changedd) {
                            number++;
                            player.updateInfoQuest(m.questid - 2005, sb.toString());
                            MapleQuest.getInstance(m.questid - 1995)
                                    .forceStart(player, 0, String.valueOf(number));
                            player.dropMessage(-1, "訪問 " + number + "/" + m.maps.length + " 個地區.");
                            player.dropMessage(-1, "稱號 " + String.valueOf(m) + " 已完成了");
                            c.sendPacket(
                                    MaplePacketCreator.showQuestMsg(
                                            "稱號 "
                                                    + String.valueOf(m)
                                                    + " 已完成訪問 "
                                                    + number
                                                    + "/"
                                                    + m.maps.length
                                                    + " 個地區"));
                        }
                    }
                    break;
                }
            case go10000:
            case go1020000:
                {
                    c.sendPacket(UIPacket.IntroDisableUI(false));
                    c.sendPacket(UIPacket.IntroLock(false));
                    c.sendPacket(MaplePacketCreator.enableActions());
                    // 有意 fallthrough：關閉 UI 後仍需顯示地圖名稱，與下方 case 共用邏輯
                    c.sendPacket(UIPacket.MapNameDisplay(player.getMapId()));
                    break;
                }
            case go20000:
            case go30000:
            case go40000:
            case go50000:
            case go1000000:
            case go2000000:
            case go1010000:
            case go1010100:
            case go1010200:
            case go1010300:
            case go1010400:
                {
                    c.sendPacket(UIPacket.MapNameDisplay(player.getMapId()));
                    break;
                }
            case goArcher:
                {
                    showIntro(
                            c,
                            "Effect/Direction3.img/archer/Scene"
                                    + (player.getGender() == 0 ? "0" : "1"));
                    break;
                }
            case goPirate:
                {
                    showIntro(
                            c,
                            "Effect/Direction3.img/pirate/Scene"
                                    + (player.getGender() == 0 ? "0" : "1"));
                    break;
                }
            case goRogue:
                {
                    showIntro(
                            c,
                            "Effect/Direction3.img/rogue/Scene"
                                    + (player.getGender() == 0 ? "0" : "1"));
                    break;
                }
            case goMagician:
                {
                    showIntro(
                            c,
                            "Effect/Direction3.img/magician/Scene"
                                    + (player.getGender() == 0 ? "0" : "1"));
                    break;
                }
            case goSwordman:
                {
                    showIntro(
                            c,
                            "Effect/Direction3.img/swordman/Scene"
                                    + (player.getGender() == 0 ? "0" : "1"));
                    break;
                }
            case goLith:
                {
                    showIntro(
                            c,
                            "Effect/Direction3.img/goLith/Scene"
                                    + (player.getGender() == 0 ? "0" : "1"));
                    break;
                }
            case TD_MC_Openning:
                {
                    showIntro(c, "Effect/Direction2.img/open");
                    break;
                }
            case TD_MC_gasi:
                {
                    showIntro(c, "Effect/Direction2.img/gasi");
                    break;
                }
            case aranDirection:
                {
                    switch (player.getMapId()) {
                        case 914090010:
                            data = "Effect/Direction1.img/aranTutorial/Scene0";
                            break;
                        case 914090011:
                            data =
                                    "Effect/Direction1.img/aranTutorial/Scene1"
                                            + (player.getGender() == 0 ? "0" : "1");
                            break;
                        case 914090012:
                            data =
                                    "Effect/Direction1.img/aranTutorial/Scene2"
                                            + (player.getGender() == 0 ? "0" : "1");
                            break;
                        case 914090013:
                            data = "Effect/Direction1.img/aranTutorial/Scene3";
                            break;
                        case 914090100:
                            data =
                                    "Effect/Direction1.img/aranTutorial/HandedPoleArm"
                                            + (player.getGender() == 0 ? "0" : "1");
                            break;
                        case 914090200:
                            data = "Effect/Direction1.img/aranTutorial/Maha";
                            break;
                    }
                    showIntro(c, data);
                    break;
                }
            case iceCave:
                {
                    player.changeSkillLevel(SkillFactory.getSkill(20000014), (byte) -1, (byte) 0);
                    player.changeSkillLevel(SkillFactory.getSkill(20000015), (byte) -1, (byte) 0);
                    player.changeSkillLevel(SkillFactory.getSkill(20000016), (byte) -1, (byte) 0);
                    player.changeSkillLevel(SkillFactory.getSkill(20000017), (byte) -1, (byte) 0);
                    player.changeSkillLevel(SkillFactory.getSkill(20000018), (byte) -1, (byte) 0);
                    c.sendPacket(
                            UIPacket.ShowWZEffect("Effect/Direction1.img/aranTutorial/ClickLirin"));
                    c.sendPacket(UIPacket.IntroDisableUI(false));
                    c.sendPacket(UIPacket.IntroLock(false));
                    c.sendPacket(MaplePacketCreator.enableActions());
                    break;
                }
            case rienArrow:
                {
                    if (player.getInfoQuest(21019).equals("miss=o;helper=clear")) {
                        player.updateInfoQuest(21019, "miss=o;arr=o;helper=clear");
                        c.sendPacket(
                                UIPacket.AranTutInstructionalBalloon(
                                        "Effect/OnUserEff.img/guideEffect/aranTutorial/tutorialArrow3"));
                    }
                    break;
                }
            case rien:
                {
                    if (player.getQuestStatus(21101) == 2
                            && player.getInfoQuest(21019).equals("miss=o;arr=o;helper=clear")) {
                        player.updateInfoQuest(21019, "miss=o;arr=o;ck=1;helper=clear");
                    }
                    c.sendPacket(UIPacket.IntroDisableUI(false));
                    c.sendPacket(UIPacket.IntroLock(false));
                    break;
                }
            case check_count:
                {
                    if (player.getMapId() == 950101010
                            && (!player.haveItem(4001433, 20)
                                    || player.getLevel() < 50)) { // ravana Map
                        final MapleMap mapp =
                                c.getChannelServer().getMapFactory().getMap(950101100); // exit Map
                        player.changeMap(mapp, mapp.getPortal(0));
                    }
                    break;
                }
            case Massacre_first:
                { // sends a whole bunch of shit.
                    if (player.getPyramidSubway() == null) {
                        player.setPyramidSubway(new Event_PyramidSubway(player));
                    }
                    break;
                }
            case Massacre_result:
                { // clear, give exp, etc.
                    // if (player.getPyramidSubway() == null) {
                    c.sendPacket(MaplePacketCreator.showEffect("killing/fail"));
                    // } else {
                    //	c.sendPacket(MaplePacketCreator.showEffect("killing/clear"));
                    // }
                    // left blank because pyramidsubway handles this.
                    break;
                }
            default:
                {
                    LOG.info("未處理的腳本 : {}, 型態 : onUserEnter - 地圖ID {}", scriptName, player.getMapId());
                    break;
                }
        }
    }

    private static final int getTiming(int ids) {
        if (ids <= 5) {
            return 5;
        } else if (ids >= 7 && ids <= 11) {
            return 6;
        } else if (ids >= 13 && ids <= 17) {
            return 7;
        } else if (ids >= 19 && ids <= 23) {
            return 8;
        } else if (ids >= 25 && ids <= 29) {
            return 9;
        } else if (ids >= 31 && ids <= 35) {
            return 10;
        } else if (ids >= 37 && ids <= 38) {
            return 15;
        }
        return 0;
    }

    private static final int getDojoStageDec(int ids) {
        if (ids <= 5) {
            return 0;
        } else if (ids >= 7 && ids <= 11) {
            return 1;
        } else if (ids >= 13 && ids <= 17) {
            return 2;
        } else if (ids >= 19 && ids <= 23) {
            return 3;
        } else if (ids >= 25 && ids <= 29) {
            return 4;
        } else if (ids >= 31 && ids <= 35) {
            return 5;
        } else if (ids >= 37 && ids <= 38) {
            return 6;
        }
        return 0;
    }

    private static void showIntro(final MapleClient c, final String data) {
        c.sendPacket(UIPacket.IntroDisableUI(true));
        c.sendPacket(UIPacket.IntroLock(true));
        c.sendPacket(UIPacket.ShowWZEffect(data));
    }

    private static void sendDojoClock(MapleClient c, int time) {
        c.sendPacket(MaplePacketCreator.getClock(time));
    }

    private static void sendDojoStart(MapleClient c, int stage) {
        c.sendPacket(MaplePacketCreator.environmentChange("Dojang/start", 4));
        c.sendPacket(MaplePacketCreator.environmentChange("dojang/start/stage", 3));
        c.sendPacket(MaplePacketCreator.environmentChange("dojang/start/number/" + stage, 3));
        c.sendPacket(MaplePacketCreator.trembleEffect(0, 1));
    }

    private static void handlePinkBeanStart(MapleClient c) {
        final MapleMap map = c.getPlayer().getMap();
        map.resetFully();

        if (!map.containsNPC(2141000)) {
            map.spawnNpc(2141000, new Point(-190, -42));
        }
    }

    private static void reloadWitchTower(MapleClient c) {
        final MapleMap map = c.getPlayer().getMap();
        map.killAllMonsters(false);

        final int level = c.getPlayer().getLevel();
        int mob;
        if (level <= 10) {
            mob = 9300367;
        } else if (level <= 20) {
            mob = 9300368;
        } else if (level <= 30) {
            mob = 9300369;
        } else if (level <= 40) {
            mob = 9300370;
        } else if (level <= 50) {
            mob = 9300371;
        } else if (level <= 60) {
            mob = 9300372;
        } else if (level <= 70) {
            mob = 9300373;
        } else if (level <= 80) {
            mob = 9300374;
        } else if (level <= 90) {
            mob = 9300375;
        } else if (level <= 100) {
            mob = 9300376;
        } else {
            mob = 9300377;
        }
        map.spawnMonsterOnGroundBelow(MapleLifeFactory.getMonster(mob), witchTowerPos);
    }
}
