var status = -1;

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == 1) {
        status++;
    } else {
        if (status == 0) { cm.dispose(); return; }
        status--;
    }

    if (status == 0) {
        var list = cm.getAutoBuffSkillList();
        if (!list || list.length === 0) {
            cm.sendOk("您沒有可設定為自動常駐的 Buff 技能。");
            return;
        }
        cm.sendSimple("請選擇要開啟／關閉的自動常駐技能：\r\n\r\n" + list);
    } else if (status == 1) {
        cm.toggleAutoBuff(selection);
        // 重新整理清單，讓玩家看到狀態已改變
        var list = cm.getAutoBuffSkillList();
        cm.sendSimple("已更新！目前自動常駐設定：\r\n\r\n" + (list.length ? list : "（無）"));
        status = 0; // 回到選單層級，可繼續點其他技能
    }
}