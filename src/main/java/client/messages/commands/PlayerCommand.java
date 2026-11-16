package client.messages.commands;

import client.MapleCharacter;
import client.MapleClient;
import client.MapleStat;
import client.inventory.MapleInventory;
import client.inventory.MapleInventoryType;
import constants.GameConstants;
import constants.ItemConstants;
import constants.ServerConstants.PlayerGMRank;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import scripting.NPCScriptManager;
import server.MapleInventoryManipulator;
import server.MapleItemInformationProvider;
import server.life.MapleMonster;
import server.maps.MapleMap;
import server.maps.MapleMapObject;
import server.maps.MapleMapObjectType;
import server.maps.SavedLocationType;
import tools.MaplePacketCreator;
import tools.StringUtil;

/**
 * @author Emilyx3
 */
public class PlayerCommand {

    public static PlayerGMRank getPlayerLevelRequired() {
        return PlayerGMRank.NORMAL;
    }

    public abstract static class OpenNPCCommand extends CommandExecute {

        protected int npc = -1;
        private static int[] npcs = { // Ish yur job to make sure these are in order and correct ;(
            9010017,
        };

        @Override
        public int execute(MapleClient c, String[] splitted) {
            if (npc != 1 && c.getPlayer().getMapId() != 910000000) { // drpcash can use anywhere
                for (int i : GameConstants.getBlockedMaps()) {
                    if (c.getPlayer().getMapId() == i) {
                        c.getPlayer().dropMessage(1, "[系統提示] 你不能在這裡使用指令.");
                        return 0;
                    }
                }
                if (c.getPlayer().getLevel() < 10) {
                    c.getPlayer().dropMessage(1, "[系統提示] 你的等級必須是10等.");
                    return 0;
                }
                if (c.getPlayer().getMap().getSquadByMap() != null
                        || c.getPlayer().getEventInstance() != null
                        || c.getPlayer().getMap().getEMByMap() != null
                        || c.getPlayer().getMapId()
                                >= 990000000 /* || FieldLimitType.VipRock.check(c.getPlayer().getMap().getFieldLimit())*/) {
                    c.getPlayer().dropMessage(1, "[系統提示] 你不能在這裡使用指令.");
                    return 0;
                }
                if ((c.getPlayer().getMapId() >= 680000210 && c.getPlayer().getMapId() <= 680000502)
                        || (c.getPlayer().getMapId() / 1000 == 980000
                                && c.getPlayer().getMapId() != 980000000)
                        || (c.getPlayer().getMapId() / 100 == 1030008)
                        || (c.getPlayer().getMapId() / 100 == 922010)
                        || (c.getPlayer().getMapId() / 10 == 13003000)) {
                    c.getPlayer().dropMessage(1, "[系統提示] 你不能在這裡使用指令.");
                    return 0;
                }
            }
            NPCScriptManager.getInstance().start(c, npcs[npc]);
            return 1;
        }
    }

    public static class DropCash extends 丟裝 {}

    public static class 丟裝 extends OpenNPCCommand {

        public 丟裝() {
            npc = 0;
        }
    }

    public static class ea extends 查看 {}

    public static class 查看 extends CommandExecute {

        @Override
        public int execute(MapleClient c, String[] splitted) {
            NPCScriptManager.getInstance().dispose(c);
            c.sendPacket(MaplePacketCreator.enableActions());
            c.getPlayer().dropMessage(1, "[系統提示] 解卡完畢");
            c.getPlayer()
                    .dropMessage(
                            6, "[系統提示] 當前時間是" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " GMT+8 ");
            c.getPlayer()
                    .dropMessage(
                            6,
                            "[系統提示] 經驗值: "
                                    + (Math.round(c.getPlayer().getEXPMod()) * 100)
                                            * Math.round(c.getPlayer().getStat().expBuff / 100.0)
                                    + "x");
            c.getPlayer()
                    .dropMessage(
                            6,
                            "[系統提示] 掉寶率: "
                                    + (Math.round(c.getPlayer().getDropMod()) * 100)
                                            * Math.round(c.getPlayer().getStat().dropBuff / 100.0)
                                    + "x");
            c.getPlayer()
                    .dropMessage(
                            6,
                            "[系統提示] 楓幣: "
                                    + Math.round(c.getPlayer().getStat().mesoBuff / 100.0) * 100
                                    + "x");
            c.getPlayer()
                    .dropMessage(
                            6, "[系統提示] Ping: " + c.getPlayer().getClient().getLatency() + " 毫秒");
            return 1;
        }
    }

