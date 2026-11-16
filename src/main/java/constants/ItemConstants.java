package constants;

import client.inventory.MapleWeaponType;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import server.MapleItemInformationProvider;

public class ItemConstants {
    public static boolean isTablet(int itemId) {
        return itemId / 1000 == 2047;
    }

    public static int getSuccessTablet(final int scrollId, final int level) {
        if (scrollId % 1000 / 100 == 2) { // 2047_2_00 = armor, 2047_3_00 = accessory
            return switch (level) {
                case 0 -> 70;
                case 1 -> 55;
                case 2 -> 43;
                case 3 -> 33;
                case 4 -> 26;
                case 5 -> 20;
                case 6 -> 16;
                case 7 -> 12;
                case 8 -> 10;
                default -> 7;
            };
        } else if (scrollId % 1000 / 100 == 3) {
            return switch (level) {
                case 0 -> 70;
                case 1 -> 35;
                case 2 -> 18;
                case 3 -> 12;
                default -> 7;
            };
        } else {
            return switch (level) {
                case 0 -> 70;
                case 1 -> 50; // -20
                case 2 -> 36; // -14
                case 3 -> 26; // -10
                case 4 -> 19; // -7
                case 5 -> 14; // -5
                case 6 -> 10; // -4
                default -> 7; // -3
            };
        }
    }

    public static int getCurseTablet(final int scrollId, final int level) {
        if (scrollId % 1000 / 100 == 2) { // 2047_2_00 = armor, 2047_3_00 = accessory
            return switch (level) {
                case 0 -> 10;
                case 1 -> 12;
                case 2 -> 16;
                case 3 -> 20;
                case 4 -> 26;
                case 5 -> 33;
                case 6 -> 43;
                case 7 -> 55;
                case 8 -> 70;
                default -> 100;
            };
        } else if (scrollId % 1000 / 100 == 3) {
            return switch (level) {
                case 0 -> 12;
                case 1 -> 18;
                case 2 -> 35;
                case 3 -> 70;
                default -> 100;
            };
        } else {
            return switch (level) {
                case 0 -> 10;
                case 1 -> 14; // +4
                case 2 -> 19; // +5
                case 3 -> 26; // +7
                case 4 -> 36; // +10
                case 5 -> 50; // +14
                case 6 -> 70; // +20
                default -> 100; // +30
            };
        }
    }

    public static class DamageSkins {

        private static Map<Integer, Integer> damageSkin = new TreeMap();

