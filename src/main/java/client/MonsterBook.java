package client;

import constants.GameConstants;
import database.DatabaseConnection;
import java.io.Serial;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import server.MapleItemInformationProvider;
import tools.MaplePacketCreator;
import tools.data.output.MaplePacketLittleEndianWriter;
import tools.packet.MonsterBookPacket;

public class MonsterBook implements Serializable {

    @Serial
    private static final long serialVersionUID = 7179541993413738569L;
    private boolean changed = false;
    private int SpecialCard = 0, NormalCard = 0, BookLevel = 1;
    private Map<Integer, Integer> cards;

    public MonsterBook(Map<Integer, Integer> cards) {
        // 防禦性拷貝，避免外部持有可變引用 (EI_EXPOSE_REP2)
        this.cards = (cards == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(cards);

        for (Entry<Integer, Integer> card : this.cards.entrySet()) {
            if (GameConstants.isSpecialCard(card.getKey())) {

                SpecialCard += card.getValue();
            } else {
                NormalCard += card.getValue();
            }
        }
        calculateLevel();
    }

    public Map<Integer, Integer> getCards() {
        // 回傳不可變視圖，避免外部修改內部狀態 (EI_EXPOSE_REP)
        return Collections.unmodifiableMap(cards);
    }

    public final int getTotalCards() {
        return SpecialCard + NormalCard;
    }

    public final int getLevelByCard(final int cardid) {
        return cards.get(cardid) == null ? 0 : cards.get(cardid);
    }

    public static final MonsterBook loadCards(final int charid) throws SQLException {
        final Map<Integer, Integer> cards = new LinkedHashMap<>();
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return new MonsterBook(cards);
            }
            try (PreparedStatement ps
                    = con.prepareStatement(
                            "SELECT cardid, level FROM monsterbook WHERE charid = ? ORDER BY cardid ASC")) {
                ps.setInt(1, charid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        cards.put(rs.getInt("cardid"), rs.getInt("level"));
                    }
                }
            }
        }
        return new MonsterBook(cards);
    }

    /** 使用呼叫端的連線儲存（與呼叫端交易一起提交/回滾）。 */
    public final void saveCards(final Connection con, final int charid) throws SQLException {
        if (!changed || cards.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM monsterbook WHERE charid = ?")) {
            ps.setInt(1, charid);
            ps.execute();
        }

        try (PreparedStatement ps =
                con.prepareStatement("INSERT INTO monsterbook VALUES (DEFAULT, ?, ?, ?)")) {
            for (final Entry<Integer, Integer> all : cards.entrySet()) {
                ps.setInt(1, charid);
                ps.setInt(2, all.getKey()); // Card ID
                ps.setInt(3, all.getValue()); // Card level
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** 獨立儲存：單一交易內完成 DELETE + INSERT。 */
    public final void saveCards(final int charid) throws SQLException {
        if (!changed || cards.isEmpty()) {
            return;
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }
            try {
                con.setAutoCommit(false);
                saveCards(con, charid);
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    private final void calculateLevel() {
        int Size = NormalCard + SpecialCard;
        BookLevel = 8;

        for (int i = 0; i < 8; i++) {
            if (Size <= GameConstants.getBookLevel(i)) {
                BookLevel = (i + 1);
                break;
            }
        }
    }

    public final void addCardPacket(final MaplePacketLittleEndianWriter mplew) {
        mplew.writeShort(cards.size());

        for (Entry<Integer, Integer> all : cards.entrySet()) {
            mplew.writeShort(GameConstants.getCardShortId(all.getKey())); // Id
            mplew.write(all.getValue()); // Level
        }
    }

    public final void addCharInfoPacket(
            final int bookcover, final MaplePacketLittleEndianWriter mplew) {
        mplew.writeInt(BookLevel);
        mplew.writeInt(NormalCard);
        mplew.writeInt(SpecialCard);
        mplew.writeInt(NormalCard + SpecialCard);
        mplew.writeInt(MapleItemInformationProvider.getInstance().getCardMobId(bookcover));
    }

    public final void updateCard(final MapleClient c, final int cardid) {
        c.sendPacket(MonsterBookPacket.changeCover(cardid));
    }

    public final void addCard(final MapleClient c, final int cardid) {
        changed = true;
        c.getPlayer()
                .getMap()
                .broadcastMessage(
                        c.getPlayer(),
                        MonsterBookPacket.showForeginCardEffect(c.getPlayer().getId()),
                        false);

        if (cards.containsKey(cardid)) {
            final int levels = cards.get(cardid);
            if (levels >= 5) {
                c.sendPacket(MonsterBookPacket.addCard(true, cardid, levels));
            } else {
                if (GameConstants.isSpecialCard(cardid)) {
                    SpecialCard += 1;
                } else {
                    NormalCard += 1;
                }
                c.sendPacket(MonsterBookPacket.addCard(false, cardid, levels));
                c.sendPacket(MonsterBookPacket.showGainCard(cardid));
                c.sendPacket(MaplePacketCreator.showSpecialEffect(13));
                cards.put(cardid, levels + 1);
                calculateLevel();
            }
            return;
        }
        if (GameConstants.isSpecialCard(cardid)) {
            SpecialCard += 1;
        } else {
            NormalCard += 1;
        }
        // New card
        cards.put(cardid, 1);
        c.sendPacket(MonsterBookPacket.addCard(false, cardid, 1));
        c.sendPacket(MonsterBookPacket.showGainCard(cardid));
        c.sendPacket(MaplePacketCreator.showSpecialEffect(13));
        calculateLevel();
    }
}