    public static class mob extends 怪物 {}

    public static class 怪物 extends CommandExecute {

        @Override
        public int execute(MapleClient c, String[] splitted) {
            MapleMonster mob = null;
            for (final MapleMapObject monstermo :
                    c.getPlayer()
                            .getMap()
                            .getMapObjectsInRange(
                                    c.getPlayer().getPosition(),
                                    100000,
                                    Arrays.asList(MapleMapObjectType.MONSTER))) {
                mob = (MapleMonster) monstermo;
                if (mob.isAlive()) {
                    c.getPlayer().dropMessage(6, "怪物 " + mob.toString());
                    break; // only one
                }
            }
            if (mob == null) {
                c.getPlayer().dropMessage(6, "[系統提示] 找不到地圖上的怪物");
            }
            return 1;
        }
    }

    public static class fm extends 自由 {}

    public static class 自由 extends CommandExecute {

        @Override
        public int execute(MapleClient c, String[] splitted) {
            if (c.getPlayer().getLevel() < 10 && c.getPlayer().getJob() != 200) {
                c.getPlayer().dropMessage(5, "[系統提示] 至少需LV.10才能進入自由市場");
                return 0;
            }
            if (c.getPlayer().getMap().getSquadByMap() != null
                    || c.getPlayer().getEventInstance() != null
                    || c.getPlayer().getMap().getEMByMap() != null
                    || c.getPlayer().getMapId() >= 990000000) {
                c.getPlayer().dropMessage(5, "[系統提示] 禁止使用傳送至自由功能");
                return 0;
            }

            if ((c.getPlayer().getMapId() >= 680000210 && c.getPlayer().getMapId() <= 680000502)
                    || (c.getPlayer().getMapId() / 1000 == 980000
                            && c.getPlayer().getMapId() != 980000000)
                    || (c.getPlayer().getMapId() / 100 == 1030008)
                    || (c.getPlayer().getMapId() / 100 == 922010)
                    || (c.getPlayer().getMapId() / 10 == 13003000)) {
                c.getPlayer().dropMessage(5, "[系統提示] 禁止使用傳送至自由功能");
                return 0;
            }
            c.getPlayer()
                    .saveLocation(
                            SavedLocationType.FREE_MARKET,
                            c.getPlayer().getMap().getReturnMap().getId());
            MapleMap map = c.getChannelServer().getMapFactory().getMap(910000000);
            c.getPlayer().changeMap(map, map.getPortal(0));
            return 1;
        }
    }

    public static class jk_hm extends CommandExecute {

        @Override
        public int execute(MapleClient c, String[] splitted) {
            c.getPlayer().clearHired();
            c.getPlayer().dropMessage("[系統提示] 卡精靈商人已經解除");
            return 1;
        }
    }

    public static class save extends 保存 {}

    public static class 保存 extends CommandExecute {

        @Override
        public int execute(MapleClient c, String[] splitted) {
            try {
                c.getPlayer().dropMessage(5, "[系統提示] 已保存");
            } catch (UnsupportedOperationException ex) {

            }
            return 1;
        }
    }

    public static class expfix extends CommandExecute {

        @Override
        public int execute(MapleClient c, String[] splitted) {
            c.getPlayer().setExp(0);
            c.getPlayer().updateSingleStat(MapleStat.EXP, c.getPlayer().getExp());
            c.getPlayer().dropMessage(5, "[系統提示] 經驗修復完成");
            return 1;
        }
    }

    public static class STR extends DistributeStatCommands {

        public STR() {
            stat = MapleStat.STR;
        }
    }

    public static class DEX extends DistributeStatCommands {