        public static Map<Integer, Integer> getDamageSkin() {
            if (damageSkin.isEmpty()) {
                // Effect.wz/BasicEff.img/damageSkin
                damageSkin.put(2431965, 0); // 基本傷害肌膚
                damageSkin.put(2431966, 1); // 數位顯示肌膚
                damageSkin.put(2432084, 1); // 數位顯示字型
                damageSkin.put(2431967, 2); // 克里提亞斯傷害肌膚
                damageSkin.put(2432131, 3); // 組隊任務傷害肌膚
                damageSkin.put(2432153, 4); // 衝擊震撼字型
                damageSkin.put(2432638, 4); // 具有衝擊性的傷害字型
                damageSkin.put(2432659, 4); // 具有衝擊性的傷害字型
                damageSkin.put(2432154, 5); // 甜蜜餅乾字型
                damageSkin.put(2432637, 5); // 傳統韓果傷害字型
                damageSkin.put(2432658, 5); // 傳統韓果傷害字型
                damageSkin.put(2432207, 6); // 鐵壁城牆字型
                damageSkin.put(2432354, 7); // 聖誕快樂傷害字型
                damageSkin.put(2432355, 8); // 雪花飄落字型
                damageSkin.put(2432972, 8); // 雪花傷害肌膚
                damageSkin.put(2432465, 9); // 愛麗西亞的傷害字型
                damageSkin.put(2432479, 10); // 桃樂絲的傷害字型
                damageSkin.put(2432526, 11); // 鍵盤戰士字型
                damageSkin.put(2432639, 11); // 鍵盤戰士傷害字型
                damageSkin.put(2432660, 11); // 鍵盤戰士傷害字型
                damageSkin.put(2432532, 12); // 多彩春風字型
                damageSkin.put(2432592, 13); // 單身部隊傷害字型
                damageSkin.put(2432640, 14); // 雷咪納杉司傷害字型
                damageSkin.put(2432661, 14); // 雷咪納杉司傷害字型
                damageSkin.put(2432710, 15); // 菇菇寶貝傷害字型
                damageSkin.put(2432836, 16); // 王冠傷害字型
                damageSkin.put(2432973, 17); // 灰白傷害肌膚
                damageSkin.put(2433063, 18); // 明星星球傷害肌膚
                damageSkin.put(2433456, 19); // 韓文的傷害字型(效果是中文)
                damageSkin.put(2433178, 20); // 2014萬聖節傷害肌膚
                //            damageSkin.put(2433456, 21); //韓文的傷害字型
                damageSkin.put(2433631, 22); // NENE雞的傷害字型
                damageSkin.put(2433655, 22); // NENE雞的傷害字型
                damageSkin.put(2433981, 28); // 皮卡啾傷害字型
                damageSkin.put(2432591, 1000); // 櫻花浪漫字型傷害肌膚
                damageSkin.put(2432803, 1004); // 濃姬傷害字型(30日)
                damageSkin.put(2432804, 1004); // 濃姬傷害字型(無限期)
                damageSkin.put(2432846, 1005); // 傑特字型交換卷
                damageSkin.put(2433049, 1009); // 初音未來傷害字型
                damageSkin.put(2433038, 1010); // 皇家神獸學院字型
                damageSkin.put(2433165, 1011); // 俠客字型交換卷
                damageSkin.put(2433197, 1012); // 菲歐娜字型交換卷
                damageSkin.put(2433195, 1013); // 橘子節字型交換卷
                damageSkin.put(2433182, 1014); // 萬聖節南瓜字型交換卷
                damageSkin.put(2433183, 1015); // 萬聖節幽靈字型交換卷
                damageSkin.put(2433184, 1016); // 萬聖節掃把字型交換卷
                // 1018 - 跟1014一樣
                damageSkin.put(2433775, 1032); // 殺人鯨傷害字型
                damageSkin.put(2433776, 1033); // 史烏傷害字型
                damageSkin.put(2433828, 1034); // 太陽傷害字型
                damageSkin.put(2433829, 1035); // 雨傷害字型
                damageSkin.put(2433830, 1036); // 彩虹傷害字型
                damageSkin.put(2433831, 1037); // 雪傷害字型
                damageSkin.put(2433832, 1038); // 閃電傷害字型
                damageSkin.put(2433833, 1039); // 風傷害字型
                damageSkin.put(2434004, 1041); // 小筱傷害字型
                damageSkin.put(2434499, 1049); // 月亮傷害字型
                // 1050 - 跟1034一樣
            }
            Map<Integer, Integer> value = new TreeMap();
            value.putAll(damageSkin);
            return value;
        }

        public static int getDamageSkinNumberByItem(int itemid) {
            Map<Integer, Integer> skin = getDamageSkin();
            if (skin.containsKey(itemid)) {
                return skin.get(itemid);
            }
            return -1;
        }

        public static Integer[] getDamageSkinsTradeBlock() {
            Map<Integer, Integer> skin = getDamageSkin();
            ArrayList<Integer> skins = new ArrayList<>();
            for (int s : skin.keySet()) {
                if (MapleItemInformationProvider.getInstance().isDropRestricted(s)) {
                    skins.add(s);
                }
            }
            Integer list[] = new Integer[skins.size()];
            return skins.toArray(list);
        }

        public static boolean isDamageSkin(int itemid) {
            Map<Integer, Integer> skin = getDamageSkin();
            if (skin.containsKey(itemid)) {
                return true;
            }
            return false;
        }
    }

    public static class Scrolls {

        public static boolean canScroll(final int itemId) {
            return itemId / 100000 != 19 && itemId / 100000 != 16; // no mech/taming/dragon
        }

