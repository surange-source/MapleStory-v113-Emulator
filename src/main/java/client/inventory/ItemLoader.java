package client.inventory;

import constants.ItemConstants;
import database.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.Pair;

public enum ItemLoader {
    INVENTORY("inventoryitems", "inventoryequipment", 0, "characterid"),
    STORAGE("inventoryitems", "inventoryequipment", 1, "accountid"),
    CASHSHOP_EXPLORER("csitems", "csequipment", 2, "accountid"),
    CASHSHOP_CYGNUS("csitems", "csequipment", 3, "accountid"),
    CASHSHOP_ARAN("csitems", "csequipment", 4, "accountid"),
    HIRED_MERCHANT(
            "hiredmerchitems", "hiredmerchequipment", 5, "packageid", "accountid", "characterid"),
    HIRED_FISHING(
            "hiredfishingitems",
            "hiredfishingequipment",
            12,
            "packageid",
            "accountid",
            "characterid"),
    DUEY("dueyitems", "dueyequipment", 6, "packageid"),
    CASHSHOP_EVAN("csitems", "csequipment", 7, "accountid"),
    MTS("mtsitems", "mtsequipment", 8, "packageid"),
    MTS_TRANSFER("mtstransfer", "mtstransferequipment", 9, "characterid"),
    CASHSHOP_DB("csitems", "csequipment", 10, "accountid"),
    CASHSHOP_RESIST("csitems", "csequipment", 11, "accountid");

    /**
     * 白名單容器：獨立靜態嵌套類，避免 enum 常量初始化時對 ItemLoader 靜態欄位的 forward reference。 構造器內以限定名 Whitelist.TABLES /
     * Whitelist.COLUMNS 訪問，不觸發 JLS 8.3.3。
     */
    private static final class Whitelist {
        static final Set<String> TABLES =
                Collections.unmodifiableSet(
                        new HashSet<>(
                                Arrays.asList(
                                        "inventoryitems", "inventoryequipment",
                                        "csitems", "csequipment",
                                        "hiredmerchitems", "hiredmerchequipment",
                                        "hiredfishingitems", "hiredfishingequipment",
                                        "dueyitems", "dueyequipment",
                                        "mtsitems", "mtsequipment",
                                        "mtstransfer", "mtstransferequipment")));

        static final Set<String> COLUMNS =
                Collections.unmodifiableSet(
                        new HashSet<>(Arrays.asList("characterid", "accountid", "packageid")));

        private Whitelist() {}
    }

    /** 白名單：允許使用的欄位名稱。 */
    private static final Set<String> ALLOWED_COLUMNS =
            Collections.unmodifiableSet(
                    new HashSet<>(Arrays.asList("characterid", "accountid", "packageid")));

    private final int value;
    private final String table;
    private final String table_equip;
    private final List<String> arg;

    private ItemLoader(String table, String table_equip, int value, String... arg) {
        if (!Whitelist.TABLES.contains(table) || !Whitelist.TABLES.contains(table_equip)) {
            throw new IllegalArgumentException(
                    "Illegal table name: " + table + " / " + table_equip);
        }
        for (String column : arg) {
            if (!Whitelist.COLUMNS.contains(column)) {
                throw new IllegalArgumentException("Illegal column name: " + column);
            }
        }
        this.table = table;
        this.table_equip = table_equip;
        this.value = value;
        this.arg = Collections.unmodifiableList(Arrays.asList(arg));
    }

    public int getValue() {
        return value;
    }

    /** 組裝 SELECT 語句。表名與欄位名皆已於建構子通過白名單驗證， 所有值一律使用 {@code ?} 參數化。 */
    private String buildSelectSql(boolean login) {
        StringBuilder query = new StringBuilder();
        query.append("SELECT * FROM `");
        query.append(table);
        query.append("` LEFT JOIN `");
        query.append(table_equip);
        query.append("` USING(`inventoryitemid`) WHERE `type` = ?");
        for (String g : arg) {
            query.append(" AND `");
            query.append(g);
            query.append("` = ?");
        }
        if (login) {
            query.append(" AND `inventorytype` = ?");
        }
        return query.toString();
    }

