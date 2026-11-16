/* global cm */

var status = -1;
var select = -1;

/* Clear inv */
var ClearText = "";
var ClearUp = 0;
var ClearTitle = Array("裝備欄", "消耗欄", "裝飾欄", "其他欄", "特殊欄");
var slot = Array();
var startnum = 0;
var endnum = 0;

function start() {
    cm.sendSimple(cm.getChannelServer().getServerName() + "管理員為您服務，請問你想做什麼呢？\r\n" +
            "\r\n-------玩家指令區-------\r\n" +
            "#L1#測試機專用升級#l\r\n" +
            "#L2#新手獎勵#l\r\n" +
            "#L3#開啟搜尋引擎#l\r\n" +
            "#L4#查看線上人數#l\r\n" +
            "\r\n-------功能區-------\r\n" +
            "#L5#清除卡精靈商人#l\r\n" +
            //"#L6#滿技能頂#l\r\n" +
            "#L6#超級傳送#l\r\n" +
            "#L7#存檔#l\r\n" +
            "#L8#autobuff#l\r\n" +
            "#L10#參加系統活動#l\r\n" +
            "#L14#開/關閉廣播顯示#l\r\n" +
            "#L21#經驗歸零(修復經驗假死)#l\r\n" +
            "#L22#查詢地圖怪物資訊#l\r\n" +
            "#L23#清除背包道具#l\r\n" +
            "\r\n-------功能區-------\r\n" +
            "#L11#萬能轉職#l\r\n" +
            //"#L8#BOSSPQ兌換#l\r\n" +
            //"#L13#我是抓羊專家#l\r\n" +
            //"#L6#楓葉道具兌換#l\r\n" +
            "#L16#查看每日挑戰BOSS次數#l\r\n" +
            "#L24#超智慧貓頭鷹#l\r\n"
            //"#L12#夏日Fun暑假#l\r\n" +
            //"#L15#台積谷週年慶活動(活動結束日期:2016/09/30)#l\r\n" +
            //"#L17#就是愛月灣 (活動結束日期:2016/09/18 23:59)#l\r\n" +
            //"#L18#新手網咖練功地(活動結束日期:2016/10/31)#l\r\n" +
            //"#L19#10-12月獎勵(活動結束日期:2016/12/31)#l\r\n" +
            //"#L20#我要領取伺服器爆炸補償#l\r\n"
            );
}

function action(mode, type, selection) {
    if (select === -1) {
        select = selection;
    }

    switch (select) {
        case 1:
        {
            cm.dispose();
            //cm.enterMTS();
            //cm.playerMessage("拍賣功能尚未開放。");
            openNpc(9010000, "levelup");
            break;
        }
        case 2:
        {
//            cm.dispose();
//            openNpc(9000058,"新手獎勵");
            if (!cm.isQuestFinished(29934)) {
                NewPlayer1();
            } else {
                cm.sendOk("有這回事?");
                cm.dispose();
            }
            break;
        }
        case 3:
        {
            cm.dispose();
            openNpc(9010000, "AdvancedSearch");
            break;
        }
        case 4:
        {
            cm.sendOk("當前" + cm.getChannelNumber() + "頻道: " + cm.getChannelOnline() + "人   當前伺服器總計線上人數: " + cm.getTotalOnline() + "個" + cm.getCurrentMaxHP());
            cm.dispose();
            break;
        }
        case 5:
        {
            cm.dispose();
            cm.processCommand("@jk_hm");
            break;
        }
//        case 6:
//        {
//            cm.base4thSkills();
//            break;
//        }
        case 6:
        {
            cm.dispose();
            openNpc(9000020, "teleport");
            break;
        }
        case 7:
        {
            cm.dispose();
            cm.processCommand("@save");
            break;
        }
        case 8:
        {
            cm.dispose();
            openNpc(9330082, "autobuff");
            break;
        }
        case 10:
        {
            openNpc(9000001);
            break;
        }
        case 11:
        {
            openNpc(9010000, "changejob");
            break;
        }
        case 12:
        {
            //openNpc(9010000, "sumnmer");
            break;
        }
        case 13:
        {
            cm.dispose();
            break;
        }
        case 14:
        {
            cm.dispose();
            cm.processCommand("@Tsmega");
            break;
        }
        case 15:
        {
            cm.saveLocation("BIRTHDAY");
            cm.warp(749020910);
            cm.dispose();
            break;
        }
        case 16:
        {
            openNpc(9010000, "BossLogs");
            break;
        }
        case 17:
        {
            openNpc(9010000, "就是愛月灣");
            break;
        }
        case 18:
        {
            if (cm.getPlayer().getMapId() == 103000000) {
                cm.warp(193000000, 0);
            } else {
                cm.sendNext("只能在墮落城市使用此功能");
            }
            cm.dispose();
            break;
        }
        case 19:
        {
            openNpc(9010000, "1012活動");
            break;
        }
        case 20:
        {
            openNpc(9010000, "爆炸補償");
            break;
        }
        case 21:
        {
            cm.dispose();
            cm.processCommand("@expfix");
            cm.dispose();
            break;
        }
        case 22:
        {
            cm.dispose();
            cm.processCommand("@mob");
            break;
        }
        case 23:
        {
            Clear(mode, type, selection);
            break;
        }
        case 24:
        {
            openNpc(9010000, "超智慧貓頭鷹");
            break;
        }
        default:
        {
            cm.sendOk("此功能未完成");
            cm.dispose();
        }
    }
}