        public DEX() {
            stat = MapleStat.DEX;
        }
    }

    public static class INT extends DistributeStatCommands {

        public INT() {
            stat = MapleStat.INT;
        }
    }

    public static class LUK extends DistributeStatCommands {

        public LUK() {
            stat = MapleStat.LUK;
        }
    }

    public abstract static class DistributeStatCommands extends CommandExecute {

        protected MapleStat stat = null;
        private static int statLim = 30000;

        private void setStat(MapleCharacter player, int amount) {
            switch (stat) {
                case STR:
                    player.getStat().setStr((short) amount);
                    player.updateSingleStat(MapleStat.STR, player.getStat().getStr());
                    break;
                case DEX:
                    player.getStat().setDex((short) amount);
                    player.updateSingleStat(MapleStat.DEX, player.getStat().getDex());
                    break;
                case INT:
                    player.getStat().setInt((short) amount);
                    player.updateSingleStat(MapleStat.INT, player.getStat().getInt());
                    break;
                case LUK:
                    player.getStat().setLuk((short) amount);
                    player.updateSingleStat(MapleStat.LUK, player.getStat().getLuk());
                    break;
            }
        }

        private int getStat(MapleCharacter player) {
            switch (stat) {
                case STR:
                    return player.getStat().getStr();
                case DEX:
                    return player.getStat().getDex();
                case INT:
                    return player.getStat().getInt();
                case LUK:
                    return player.getStat().getLuk();
                default:
                    throw new RuntimeException(); // Will never happen.
            }
        }

        @Override
        public int execute(MapleClient c, String[] splitted) {
            if (splitted.length < 2) {
                c.getPlayer().dropMessage(5, "[系統提示] 指令使用錯誤");
                return 0;
            }
            int change = 0;
            try {
                change = Integer.parseInt(splitted[1]);
            } catch (NumberFormatException nfe) {
                c.getPlayer().dropMessage(5, "[系統提示] 輸入的數字無效.");
                return 0;
            }
            if (change <= 0) {
                c.getPlayer().dropMessage(5, "[系統提示] 您必須輸入一個大於 0 的數字.");
                return 0;
            }
            if (c.getPlayer().getRemainingAp() < change) {
                c.getPlayer().dropMessage(5, "[系統提示] 您的AP不足.");
                return 0;
            }
            if (getStat(c.getPlayer()) + change > statLim) {
                c.getPlayer().dropMessage(5, "[系統提示] 所要分配的AP不能大於 " + statLim + "");
                return 0;
            }
            setStat(c.getPlayer(), getStat(c.getPlayer()) + change);
            c.getPlayer().setRemainingAp((short) (c.getPlayer().getRemainingAp() - change));
            c.getPlayer().updateSingleStat(MapleStat.AVAILABLEAP, c.getPlayer().getRemainingAp());
            c.getPlayer()
                    .dropMessage(
                            5,
                            StringUtil.makeEnumHumanReadable(stat.name()) + "提高了 " + change + "點.");
            return 1;
        }
    }

    public static class petvac extends 寵吸 {}

    public static class 寵吸 extends CommandExecute {

        @Override
        public int execute(MapleClient c, String[] splitted) {
            final MapleCharacter chr = c.getPlayer();
            final boolean now = !chr.isHasVac();
            chr.setHasVac(now);
            chr.dropMessage(5, "[系統提示] 寵物全圖撿取已" + (now ? "開啟" : "關閉"));
            return 1;
        }
    }

    public static class TSmega extends 廣播 {}

    public static class 廣播 extends CommandExecute {

        @Override
        public int execute(MapleClient c, String[] splitted) {
            c.getPlayer().setSmega();
            return 1;
        }
    }

    public static class Sell extends 販賣 {}

    public static class 販賣 extends CommandExecute {

