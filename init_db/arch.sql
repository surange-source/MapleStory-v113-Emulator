/*
 Navicat Premium Dump SQL

 Source Server         : 5.7
 Source Server Type    : MySQL
 Source Server Version : 80411 (8.4.11)
 Source Host           : localhost:3306
 Source Schema         : cwbwmsv113

 Target Server Type    : MySQL
 Target Server Version : 80411 (8.4.11)
 File Encoding         : 65001

 Date: 22/10/2026 02:09:05
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for accounts
-- ----------------------------
DROP TABLE IF EXISTS `accounts`;
CREATE TABLE `accounts`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  `password` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  `salt` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `2ndpassword` varchar(134) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `salt2` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `loggedin` tinyint UNSIGNED NOT NULL DEFAULT 0,
  `lastlogin` timestamp NULL DEFAULT NULL,
  `createdat` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `birthday` date NULL DEFAULT NULL,
  `banned` tinyint(1) NOT NULL DEFAULT 0,
  `banreason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `gm` tinyint(1) NOT NULL DEFAULT 0,
  `email` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `macs` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `tempban` timestamp NULL DEFAULT NULL,
  `greason` tinyint UNSIGNED NULL DEFAULT NULL,
  `ACash` int NULL DEFAULT NULL,
  `mPoints` int NULL DEFAULT NULL,
  `gender` tinyint UNSIGNED NOT NULL DEFAULT 0,
  `SessionIP` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `points` int NOT NULL DEFAULT 0,
  `vpoints` int NOT NULL DEFAULT 0,
  `lastlogon` timestamp NULL DEFAULT NULL,
  `facebook_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `access_token` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '',
  `password_otp` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '',
  `expiration` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `name`(`name` ASC) USING BTREE,
  INDEX `ranking1`(`id` ASC, `banned` ASC, `gm` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for achievements
-- ----------------------------
DROP TABLE IF EXISTS `achievements`;
CREATE TABLE `achievements`  (
  `achievementid` int NOT NULL DEFAULT 0,
  `charid` int NOT NULL DEFAULT 0,
  `accountid` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`achievementid`, `charid`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for alliances
-- ----------------------------
DROP TABLE IF EXISTS `alliances`;
CREATE TABLE `alliances`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(13) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `leaderid` int NOT NULL,
  `guild1` int NOT NULL,
  `guild2` int NOT NULL,
  `guild3` int NOT NULL DEFAULT 0,
  `guild4` int NOT NULL DEFAULT 0,
  `guild5` int NOT NULL DEFAULT 0,
  `rank1` varchar(13) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '公會長',
  `rank2` varchar(13) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '公會副會長',
  `rank3` varchar(13) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '公會成員',
  `rank4` varchar(13) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '公會成員',
  `rank5` varchar(13) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '公會成員',
  `capacity` int NOT NULL DEFAULT 2,
  `notice` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for auth_server_channel
-- ----------------------------
DROP TABLE IF EXISTS `auth_server_channel`;
CREATE TABLE `auth_server_channel`  (
  `channelid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `world` int NOT NULL DEFAULT 0,
  `number` int NULL DEFAULT NULL,
  `key` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  PRIMARY KEY (`channelid`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for auth_server_channel_ip
-- ----------------------------
DROP TABLE IF EXISTS `auth_server_channel_ip`;
CREATE TABLE `auth_server_channel_ip`  (
  `channelconfigid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `channelid` int UNSIGNED NOT NULL DEFAULT 0,
  `name` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `value` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  PRIMARY KEY (`channelconfigid`) USING BTREE,
  INDEX `channelid`(`channelid`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for auth_server_cs
-- ----------------------------
DROP TABLE IF EXISTS `auth_server_cs`;
CREATE TABLE `auth_server_cs`  (
  `CashShopServerId` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `key` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `world` int UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`CashShopServerId`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for auth_server_login
-- ----------------------------
DROP TABLE IF EXISTS `auth_server_login`;
CREATE TABLE `auth_server_login`  (
  `loginserverid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `key` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  `world` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`loginserverid`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for auth_server_mts
-- ----------------------------
DROP TABLE IF EXISTS `auth_server_mts`;
CREATE TABLE `auth_server_mts`  (
  `MTSServerId` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `key` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `world` int UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`MTSServerId`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bbs_replies
-- ----------------------------
DROP TABLE IF EXISTS `bbs_replies`;
CREATE TABLE `bbs_replies`  (
  `replyid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `threadid` int UNSIGNED NOT NULL,
  `postercid` int UNSIGNED NOT NULL,
  `timestamp` bigint UNSIGNED NOT NULL,
  `content` varchar(26) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  `guildid` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`replyid`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for bbs_threads
-- ----------------------------
DROP TABLE IF EXISTS `bbs_threads`;
CREATE TABLE `bbs_threads`  (
  `threadid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `postercid` int UNSIGNED NOT NULL,
  `name` varchar(26) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  `timestamp` bigint UNSIGNED NOT NULL,
  `icon` smallint UNSIGNED NOT NULL,
  `startpost` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `guildid` int UNSIGNED NOT NULL,
  `localthreadid` int UNSIGNED NOT NULL,
  PRIMARY KEY (`threadid`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for blocklogin
-- ----------------------------
DROP TABLE IF EXISTS `blocklogin`;
CREATE TABLE `blocklogin`  (
  `account` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `blocktime` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `unblocktime` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `ip` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `active` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for blockuse
-- ----------------------------
DROP TABLE IF EXISTS `blockuse`;
CREATE TABLE `blockuse`  (
  `account` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `blocktime` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `unblocktime` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `ip` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `usetype` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `active` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bosslog
-- ----------------------------
DROP TABLE IF EXISTS `bosslog`;
CREATE TABLE `bosslog`  (
  `bosslogid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `characterid` int UNSIGNED NOT NULL,
  `bossid` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `lastattempt` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`bosslogid`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for buddies
-- ----------------------------
DROP TABLE IF EXISTS `buddies`;
CREATE TABLE `buddies`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `characterid` int NOT NULL,
  `buddyid` int NOT NULL,
  `pending` tinyint NOT NULL DEFAULT 0,
  `groupname` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '其他',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `buddies_ibfk_1`(`characterid` ASC) USING BTREE,
  CONSTRAINT `buddies_ibfk_1` FOREIGN KEY (`characterid`) REFERENCES `characters` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for cashshop_limit_sell
-- ----------------------------
DROP TABLE IF EXISTS `cashshop_limit_sell`;
CREATE TABLE `cashshop_limit_sell`  (
  `serial` int NOT NULL,
  `amount` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`serial`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Fixed;

-- ----------------------------
-- Table structure for cashshop_modified_items
-- ----------------------------
DROP TABLE IF EXISTS `cashshop_modified_items`;
CREATE TABLE `cashshop_modified_items`  (
  `name` varchar(70) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `serial` int NOT NULL,
  `discount_price` int NOT NULL DEFAULT -1,
  `mark` tinyint(1) NOT NULL DEFAULT -1,
  `showup` tinyint(1) NOT NULL DEFAULT 0,
  `itemid` int NOT NULL DEFAULT 0,
  `priority` tinyint NOT NULL DEFAULT 0,
  `package` tinyint(1) NOT NULL DEFAULT 0,
  `period` tinyint NOT NULL DEFAULT 0,
  `gender` tinyint(1) NOT NULL DEFAULT 0,
  `count` tinyint NOT NULL DEFAULT 0,
  `meso` int NOT NULL DEFAULT 0,
  `unk_1` tinyint(1) NOT NULL DEFAULT 0,
  `unk_2` tinyint(1) NOT NULL DEFAULT 0,
  `unk_3` tinyint(1) NOT NULL DEFAULT 0,
  `extra_flags` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`serial`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for character_slots
-- ----------------------------
DROP TABLE IF EXISTS `character_slots`;
CREATE TABLE `character_slots`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `accid` int NOT NULL DEFAULT 0,
  `worldid` int NOT NULL DEFAULT 0,
  `charslots` int NOT NULL DEFAULT 6,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for characters
-- ----------------------------
DROP TABLE IF EXISTS `characters`;
CREATE TABLE `characters`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `accountid` int NOT NULL DEFAULT 0,
  `world` tinyint(1) NOT NULL DEFAULT 0,
  `name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  `level` int UNSIGNED NOT NULL DEFAULT 0,
  `exp` int NOT NULL DEFAULT 0,
  `str` int NOT NULL DEFAULT 0,
  `dex` int NOT NULL DEFAULT 0,
  `luk` int NOT NULL DEFAULT 0,
  `int` int NOT NULL DEFAULT 0,
  `hp` int NOT NULL DEFAULT 0,
  `mp` int NOT NULL DEFAULT 0,
  `maxhp` int NOT NULL DEFAULT 0,
  `maxmp` int NOT NULL DEFAULT 0,
  `meso` int NOT NULL DEFAULT 0,
  `hpApUsed` int NOT NULL DEFAULT 0,
  `job` int NOT NULL DEFAULT 0,
  `skincolor` tinyint(1) NOT NULL DEFAULT 0,
  `gender` tinyint(1) NOT NULL DEFAULT 0,
  `fame` int NOT NULL DEFAULT 0,
  `hair` int NOT NULL DEFAULT 0,
  `face` int NOT NULL DEFAULT 0,
  `ap` int NOT NULL DEFAULT 0,
  `map` int NOT NULL DEFAULT 0,
  `spawnpoint` int NOT NULL DEFAULT 0,
  `gm` int NOT NULL DEFAULT 0,
  `party` int NOT NULL DEFAULT 0,
  `buddyCapacity` int NOT NULL DEFAULT 25,
  `createdate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `guildid` int UNSIGNED NOT NULL DEFAULT 0,
  `guildrank` tinyint UNSIGNED NOT NULL DEFAULT 5,
  `allianceRank` tinyint UNSIGNED NOT NULL DEFAULT 5,
  `monsterbookcover` int UNSIGNED NOT NULL DEFAULT 0,
  `dojo_pts` int UNSIGNED NOT NULL DEFAULT 0,
  `dojoRecord` tinyint UNSIGNED NOT NULL DEFAULT 0,
  `pets` varchar(13) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '-1,-1,-1',
  `sp` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0,0,0,0,0,0,0,0,0,0',
  `subcategory` int NOT NULL DEFAULT 0,
  `Jaguar` int NOT NULL DEFAULT 0,
  `rank` int NOT NULL DEFAULT 1,
  `rankMove` int NOT NULL DEFAULT 0,
  `jobRank` int NOT NULL DEFAULT 1,
  `jobRankMove` int NOT NULL DEFAULT 0,
  `marriageId` int NOT NULL DEFAULT 0,
  `familyid` int NOT NULL DEFAULT 0,
  `seniorid` int NOT NULL DEFAULT 0,
  `junior1` int NOT NULL DEFAULT 0,
  `junior2` int NOT NULL DEFAULT 0,
  `currentrep` int NOT NULL DEFAULT 0,
  `totalrep` int NOT NULL DEFAULT 0,
  `charmessage` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '安安',
  `expression` int NOT NULL DEFAULT 0,
  `constellation` int NOT NULL DEFAULT 0,
  `blood` int NOT NULL DEFAULT 0,
  `month` int NOT NULL DEFAULT 0,
  `day` int NOT NULL DEFAULT 0,
  `beans` int NOT NULL DEFAULT 0,
  `prefix` int NULL DEFAULT 0,
  `gachexp` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `accountid`(`accountid` ASC) USING BTREE,
  INDEX `party`(`party` ASC) USING BTREE,
  INDEX `ranking1`(`level` ASC, `exp` ASC) USING BTREE,
  INDEX `ranking2`(`gm` ASC, `job` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for cheatlog
-- ----------------------------
DROP TABLE IF EXISTS `cheatlog`;
CREATE TABLE `cheatlog`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `characterid` int NOT NULL DEFAULT 0,
  `offense` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `count` int NOT NULL DEFAULT 0,
  `lastoffensetime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `param` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `cid`(`characterid`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for csequipment
-- ----------------------------
DROP TABLE IF EXISTS `csequipment`;
CREATE TABLE `csequipment`  (
  `inventoryequipmentid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `inventoryitemid` int UNSIGNED NOT NULL DEFAULT 0,
  `upgradeslots` int NOT NULL DEFAULT 0,
  `level` int NOT NULL DEFAULT 0,
  `str` int NOT NULL DEFAULT 0,
  `dex` int NOT NULL DEFAULT 0,
  `int` int NOT NULL DEFAULT 0,
  `luk` int NOT NULL DEFAULT 0,
  `hp` int NOT NULL DEFAULT 0,
  `mp` int NOT NULL DEFAULT 0,
  `watk` int NOT NULL DEFAULT 0,
  `matk` int NOT NULL DEFAULT 0,
  `wdef` int NOT NULL DEFAULT 0,
  `mdef` int NOT NULL DEFAULT 0,
  `acc` int NOT NULL DEFAULT 0,
  `avoid` int NOT NULL DEFAULT 0,
  `hands` int NOT NULL DEFAULT 0,
  `speed` int NOT NULL DEFAULT 0,
  `jump` int NOT NULL DEFAULT 0,
  `ViciousHammer` tinyint NOT NULL DEFAULT 0,
  `itemEXP` int NOT NULL DEFAULT 0,
  `durability` int NOT NULL DEFAULT -1,
  `enhance` tinyint NOT NULL DEFAULT 0,
  `potential1` smallint NOT NULL DEFAULT 0,
  `potential2` smallint NOT NULL DEFAULT 0,
  `potential3` smallint NOT NULL DEFAULT 0,
  `hpR` smallint NOT NULL DEFAULT 0,
  `mpR` smallint NOT NULL DEFAULT 0,
  PRIMARY KEY (`inventoryequipmentid`) USING BTREE,
  INDEX `inventoryitemid`(`inventoryitemid` ASC) USING BTREE,
  CONSTRAINT `csequiptment_ibfk_1` FOREIGN KEY (`inventoryitemid`) REFERENCES `csitems` (`inventoryitemid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for csitems
-- ----------------------------
DROP TABLE IF EXISTS `csitems`;
CREATE TABLE `csitems`  (
  `inventoryitemid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `characterid` int NULL DEFAULT NULL,
  `accountid` int NULL DEFAULT NULL,
  `packageid` int NULL DEFAULT NULL,
  `itemid` int NOT NULL DEFAULT 0,
  `inventorytype` int NOT NULL DEFAULT 0,
  `position` int NOT NULL DEFAULT 0,
  `quantity` int NOT NULL DEFAULT 0,
  `owner` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `GM_Log` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `uniqueid` int NOT NULL DEFAULT -1,
  `flag` int NOT NULL DEFAULT 0,
  `expiredate` bigint NOT NULL DEFAULT -1,
  `type` tinyint(1) NOT NULL DEFAULT 0,
  `sender` varchar(13) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  PRIMARY KEY (`inventoryitemid`) USING BTREE,
  INDEX `inventoryitems_ibfk_1`(`characterid` ASC) USING BTREE,
  INDEX `characterid`(`characterid` ASC) USING BTREE,
  INDEX `inventorytype`(`inventorytype` ASC) USING BTREE,
  INDEX `accountid`(`accountid` ASC) USING BTREE,
  INDEX `packageid`(`packageid` ASC) USING BTREE,
  INDEX `characterid_2`(`characterid` ASC, `inventorytype` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for drop_data
-- ----------------------------
DROP TABLE IF EXISTS `drop_data`;
CREATE TABLE `drop_data`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dropperid` int NOT NULL,
  `itemid` int NOT NULL DEFAULT 0,
  `minimum_quantity` int NOT NULL DEFAULT 1,
  `maximum_quantity` int NOT NULL DEFAULT 1,
  `questid` int NOT NULL DEFAULT 0,
  `chance` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `mobid`(`dropperid`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Fixed;

-- ----------------------------
-- Table structure for drop_data_global
-- ----------------------------
DROP TABLE IF EXISTS `drop_data_global`;
CREATE TABLE `drop_data_global`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `continent` int NOT NULL,
  `dropType` tinyint(1) NOT NULL DEFAULT 0,
  `itemid` int NOT NULL DEFAULT 0,
  `minimum_quantity` int NOT NULL DEFAULT 1,
  `maximum_quantity` int NOT NULL DEFAULT 1,
  `questid` int NOT NULL DEFAULT 0,
  `chance` int NOT NULL DEFAULT 0,
  `comments` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `mobid`(`continent` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for dueyequipment
-- ----------------------------
DROP TABLE IF EXISTS `dueyequipment`;
CREATE TABLE `dueyequipment`  (
  `inventoryequipmentid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `inventoryitemid` int UNSIGNED NOT NULL DEFAULT 0,
  `upgradeslots` int NOT NULL DEFAULT 0,
  `level` int NOT NULL DEFAULT 0,
  `str` int NOT NULL DEFAULT 0,
  `dex` int NOT NULL DEFAULT 0,
  `int` int NOT NULL DEFAULT 0,
  `luk` int NOT NULL DEFAULT 0,
  `hp` int NOT NULL DEFAULT 0,
  `mp` int NOT NULL DEFAULT 0,
  `watk` int NOT NULL DEFAULT 0,
  `matk` int NOT NULL DEFAULT 0,
  `wdef` int NOT NULL DEFAULT 0,
  `mdef` int NOT NULL DEFAULT 0,
  `acc` int NOT NULL DEFAULT 0,
  `avoid` int NOT NULL DEFAULT 0,
  `hands` int NOT NULL DEFAULT 0,
  `speed` int NOT NULL DEFAULT 0,
  `jump` int NOT NULL DEFAULT 0,
  `ViciousHammer` tinyint NOT NULL DEFAULT 0,
  `itemEXP` int NOT NULL DEFAULT 0,
  `durability` int NOT NULL DEFAULT -1,
  `enhance` tinyint NOT NULL DEFAULT 0,
  `potential1` smallint NOT NULL DEFAULT 0,
  `potential2` smallint NOT NULL DEFAULT 0,
  `potential3` smallint NOT NULL DEFAULT 0,
  `hpR` smallint NOT NULL DEFAULT 0,
  `mpR` smallint NOT NULL DEFAULT 0,
  PRIMARY KEY (`inventoryequipmentid`) USING BTREE,
  INDEX `inventoryitemid`(`inventoryitemid` ASC) USING BTREE,
  CONSTRAINT `dueyequipment_ibfk_1` FOREIGN KEY (`inventoryitemid`) REFERENCES `dueyitems` (`inventoryitemid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for dueyitems
-- ----------------------------
DROP TABLE IF EXISTS `dueyitems`;
CREATE TABLE `dueyitems`  (
  `inventoryitemid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `characterid` int NULL DEFAULT NULL,
  `accountid` int NULL DEFAULT NULL,
  `packageid` int NULL DEFAULT NULL,
  `itemid` int NOT NULL DEFAULT 0,
  `inventorytype` int NOT NULL DEFAULT 0,
  `position` int NOT NULL DEFAULT 0,
  `quantity` int NOT NULL DEFAULT 0,
  `owner` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `GM_Log` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `uniqueid` int NOT NULL DEFAULT -1,
  `flag` int NOT NULL DEFAULT 0,
  `expiredate` bigint NOT NULL DEFAULT -1,
  `type` tinyint(1) NOT NULL DEFAULT 0,
  `sender` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  PRIMARY KEY (`inventoryitemid`) USING BTREE,
  INDEX `inventoryitems_ibfk_1`(`characterid` ASC) USING BTREE,
  INDEX `characterid`(`characterid` ASC) USING BTREE,
  INDEX `inventorytype`(`inventorytype` ASC) USING BTREE,
  INDEX `accountid`(`accountid` ASC) USING BTREE,
  INDEX `packageid`(`packageid` ASC) USING BTREE,
  INDEX `characterid_2`(`characterid` ASC, `inventorytype` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for dueypackages
-- ----------------------------
DROP TABLE IF EXISTS `dueypackages`;
CREATE TABLE `dueypackages`  (
  `PackageId` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `RecieverId` int NOT NULL,
  `SenderName` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `Mesos` int UNSIGNED NULL DEFAULT 0,
  `TimeStamp` bigint UNSIGNED NULL DEFAULT NULL,
  `Checked` tinyint UNSIGNED NULL DEFAULT 1,
  `Type` tinyint UNSIGNED NOT NULL,
  PRIMARY KEY (`PackageId`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for eventstats
-- ----------------------------
DROP TABLE IF EXISTS `eventstats`;
CREATE TABLE `eventstats`  (
  `eventstatid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `event` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `instance` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `characterid` int NOT NULL,
  `channel` int NOT NULL,
  `time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`eventstatid`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for famelog
-- ----------------------------
DROP TABLE IF EXISTS `famelog`;
CREATE TABLE `famelog`  (
  `famelogid` int NOT NULL AUTO_INCREMENT,
  `characterid` int NOT NULL DEFAULT 0,
  `characterid_to` int NOT NULL DEFAULT 0,
  `when` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`famelogid`) USING BTREE,
  INDEX `characterid`(`characterid` ASC) USING BTREE,
  CONSTRAINT `famelog_ibfk_1` FOREIGN KEY (`characterid`) REFERENCES `characters` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for families
-- ----------------------------
DROP TABLE IF EXISTS `families`;
CREATE TABLE `families`  (
  `familyid` int NOT NULL AUTO_INCREMENT,
  `leaderid` int NOT NULL DEFAULT 0,
  `notice` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  PRIMARY KEY (`familyid`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for game_poll_reply
-- ----------------------------
DROP TABLE IF EXISTS `game_poll_reply`;
CREATE TABLE `game_poll_reply`  (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `AccountId` int UNSIGNED NOT NULL,
  `SelectAns` tinyint UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for gifts
-- ----------------------------
DROP TABLE IF EXISTS `gifts`;
CREATE TABLE `gifts`  (
  `giftid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `recipient` int NOT NULL DEFAULT 0,
  `from` varchar(13) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  `message` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  `sn` int NOT NULL DEFAULT 0,
  `uniqueid` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`giftid`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for gmlog
-- ----------------------------
DROP TABLE IF EXISTS `gmlog`;
CREATE TABLE `gmlog`  (
  `gmlogid` int NOT NULL AUTO_INCREMENT,
  `cid` int NOT NULL DEFAULT 0,
  `command` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `mapid` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`gmlogid`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for guilds
-- ----------------------------
DROP TABLE IF EXISTS `guilds`;
CREATE TABLE `guilds`  (
  `guildid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `leader` int UNSIGNED NOT NULL DEFAULT 0,
  `GP` int NOT NULL DEFAULT 0,
  `logo` int UNSIGNED NULL DEFAULT NULL,
  `logoColor` smallint UNSIGNED NOT NULL DEFAULT 0,
  `name` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `rank1title` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '公會長',
  `rank2title` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '公會副會長',
  `rank3title` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '公會成員',
  `rank4title` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '公會成員',
  `rank5title` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '公會成員',
  `capacity` int UNSIGNED NOT NULL DEFAULT 10,
  `logoBG` int UNSIGNED NULL DEFAULT NULL,
  `logoBGColor` smallint UNSIGNED NOT NULL DEFAULT 0,
  `notice` varchar(101) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `signature` int NOT NULL DEFAULT 0,
  `alliance` int UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`guildid`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for hiredfishing
-- ----------------------------
DROP TABLE IF EXISTS `hiredfishing`;
CREATE TABLE `hiredfishing`  (
  `PackageId` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `characterid` int UNSIGNED NULL DEFAULT 0,
  `accountid` int UNSIGNED NULL DEFAULT NULL,
  `Mesos` int UNSIGNED NULL DEFAULT 0,
  `time` bigint UNSIGNED NULL DEFAULT NULL,
  PRIMARY KEY (`PackageId`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for hiredfishingequipment
-- ----------------------------
DROP TABLE IF EXISTS `hiredfishingequipment`;
CREATE TABLE `hiredfishingequipment`  (
  `inventoryequipmentid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `inventoryitemid` int UNSIGNED NOT NULL DEFAULT 0,
  `upgradeslots` int NOT NULL DEFAULT 0,
  `level` int NOT NULL DEFAULT 0,
  `str` int NOT NULL DEFAULT 0,
  `dex` int NOT NULL DEFAULT 0,
  `int` int NOT NULL DEFAULT 0,
  `luk` int NOT NULL DEFAULT 0,
  `hp` int NOT NULL DEFAULT 0,
  `mp` int NOT NULL DEFAULT 0,
  `watk` int NOT NULL DEFAULT 0,
  `matk` int NOT NULL DEFAULT 0,
  `wdef` int NOT NULL DEFAULT 0,
  `mdef` int NOT NULL DEFAULT 0,
  `acc` int NOT NULL DEFAULT 0,
  `avoid` int NOT NULL DEFAULT 0,
  `hands` int NOT NULL DEFAULT 0,
  `speed` int NOT NULL DEFAULT 0,
  `jump` int NOT NULL DEFAULT 0,
  `ViciousHammer` tinyint NOT NULL DEFAULT 0,
  `itemEXP` int NOT NULL DEFAULT 0,
  `durability` int NOT NULL DEFAULT -1,
  `enhance` tinyint NOT NULL DEFAULT 0,
  `potential1` smallint NOT NULL DEFAULT 0,
  `potential2` smallint NOT NULL DEFAULT 0,
  `potential3` smallint NOT NULL DEFAULT 0,
  `hpR` smallint NOT NULL DEFAULT 0,
  `mpR` smallint NOT NULL DEFAULT 0,
  PRIMARY KEY (`inventoryequipmentid`) USING BTREE,
  INDEX `inventoryitemid`(`inventoryitemid` ASC) USING BTREE,
  CONSTRAINT `hiredfishingequipment` FOREIGN KEY (`inventoryitemid`) REFERENCES `hiredfishingitems` (`inventoryitemid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for hiredfishingitems
-- ----------------------------
DROP TABLE IF EXISTS `hiredfishingitems`;
CREATE TABLE `hiredfishingitems`  (
  `inventoryitemid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `characterid` int NULL DEFAULT NULL,
  `accountid` int NULL DEFAULT NULL,
  `packageid` int NULL DEFAULT NULL,
  `itemid` int NOT NULL DEFAULT 0,
  `inventorytype` int NOT NULL DEFAULT 0,
  `position` int NOT NULL DEFAULT 0,
  `quantity` int NOT NULL DEFAULT 0,
  `owner` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `GM_Log` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `uniqueid` int NOT NULL DEFAULT -1,
  `flag` int NOT NULL DEFAULT 0,
  `expiredate` bigint NOT NULL DEFAULT -1,
  `type` tinyint(1) NOT NULL DEFAULT 0,
  `sender` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  PRIMARY KEY (`inventoryitemid`) USING BTREE,
  INDEX `inventoryitems_ibfk_1`(`characterid` ASC) USING BTREE,
  INDEX `characterid`(`characterid` ASC) USING BTREE,
  INDEX `inventorytype`(`inventorytype` ASC) USING BTREE,
  INDEX `accountid`(`accountid` ASC) USING BTREE,
  INDEX `packageid`(`packageid` ASC) USING BTREE,
  INDEX `characterid_2`(`characterid` ASC, `inventorytype` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for hiredmerch
-- ----------------------------
DROP TABLE IF EXISTS `hiredmerch`;
CREATE TABLE `hiredmerch`  (
  `PackageId` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `characterid` int UNSIGNED NULL DEFAULT 0,
  `accountid` int UNSIGNED NULL DEFAULT NULL,
  `Mesos` int UNSIGNED NULL DEFAULT 0,
  `time` bigint UNSIGNED NULL DEFAULT NULL,
  PRIMARY KEY (`PackageId`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for hiredmerchequipment
-- ----------------------------
DROP TABLE IF EXISTS `hiredmerchequipment`;
CREATE TABLE `hiredmerchequipment`  (
  `inventoryequipmentid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `inventoryitemid` int UNSIGNED NOT NULL DEFAULT 0,
  `upgradeslots` int NOT NULL DEFAULT 0,
  `level` int NOT NULL DEFAULT 0,
  `str` int NOT NULL DEFAULT 0,
  `dex` int NOT NULL DEFAULT 0,
  `int` int NOT NULL DEFAULT 0,
  `luk` int NOT NULL DEFAULT 0,
  `hp` int NOT NULL DEFAULT 0,
  `mp` int NOT NULL DEFAULT 0,
  `watk` int NOT NULL DEFAULT 0,
  `matk` int NOT NULL DEFAULT 0,
  `wdef` int NOT NULL DEFAULT 0,
  `mdef` int NOT NULL DEFAULT 0,
  `acc` int NOT NULL DEFAULT 0,
  `avoid` int NOT NULL DEFAULT 0,
  `hands` int NOT NULL DEFAULT 0,
  `speed` int NOT NULL DEFAULT 0,
  `jump` int NOT NULL DEFAULT 0,
  `ViciousHammer` tinyint NOT NULL DEFAULT 0,
  `itemEXP` int NOT NULL DEFAULT 0,
  `durability` int NOT NULL DEFAULT -1,
  `enhance` tinyint NOT NULL DEFAULT 0,
  `potential1` smallint NOT NULL DEFAULT 0,
  `potential2` smallint NOT NULL DEFAULT 0,
  `potential3` smallint NOT NULL DEFAULT 0,
  `hpR` smallint NOT NULL DEFAULT 0,
  `mpR` smallint NOT NULL DEFAULT 0,
  PRIMARY KEY (`inventoryequipmentid`) USING BTREE,
  INDEX `inventoryitemid`(`inventoryitemid` ASC) USING BTREE,
  CONSTRAINT `hiredmerchantequipment_ibfk_1` FOREIGN KEY (`inventoryitemid`) REFERENCES `hiredmerchitems` (`inventoryitemid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for hiredmerchitems
-- ----------------------------
DROP TABLE IF EXISTS `hiredmerchitems`;
CREATE TABLE `hiredmerchitems`  (
  `inventoryitemid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `characterid` int NULL DEFAULT NULL,
  `accountid` int NULL DEFAULT NULL,
  `packageid` int NULL DEFAULT NULL,
  `itemid` int NOT NULL DEFAULT 0,
  `inventorytype` int NOT NULL DEFAULT 0,
  `position` int NOT NULL DEFAULT 0,
  `quantity` int NOT NULL DEFAULT 0,
  `owner` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `GM_Log` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `uniqueid` int NOT NULL DEFAULT -1,
  `flag` int NOT NULL DEFAULT 0,
  `expiredate` bigint NOT NULL DEFAULT -1,
  `type` tinyint(1) NOT NULL DEFAULT 0,
  `sender` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  PRIMARY KEY (`inventoryitemid`) USING BTREE,
  INDEX `inventoryitems_ibfk_1`(`characterid` ASC) USING BTREE,
  INDEX `characterid`(`characterid` ASC) USING BTREE,
  INDEX `inventorytype`(`inventorytype` ASC) USING BTREE,
  INDEX `accountid`(`accountid` ASC) USING BTREE,
  INDEX `packageid`(`packageid` ASC) USING BTREE,
  INDEX `characterid_2`(`characterid` ASC, `inventorytype` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for htsquads
-- ----------------------------
DROP TABLE IF EXISTS `htsquads`;
CREATE TABLE `htsquads`  (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `channel` int UNSIGNED NOT NULL,
  `leaderid` int UNSIGNED NOT NULL DEFAULT 0,
  `status` int UNSIGNED NOT NULL DEFAULT 0,
  `members` int UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for inventoryequipment
-- ----------------------------
DROP TABLE IF EXISTS `inventoryequipment`;
CREATE TABLE `inventoryequipment`  (
  `inventoryequipmentid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `inventoryitemid` int UNSIGNED NOT NULL DEFAULT 0,
  `upgradeslots` tinyint UNSIGNED NOT NULL DEFAULT 0,
  `level` tinyint UNSIGNED NOT NULL DEFAULT 0,
  `str` smallint NOT NULL DEFAULT 0,
  `dex` smallint NOT NULL DEFAULT 0,
  `int` smallint NOT NULL DEFAULT 0,
  `luk` smallint NOT NULL DEFAULT 0,
  `hp` smallint NOT NULL DEFAULT 0,
  `mp` smallint NOT NULL DEFAULT 0,
  `watk` smallint NOT NULL DEFAULT 0,
  `matk` smallint NOT NULL DEFAULT 0,
  `wdef` smallint NOT NULL DEFAULT 0,
  `mdef` smallint NOT NULL DEFAULT 0,
  `acc` smallint NOT NULL DEFAULT 0,
  `avoid` smallint NOT NULL DEFAULT 0,
  `hands` smallint NOT NULL DEFAULT 0,
  `speed` smallint NOT NULL DEFAULT 0,
  `jump` smallint NOT NULL DEFAULT 0,
  `ViciousHammer` tinyint NOT NULL DEFAULT 0,
  `itemEXP` int NOT NULL DEFAULT 0,
  `durability` mediumint NOT NULL DEFAULT -1,
  `enhance` tinyint NOT NULL DEFAULT 0,
  `potential1` smallint NOT NULL DEFAULT 0,
  `potential2` smallint NOT NULL DEFAULT 0,
  `potential3` smallint NOT NULL DEFAULT 0,
  `hpR` smallint NOT NULL DEFAULT 0,
  `mpR` smallint NOT NULL DEFAULT 0,
  PRIMARY KEY (`inventoryequipmentid`) USING BTREE,
  INDEX `inventoryitemid`(`inventoryitemid` ASC) USING BTREE,
  CONSTRAINT `inventoryequipment_ibfk_1` FOREIGN KEY (`inventoryitemid`) REFERENCES `inventoryitems` (`inventoryitemid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for inventoryitems
-- ----------------------------
DROP TABLE IF EXISTS `inventoryitems`;
CREATE TABLE `inventoryitems`  (
  `inventoryitemid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `characterid` int NULL DEFAULT NULL,
  `accountid` int NULL DEFAULT NULL,
  `packageid` int NULL DEFAULT NULL,
  `itemid` int NOT NULL DEFAULT 0,
  `inventorytype` int NOT NULL DEFAULT 0,
  `position` int NOT NULL DEFAULT 0,
  `quantity` int NOT NULL DEFAULT 0,
  `owner` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `GM_Log` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `uniqueid` int NOT NULL DEFAULT -1,
  `flag` int NOT NULL DEFAULT 0,
  `expiredate` bigint NOT NULL DEFAULT -1,
  `type` tinyint(1) NOT NULL DEFAULT 0,
  `sender` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  PRIMARY KEY (`inventoryitemid`) USING BTREE,
  INDEX `inventorytype`(`inventorytype` ASC) USING BTREE,
  INDEX `accountid`(`accountid` ASC) USING BTREE,
  INDEX `packageid`(`packageid` ASC) USING BTREE,
  INDEX `characterid_2`(`characterid` ASC, `inventorytype` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for inventorylog
-- ----------------------------
DROP TABLE IF EXISTS `inventorylog`;
CREATE TABLE `inventorylog`  (
  `inventorylogid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `inventoryitemid` int UNSIGNED NOT NULL DEFAULT 0,
  `msg` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  PRIMARY KEY (`inventorylogid`) USING BTREE,
  INDEX `inventoryitemid`(`inventoryitemid` ASC) USING BTREE,
  CONSTRAINT `inventorylog_ibfk_1` FOREIGN KEY (`inventoryitemid`) REFERENCES `inventoryitems` (`inventoryitemid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for inventoryslot
-- ----------------------------
DROP TABLE IF EXISTS `inventoryslot`;
CREATE TABLE `inventoryslot`  (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `characterid` int UNSIGNED NULL DEFAULT NULL,
  `equip` tinyint UNSIGNED NULL DEFAULT NULL,
  `use` tinyint UNSIGNED NULL DEFAULT NULL,
  `setup` tinyint UNSIGNED NULL DEFAULT NULL,
  `etc` tinyint UNSIGNED NULL DEFAULT NULL,
  `cash` tinyint UNSIGNED NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for invitecodedata
-- ----------------------------
DROP TABLE IF EXISTS `invitecodedata`;
CREATE TABLE `invitecodedata`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `time` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `ip` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `active` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ipbans
-- ----------------------------
DROP TABLE IF EXISTS `ipbans`;
CREATE TABLE `ipbans`  (
  `ipbanid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `ip` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  PRIMARY KEY (`ipbanid`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ipvotelog
-- ----------------------------
DROP TABLE IF EXISTS `ipvotelog`;
CREATE TABLE `ipvotelog`  (
  `vid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `accid` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0',
  `ipaddress` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '127.0.0.1',
  `votetime` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0',
  `votetype` tinyint UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`vid`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for keymap
-- ----------------------------
DROP TABLE IF EXISTS `keymap`;
CREATE TABLE `keymap`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `characterid` int NOT NULL DEFAULT 0,
  `key` tinyint UNSIGNED NOT NULL DEFAULT 0,
  `type` tinyint UNSIGNED NOT NULL DEFAULT 0,
  `action` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `keymap_ibfk_1`(`characterid` ASC) USING BTREE,
  CONSTRAINT `keymap_ibfk_1` FOREIGN KEY (`characterid`) REFERENCES `characters` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for loginlog
-- ----------------------------
DROP TABLE IF EXISTS `loginlog`;
CREATE TABLE `loginlog`  (
  `account` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `logintype` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `ip` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `time` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `active` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for macbans
-- ----------------------------
DROP TABLE IF EXISTS `macbans`;
CREATE TABLE `macbans`  (
  `macbanid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `mac` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  PRIMARY KEY (`macbanid`) USING HASH,
  UNIQUE INDEX `mac_2`(`mac`) USING HASH
) ENGINE = MEMORY CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for macfilters
-- ----------------------------
DROP TABLE IF EXISTS `macfilters`;
CREATE TABLE `macfilters`  (
  `macfilterid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `filter` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  PRIMARY KEY (`macfilterid`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for monsterbook
-- ----------------------------
DROP TABLE IF EXISTS `monsterbook`;
CREATE TABLE `monsterbook`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `charid` int UNSIGNED NOT NULL DEFAULT 0,
  `cardid` int UNSIGNED NOT NULL DEFAULT 0,
  `level` tinyint UNSIGNED NULL DEFAULT 1,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mountdata
-- ----------------------------
DROP TABLE IF EXISTS `mountdata`;
CREATE TABLE `mountdata`  (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `characterid` int UNSIGNED NULL DEFAULT NULL,
  `Level` int UNSIGNED NOT NULL DEFAULT 0,
  `Exp` int UNSIGNED NOT NULL DEFAULT 0,
  `Fatigue` int UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Fixed;

-- ----------------------------
-- Table structure for mts_cart
-- ----------------------------
DROP TABLE IF EXISTS `mts_cart`;
CREATE TABLE `mts_cart`  (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `characterid` int NOT NULL DEFAULT 0,
  `itemid` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mts_items
-- ----------------------------
DROP TABLE IF EXISTS `mts_items`;
CREATE TABLE `mts_items`  (
  `id` int NOT NULL,
  `tab` tinyint(1) NOT NULL DEFAULT 1,
  `price` int NOT NULL DEFAULT 0,
  `characterid` int NOT NULL DEFAULT 0,
  `seller` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  `expiration` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mtsequipment
-- ----------------------------
DROP TABLE IF EXISTS `mtsequipment`;
CREATE TABLE `mtsequipment`  (
  `inventoryequipmentid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `inventoryitemid` int UNSIGNED NOT NULL DEFAULT 0,
  `upgradeslots` int NOT NULL DEFAULT 0,
  `level` int NOT NULL DEFAULT 0,
  `str` int NOT NULL DEFAULT 0,
  `dex` int NOT NULL DEFAULT 0,
  `int` int NOT NULL DEFAULT 0,
  `luk` int NOT NULL DEFAULT 0,
  `hp` int NOT NULL DEFAULT 0,
  `mp` int NOT NULL DEFAULT 0,
  `watk` int NOT NULL DEFAULT 0,
  `matk` int NOT NULL DEFAULT 0,
  `wdef` int NOT NULL DEFAULT 0,
  `mdef` int NOT NULL DEFAULT 0,
  `acc` int NOT NULL DEFAULT 0,
  `avoid` int NOT NULL DEFAULT 0,
  `hands` int NOT NULL DEFAULT 0,
  `speed` int NOT NULL DEFAULT 0,
  `jump` int NOT NULL DEFAULT 0,
  `ViciousHammer` tinyint NOT NULL DEFAULT 0,
  `itemEXP` int NOT NULL DEFAULT 0,
  `durability` int NOT NULL DEFAULT -1,
  `enhance` tinyint NOT NULL DEFAULT 0,
  `potential1` smallint NOT NULL DEFAULT 0,
  `potential2` smallint NOT NULL DEFAULT 0,
  `potential3` smallint NOT NULL DEFAULT 0,
  `hpR` smallint NOT NULL DEFAULT 0,
  `mpR` smallint NOT NULL DEFAULT 0,
  PRIMARY KEY (`inventoryequipmentid`) USING BTREE,
  INDEX `inventoryitemid`(`inventoryitemid` ASC) USING BTREE,
  CONSTRAINT `mtsequipment_ibfk_1` FOREIGN KEY (`inventoryitemid`) REFERENCES `mtsitems` (`inventoryitemid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mtsitems
-- ----------------------------
DROP TABLE IF EXISTS `mtsitems`;
CREATE TABLE `mtsitems`  (
  `inventoryitemid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `characterid` int NULL DEFAULT NULL,
  `accountid` int NULL DEFAULT NULL,
  `packageId` int NULL DEFAULT NULL,
  `itemid` int NOT NULL DEFAULT 0,
  `inventorytype` int NOT NULL DEFAULT 0,
  `position` int NOT NULL DEFAULT 0,
  `quantity` int NOT NULL DEFAULT 0,
  `owner` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `GM_Log` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `uniqueid` int NOT NULL DEFAULT -1,
  `flag` int NOT NULL DEFAULT 0,
  `expiredate` bigint NOT NULL DEFAULT -1,
  `type` tinyint(1) NOT NULL DEFAULT 0,
  `sender` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  PRIMARY KEY (`inventoryitemid`) USING BTREE,
  INDEX `inventoryitems_ibfk_1`(`characterid` ASC) USING BTREE,
  INDEX `characterid`(`characterid` ASC) USING BTREE,
  INDEX `inventorytype`(`inventorytype` ASC) USING BTREE,
  INDEX `accountid`(`accountid` ASC) USING BTREE,
  INDEX `characterid_2`(`characterid` ASC, `inventorytype` ASC) USING BTREE,
  INDEX `packageid`(`packageId` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mtstransfer
-- ----------------------------
DROP TABLE IF EXISTS `mtstransfer`;
CREATE TABLE `mtstransfer`  (
  `inventoryitemid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `characterid` int NULL DEFAULT NULL,
  `accountid` int NULL DEFAULT NULL,
  `packageid` int NULL DEFAULT NULL,
  `itemid` int NOT NULL DEFAULT 0,
  `inventorytype` int NOT NULL DEFAULT 0,
  `position` int NOT NULL DEFAULT 0,
  `quantity` int NOT NULL DEFAULT 0,
  `owner` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `GM_Log` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `uniqueid` int NOT NULL DEFAULT -1,
  `flag` int NOT NULL DEFAULT 0,
  `expiredate` bigint NOT NULL DEFAULT -1,
  `type` tinyint(1) NOT NULL DEFAULT 0,
  `sender` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  PRIMARY KEY (`inventoryitemid`) USING BTREE,
  INDEX `inventoryitems_ibfk_1`(`characterid` ASC) USING BTREE,
  INDEX `characterid`(`characterid` ASC) USING BTREE,
  INDEX `inventorytype`(`inventorytype` ASC) USING BTREE,
  INDEX `accountid`(`accountid` ASC) USING BTREE,
  INDEX `packageid`(`packageid` ASC) USING BTREE,
  INDEX `characterid_2`(`characterid` ASC, `inventorytype` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mtstransferequipment
-- ----------------------------
DROP TABLE IF EXISTS `mtstransferequipment`;
CREATE TABLE `mtstransferequipment`  (
  `inventoryequipmentid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `inventoryitemid` int UNSIGNED NOT NULL DEFAULT 0,
  `upgradeslots` int NOT NULL DEFAULT 0,
  `level` int NOT NULL DEFAULT 0,
  `str` int NOT NULL DEFAULT 0,
  `dex` int NOT NULL DEFAULT 0,
  `int` int NOT NULL DEFAULT 0,
  `luk` int NOT NULL DEFAULT 0,
  `hp` int NOT NULL DEFAULT 0,
  `mp` int NOT NULL DEFAULT 0,
  `watk` int NOT NULL DEFAULT 0,
  `matk` int NOT NULL DEFAULT 0,
  `wdef` int NOT NULL DEFAULT 0,
  `mdef` int NOT NULL DEFAULT 0,
  `acc` int NOT NULL DEFAULT 0,
  `avoid` int NOT NULL DEFAULT 0,
  `hands` int NOT NULL DEFAULT 0,
  `speed` int NOT NULL DEFAULT 0,
  `jump` int NOT NULL DEFAULT 0,
  `ViciousHammer` tinyint NOT NULL DEFAULT 0,
  `itemEXP` int NOT NULL DEFAULT 0,
  `durability` int NOT NULL DEFAULT -1,
  `enhance` tinyint NOT NULL DEFAULT 0,
  `potential1` smallint NOT NULL DEFAULT 0,
  `potential2` smallint NOT NULL DEFAULT 0,
  `potential3` smallint NOT NULL DEFAULT 0,
  `hpR` smallint NOT NULL DEFAULT 0,
  `mpR` smallint NOT NULL DEFAULT 0,
  PRIMARY KEY (`inventoryequipmentid`) USING BTREE,
  INDEX `inventoryitemid`(`inventoryitemid` ASC) USING BTREE,
  CONSTRAINT `mtstransferequipment_ibfk_1` FOREIGN KEY (`inventoryitemid`) REFERENCES `mtstransfer` (`inventoryitemid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for mulungdojo
-- ----------------------------
DROP TABLE IF EXISTS `mulungdojo`;
CREATE TABLE `mulungdojo`  (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `charid` int NOT NULL DEFAULT 0,
  `stage` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Fixed;

-- ----------------------------
-- Table structure for notes
-- ----------------------------
DROP TABLE IF EXISTS `notes`;
CREATE TABLE `notes`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `to` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  `from` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `timestamp` bigint UNSIGNED NOT NULL,
  `gift` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for nxcode
-- ----------------------------
DROP TABLE IF EXISTS `nxcode`;
CREATE TABLE `nxcode`  (
  `code` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `valid` int NOT NULL DEFAULT 1,
  `user` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `type` int NOT NULL DEFAULT 0,
  `item` int NOT NULL DEFAULT 10000,
  PRIMARY KEY (`code`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for pets
-- ----------------------------
DROP TABLE IF EXISTS `pets`;
CREATE TABLE `pets`  (
  `petid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` varchar(13) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `level` int UNSIGNED NOT NULL,
  `closeness` int UNSIGNED NOT NULL,
  `fullness` int UNSIGNED NOT NULL,
  `seconds` int NOT NULL DEFAULT 0,
  `flags` smallint NOT NULL DEFAULT 0,
  PRIMARY KEY (`petid`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for playernpcs
-- ----------------------------
DROP TABLE IF EXISTS `playernpcs`;
CREATE TABLE `playernpcs`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `hair` int NOT NULL,
  `face` int NOT NULL,
  `skin` int NOT NULL,
  `x` int NOT NULL DEFAULT 0,
  `y` int NOT NULL DEFAULT 0,
  `map` int NOT NULL,
  `charid` int NOT NULL,
  `scriptid` int NOT NULL,
  `foothold` int NOT NULL,
  `dir` tinyint(1) NOT NULL DEFAULT 0,
  `gender` tinyint(1) NOT NULL DEFAULT 0,
  `pets` varchar(25) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0,0,0',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `scriptid`(`scriptid` ASC) USING BTREE,
  INDEX `playernpcs_ibfk_1`(`charid` ASC) USING BTREE,
  CONSTRAINT `playernpcs_ibfk_1` FOREIGN KEY (`charid`) REFERENCES `characters` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for playernpcs_equip
-- ----------------------------
DROP TABLE IF EXISTS `playernpcs_equip`;
CREATE TABLE `playernpcs_equip`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `npcid` int NOT NULL,
  `equipid` int NOT NULL,
  `equippos` int NOT NULL,
  `charid` int NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `playernpcs_equip_ibfk_1`(`charid` ASC) USING BTREE,
  INDEX `playernpcs_equip_ibfk_2`(`npcid` ASC) USING BTREE,
  CONSTRAINT `playernpcs_equip_ibfk_1` FOREIGN KEY (`charid`) REFERENCES `characters` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `playernpcs_equip_ibfk_2` FOREIGN KEY (`npcid`) REFERENCES `playernpcs` (`scriptid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for questactions
-- ----------------------------
DROP TABLE IF EXISTS `questactions`;
CREATE TABLE `questactions`  (
  `questactionid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `questid` int NOT NULL DEFAULT 0,
  `status` int NOT NULL DEFAULT 0,
  `data` blob NOT NULL,
  PRIMARY KEY (`questactionid`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for questinfo
-- ----------------------------
DROP TABLE IF EXISTS `questinfo`;
CREATE TABLE `questinfo`  (
  `questinfoid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `characterid` int NOT NULL DEFAULT 0,
  `quest` int NOT NULL DEFAULT 0,
  `customData` varchar(555) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`questinfoid`) USING BTREE,
  INDEX `characterid`(`characterid` ASC) USING BTREE,
  CONSTRAINT `questsinfo_ibfk_1` FOREIGN KEY (`characterid`) REFERENCES `characters` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for questrequirements
-- ----------------------------
DROP TABLE IF EXISTS `questrequirements`;
CREATE TABLE `questrequirements`  (
  `questrequirementid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `questid` int NOT NULL DEFAULT 0,
  `status` int NOT NULL DEFAULT 0,
  `data` blob NOT NULL,
  PRIMARY KEY (`questrequirementid`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for queststatus
-- ----------------------------
DROP TABLE IF EXISTS `queststatus`;
CREATE TABLE `queststatus`  (
  `queststatusid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `characterid` int NOT NULL DEFAULT 0,
  `quest` int NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 0,
  `time` int NOT NULL DEFAULT 0,
  `forfeited` int NOT NULL DEFAULT 0,
  `customData` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`queststatusid`) USING BTREE,
  INDEX `characterid`(`characterid` ASC) USING BTREE,
  CONSTRAINT `queststatus_ibfk_1` FOREIGN KEY (`characterid`) REFERENCES `characters` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for queststatusmobs
-- ----------------------------
DROP TABLE IF EXISTS `queststatusmobs`;
CREATE TABLE `queststatusmobs`  (
  `queststatusmobid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `queststatusid` int UNSIGNED NOT NULL DEFAULT 0,
  `mob` int NOT NULL DEFAULT 0,
  `count` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`queststatusmobid`) USING BTREE,
  INDEX `queststatusid`(`queststatusid` ASC) USING BTREE,
  CONSTRAINT `queststatusmobs_ibfk_1` FOREIGN KEY (`queststatusid`) REFERENCES `queststatus` (`queststatusid`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for reactordrops
-- ----------------------------
DROP TABLE IF EXISTS `reactordrops`;
CREATE TABLE `reactordrops`  (
  `reactordropid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `reactorid` int NOT NULL,
  `itemid` int NOT NULL,
  `chance` int NOT NULL,
  `questid` int NOT NULL DEFAULT -1,
  PRIMARY KEY (`reactordropid`) USING BTREE,
  INDEX `reactorid`(`reactorid`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci PACK_KEYS = 1 ROW_FORMAT = Fixed;

-- ----------------------------
-- Table structure for regrocklocations
-- ----------------------------
DROP TABLE IF EXISTS `regrocklocations`;
CREATE TABLE `regrocklocations`  (
  `trockid` int NOT NULL AUTO_INCREMENT,
  `characterid` int NULL DEFAULT NULL,
  `mapid` int NULL DEFAULT NULL,
  PRIMARY KEY (`trockid`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Fixed;

-- ----------------------------
-- Table structure for reports
-- ----------------------------
DROP TABLE IF EXISTS `reports`;
CREATE TABLE `reports`  (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `reporttime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `reporterid` int NOT NULL,
  `victimid` int NOT NULL,
  `reason` tinyint NOT NULL,
  `chatlog` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `status` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for rings
-- ----------------------------
DROP TABLE IF EXISTS `rings`;
CREATE TABLE `rings`  (
  `ringid` int NOT NULL AUTO_INCREMENT,
  `partnerRingId` int NOT NULL DEFAULT 0,
  `partnerChrId` int NOT NULL DEFAULT 0,
  `itemid` int NOT NULL DEFAULT 0,
  `partnername` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  PRIMARY KEY (`ringid`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for savedlocations
-- ----------------------------
DROP TABLE IF EXISTS `savedlocations`;
CREATE TABLE `savedlocations`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `characterid` int NOT NULL,
  `locationtype` int NOT NULL DEFAULT 0,
  `map` int NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `savedlocations_ibfk_1`(`characterid` ASC) USING BTREE,
  CONSTRAINT `savedlocations_ibfk_1` FOREIGN KEY (`characterid`) REFERENCES `characters` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for shopitems
-- ----------------------------
DROP TABLE IF EXISTS `shopitems`;
CREATE TABLE `shopitems`  (
  `shopitemid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `shopid` int UNSIGNED NOT NULL,
  `itemid` int NOT NULL,
  `price` int NOT NULL,
  `pitch` int NOT NULL DEFAULT 0,
  `position` int NOT NULL COMMENT 'sort is an arbitrary field designed to give leeway when modifying shops. The lowest number is 104 and it increments by 4 for each item to allow decent space for swapping/inserting/removing items.',
  `reqitem` int NOT NULL,
  `reqitemq` int NOT NULL,
  PRIMARY KEY (`shopitemid`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Fixed;

-- ----------------------------
-- Table structure for shops
-- ----------------------------
DROP TABLE IF EXISTS `shops`;
CREATE TABLE `shops`  (
  `shopid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `npcid` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`shopid`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Fixed;

-- ----------------------------
-- Table structure for skillmacros
-- ----------------------------
DROP TABLE IF EXISTS `skillmacros`;
CREATE TABLE `skillmacros`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `characterid` int NOT NULL DEFAULT 0,
  `position` tinyint NOT NULL DEFAULT 0,
  `skill1` int NOT NULL DEFAULT 0,
  `skill2` int NOT NULL DEFAULT 0,
  `skill3` int NOT NULL DEFAULT 0,
  `name` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `shout` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for skills
-- ----------------------------
DROP TABLE IF EXISTS `skills`;
CREATE TABLE `skills`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `skillid` int NOT NULL DEFAULT 0,
  `characterid` int NOT NULL DEFAULT 0,
  `skilllevel` tinyint NOT NULL DEFAULT 0,
  `masterlevel` tinyint NOT NULL DEFAULT 0,
  `expiration` bigint NOT NULL DEFAULT -1,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `skills_ibfk_1`(`characterid` ASC) USING BTREE,
  CONSTRAINT `skills_ibfk_1` FOREIGN KEY (`characterid`) REFERENCES `characters` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for skills_cooldowns
-- ----------------------------
DROP TABLE IF EXISTS `skills_cooldowns`;
CREATE TABLE `skills_cooldowns`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `charid` int NOT NULL,
  `SkillID` int NOT NULL,
  `length` bigint NOT NULL,
  `StartTime` bigint UNSIGNED NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Fixed;

-- ----------------------------
-- Table structure for speedruns
-- ----------------------------
DROP TABLE IF EXISTS `speedruns`;
CREATE TABLE `speedruns`  (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `type` varchar(13) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `leader` varchar(13) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `timestring` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `time` bigint NOT NULL DEFAULT 0,
  `members` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for storages
-- ----------------------------
DROP TABLE IF EXISTS `storages`;
CREATE TABLE `storages`  (
  `storageid` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `accountid` int NOT NULL DEFAULT 0,
  `slots` int NOT NULL DEFAULT 0,
  `meso` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`storageid`) USING BTREE,
  INDEX `accountid`(`accountid` ASC) USING BTREE,
  CONSTRAINT `storages_ibfk_1` FOREIGN KEY (`accountid`) REFERENCES `accounts` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for trocklocations
-- ----------------------------
DROP TABLE IF EXISTS `trocklocations`;
CREATE TABLE `trocklocations`  (
  `trockid` int NOT NULL AUTO_INCREMENT,
  `characterid` int NULL DEFAULT NULL,
  `mapid` int NULL DEFAULT NULL,
  PRIMARY KEY (`trockid`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Fixed;

-- ----------------------------
-- Table structure for uselog
-- ----------------------------
DROP TABLE IF EXISTS `uselog`;
CREATE TABLE `uselog`  (
  `account` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `ip` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `time` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `usetype` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `active` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `newpassword` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `oldpassword` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for wishlist
-- ----------------------------
DROP TABLE IF EXISTS `wishlist`;
CREATE TABLE `wishlist`  (
  `characterid` int NOT NULL,
  `sn` int NOT NULL
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Fixed;

-- ----------------------------
-- Table structure for wz_mobskilldata
-- ----------------------------
DROP TABLE IF EXISTS `wz_mobskilldata`;
CREATE TABLE `wz_mobskilldata`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `skillid` int NOT NULL,
  `level` int NOT NULL,
  `hp` int NOT NULL DEFAULT 100,
  `mpcon` int NOT NULL DEFAULT 0,
  `x` int NOT NULL DEFAULT 1,
  `y` int NOT NULL DEFAULT 1,
  `time` int NOT NULL DEFAULT 0,
  `prop` int NOT NULL DEFAULT 100,
  `limit` int NOT NULL DEFAULT 0,
  `spawneffect` int NOT NULL DEFAULT 0,
  `interval` int NOT NULL DEFAULT 0,
  `summons` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  `ltx` int NOT NULL DEFAULT 0,
  `lty` int NOT NULL DEFAULT 0,
  `rbx` int NOT NULL DEFAULT 0,
  `rby` int NOT NULL DEFAULT 0,
  `once` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for wz_oxdata
-- ----------------------------
DROP TABLE IF EXISTS `wz_oxdata`;
CREATE TABLE `wz_oxdata`  (
  `questionset` smallint NOT NULL DEFAULT 0,
  `questionid` smallint NOT NULL DEFAULT 0,
  `question` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  `display` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  `answer` enum('o','x') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  PRIMARY KEY (`questionset`, `questionid`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for zaksquads
-- ----------------------------
DROP TABLE IF EXISTS `zaksquads`;
CREATE TABLE `zaksquads`  (
  `id` int UNSIGNED NOT NULL AUTO_INCREMENT,
  `channel` int UNSIGNED NOT NULL,
  `leaderid` int UNSIGNED NOT NULL DEFAULT 0,
  `status` int UNSIGNED NOT NULL DEFAULT 0,
  `members` int UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- View structure for readable_cheatlog
-- ----------------------------
DROP VIEW IF EXISTS `readable_cheatlog`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `readable_cheatlog` AS select `a`.`name` AS `accountname`,`a`.`id` AS `accountid`,`c`.`name` AS `name`,`c`.`id` AS `characterid`,`cl`.`offense` AS `offense`,`cl`.`count` AS `count`,`cl`.`lastoffensetime` AS `lastoffensetime`,`cl`.`param` AS `param` from ((`cheatlog` `cl` join `characters` `c`) join `accounts` `a`) where ((`cl`.`id` = `c`.`id`) and (`a`.`id` = `c`.`accountid`) and (`a`.`banned` = 0));

-- ----------------------------
-- View structure for readable_last_hour_cheatlog
-- ----------------------------
DROP VIEW IF EXISTS `readable_last_hour_cheatlog`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `readable_last_hour_cheatlog` AS select `a`.`name` AS `accountname`,`a`.`id` AS `accountid`,`c`.`name` AS `name`,`c`.`id` AS `characterid`,sum(`cl`.`count`) AS `numrepos` from ((`cheatlog` `cl` join `characters` `c`) join `accounts` `a`) where ((`cl`.`id` = `c`.`id`) and (`a`.`id` = `c`.`accountid`) and (timestampdiff(HOUR,`cl`.`lastoffensetime`,now()) < 1) and (`a`.`banned` = 0)) group by `cl`.`id` order by sum(`cl`.`count`) desc;

SET FOREIGN_KEY_CHECKS = 1;
