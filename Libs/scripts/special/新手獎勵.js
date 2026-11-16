/* global cm */
        load('nashorn:mozilla_compat.js');
importPackage(Packages.tools);

var status = -1;
var banMap = Array(109080000, 109080010, 109040000, 109030001, 109060000, 109010000);

function start() {
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode === 1) {
        status++;
    } else if (mode === 0) {
        status--;
    } else {
        cm.dispose();
        return;
    }

    if (status === 0) {
//		if (cm.isQuestFinished(29934)) {
//			NewPlayer1();
//		} else {
//			if (cm.getPlayer().getOneTimeLog("新手獎勵") == 0) {
//				cm.getPlayer().setOneTimeLog("新手獎勵");
//			}
//		}
        for (var i = 0; i < banMap.length; i++) {
            if (cm.getPlayer().getMapId() == banMap[i]) {
                cm.sendOk("幹！想要偷跑吃屎去吧！");
                cm.dispose();
                break;
            }
        }
        cm.sendSimple("#b歡迎來到台積谷!!\r\n" +
                "#L2#超級搜尋#l\r\n" +
                "#L3#新手獎勵#l\r\n" +
                "#L4#ㄟ沙小!!#l\r\n" +
                //"#L5#我要進行忍影瞬殺的任務(四轉盜賊限定)#l\r\n" +
                //"#L6#我要刪除銀或金寶箱空白道具(並且補償一次道具)#l\r\n" +
                //"#L7#我要完成燈泡不能接的任務#l\r\n" +
                // "#L8#我領取廣播道具#ll\r\n" +
                //"#L9#我領取愛心廣播道具#\l\r\n" +
                //"#L10#我領取骷簍廣播道具#l\r\n" +
                //"#L11#我領取精靈商人#l\r\n" +
                //"#L12#我要打恰吉#l\r\n" +
                //"#L13#我要廣播道具#l\r\n" +
                "#L17#GaY");
    } else if (status === 1) {
        var level = cm.getPlayer().getLevel();
        if (selection === 2) {
            cm.dispose();
            cm.openNpc(9010000, "AdvancedSearch");
            return;
        } else if (selection === 3) {
            NewPlayer1();
        } else if (selection === 4) {
            cm.dispose();
            cm.openNpc(9000056);
            return;
        } else if (selection === 17) {
            cm.dispose();
        }
    } else {
        cm.dispose();
    }

}

function NewPlayer1() {
//	if (cm.getPlayer().getOneTimeLog("新手獎勵") == 0) {
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
    //cm.gainItemPeriod(1002419, 1, 30); //紅葉黑頭巾
    //cm.gainItemPeriod(5370000, 1, 7); //黑板 7天
    cm.forceCompleteQuest(29934); //完成新手獎勵
    //cm.sendNext("歡迎來到 台積谷 請使用 @help/@幫助 了解各式指令\r\n\r\n\r\n遊戲愉快^^");
    //FileoutputUtil.logToFile("logs/Data/新手獎勵.txt", "領取時間:" + FileoutputUtil.NowTime() + "角色名稱:" + cm.getPlayer().getName() + "角色等級: " + cm.getPlayer().getLevel() + "\r\n");
    //cm.getPlayer().setOneTimeLog("新手獎勵");
    cm.dispose();
//	}
}