        @Override
        public int execute(MapleClient c, String[] splitted) {
            if (splitted.length < 4) {
                return 0;
            }
            MapleInventory inv;
            MapleInventoryType type;
            String Column = "null";
            int start = -1;
            int end = -1;
            try {
                Column = splitted[1];
                start = Integer.parseInt(splitted[2]);
                end = Integer.parseInt(splitted[3]);
            } catch (NumberFormatException ex) {
            }
            if (start == -1 || end == -1) {
                c.getPlayer()
                        .dropMessage(
                                "@sell  <裝備欄(equip)/消耗欄(use)/裝飾欄(setup)/其他欄(etc)/特殊欄(cash)> <開始格數> <結束格數>");
                return 1;
            }
            if (start < 1) {
                start = 1;
            }
            if (end > 96) {
                end = 96;
            }

            switch (Column) {
                case "裝備欄":
                case "equip":
                    type = MapleInventoryType.EQUIP;
                    break;
                case "消耗欄":
                case "use":
                    type = MapleInventoryType.USE;
                    break;
                case "裝飾欄":
                case "setup":
                    type = MapleInventoryType.SETUP;
                    break;
                case "其他欄":
                case "etc":
                    type = MapleInventoryType.ETC;
                    break;
                case "特殊欄":
                case "cash":
                    type = MapleInventoryType.CASH;
                    break;
                default:
                    type = null;
                    break;
            }
            if (type == null) {
                c.getPlayer()
                        .dropMessage(
                                "@sell  <裝備欄(equip)/消耗欄(use)/裝飾欄(setup)/其他欄(etc)/特殊欄(cash)> <開始格數> <結束格數>");
                return 1;
            }
            inv = c.getPlayer().getInventory(type);
            final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
            int totalMesosGained = 0;

            for (int i = start; i <= end; i++) {
                if (inv.getItem((short) i) != null) {
                    int itemPrice = (int) ii.getPrice(inv.getItem((short) i).getItemId());
                    totalMesosGained += itemPrice;
                    if (!ii.cantSell(inv.getItem((short) i).getItemId())) {
                        if (!ItemConstants.ItemTypes.isRechargable(
                                inv.getItem((short) i).getItemId())) {
                            if (inv.getItem((short) i).getQuantity() > 0) {
                                double price;
                                if (ItemConstants.ItemTypes.isThrowingStar(
                                                inv.getItem((short) i).getItemId())
                                        || ItemConstants.ItemTypes.isBullet(
                                                inv.getItem((short) i).getItemId())) {
                                    price =
                                            ii.getWholePrice(inv.getItem((short) i).getItemId())
                                                    / (double)
                                                            ii.getSlotMax(
                                                                    c,
                                                                    inv.getItem((short) i)
                                                                            .getItemId());
                                } else {
                                    price = ii.getPrice(inv.getItem((short) i).getItemId());
                                }
                                if (inv.getItem((short) i).getItemId() == 2022195) {
                                    price = 1;
                                }
                                if (inv.getItem((short) i).getItemId() == 4031348) {
                                    price = 1;
                                }

                                int quantity = inv.getItem((short) i).getQuantity();
                                int itemId = inv.getItem((short) i).getItemId();
                                MapleInventoryManipulator.removeFromSlot(
                                        c,
                                        type,
                                        (short) i,
                                        inv.getItem((short) i).getQuantity(),
                                        true);

                                int recvMesos = (int) Math.max(Math.ceil(price * quantity), 0);
                                if (Double.compare(price, -1.0) != 0 && recvMesos > 0) {
                                    c.getPlayer().gainMeso(recvMesos, false);
                                }
                            }
                        }
                    }
                }
            }
            c.getPlayer()
                    .dropMessage(
                            6, "您已經販賣了第 " + start + " 格到 " + end + "格得到" + totalMesosGained + "元");
            return 1;
        }
    }

    public static class clear extends 清除道具 {}

    public static class 清除道具 extends CommandExecute {