        public static boolean canHammer(final int itemId) {
            switch (itemId) {
                case 1122000:
                case 1122076: // ht, chaos ht
                    return false;
            }
            if (!canScroll(itemId)) {
                return false;
            }
            return true;
        }

        public static int getBonusPotentialScrollSucc(int scrollId) {
            switch (scrollId) {
                case 2048306: // 成功100,3行
                case 2048307: // 成功100
                case 2048315: // 成功100
                case 2048316: // 成功100
                    return 100;
                case 2048313: // 心之項鍊專用,成功80
                    return 80;
                case 2048305: // 成功70,失敗損壞100
                    return 70;
                case 2048309: // 成功60,無損
                case 2048310: // 成功60,失敗損壞100
                case 2048314: // 成功60
                    return 60;
                case 2048308: // 成功50,失敗損壞50
                case 2048311: // 成功50,失敗損壞50
                    return 50;
                case 2048312: // 成功1
                    return 1;
                default:
                    return 0;
            }
        }

        public static int getBonusPotentialScrollCurse(int scrollId) {
            switch (scrollId) {
                case 2048305: // 成功70,失敗損壞100
                case 2048310: // 成功60,失敗損壞100
                    return 100;
                case 2048308: // 成功50,失敗損壞50
                case 2048311: // 成功50,失敗損壞50
                    return 50;
                default:
                    return 0;
            }
        }

        public static int getSuccessTablet(final int scrollId, final int level) {
            switch (scrollId % 1000 / 100) {
                case 2:
                    // 2047_2_00 = armor, 2047_3_00 = accessory
                    switch (level) {
                        case 0:
                            return 70;
                        case 1:
                            return 55;
                        case 2:
                            return 43;
                        case 3:
                            return 33;
                        case 4:
                            return 26;
                        case 5:
                            return 20;
                        case 6:
                            return 16;
                        case 7:
                            return 12;
                        case 8:
                            return 10;
                        default:
                            return 7;
                    }
                case 3:
                    switch (level) {
                        case 0:
                            return 70;
                        case 1:
                            return 35;
                        case 2:
                            return 18;
                        case 3:
                            return 12;
                        default:
                            return 7;
                    }
                default:
                    switch (level) {
                        case 0:
                            return 70;
                        case 1:
                            return 50; // -20
                        case 2:
                            return 36; // -14
                        case 3:
                            return 26; // -10
                        case 4:
                            return 19; // -7
                        case 5:
                            return 14; // -5
                        case 6:
                            return 10; // -4
                        default:
                            return 7; // -3
                    }
            }
        }

        public static int getCurseTablet(final int scrollId, final int level) {
            switch (scrollId % 1000 / 100) {
                case 2:
                    // 2047_2_00 = armor, 2047_3_00 = accessory
                    switch (level) {
                        case 0:
                            return 10;
                        case 1:
                            return 12;
                        case 2:
                            return 16;
                        case 3:
                            return 20;
                        case 4:
                            return 26;
                        case 5:
                            return 33;
                        case 6:
                            return 43;
                        case 7:
                            return 55;
                        case 8:
                            return 70;
                        default:
                            return 100;
                    }
                case 3:
                    switch (level) {
                        case 0:
                            return 12;
                        case 1:
                            return 18;
                        case 2:
                            return 35;
                        case 3:
                            return 70;
                        default:
                            return 100;
                    }
                default:
                    switch (level) {
                        case 0:
                            return 10;
                        case 1:
                            return 14; // +4
                        case 2:
                            return 19; // +5
                        case 3:
                            return 26; // +7
                        case 4:
                            return 36; // +10
                        case 5:
                            return 50; // +14
                        case 6:
                            return 70; // +20
                        default:
                            return 100; // +30
                    }
            }
        }