function select3(mode, type, selection) {
    if (mode === 1) {
        status++;
    } else if (mode === 0) {
        status--;
    }

    var i = -1;
    if (status <= i++) {
        cm.dispose();
    } else if (status === i++) {
        var gain = cm.getMP();
        if (gain <= 0) {
            cm.sendOk("目前沒有任何在線點數唷。");
            cm.dispose();
            return;
        } else {
            cm.sendYesNo("目前楓葉點數: " + cm.getMaplePoint() + "\r\n" + "目前在線點數已經累積: " + gain + " 點，是否領取?");
        }
    } else if (status === i++) {
        var gain = cm.getMP();
        cm.setMP(0);
        cm.gainMaplePoint(gain);
        cm.save();
        cm.sendOk("領取了 " + gain + " 點在線點數, 目前楓葉點數: " + cm.getMaplePoint());
        cm.dispose();
    } else {
        cm.dispose();
    }
}

function Clear(mode, type, selection) {
    if (mode == 1) {
        status++;
    } else if (mode == 0) {
        status--;
    }

    var i = -1;
    if (status <= i++) {
        cm.dispose();
    } else if (status == i++) {
        ClearText = "";
        for (var i = 0; i < ClearTitle.length; i++)
            ClearText += "\r\n#b#L" + i + "#" + ClearTitle[i] + "#l#k";

        cm.sendSimple("清除身上背包的道具是一個很慎重的事情!!\r\n#r請慎重做抉擇，若誤清GM不會負責!!" + ClearText);
    } else if (status == i++) {
        ClearText = ClearTitle[selection];
        switch (ClearText) {
            case '裝備欄':
                ClearUp = 1;
                break;
            case '消耗欄':
                ClearUp = 2;
                break;
            case '裝飾欄':
                ClearUp = 3;
                break;
            case '其他欄':
                ClearUp = 4;
                break;
            case '特殊欄':
                ClearUp = 5;
                break;
        }
        var avail = "";
        var dd = 0;
        for (var i = 0; i < 96; i++) {
            if (cm.getInventory(ClearUp).getItem(i) != null) {
                var itemId = cm.getInventory(ClearUp).getItem(i).getItemId();
                if (itemId == null) {
                    i++; //防止下一步錯誤
                }
                avail += "#L" + Math.abs(i) + "##i" + cm.getInventory(ClearUp).getItem(i).getItemId() + "##z" + cm.getInventory(ClearUp).getItem(i).getItemId() + "##l\r\n";
            } else {
                dd++;
            }
            slot.push(i);
        }
        if (dd == 96) {
            cm.sendNext(ClearText + "沒有任何道具可以清除!");
            cm.dispose();
            return;
        }
        cm.sendSimple("想要從哪裡開始清除呢??\r\n#b" + avail);
    } else if (status == i++) {
        startnum = selection;
        var avail = "";
        for (var i = startnum; i < 96; i++) {
            if (cm.getInventory(ClearUp).getItem(i) != null) {
                avail += "#L" + Math.abs(i) + "##i" + cm.getInventory(ClearUp).getItem(i).getItemId() + "##z" + cm.getInventory(ClearUp).getItem(i).getItemId() + "##l\r\n";
            }
            slot.push(i);
        }
        cm.sendSimple("想要從哪裡結束清除呢??\r\n#b" + avail);
    } else if (status == i++) {
        endnum = selection;
        cm.dispose();
        cm.processCommand("@清除道具 " + ClearText + " " + startnum + " " + endnum);
    } else {
        cm.dispose();
    }
}

