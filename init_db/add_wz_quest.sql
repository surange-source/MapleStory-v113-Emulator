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

 Date: 26/10/2026 04:32:08
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for wz_questactdata
-- ----------------------------
DROP TABLE IF EXISTS `wz_questactdata`;
CREATE TABLE `wz_questactdata`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `questid` int NOT NULL DEFAULT 0,
  `name` varchar(127) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `type` tinyint(1) NOT NULL DEFAULT 0,
  `intStore` int NOT NULL DEFAULT 0,
  `applicableJobs` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `uniqueid` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `quests_ibfk_2`(`questid`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for wz_questactitemdata
-- ----------------------------
DROP TABLE IF EXISTS `wz_questactitemdata`;
CREATE TABLE `wz_questactitemdata`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `itemid` int NOT NULL DEFAULT 0,
  `count` smallint NOT NULL DEFAULT 0,
  `period` int NOT NULL DEFAULT 0,
  `gender` tinyint(1) NOT NULL DEFAULT 2,
  `job` int NOT NULL DEFAULT -1,
  `jobEx` int NOT NULL DEFAULT -1,
  `prop` int NOT NULL DEFAULT -1,
  `uniqueid` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = FIXED;

-- ----------------------------
-- Table structure for wz_questactquestdata
-- ----------------------------
DROP TABLE IF EXISTS `wz_questactquestdata`;
CREATE TABLE `wz_questactquestdata`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `quest` int NOT NULL DEFAULT 0,
  `state` tinyint(1) NOT NULL DEFAULT 2,
  `uniqueid` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = FIXED;

-- ----------------------------
-- Table structure for wz_questactskilldata
-- ----------------------------
DROP TABLE IF EXISTS `wz_questactskilldata`;
CREATE TABLE `wz_questactskilldata`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `skillid` int NOT NULL DEFAULT 0,
  `skillLevel` int NOT NULL DEFAULT -1,
  `masterLevel` int NOT NULL DEFAULT -1,
  `uniqueid` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = FIXED;

-- ----------------------------
-- Table structure for wz_questdata
-- ----------------------------
DROP TABLE IF EXISTS `wz_questdata`;
CREATE TABLE `wz_questdata`  (
  `questid` int NOT NULL,
  `name` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `autoStart` tinyint(1) NOT NULL DEFAULT 0,
  `autoPreComplete` tinyint(1) NOT NULL DEFAULT 0,
  `viewMedalItem` int NOT NULL DEFAULT 0,
  `selectedSkillID` int NOT NULL DEFAULT 0,
  `blocked` tinyint(1) NOT NULL DEFAULT 0,
  `autoAccept` tinyint(1) NOT NULL DEFAULT 0,
  `autoComplete` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`questid`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for wz_questpartydata
-- ----------------------------
DROP TABLE IF EXISTS `wz_questpartydata`;
CREATE TABLE `wz_questpartydata`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `questid` int NOT NULL DEFAULT 0,
  `rank` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `mode` varchar(13) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `property` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `value` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `quests_ibfk_7`(`questid`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for wz_questreqdata
-- ----------------------------
DROP TABLE IF EXISTS `wz_questreqdata`;
CREATE TABLE `wz_questreqdata`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `questid` int NOT NULL DEFAULT 0,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `type` tinyint(1) NOT NULL DEFAULT 0,
  `intStore` int NOT NULL DEFAULT -1,
  `stringStore` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `intStoresFirst` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `intStoresSecond` varchar(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `quests_ibfk_1`(`questid`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