        public static int getEnhanceTimes(int itemId) {
            MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
            int level = ii.getReqLevel(itemId);
            boolean isSuperiorEquip = false; // ii.isSuperiorEquip(itemId);
            int enhanceTimes = 0;
            if (level >= 0 && level < 95) {
                enhanceTimes = isSuperiorEquip ? 3 : 5;
            } else if (level >= 95 && level < 108) {
                enhanceTimes = isSuperiorEquip ? 5 : 8;
            } else if (level >= 108 && level < 118) {
                enhanceTimes = isSuperiorEquip ? 8 : 10;
            } else if (level >= 118 && level < 128) {
                enhanceTimes = isSuperiorEquip ? 10 : 15;
            } else if (level >= 128 && level < 138) {
                enhanceTimes = isSuperiorEquip ? 12 : 20;
            } else if (level >= 138) {
                enhanceTimes = isSuperiorEquip ? 15 : 25;
            }
            return enhanceTimes;
        }
    }

    public static class EquipSets {

        public static ArrayList<Integer> get6YearSet() {
            int[] set = {
                1462116, 1342039, 1402109, 1472139, 1332147, 1322105, 1442135, 1452128, 1312071,
                1382123, 1492100, 1372099, 1432098, 1422072, 1302172, 1482101, 1412070
            };
            ArrayList<Integer> list = new ArrayList();
            for (int i : set) {
                list.add(i);
            }
            return list;
        }

        public static ArrayList<Integer> get7YearSet() {
            int[] set = {
                1003243, 1052358, 1072522, 1082315, 1102295, 1132093, 1152061, 1332145, 1402107,
                        1442133,
                1462114, 1472137, 1532070, 1522066, 1452126, 1312069, 1382121, 1492098, 1372097,
                        1362058,
                1432096, 1422070, 1302170, 1482099, 1412068
            };
            ArrayList<Integer> list = new ArrayList();
            for (int i : set) {
                list.add(i);
            }
            return list;
        }

        public static ArrayList<Integer> get8YearSet() {
            int[] set = {
                1462159, 1462156, 1402145, 1402151, 1052461, 1052457, 1532073, 1532074, 1472177,
                        1472179,
                1332186, 1332193, 1322154, 1322162, 1442173, 1442182, 1522068, 1522071, 1452165,
                        1312114,
                1312116, 1382160, 1132154, 1132151, 1072666, 1072660, 1212069, 1212068, 1492152,
                        1492138,
                1372139, 1372131, 1222063, 1222064, 1082433, 1082430, 1362060, 1362067, 1432138,
                        1432135,
                1152088, 1152089, 1003529, 1003552, 1422107, 1422105, 1232070, 1232063, 1302227,
                        1302212,
                1113036, 1113035, 1112743, 1112742, 1482140, 1482138, 1242048, 1242075, 1412102,
                        1412014,
                1102394, 1102441
            };
            ArrayList<Integer> list = new ArrayList();
            for (int i : set) {
                list.add(i);
            }
            return list;
        }

        public static ArrayList<Integer> get10YearSet() {
            int[] set = {
                1004172, // 帽子
                1012471, // 臉飾
                1052758, // 套服
                1102691, // 披風
                1122280, // 墜飾
                1302304, // 單手劍
                1312179, // 單手斧
                1322230, // 單手棍
                1332254, // 短劍
                1372201, // 短杖
                1382239, // 长杖
                1402229, // 雙手劍
                1412158, // 雙手斧
                1422165, // 雙手棍
                1432194, // 槍
                1442248, // 矛
                1452232, // 弓
                1462219, // 弩
                1472241, // 拳套
                1482196, // 指虎
                1492205, // 火槍
            };
            ArrayList<Integer> list = new ArrayList();
            for (int i : set) {
                list.add(i);
            }
            return list;
        }
    }

    public static class ItemTypes {

        public static boolean isHat(int itemid) {
            return itemid / 10000 == 100;
        }

        public static boolean isFaceAccessory(int itemid) {
            return itemid / 10000 == 101;
        }

        public static boolean isEyeAccessory(int itemid) {
            return itemid / 10000 == 102;
        }

        public static boolean isEarring(int itemid) {
            return itemid / 10000 == 103;
        }

        public static boolean isTopE(int itemid) {
            return itemid / 10000 == 104;
        }

        public static boolean isOverallE(int itemId) {
            return itemId / 10000 == 105;
        }

        public static boolean isBottomE(int itemid) {
            return itemid / 10000 == 106;
        }

