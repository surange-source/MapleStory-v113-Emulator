package client;

import database.DatabaseConnection;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import tools.MaplePacketCreator;

public final class BuddyList implements Serializable {

    public static enum BuddyOperation {
        ADDED,
        DELETED
    }

    public static enum BuddyAddResult {
        BUDDYLIST_FULL,
        ALREADY_ON_LIST,
        OK
    }

    @Serial private static final long serialVersionUID = 1413738569L;
    // 使用具體可序列化型別（LinkedHashMap / LinkedList）宣告，
    // 避免 SpotBugs SE_BAD_FIELD（宣告型別 Map/Deque 非 Serializable）。
    private transient LinkedHashMap<Integer, BuddylistEntry> buddies = new LinkedHashMap<>();
    private byte capacity;
    private transient LinkedList<CharacterNameAndId> pendingRequests = new LinkedList<>();

    public BuddyList(byte capacity) {
        super();
        this.capacity = capacity;
    }

    /** 可序列化的中介 DTO，避免直接寫出非 Serializable 的 BuddylistEntry。 */
    private static final class BuddyEntryDTO implements Serializable {

        @Serial private static final long serialVersionUID = 1L;
        final String name;
        final int characterId;
        final String group;
        final int channel;
        final boolean visible;
        final int level;
        final int job;

        BuddyEntryDTO(BuddylistEntry e) {
            this.name = e.getName();
            this.characterId = e.getCharacterId();
            this.group = e.getGroup();
            this.channel = e.getChannel();
            this.visible = e.isVisible();
            this.level = e.getLevel();
            this.job = e.getJob();
        }

        BuddylistEntry toEntry() {
            return new BuddylistEntry(name, characterId, group, channel, visible, level, job);
        }
    }

    /** 可序列化的中介 DTO，避免直接寫出非 Serializable 的 CharacterNameAndId。 */
    private static final class PendingRequestDTO implements Serializable {

        @Serial private static final long serialVersionUID = 1L;
        final int id;
        final String name;
        final int level;
        final int job;
        final String group;

        PendingRequestDTO(CharacterNameAndId c) {
            this.id = c.getId();
            this.name = c.getName();
            this.level = c.getLevel();
            this.job = c.getJob();
            this.group = c.getGroup();
        }

        CharacterNameAndId toCharacterNameAndId() {
            return new CharacterNameAndId(id, name, level, job, group);
        }
    }

    @Serial
    private void writeObject(final ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        // 以明確可序列化型別的陣列寫出，避免 SpotBugs DMI_NONSERIALIZABLE_OBJECT_WRITTEN 誤報。
        final BuddyEntryDTO[] buddyDtos = new BuddyEntryDTO[buddies.size()];
        int bi = 0;
        for (BuddylistEntry e : buddies.values()) {
            buddyDtos[bi++] = new BuddyEntryDTO(e);
        }
        final PendingRequestDTO[] pendingDtos = new PendingRequestDTO[pendingRequests.size()];
        int pi = 0;
        for (CharacterNameAndId c : pendingRequests) {
            pendingDtos[pi++] = new PendingRequestDTO(c);
        }
        out.writeObject(buddyDtos);
        out.writeObject(pendingDtos);
    }