function NewPlayer1() {
    var item = [5000004, 1112127, 1122334];
    var amount = [3, 1, 1];
    var next = true;
    for (var i = 0; i < item.length; i++) {
        if (!cm.canHold(item[i], amount[i])) {
            next = false;
        }
    }
    if (!next) {
        cm.sendNext("背包空間不足以領新手獎勵唷。");
        cm.dispose();
        return;
    }
    cm.gainPet(5000004, "黑色小貓", 1, 0, 100, 180, 127);
    cm.gainPet(5000004, "黑色小貓", 1, 0, 100, 180, 127);
    cm.gainPet(5000004, "黑色小貓", 1, 0, 100, 180, 127);
    /*
     cm.gainItem(1002419, 1, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 50, 100);
     cm.gainItem(1040002, 1, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 50, 100);
     cm.gainItem(1060002, 1, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 50, 100);
     cm.gainItem(1072001, 1, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 50, 100);
     cm.gainItem(1082002, 1, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 50, 100);
     cm.gainItem(1102174, 1, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 50, 100);
     cm.gainItem(1092003, 1, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 50, 100);
     cm.gainItem(1032033, 1, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 50, 100);
     cm.gainItem(1112306, 1, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 50, 100);
     cm.gainItem(1112307, 1, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 50, 100);
     cm.gainItem(1112308, 1, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 50, 100);*/
    cm.gainItem(1112127, 1, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 50, 100);
    cm.gainItem(1122334, 1, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000, 50, 100);

    //正常版
    //cm.gainItem(1112127, 1);
    //cm.gainItem(1122334, 1);
    //cm.gainItem(2450000, 2); //獵人的幸運
    cm.forceCompleteQuest(29934); //完成新手獎勵
    cm.sendNext("歡迎來到 楓之谷 請使用 @help/@幫助 了解各式指令\r\n\r\n\r\n遊戲愉快^^");
    cm.getPlayer().setOneTimeLog("新手獎勵");
    cm.dispose();
}

function openNpc(npcid) {
    openNpc(npcid, null);
}

function openNpc(npcid, script) {
    var mapid = cm.getMapId();
    cm.dispose();
    if (
//		cm.hasSquadByMap() ||
//		cm.hasEventInstance() ||
//		cm.hasEMByMap() ||
            mapid >= 990000000 ||
            (mapid >= 680000210 && mapid <= 680000502) ||
            (mapid / 1000 === 980000 && mapid !== 980000000) ||
            mapid / 100 === 1030008 ||
            mapid / 100 === 922010 ||
            mapid / 10 === 13003000) {
        cm.sendOk("你不能在這裡使用這個功能.");
    } else {
        if (script == null) {
            cm.openNpc(npcid);
        } else {
            cm.openNpc(npcid, script);
        }
    }
}