        public static boolean isShoes(int itemid) {
            return itemid / 10000 == 107;
        }

        public static boolean isGlove(int itemid) {
            return itemid / 10000 == 108;
        }

        public static boolean isShield(int itemid) {
            return itemid / 10000 == 109;
        }

        public static boolean isCape(int itemid) {
            return itemid / 10000 == 110;
        }

        public static boolean isRing(int itemid) {
            return itemid / 10000 == 111;
        }

        public static boolean isPendant(int itemid) {
            return itemid / 10000 == 112;
        }

        public static boolean isBelt(int itemid) {
            return itemid / 10000 == 113;
        }

        public static boolean isMedal(int itemid) {
            return itemid / 10000 == 114;
        }

        public static boolean isSword_1H(final int itemid) {
            return itemid / 10000 == 130;
        }

        public static boolean isAxe_1H(final int itemid) {
            return itemid / 10000 == 131;
        }

        public static boolean isBlunt_1H(final int itemid) {
            return itemid / 10000 == 132;
        }

        public static boolean isDagger(final int itemid) {
            return itemid / 10000 == 133;
        }

        public static boolean isWand(final int itemid) {
            return itemid / 10000 == 137;
        }

        public static boolean isStaff(final int itemid) {
            return itemid / 10000 == 138;
        }

        public static boolean isSword_2H(final int itemid) {
            return itemid / 10000 == 140;
        }

        public static boolean isAxe_2H(final int itemid) {
            return itemid / 10000 == 141;
        }

        public static boolean isBlunt_2H(final int itemid) {
            return itemid / 10000 == 142;
        }

        public static boolean isSpear(final int itemid) {
            return itemid / 10000 == 143;
        }

        public static boolean isPolearm(final int itemid) {
            return itemid / 10000 == 144;
        }

        public static boolean isBow(final int itemid) {
            return itemid / 10000 == 145;
        }

        public static boolean isCrossbow(final int itemid) {
            return itemid / 10000 == 146;
        }

        public static boolean isClaw(final int itemid) {
            return itemid / 10000 == 147;
        }

        public static boolean isKnuckle(final int itemid) {
            return itemid / 10000 == 148;
        }

        public static boolean isGun(final int itemid) {
            return itemid / 10000 == 149;
        }

        public static boolean isRobot(int itemid) {
            return itemid / 10000 == 166;
        }

        public static boolean isRobotHeart(int itemId) {
            return itemId / 10000 == 167;
        }

        public static boolean isPetEquip(int itemid) {
            return itemid / 10000 >= 180 && itemid / 10000 <= 183;
        }

        public static boolean isMount(int itemid) {
            return itemid / 10000 == 190 || itemid / 10000 == 193;
        }

        public static boolean isSaddle(int itemid) {
            return itemid / 10000 == 191;
        }

        public static boolean isThrowingStar(int itemid) {
            return itemid / 10000 == 207;
        }

        public static boolean isBullet(int itemid) {
            return itemid / 10000 == 233;
        }

        public static boolean isPet(int id) {
            return id / 10000 == 500;
        }

        public static boolean isEquipments(int itemid) {
            return isHat(itemid)
                    || isTopE(itemid)
                    || isOverallE(itemid)
                    || isBottomE(itemid)
                    || isShoes(itemid)
                    || isGlove(itemid)
                    || isCape(itemid);
        }

        public static boolean isAccessories(int itemid) {
            return isFaceAccessory(itemid)
                    || isEyeAccessory(itemid)
                    || isEarring(itemid)
                    || isRing(itemid)
                    || isPendant(itemid)
                    || isBelt(itemid)
                    || isMedal(itemid);
        }

        public static boolean isSecondhands(int itemid) {
            return isShield(itemid);
        }

        public static boolean isWeapon(int itemid) {
            return isSword_1H(itemid)
                    || isAxe_1H(itemid)
                    || isBlunt_1H(itemid)
                    || isDagger(itemid)
                    || isWand(itemid)
                    || isStaff(itemid)
                    || isSword_2H(itemid)
                    || isAxe_2H(itemid)
                    || isBlunt_2H(itemid)
                    || isSpear(itemid)
                    || isPolearm(itemid)
                    || isBow(itemid)
                    || isCrossbow(itemid)
                    || isClaw(itemid)
                    || isKnuckle(itemid)
                    || isGun(itemid);
        }