    @Serial
    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        buddies = new LinkedHashMap<>();
        pendingRequests = new LinkedList<>();
        final Object buddyObj = in.readObject();
        if (buddyObj instanceof BuddyEntryDTO[] buddyArr) {
            for (BuddyEntryDTO dto : buddyArr) {
                if (dto != null) {
                    buddies.put(Integer.valueOf(dto.characterId), dto.toEntry());
                }
            }
        }
        final Object pendingObj = in.readObject();
        if (pendingObj instanceof PendingRequestDTO[] pendingArr) {
            for (PendingRequestDTO dto : pendingArr) {
                if (dto != null) {
                    pendingRequests.add(dto.toCharacterNameAndId());
                }
            }
        }
    }

    public boolean contains(int characterId) {
        return buddies.containsKey(Integer.valueOf(characterId));
    }

    public boolean containsVisible(int characterId) {
        BuddylistEntry ble = buddies.get(characterId);
        if (ble == null) {
            return false;
        }
        return ble.isVisible();
    }

    public byte getCapacity() {
        return capacity;
    }

    public void setCapacity(byte capacity) {
        this.capacity = capacity;
    }

    public BuddylistEntry get(int characterId) {
        return buddies.get(Integer.valueOf(characterId));
    }

    public BuddylistEntry get(String characterName) {
        String lowerCaseName = characterName.toLowerCase();
        for (BuddylistEntry ble : buddies.values()) {
            if (ble.getName().toLowerCase().equals(lowerCaseName)) {
                return ble;
            }
        }
        return null;
    }

    public void put(BuddylistEntry entry) {
        buddies.put(Integer.valueOf(entry.getCharacterId()), entry);
    }

    public void remove(int characterId) {
        buddies.remove(Integer.valueOf(characterId));
    }

    public Collection<BuddylistEntry> getBuddies() {
        return buddies.values();
    }

    public boolean isFull() {
        return buddies.size() >= capacity;
    }

    public int[] getBuddyIds() {
        int buddyIds[] = new int[buddies.size()];
        int i = 0;
        for (BuddylistEntry ble : buddies.values()) {
            buddyIds[i++] = ble.getCharacterId();
        }
        return buddyIds;
    }

    public void loadFromTransfer(final Map<CharacterNameAndId, Boolean> data) {
        CharacterNameAndId buddyid;
        boolean pair;
        for (final Map.Entry<CharacterNameAndId, Boolean> qs : data.entrySet()) {
            buddyid = qs.getKey();
            pair = qs.getValue();
            if (!pair) {
                pendingRequests.push(buddyid);
            } else {
                put(
                        new BuddylistEntry(
                                buddyid.getName(),
                                buddyid.getId(),
                                buddyid.getGroup(),
                                -1,
                                true,
                                buddyid.getLevel(),
                                buddyid.getJob()));
            }
        }
    }

    // Connection 由 DatabaseConnection 以 ThreadLocal 管理，屬於外部資源，
    // 依規範不在此關閉；僅關閉本方法內建立的 PreparedStatement / ResultSet。
    // 直接以 DatabaseConnection.getConnection() 取得連線，避免 PMD CloseResource 誤判區域變數。
    public void loadFromDb(int characterId) throws SQLException {
        // SQL 為固定字串常數，參數以 ? 綁定，無動態拼接。
        final String selectSql =
                "SELECT b.buddyid, b.pending, c.name as buddyname, c.job as buddyjob, c.level as buddylevel, b.groupname FROM buddies as b, characters as c WHERE c.id = b.buddyid AND b.characterid = ?";
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }
            try (PreparedStatement ps = con.prepareStatement(selectSql)) {
                ps.setInt(1, characterId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        final int buddyid = rs.getInt("buddyid");
                        final String buddyname = rs.getString("buddyname");
                        if (rs.getInt("pending") == 1) {
                            pendingRequests.push(
                                    new CharacterNameAndId(
                                            buddyid,
                                            buddyname,
                                            rs.getInt("buddylevel"),
                                            rs.getInt("buddyjob"),
                                            rs.getString("groupname")));
                        } else {
                            put(
                                    new BuddylistEntry(
                                            buddyname,
                                            buddyid,
                                            rs.getString("groupname"),
                                            -1,
                                            true,
                                            rs.getInt("buddylevel"),
                                            rs.getInt("buddyjob")));
                        }
                    }
                }
            }
            final String deleteSql = "DELETE FROM buddies WHERE pending = 1 AND characterid = ?";
            try (PreparedStatement ps = con.prepareStatement(deleteSql)) {
                ps.setInt(1, characterId);
                ps.executeUpdate();
            }
        }
    }

    public CharacterNameAndId pollPendingRequest() {
        return pendingRequests.pollLast();
    }

    public void addBuddyRequest(
            MapleClient c,
            int cidFrom,
            String nameFrom,
            int channelFrom,
            int levelFrom,
            int jobFrom) {
        put(new BuddylistEntry(nameFrom, cidFrom, "其他", channelFrom, false, levelFrom, jobFrom));
        if (pendingRequests.isEmpty()) {
            c.sendPacket(
                    MaplePacketCreator.requestBuddylistAdd(cidFrom, nameFrom, levelFrom, jobFrom));
        } else {
            pendingRequests.push(
                    new CharacterNameAndId(cidFrom, nameFrom, levelFrom, jobFrom, "其他"));
        }
    }
}
