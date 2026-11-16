var status = -1;

function start(){
	action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == -1) {
        cm.dispose();
    } else {
        if (mode == 0) {
            cm.dispose();
            return;
        }
        if (mode == 1)
            status++;
        else
            status--;
        if (status == 0) {
            cm.sendGetNumber("#d請輸入你要#r升級#d的等級", 1, 1, (255 - cm.getPlayer().getLevel()));
        } else if (status == 1) {
			input = selection;
            for (var x = 0; x < input; x++) {
                cm.getPlayer().levelUp();
            }
            cm.getPlayer().setExp(0);
			cm.getPlayer().updateSingleStat(Packages.client.MapleStat.EXP, 0);
            cm.dispose();
        } else {
            cm.dispose();
        }
    }
}