        public static boolean isRechargable(int itemid) {
            return (isThrowingStar(itemid)) || (isBullet(itemid));
        }

        public static boolean isArrowForCrossBow(final int itemId) {
            return itemId >= 2061000 && itemId < 2062000;
        }

        public static boolean isArrowForBow(final int itemId) {
            return itemId >= 2060000 && itemId < 2061000;
        }

        public static boolean isWeapon_1H(int itemid) {
            return isWeapon(itemid) && !isTwoHanded(itemid);
        }

        public static boolean isTwoHanded(final int itemid) {
            return isSword_2H(itemid)
                    || isAxe_2H(itemid)
                    || isBlunt_2H(itemid)
                    || isSpear(itemid)
                    || isPolearm(itemid)
                    || isBow(itemid)
                    || isCrossbow(itemid)
                    || isClaw(itemid)
                    || isKnuckle(itemid)
                    || isGun(itemid);
        }

        public static boolean isADWeapon(int itemid) {
            return isWeapon(itemid) && !isAPWeapon(itemid);
        }

        public static boolean isAPWeapon(int itemid) {
            return isWand(itemid) || isStaff(itemid);
        }

        public static boolean isMountItem(int itemid) {
            return isMount(itemid) || isSaddle(itemid);
        }

        public static boolean isEquip(int itemid) {
            return (itemid / 10000 >= 100) && (itemid / 10000 < 200);
        }

        public static boolean isUse(int itemid) {
            return (itemid / 10000 >= 200) && (itemid / 10000 < 300);
        }

        public static boolean isSetup(int itemid) {
            return (itemid / 10000 >= 300) && (itemid / 10000 < 400);
        }

        public static boolean isETC(int itemid) {
            return (itemid / 10000 >= 400) && (itemid / 10000 < 500);
        }

        public static boolean isCash(int itemid) {
            return itemid / 1000 >= 500;
        }

        public static boolean isFriendshipRing(int itemid) {
            switch (itemid) {
                case 1112800:
                case 1112801:
                case 1112802:
                case 1112810:
                case 1112811:
                case 1112816:
                case 1112817:
                    return true;
                case 1112803:
                case 1112804:
                case 1112805:
                case 1112806:
                case 1112807:
                case 1112808:
                case 1112809:
                case 1112812:
                case 1112813:
                case 1112814:
                case 1112815:
            }
            return false;
        }

        public static boolean isCrushRing(int itemid) {
            switch (itemid) {
                case 1112001:
                case 1112002:
                case 1112003:
                case 1112005:
                case 1112006:
                case 1112007:
                case 1112012:
                case 1112015:
                case 1048000:
                case 1048001:
                case 1048002:
                    return true;
                case 1112004:
                case 1112008:
                case 1112009:
                case 1112010:
                case 1112011:
                case 1112013:
                case 1112014:
            }
            return false;
        }

        public static boolean isMRing(int itemid) {
            switch (itemid) {
                case 1112300:
                case 1112301:
                case 1112302:
                case 1112303:
                case 1112304:
                case 1112305:
                case 1112306:
                case 1112307:
                case 1112308:
                case 1112309:
                case 1112310:
                case 1112311:
                case 1112315:
                case 1112316:
                case 1112317:
                case 1112318:
                case 1112319:
                case 1112320:
                case 1112803:
                case 1112806:
                case 1112807:
                case 1112808:
                case 1112809:
                    return true;
            }
            return false;
        }

        public static boolean isEffectRing(int itemid) {
            return isFriendshipRing(itemid) || isCrushRing(itemid) || isMRing(itemid);
        }