        @Override
        public int execute(MapleClient c, String[] splitted) {
            if (splitted.length < 4) {
                return 0;
            }
            MapleInventory inv;
            MapleInventoryType type;
            String Column = "null";
            int start = -1;
            int end = -1;
            try {
                Column = splitted[1];
                start = Integer.parseInt(splitted[2]);
                end = Integer.parseInt(splitted[3]);
            } catch (Exception ex) {
            }
            if (start == -1 || end == -1) {
                c.getPlayer().dropMessage("@清除道具 <裝備欄/消耗欄/裝飾欄/其他欄/特殊欄> <開始格數> <結束格數>");
                return 1;
            }
            if (start < 1) {
                start = 1;
            }
            if (end > 96) {
                end = 96;
            }

            switch (Column) {
                case "裝備欄":
                    type = MapleInventoryType.EQUIP;
                    break;
                case "消耗欄":
                    type = MapleInventoryType.USE;
                    break;
                case "裝飾欄":
                    type = MapleInventoryType.SETUP;
                    break;
                case "其他欄":
                    type = MapleInventoryType.ETC;
                    break;
                case "特殊欄":
                    type = MapleInventoryType.CASH;
                    break;
                default:
                    type = null;
                    break;
            }
            if (type == null) {
                c.getPlayer().dropMessage("@清除道具 <裝備欄/消耗欄/裝飾欄/其他欄/特殊欄> <開始格數> <結束格數>");
                return 1;
            }
            inv = c.getPlayer().getInventory(type);

            for (int i = start; i <= end; i++) {
                if (inv.getItem((short) i) != null) {
                    MapleInventoryManipulator.removeFromSlot(
                            c, type, (short) i, inv.getItem((short) i).getQuantity(), true);
                }
            }
            c.getPlayer().dropMessage(6, "您已經清除了第 " + start + " 格到 " + end + "格的" + Column + "道具");
            return 1;
        }
    }

    public static class help extends 幫助 {}

    public static class 幫助 extends CommandExecute {

        @Override
        public int execute(MapleClient c, String[] splitted) {
            c.getPlayer()
                    .dropNPC(
                            9010000,
                            ""
                                    + "\t   #i3994014##i3994018##i3994070##i3994061##i3994005##i3991038##i3991004#\r\n"
                                    + "\t\t  #fMob/0100101.img/move/1##b 親愛的： #h \r\n"
                                    + " #fMob/0100101.img/move/1##k\r\r\n"
                                    + "\t\t#fMob/0130101.img/move/1##g[以下是"
                                    + c.getChannelServer().getServerName()
                                    + " 玩家指令]#k#fMob/0130101.img/move/1#\r\n"
                                    + "\t  #r▇▇▆▅▄▃▂#d萬用指令區#r▂▃▄▅▆▇▇\r\n"
                                    + "\t\t#b@清除道具 <裝備欄/消耗欄/裝飾欄/其他欄/特殊欄> <開始格數> <結束格數>#k - #r<清除背包道具>#k\r\n"
                                    + "\t\t#b@ea#k - #r<解除異常+查看當前狀態>#k\r\n"
                                    + "\t\t#b@在線點數/@jcds#k - #r<領取在線點數>#k\r\n"
                                    + "\t\t#b@mob#k - #r<查看身邊怪物訊息>#k\r\n"
                                    + "\t\t#b@expfix#k - #r<經驗歸零(修復假死)>#k\r\n"
                                    + "\t\t#b@CGM <訊息>#k - #r<傳送訊息給GM>#k\r\n"
                                    + "\t\t#b@jk_hm #k - #r<清除卡精靈商人>#k\r\n"
                                    + "\t\t#b@save#k - #r<存檔>#k\r\n"
                                    + "\t\t#b@TSmega#k - #r<開/關所有廣播>#k\r\n"
                                    + "\t\t#b@dice#k - #r<單骰>#k\r\n"
                                    + "\t\t#b@dice3#k - #r<三骰>#k\r\n"
                                    + "\t\t#b@轉蛋綠廣#k - #r<開關轉蛋綠廣>#k\r\n"
                                    + "\t\t#b@str @dex @int @luk 數量#k - #r<能力分配>#k\r\n"
                                    + "\t\t#b@sell <裝備欄/消耗欄/裝飾欄/其他欄/特殊欄> <開始格數> <結束格數>#k - #r<販賣背包道具>#k\r\n");
            return 1;
        }
    }
}
