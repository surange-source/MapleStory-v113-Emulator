/**
 * 萬能轉職 NPC (重構版)
 *
 * 結構：
 *   1. JOB_NAME  職業代碼 -> 名稱
 *   2. TREE      轉職規則表 (目前職業 -> 條件與可轉職業)，要改等級/新增職業只改這裡
 *   3. 邏輯      start / action 只負責流程，不含任何職業判斷
 */

// ===================== 1. 職業名稱 =====================
var JOB_NAME = {
    // 劍士
    100: "劍士", 110: "狂戰士", 111: "十字軍", 112: "英雄",
    120: "見習騎士", 121: "騎士", 122: "聖騎士",
    130: "槍騎兵", 131: "龍騎士", 132: "黑騎士",
    // 法師
    200: "法師",
    210: "火毒法師", 211: "火毒魔導士", 212: "火毒大魔導士",
    220: "冰雷法師", 221: "冰雷魔導士", 222: "冰雷大魔導士",
    230: "僧侶", 231: "祭司", 232: "主教",
    // 弓箭手
    300: "弓箭手",
    310: "獵人", 311: "遊俠", 312: "箭神",
    320: "弩弓手", 321: "狙擊手", 322: "神射手",
    // 盜賊
    400: "盜賊",
    410: "刺客", 411: "暗殺者", 412: "夜使者",
    420: "俠盜", 421: "神偷", 422: "暗影神偷",
    // 海盜
    500: "海盜",
    510: "打手", 511: "格鬥家", 512: "拳霸",
    520: "槍手", 521: "神槍手", 522: "槍神",
    // 皇家騎士團
    1100: "聖魂劍士(一轉)", 1110: "聖魂劍士(二轉)", 1111: "聖魂劍士(三轉)",
    1200: "烈焰巫師(一轉)", 1210: "烈焰巫師(二轉)", 1211: "烈焰巫師(三轉)",
    1300: "破風使者(一轉)", 1310: "破風使者(二轉)", 1311: "破風使者(三轉)",
    1400: "暗夜行者(一轉)", 1410: "暗夜行者(二轉)", 1411: "暗夜行者(三轉)",
    1500: "閃雷悍將(一轉)", 1510: "閃雷悍將(二轉)", 1511: "閃雷悍將(三轉)",
    // 狂狼勇士
    2100: "狂狼(一轉)", 2110: "狂狼(二轉)", 2111: "狂狼(三轉)", 2112: "狂狼(四轉)"
};

// ===================== 2. 轉職規則表 =====================
// 每筆規則：{ lv: 最低等級, npc: 對話標題, menu: 是否顯示選單, to: [可轉職業] }
// 同一職業有多筆規則時，請由「等級高」排到「等級低」。

/** 選單式：玩家從 to 之中挑一個 */
function pick(lv, npc, to) {
    return [{ lv: lv, npc: npc, menu: true, to: to }];
}
/** 自動式：符合條件直接轉成唯一的 to */
function auto(lv, npc, target) {
    return [{ lv: lv, npc: npc, menu: false, to: [target] }];
}

