/*
 Navicat Premium Dump SQL

 Source Server         : 5.7
 Source Server Type    : MySQL
 Source Server Version : 80411 (8.4.11)
 Source Host           : localhost:3306
 Source Schema         : tsmcmaplestoryv113

 Target Server Type    : MySQL
 Target Server Version : 80411 (8.4.11)
 File Encoding         : 65001

 Date: 26/10/2026 04:49:07
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for wz_itemadddata
-- ----------------------------
DROP TABLE IF EXISTS `wz_itemadddata`;
CREATE TABLE `wz_itemadddata`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `itemid` int NOT NULL,
  `key` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `subKey` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `value` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for wz_itemdata
-- ----------------------------
DROP TABLE IF EXISTS `wz_itemdata`;
CREATE TABLE `wz_itemdata`  (
  `itemid` int NOT NULL,
  `name` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
  `msg` varchar(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `desc` varchar(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `slotMax` smallint NOT NULL DEFAULT 1,
  `price` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '-1.0',
  `wholePrice` int NOT NULL DEFAULT -1,
  `stateChange` int NOT NULL DEFAULT 0,
  `flags` smallint NOT NULL DEFAULT 0,
  `karma` tinyint(1) NOT NULL DEFAULT 0,
  `meso` int NOT NULL DEFAULT 0,
  `monsterBook` int NOT NULL DEFAULT 0,
  `itemMakeLevel` smallint NOT NULL DEFAULT 0,
  `questId` int NOT NULL DEFAULT 0,
  `scrollReqs` varchar(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `consumeItem` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
  `totalprob` int NOT NULL DEFAULT 0,
  `incSkill` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `replaceid` int NOT NULL DEFAULT 0,
  `replacemsg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `create` int NOT NULL DEFAULT 0,
  `afterImage` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  PRIMARY KEY (`itemid`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for wz_itemequipdata
-- ----------------------------
DROP TABLE IF EXISTS `wz_itemequipdata`;
CREATE TABLE `wz_itemequipdata`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `itemid` int NOT NULL,
  `itemLevel` int NOT NULL DEFAULT -1,
  `key` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `value` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for wz_itemrewarddata
-- ----------------------------
DROP TABLE IF EXISTS `wz_itemrewarddata`;
CREATE TABLE `wz_itemrewarddata`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `itemid` int NOT NULL,
  `item` int NOT NULL,
  `prob` int NOT NULL DEFAULT 0,
  `quantity` smallint NOT NULL DEFAULT 0,
  `period` int NOT NULL DEFAULT -1,
  `worldMsg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `effect` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
