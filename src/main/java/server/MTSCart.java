package server;

import client.inventory.IItem;
import client.inventory.ItemLoader;
import client.inventory.MapleInventoryType;
import constants.GameConstants;
import database.DatabaseConnection;
import java.io.Serial;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import tools.Pair;

public final class MTSCart implements Serializable {

    @Serial
    private static final long serialVersionUID = 231541893513373578L;
    private int characterId, tab = 1, type = 0, page = 0;
    // tab; 1 = buy now, 2 = wanted, 3 = auction, 4 = cart
    // type = inventorytype; 0 = anything
    // page = whatever
    private List<IItem> transfer = new ArrayList<>();
    private List<Integer> cart = new ArrayList<>();
    private List<Integer> notYetSold = new ArrayList<>(10);
    private int owedNX = 0;

    MTSCart(int characterId) {
        this.characterId = characterId;
    }

    /**
     * 建立並初始化 MTSCart。將可能失敗的初始化從建構子移出， 避免建構子拋出例外（CT_CONSTRUCTOR_THROW）。
     *
     * @param characterId 角色 ID
     * @return 已載入資料的 MTSCart
     * @throws SQLException 載入資料失敗時
     */
    public static MTSCart create(int characterId) throws SQLException {
        MTSCart cart = new MTSCart(characterId);
        for (Pair<IItem, MapleInventoryType> item
                : ItemLoader.MTS_TRANSFER.loadItems(false, characterId).values()) {
            cart.transfer.add(item.left());
        }
        cart.loadCart();
        cart.loadNotYetSold();
        return cart;
    }

    public List<IItem> getInventory() {
        if (transfer == null) {
            transfer = new ArrayList<>();
        }
        return transfer;
    }

    public void addToInventory(IItem item) {
        transfer.add(item);
    }

    public void removeFromInventory(IItem item) {
        transfer.remove(item);
    }

    public List<Integer> getCart() {
        return cart;
    }

    public boolean addToCart(final int car) {
        if (!cart.contains(car)) {
            cart.add(car);
            return true;
        }
        return false;
    }

    public void removeFromCart(final int car) {
        for (int i = 0; i < cart.size(); i++) {
            if (cart.get(i) == car) {
                cart.remove(i);
            }
        }
    }

    public List<Integer> getNotYetSold() {
        return notYetSold;
    }

    public void addToNotYetSold(final int car) {
        notYetSold.add(car);
    }

    public void removeFromNotYetSold(final int car) {
        for (int i = 0; i < notYetSold.size(); i++) {
            if (notYetSold.get(i) == car) {
                notYetSold.remove(i);
            }
        }
    }

    public final int getSetOwedNX() {
        final int on = owedNX;
        owedNX = 0;
        return on;
    }

    public void increaseOwedNX(final int newNX) {
        owedNX += newNX;
    }

    public void save() throws SQLException {
        List<Pair<IItem, MapleInventoryType>> itemsWithType = new ArrayList<>();

        for (IItem item : getInventory()) {
            itemsWithType.add(new Pair<>(item, GameConstants.getInventoryType(item.getItemId())));
        }

        ItemLoader.MTS_TRANSFER.saveItems(itemsWithType, characterId);
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }
            try (PreparedStatement ps
                    = con.prepareStatement("DELETE FROM mts_cart WHERE characterid = ?")) {
                ps.setInt(1, characterId);
                ps.execute();
            }
            try (PreparedStatement ps
                    = con.prepareStatement("INSERT INTO mts_cart VALUES(DEFAULT, ?, ?)")) {
                ps.setInt(1, characterId);
                for (int i : cart) {
                    ps.setInt(2, i);
                    ps.executeUpdate();
                }
                if (owedNX > 0) {
                    ps.setInt(2, -owedNX);
                    ps.executeUpdate();
                }
            }
        }
    }

    public void loadCart() throws SQLException {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }
            try (PreparedStatement ps
                    = con.prepareStatement("SELECT * FROM mts_cart WHERE characterid = ?")) {
                ps.setInt(1, characterId);
                try (ResultSet rs = ps.executeQuery()) {
                    int iId;
                    while (rs.next()) {
                        iId = rs.getInt("itemid");
                        if (iId < 0) {
                            owedNX -= iId;
                        } else if (MTSStorage.getInstance().check(iId)) {
                            cart.add(iId);
                        }
                    }
                }
            }
        }
    }

    public void loadNotYetSold() throws SQLException {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }
            try (PreparedStatement ps
                    = con.prepareStatement("SELECT * FROM mts_items WHERE characterid = ?")) {
                ps.setInt(1, characterId);
                try (ResultSet rs = ps.executeQuery()) {
                    int pId;
                    while (rs.next()) {
                        pId = rs.getInt("id");
                        if (MTSStorage.getInstance().check(pId)) {
                            notYetSold.add(pId);
                        }
                    }
                }
            }
        }
    }

    public void changeInfo(final int tab, final int type, final int page) {
        this.tab = tab;
        this.type = type;
        this.page = page;
    }

    public int getTab() {
        return tab;
    }

    public int getType() {
        return type;
    }

    public int getPage() {
        return page;
    }
}