        public static boolean isGM_EQP(final int itemid) {
            switch (itemid) {
                case 1002140: // 維澤特帽
                case 1003142: // 維澤特帽
                case 1003274: // 維澤特帽
                case 1042003: // 維澤特西裝
                case 1042223: // 維澤特西裝
                case 1062007: // 維澤特西褲
                case 1062140: // 維澤特西褲
                case 1322013: // 維澤特手提包
                case 1322106: // 維澤特手提包
                case 1002959:
                    return true;
            }
            return false;
        }

        public static boolean isTownScroll(final int itemid) {
            return itemid >= 2030000 && itemid < 2040000;
        }

        public static boolean isUpgradeScroll(int itemid) {
            return itemid >= 2040000 && itemid <= 2048100 && !阿斯旺卷軸(itemid);
        }

        //        public static boolean isUpgradeScroll(final int id) {
        //            return id >= 2040000 && id < 2050000;
        //        }
        public static boolean 阿斯旺卷軸(int itemid) {
            return itemid >= 2046060 && itemid <= 2046069
                    || itemid >= 2046141 && itemid <= 2046145
                    || itemid >= 2046519 && itemid <= 2046530
                    || itemid >= 2046701 && itemid <= 2046712;
        }

        public static boolean isEnhanceScroll(int itemid) { // 龍騎士獲得的強化牌板
            return itemid >= 2047000 && itemid < 2047310;
        }

        public static boolean isWhiteScroll(int itemid) {
            return itemid / 100 == 20490;
        }

        public static boolean isChaosScroll(int itemid) {
            if (itemid >= 2049105 && itemid <= 2049110) {
                return false;
            }
            return itemid / 100 == 20491 || itemid == 2040126;
        }

        public static boolean isGChaosScroll(int itemid) {
            if (!isChaosScroll(itemid)) {
                return false;
            }
            switch (itemid) {
                case 2049122: // 樂觀的混卷軸50%
                case 2049129: // 樂觀的混卷軸 50%
                case 2049130: // 樂觀的混卷軸 30%
                case 2049131: // 樂觀的混卷軸 20%
                case 2049135: // 驚訝樂觀的混卷軸 20%
                case 2049136: // 驚訝樂觀的混卷軸 20%
                case 2049137: // 驚訝樂觀的混卷軸 40%
                case 2049141: // 珠寶戒指的樂觀的混卷軸 30%
                case 2049155: // 珠寶工藝樂觀的混卷軸 30%
                case 2049153: // 驚訝樂觀的混卷軸
                    return true;
            }
            return false;
        }

        public static boolean isAccessoryScroll(int itemid) {
            return itemid / 100 == 20492;
        }

        public static boolean isEquipScroll(int itemid) {
            return itemid / 100 == 20493;
        }

        public static boolean isSpecialScroll(final int scrollId) {
            switch (scrollId) {
                case 2040727: // Spikes on show
                case 2041058: // Cape for Cold protection
                    return true;
            }
            return false;
        }

        public static boolean 鐵鎚(int itemid) {
            return itemid / 10000 == 247;
        }

        public static boolean isPotentialScroll(int itemid) {
            return itemid / 100 == 20494 || itemid / 100 == 20497 || itemid == 5534000;
        }

        public static boolean 回真卷軸(int itemid) {
            switch (itemid) {
                case 5064200: // 完美回真卡
                case 5064201: // 星光回真卷軸
                    return true;
                default:
                    return itemid / 100 == 20496;
            }
        }

        public static boolean 幸運日卷軸(int itemid) {
            switch (itemid) {
                case 5063100: // 幸運保護券(防爆+幸運)
                case 5068000: // 寵物專用幸運日卷軸
                    return true;
                default:
                    return itemid / 1000 == 2530;
            }
        }

        public static boolean 保護卷軸(int itemid) {
            switch (itemid) {
                case 5063100: // 幸運保護券(防爆+幸運)
                case 5064000: // 裝備保護卷軸(無法用於尊貴或12星以上)
                case 5064002: // 星光裝備保護卷軸(105以下的裝備且無法用於尊貴或12星以上)
                case 5064003: // 尊貴裝備保護卷軸(無法用於非尊貴以及尊貴7星以上)
                case 5064004: // [MS特價] 裝備保護卷軸(無法用於尊貴或12星以上)
                    return true;
                default:
                    return itemid / 1000 == 2531;
            }
        }