    // does not need connection con to be auto commit
    public Map<Integer, Pair<IItem, MapleInventoryType>> loadItems(boolean login, Integer... id)
            throws SQLException {
        Map<Integer, Pair<IItem, MapleInventoryType>> items = new LinkedHashMap<>();
        if (id == null) {
            return items;
        }
        List<Integer> lulz = Arrays.asList(id);
        if (lulz.size() != arg.size()) {
            return items;
        }

try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return items;
            }
            try (PreparedStatement ps = con.prepareStatement(buildSelectSql(login))) {
                ps.setInt(1, value);
                for (int i = 0; i < lulz.size(); i++) {
                    ps.setInt(i + 2, lulz.get(i));
                }
                if (login) {
                    ps.setInt(lulz.size() + 2, MapleInventoryType.EQUIPPED.getType());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        MapleInventoryType mit =
                                MapleInventoryType.getByType(rs.getByte("inventorytype"));

                        if (mit.equals(MapleInventoryType.EQUIP)
                                || mit.equals(MapleInventoryType.EQUIPPED)) {
                            Equip equip =
                                    new Equip(
                                            rs.getInt("itemid"),
                                            rs.getShort("position"),
                                            rs.getInt("uniqueid"),
                                            rs.getByte("flag"));
                            if (!login) {
                                equip.setQuantity((short) 1);
                                equip.setOwner(rs.getString("owner"));
                                equip.setExpiration(rs.getLong("expiredate"));
                                equip.setUpgradeSlots(rs.getByte("upgradeslots"));
                                equip.setLevel(rs.getByte("level"));
                                equip.setStr(rs.getShort("str"));
                                equip.setDex(rs.getShort("dex"));
                                equip.setInt(rs.getShort("int"));
                                equip.setLuk(rs.getShort("luk"));
                                equip.setHp(rs.getShort("hp"));
                                equip.setMp(rs.getShort("mp"));
                                equip.setWatk(rs.getShort("watk"));
                                equip.setMatk(rs.getShort("matk"));
                                equip.setWdef(rs.getShort("wdef"));
                                equip.setMdef(rs.getShort("mdef"));
                                equip.setAcc(rs.getShort("acc"));
                                equip.setAvoid(rs.getShort("avoid"));
                                equip.setHands(rs.getShort("hands"));
                                equip.setSpeed(rs.getShort("speed"));
                                equip.setJump(rs.getShort("jump"));
                                equip.setViciousHammer(rs.getByte("ViciousHammer"));
                                equip.setItemEXP(rs.getInt("itemEXP"));
                                equip.setGMLog(rs.getString("GM_Log"));
                                equip.setDurability(rs.getInt("durability"));
                                equip.setEnhance(rs.getByte("enhance"));
                                equip.setPotential1(rs.getShort("potential1"));
                                equip.setPotential2(rs.getShort("potential2"));
                                equip.setPotential3(rs.getShort("potential3"));
                                equip.setHpR(rs.getShort("hpR"));
                                equip.setMpR(rs.getShort("mpR"));
                                equip.setGiftFrom(rs.getString("sender"));
                                if (equip.getUniqueId() > -1) {
                                    if (ItemConstants.ItemTypes.isEffectRing(rs.getInt("itemid"))) {
                                        MapleRing ring =
                                                MapleRing.loadFromDb(
                                                        equip.getUniqueId(),
                                                        mit.equals(MapleInventoryType.EQUIPPED));
                                        if (ring != null) {
                                            equip.setRing(ring);
                                        }
                                    }
                                }
                            }
                            items.put(rs.getInt("inventoryitemid"), new Pair<>(equip.copy(), mit));
                        } else {
                            Item item =
                                    new Item(
                                            rs.getInt("itemid"),
                                            rs.getShort("position"),
                                            rs.getShort("quantity"),
                                            rs.getByte("flag"));
                            item.setUniqueId(rs.getInt("uniqueid"));
                            item.setOwner(rs.getString("owner"));
                            item.setExpiration(rs.getLong("expiredate"));
                            item.setGMLog(rs.getString("GM_Log"));
                            item.setGiftFrom(rs.getString("sender"));
                            if (ItemConstants.ItemTypes.isPet(item.getItemId())) {
                                if (item.getUniqueId() > -1) {
                                    MaplePet pet =
                                            MaplePet.loadFromDb(
                                                    item.getItemId(),
                                                    item.getUniqueId(),
                                                    item.getPosition());
                                    if (pet != null) {
                                        item.setPet(pet);
                                    }
                                } else {
                                    // O_O hackish fix
                                    final int new_unique = MapleInventoryIdentifier.getInstance();
                                    item.setUniqueId(new_unique);
                                    item.setPet(MaplePet.createPet(item.getItemId(), new_unique));
                                }
                            }
                            items.put(rs.getInt("inventoryitemid"), new Pair<>(item.copy(), mit));
                        }
                    }
                }
            }
        }
        return items;
    }

    public void saveItems(List<Pair<IItem, MapleInventoryType>> items, Integer... id)
            throws SQLException {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }
            saveItems(items, con, id);
        }
    }

    /** 組裝 DELETE 語句。表名與欄位名皆已於建構子通過白名單驗證。 */
    private String buildDeleteSql() {
        StringBuilder query = new StringBuilder();
        query.append("DELETE FROM `");
        query.append(table);
        query.append("` WHERE `type` = ? AND (`");
        query.append(arg.get(0));
        query.append("` = ?");
        for (int i = 1; i < arg.size(); i++) {
            query.append(" OR `");
            query.append(arg.get(i));
            query.append("` = ?");
        }
        query.append(")");
        return query.toString();
    }

    /** 組裝 INSERT 語句。表名與欄位名皆已於建構子通過白名單驗證。 */
    private String buildInsertSql() {
        StringBuilder query = new StringBuilder("INSERT INTO `");
        query.append(table);
        query.append("` (");
        for (String g : arg) {
            query.append(g);
            query.append(", ");
        }
        query.append(
                "itemid, inventorytype, position, quantity, owner, GM_Log, uniqueid, expiredate, flag, `type`, sender) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?");
        for (int i = 0; i < arg.size(); i++) {
            query.append(", ?");
        }
        query.append(")");
        return query.toString();
    }

    /** 組裝裝備 INSERT 語句。表名已於建構子通過白名單驗證。 */
    private String buildInsertEquipSql() {
        return "INSERT INTO `"
                + table_equip
                + "` VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    public void saveItems(
            List<Pair<IItem, MapleInventoryType>> items, final Connection con, Integer... id)
            throws SQLException {
        // con 為外部傳入資源，由呼叫端/DatabaseConnection 管理，本方法不負責關閉。
        if (con == null || id == null) {
            return;
        }
        List<Integer> lulz = Arrays.asList(id);
        if (lulz.size() != arg.size()) {
            return;
        }

        try (PreparedStatement ps = con.prepareStatement(buildDeleteSql())) {
            ps.setInt(1, value);
            for (int i = 0; i < lulz.size(); i++) {
                ps.setInt(i + 2, lulz.get(i));
            }
            ps.executeUpdate();
        }
        if (items == null) {
            return;
        }
        try (PreparedStatement ps =
                        con.prepareStatement(
                                buildInsertSql(), PreparedStatement.RETURN_GENERATED_KEYS);
                PreparedStatement pse = con.prepareStatement(buildInsertEquipSql())) {
            final Iterator<Pair<IItem, MapleInventoryType>> iter = items.iterator();
            Pair<IItem, MapleInventoryType> pair;
            while (iter.hasNext()) {
                pair = iter.next();
                if (pair == null) {
                    continue;
                }
                IItem item = pair.left();
                MapleInventoryType mit = pair.right();
                if (item == null || mit == null) {
                    continue;
                }
                int i = 1;
                for (int x = 0; x < lulz.size(); x++) {
                    ps.setInt(i, lulz.get(x));
                    i++;
                }
                ps.setInt(i, item.getItemId());
                ps.setInt(i + 1, mit.getType());
                ps.setInt(i + 2, item.getPosition());
                ps.setInt(i + 3, item.getQuantity());
                ps.setString(i + 4, item.getOwner());
                ps.setString(i + 5, item.getGMLog());
                ps.setInt(i + 6, item.getUniqueId());
                ps.setLong(i + 7, item.getExpiration());
                ps.setByte(i + 8, item.getFlag());
                ps.setByte(i + 9, (byte) value);
                ps.setString(i + 10, item.getGiftFrom());
                ps.executeUpdate();

                if (mit.equals(MapleInventoryType.EQUIP)
                        || mit.equals(MapleInventoryType.EQUIPPED)) {
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) {
                            throw new RuntimeException("Inserting item failed.");
                        }

                        pse.setInt(1, rs.getInt(1));
                    }
                    IEquip equip = (IEquip) item;
                    pse.setInt(2, equip.getUpgradeSlots());
                    pse.setInt(3, equip.getLevel());
                    pse.setInt(4, equip.getStr());
                    pse.setInt(5, equip.getDex());
                    pse.setInt(6, equip.getInt());
                    pse.setInt(7, equip.getLuk());
                    pse.setInt(8, equip.getHp());
                    pse.setInt(9, equip.getMp());
                    pse.setInt(10, equip.getWatk());
                    pse.setInt(11, equip.getMatk());
                    pse.setInt(12, equip.getWdef());
                    pse.setInt(13, equip.getMdef());
                    pse.setInt(14, equip.getAcc());
                    pse.setInt(15, equip.getAvoid());
                    pse.setInt(16, equip.getHands());
                    pse.setInt(17, equip.getSpeed());
                    pse.setInt(18, equip.getJump());
                    pse.setInt(19, equip.getViciousHammer());
                    pse.setInt(20, equip.getItemEXP());
                    pse.setInt(21, equip.getDurability());
                    pse.setByte(22, equip.getEnhance());
                    pse.setInt(23, equip.getPotential1());
                    pse.setInt(24, equip.getPotential2());
                    pse.setInt(25, equip.getPotential3());
                    pse.setInt(26, equip.getHpR());
                    pse.setInt(27, equip.getMpR());
                    pse.executeUpdate();
                }
            }
        }
    }
}