var TREE = {
    // ---- 一轉 ----
    0: [
        { lv: 10, npc: "萬能轉職NPC", menu: true, to: [100, 200, 300, 400, 500] },
        { lv: 8,  npc: "萬能轉職NPC", menu: true, to: [200] }          // 8~9 級只開放法師
    ],
    1000: pick(10, "萬能轉職NPC", [1100, 1200, 1300, 1400, 1500]),
    2000: auto(10, "萬能轉職NPC", 2100),

    // ---- 二轉 ----
    100: pick(30, "劍士二轉轉職NPC",   [110, 120, 130]),
    200: pick(30, "法師二轉轉職NPC",   [210, 220, 230]),
    300: pick(30, "弓箭手二轉轉職NPC", [310, 320]),
    400: pick(30, "盜賊二轉轉職NPC",   [410, 420]),
    500: pick(30, "海盜二轉轉職NPC",   [510, 520]),
    1100: auto(30, "聖魂劍士二轉轉職NPC", 1110),
    1200: auto(30, "烈焰巫師二轉轉職NPC", 1210),
    1300: auto(30, "破風使者二轉轉職NPC", 1310),
    1400: auto(30, "暗夜行者二轉轉職NPC", 1410),
    1500: auto(30, "閃雷悍將二轉轉職NPC", 1510),
    2100: auto(30, "狂狼二轉轉職NPC", 2110),

    // ---- 三轉 ----
    110: auto(70, "劍士三轉轉職NPC", 111),
    120: auto(70, "劍士三轉轉職NPC", 121),
    130: auto(70, "劍士三轉轉職NPC", 131),
    210: auto(70, "法師三轉轉職NPC", 211),
    220: auto(70, "法師三轉轉職NPC", 221),
    230: auto(70, "法師三轉轉職NPC", 231),
    310: auto(70, "弓箭手三轉轉職NPC", 311),
    320: auto(70, "弓箭手三轉轉職NPC", 321),
    410: auto(70, "盜賊三轉轉職NPC", 411),
    420: auto(70, "盜賊三轉轉職NPC", 421),
    510: auto(70, "海盜三轉轉職NPC", 511),
    520: auto(70, "海盜三轉轉職NPC", 521),
    1110: auto(70, "聖魂劍士三轉轉職NPC", 1111),
    1210: auto(70, "烈焰巫師三轉轉職NPC", 1211),
    1310: auto(70, "破風使者三轉轉職NPC", 1311),
    1410: auto(70, "暗夜行者三轉轉職NPC", 1411),
    1510: auto(70, "閃雷悍將三轉轉職NPC", 1511),
    2110: auto(70, "狂狼三轉轉職NPC", 2111),

    // ---- 四轉 ----
    111: auto(120, "劍士四轉轉職NPC", 112),
    121: auto(120, "劍士四轉轉職NPC", 122),
    131: auto(120, "劍士四轉轉職NPC", 132),
    211: auto(120, "法師四轉轉職NPC", 212),
    221: auto(120, "法師四轉轉職NPC", 222),
    231: auto(120, "法師四轉轉職NPC", 232),
    311: auto(120, "弓箭手四轉轉職NPC", 312),
    321: auto(120, "弓箭手四轉轉職NPC", 322),
    411: auto(120, "盜賊四轉轉職NPC", 412),
    421: auto(120, "盜賊四轉轉職NPC", 422),
    511: auto(120, "海盜四轉轉職NPC", 512),
    521: auto(120, "海盜四轉轉職NPC", 522),
    2111: auto(120, "狂狼四轉轉職NPC", 2112)
};

// ===================== 3. 邏輯 =====================

/** 依目前職業與等級取得符合的規則，找不到回傳 null (O(1) 查表) */
function findRule(job, level) {
    var rules = TREE[job];
    if (!rules) {
        return null;
    }
    for (var i = 0; i < rules.length; i++) {
        if (level >= rules[i].lv) {
            return rules[i];
        }
    }
    return null;
}

function contains(arr, value) {
    for (var i = 0; i < arr.length; i++) {
        if (arr[i] === value) {
            return true;
        }
    }
    return false;
}

function buildMenu(rule) {
    var text = "嗨, 我是#e#d" + rule.npc + "#n#k\r\n";
    for (var i = 0; i < rule.to.length; i++) {
        var id = rule.to[i];
        text += "#L" + id + "# " + JOB_NAME[id] + "#l\r\n";
    }
    return text;
}

function advance(jobId) {
    cm.changeJob(jobId);
    cm.sendOk("恭喜你成為 #b" + JOB_NAME[jobId] + "#k！");
    cm.dispose();
}

function start() {
    // 只取一次，不再於每個判斷式重複呼叫
    var rule = findRule(cm.getJob(), cm.getPlayerStat("LVL"));

    if (rule === null) {
        cm.sendOk("你不符合轉職條件");
        cm.dispose();
    } else if (rule.menu) {
        cm.sendSimple(buildMenu(rule));
    } else {
        advance(rule.to[0]);
    }
}

function action(mode, type, selection) {
    if (mode !== 1) {            // 關閉視窗或按取消
        cm.dispose();
        return;
    }

    // 重新驗證，避免玩家送出不屬於當前選單的選項
    var rule = findRule(cm.getJob(), cm.getPlayerStat("LVL"));
    if (rule === null || !rule.menu || !contains(rule.to, selection)) {
        cm.sendOk("您還沒選擇職業!");
        cm.dispose();
        return;
    }

    advance(selection);
}