        public static boolean 安全卷軸(int itemid) {
            switch (itemid) {
                case 5064100: // 安全盾牌卷軸
                case 5064101: // 星光安全盾牌卷軸(105以下的裝備)
                case 5068100: // 寵物安全盾牌卷軸
                    return true;
                default:
                    return itemid / 1000 == 2532;
            }
        }

        public static boolean 卷軸保護卡(int itemid) {
            switch (itemid) {
                case 5064300: // 卷軸保護卡
                case 5064301: // 星光卷軸保護卡(105以下的裝備)
                case 5068200: // 寵物卷軸保護卡
                    return true;
            }
            return false;
        }

        public static boolean 靈魂卷軸_附魔器(int itemid) {
            return itemid / 1000 == 2590;
        }

        public static boolean 靈魂寶珠(int itemid) {
            return itemid / 1000 == 2591;
        }

        public static boolean TMS特殊卷軸(int itemid) {
            return itemid / 10000 == 261;
        }

        public static boolean 特殊卷軸(final int itemid) {
            return 幸運日卷軸(itemid) || 保護卷軸(itemid) || 安全卷軸(itemid) || 卷軸保護卡(itemid);
        }

        public static boolean 特殊潛能道具(final int itemid) {
            if (itemid / 100 == 10121
                    && itemid % 100 >= 64
                    && itemid % 100 <= 74
                    && itemid % 100 != 65
                    && itemid % 100 != 66) { // 恰吉
                return true;
            } else if (itemid / 10 == 112212 && (itemid % 10 >= 2 && itemid % 10 <= 6)) { // 真. 楓葉之心
                return true;
            } else if (itemid >= 1122224 && itemid <= 1122245) { // 心之項鍊
                return true;
            } else if (itemid / 10 == 101244) { // 卡爾頓的鬍子
                return true;
            }
            return false;
        }
    }

    public static int getChaosNumber(int itemId) {
        return itemId == 2049116 ? 10 : 5;
    }

    public static boolean isCleanSlate(int itemId) {
        return itemId / 100 == 20490;
    }

    public static MapleWeaponType isWeaponType(final int itemid) {
        if (ItemTypes.isSword_1H(itemid)) {
            return MapleWeaponType.SWORD1H;
        }
        if (ItemTypes.isAxe_1H(itemid)) {
            return MapleWeaponType.AXE1H;
        }
        if (ItemTypes.isBlunt_1H(itemid)) {
            return MapleWeaponType.BLUNT1H;
        }
        if (ItemTypes.isDagger(itemid)) {
            return MapleWeaponType.DAGGER;
        }
        if (ItemTypes.isWand(itemid)) {
            return MapleWeaponType.WAND;
        }
        if (ItemTypes.isStaff(itemid)) {
            return MapleWeaponType.STAFF;
        }
        if (ItemTypes.isSword_2H(itemid)) {
            return MapleWeaponType.AXE2H;
        }
        if (ItemTypes.isAxe_2H(itemid)) {
            return MapleWeaponType.AXE2H;
        }
        if (ItemTypes.isBlunt_2H(itemid)) {
            return MapleWeaponType.BLUNT2H;
        }
        if (ItemTypes.isSpear(itemid)) {
            return MapleWeaponType.SPEAR;
        }
        if (ItemTypes.isPolearm(itemid)) {
            return MapleWeaponType.POLE_ARM;
        }
        if (ItemTypes.isBow(itemid)) {
            return MapleWeaponType.BOW;
        }
        if (ItemTypes.isCrossbow(itemid)) {
            return MapleWeaponType.CROSSBOW;
        }
        if (ItemTypes.isClaw(itemid)) {
            return MapleWeaponType.CLAW;
        }
        if (ItemTypes.isKnuckle(itemid)) {
            return MapleWeaponType.KNUCKLE;
        }
        if (ItemTypes.isGun(itemid)) {
            return MapleWeaponType.GUN;
        }
        return MapleWeaponType.NOT_A_WEAPON;
    }
}
