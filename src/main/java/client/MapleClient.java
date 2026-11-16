package client;

import database.DatabaseConnection;
import database.DatabaseException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import handling.MaplePacket;
import handling.cashshop.CashShopServer;
import handling.channel.ChannelServer;
import handling.world.MapleMessengerCharacter;
import handling.world.MapleParty;
import handling.world.MaplePartyCharacter;
import handling.world.PartyOperation;
import handling.world.World;
import handling.world.family.MapleFamilyCharacter;
import handling.world.guild.MapleGuildCharacter;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.script.ScriptEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.MapleTrade;
import server.Timer.PingTimer;
import server.maps.MapleMap;
import server.quest.MapleQuest;
import server.shops.IMaplePlayerShop;
import tools.MapleAESOFB;
import tools.MaplePacketCreator;
import tools.packet.LoginPacket;

@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
        justification =
                "MapleClient is a session-scoped object that intentionally shares mutable "
                        + "Netty Channel, crypto state and player reference with the rest of the server. "
                        + "Defensive copies would break the live session semantics.")
public final class MapleClient implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(MapleClient.class);

    @Serial private static final long serialVersionUID = 9179541993413738569L;
    public static final transient byte LOGIN_NOTLOGGEDIN = 0,
            LOGIN_SERVER_TRANSITION = 1,
            LOGIN_LOGGEDIN = 2,
            LOGIN_WAITING = 3,
            CASH_SHOP_TRANSITION = 4,
            LOGIN_CS_LOGGEDIN = 5,
            CHANGE_CHANNEL = 6;
    public static final int DEFAULT_CHARSLOT = 6;
    public static final AttributeKey<MapleClient> CLIENT_KEY = AttributeKey.valueOf("Client");
    private transient MapleAESOFB send, receive;
    private transient Channel connection;
    private MapleCharacter player;
    private int channel = 1, accId = 1, world, birthday;
    private int charslots = DEFAULT_CHARSLOT;
    private boolean loggedIn = false, serverTransition = false;
    private transient Calendar tempban = null;
    private String accountName;
    private transient long lastPong = 0, lastPing = 0;
    private boolean monitored = false, receiving = true;
    private boolean gm;
    private byte greason = 1, gender = -1;
    public transient short loginAttempt = 0;
    private transient List<Integer> allowedChar = new LinkedList<>();
    private transient Set<String> macs = new HashSet<>();
    private transient Map<String, ScriptEngine> engines = new ConcurrentHashMap<>();
    private transient ScheduledFuture<?> idleTask = null;
    private transient ScheduledFuture<?> pingTask = null;
    private transient AtomicBoolean disconnecting = new AtomicBoolean(false);
    private transient String secondPassword, salt2; // To be used only on login
    private transient Lock mutex = new ReentrantLock(true);
    private transient Lock npc_mutex = new ReentrantLock();
    private long lastNpcClick = 0, lastLoginTime;
    private static final Lock login_mutex = new ReentrantLock(true);

    public MapleClient(MapleAESOFB send, MapleAESOFB receive, Channel connection) {
        this.send = send;
        this.receive = receive;
        this.connection = connection;
    }

    @Serial
    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.allowedChar = new LinkedList<>();
        this.macs = new HashSet<>();
        this.engines = new ConcurrentHashMap<>();
        this.mutex = new ReentrantLock(true);
        this.npc_mutex = new ReentrantLock();
        this.disconnecting = new AtomicBoolean(false);
        this.idleTask = null;
        this.pingTask = null;
        this.secondPassword = null;
        this.salt2 = null;
    }

    public final MapleAESOFB getReceiveCrypto() {
        return receive;
    }

    public final MapleAESOFB getSendCrypto() {
        return send;
    }

    public final Channel getSession() {
        return connection;
    }

    public final Lock getLock() {
        return mutex;
    }

    public final Lock getNPCLock() {
        return npc_mutex;
    }

    public MapleCharacter getPlayer() {
        return player;
    }

    public void setPlayer(MapleCharacter player) {
        this.player = player;
    }

    public void createdChar(final int id) {
        allowedChar.add(id);
    }

    public final boolean login_Auth(final int id) {
        return allowedChar.contains(id);
    }

    public final List<MapleCharacter> loadCharacters(final int serverId) {
        final List<MapleCharacter> chars = new LinkedList<>();

        for (final CharNameAndId cni : loadCharactersInternal(serverId)) {
            final MapleCharacter chr = MapleCharacter.loadCharFromDB(cni.id, this, false);
            chars.add(chr);
            allowedChar.add(chr.getId());
        }
        return chars;
    }

    public List<String> loadCharacterNames(int serverId) {
        List<String> chars = new LinkedList<>();
        for (CharNameAndId cni : loadCharactersInternal(serverId)) {
            chars.add(cni.name);
        }
        return chars;
    }

    private List<CharNameAndId> loadCharactersInternal(int serverId) {
        List<CharNameAndId> chars = new LinkedList<>();
        try (Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps =
                        con.prepareStatement(
                                "SELECT id, name FROM characters WHERE accountid = ? AND world = ?")) {
            ps.setInt(1, accId);
            ps.setInt(2, serverId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    chars.add(new CharNameAndId(rs.getString("name"), rs.getInt("id")));
                }
            }
        } catch (SQLException e) {
            LOG.error("error loading characters internal", e);
        }
        return chars;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    private Calendar getTempBanCalendar(ResultSet rs) throws SQLException {
        Calendar lTempban = Calendar.getInstance();
        if (rs.getLong("tempban") == 0) {
            lTempban.setTimeInMillis(0);
            return lTempban;
        }
        Calendar today = Calendar.getInstance();
        lTempban.setTimeInMillis(rs.getTimestamp("tempban").getTime());
        if (today.getTimeInMillis() < lTempban.getTimeInMillis()) {
            return lTempban;
        }

        lTempban.setTimeInMillis(0);
        return lTempban;
    }

    public Calendar getTempBanCalendar() {
        return tempban == null ? null : (Calendar) tempban.clone();
    }

    public byte getBanReason() {
        return greason;
    }

    public boolean hasBannedIP() {
        boolean ret = false;
        if (connection == null || connection.remoteAddress() == null) {
            return false;
        }
        try (Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps =
                        con.prepareStatement(
                                "SELECT COUNT(*) FROM ipbans WHERE ? LIKE CONCAT(ip, '%')")) {
            ps.setString(1, connection.remoteAddress().toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    ret = true;
                }
            }
        } catch (SQLException ex) {
            LOG.error("Error checking ip bans", ex);
        }
        return ret;
    }

    public boolean hasBannedMac() {
        if (macs.isEmpty()) {
            return false;
        }
        boolean ret = false;
        int i = 0;
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM macbans WHERE mac IN (");
        for (i = 0; i < macs.size(); i++) {
            sql.append("?");
            if (i != macs.size() - 1) {
                sql.append(", ");
            }
        }
        sql.append(")");
        try (Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql.toString())) {
            i = 0;
            for (String mac : macs) {
                i++;
                ps.setString(i, mac);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    ret = true;
                }
            }
        } catch (SQLException ex) {
            LOG.error("Error checking mac bans", ex);
        }
        return ret;
    }

    private void loadMacsIfNescessary() throws SQLException {
        if (macs.isEmpty()) {
            try (Connection con = DatabaseConnection.getConnection();
                    PreparedStatement ps =
                            con.prepareStatement("SELECT macs FROM accounts WHERE id = ?")) {
                ps.setInt(1, accId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        if (rs.getString("macs") != null) {
                            String[] macData = rs.getString("macs").split(", ");
                            for (String mac : macData) {
                                if (!mac.equals("")) {
                                    macs.add(mac);
                                }
                            }
                        }
                    } else {
                        throw new RuntimeException("No valid account associated with this client.");
                    }
                }
            }
        }
    }

    public void banMacs() {
        try {
            loadMacsIfNescessary();
            if (this.macs.size() > 0) {
                String[] macBans = new String[this.macs.size()];
                int z = 0;
                for (String mac : this.macs) {
                    macBans[z] = mac;
                    z++;
                }
                banMacs(macBans);
            }
        } catch (SQLException e) {
            LOG.error("MapleClient.banMacs 發生例外", e);
        }
    }

    public static final void banMacs(String[] macs) {
        try (Connection con = DatabaseConnection.getConnection()) {
            List<String> filtered = new LinkedList<>();
            try (PreparedStatement ps = con.prepareStatement("SELECT filter FROM macfilters");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    filtered.add(rs.getString("filter"));
                }
            }

            try (PreparedStatement ps =
                    con.prepareStatement("INSERT INTO macbans (mac) VALUES (?)")) {
                for (String mac : macs) {
                    boolean matched = false;
                    for (String filter : filtered) {
                        if (mac.matches(filter)) {
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) {
                        ps.setString(1, mac);
                        try {
                            ps.executeUpdate();
                        } catch (SQLException e) {
                            // can fail because of UNIQUE key, we dont care
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOG.error("Error banning MACs", e);
        }
    }

    public int finishLogin() {
        login_mutex.lock();
        try {
            final byte state = getLoginState();
            if (state > MapleClient.LOGIN_NOTLOGGEDIN
                    && state != MapleClient.LOGIN_WAITING) {
                loggedIn = false;
                return 7;
            }
            updateLoginState(MapleClient.LOGIN_LOGGEDIN, getSessionIPAddress());
        } finally {
            login_mutex.unlock();
        }
        return 0;
    }

    public int login(String login, String pwd, boolean ipMacBanned) {
        int loginok = 5;
        try (Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT * FROM accounts WHERE name = ?")) {
            ps.setString(1, login);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    final int banned = rs.getInt("banned");
                    final String passhash = rs.getString("password");
                    final String salt = rs.getString("salt");

                    accId = rs.getInt("id");
                    secondPassword = rs.getString("2ndpassword");
                    salt2 = rs.getString("salt2");
                    gm = rs.getInt("gm") > 0;
                    greason = rs.getByte("greason");
                    tempban = getTempBanCalendar(rs);
                    gender = rs.getByte("gender");

                    if (secondPassword != null && salt2 != null) {
                        secondPassword = LoginCrypto.rand_r(secondPassword);
                    }

                    if (banned > 0 && !gm) {
                        loginok = 3;
                    } else {
                        if (banned == -1) {
                            unban();
                        }
                        byte loginstate = getLoginState();
                        if (loginstate > MapleClient.LOGIN_NOTLOGGEDIN) {
                            loggedIn = false;
                            if (salt == null && LoginCrypto.checkSha1Hash(passhash, pwd)) {
                                loginok = 7;
                                unlockAcc();
                            } else {
                                loginok = 4;
                            }
                        } else {
                            boolean updatePasswordHash = false;
                            boolean updatePasswordHashtosha1 = false;
                            if (LoginCryptoLegacy.isLegacyPassword(passhash)
                                    && LoginCryptoLegacy.checkPassword(pwd, passhash)) {
                                loginok = 0;
                                updatePasswordHashtosha1 = true;
                            } else if (salt == null) {
                                if (LoginCrypto.checkSha1Hash(passhash, pwd)) {
                                    loginok = 0;
                                    if (LoginCrypto.isLegacySha1Hash(passhash, pwd)) {
                                        updatePasswordHashtosha1 = true;
                                    }
                                }
                            } else if (LoginCrypto.checkSaltedSha512Hash(passhash, pwd, salt)) {
                                loginok = 0;
                                if (LoginCrypto.isLegacySaltedSha512Hash(passhash, pwd, salt)) {
                                    updatePasswordHash = true;
                                }
                            } else {
                                loggedIn = false;
                                loginok = 4;
                            }
                            if (updatePasswordHash) {
                                try (PreparedStatement pss =
                                        con.prepareStatement(
                                                "UPDATE `accounts` SET `password` = ?, `salt` = ? WHERE id = ?")) {
                                    final String newSalt = LoginCrypto.makeSalt();
                                    pss.setString(
                                            1, LoginCrypto.makeSaltedSha512Hash(pwd, newSalt));
                                    pss.setString(2, newSalt);
                                    pss.setInt(3, accId);
                                    pss.executeUpdate();
                                }
                            }
                            if (updatePasswordHashtosha1) {
                                try (PreparedStatement pss =
                                        con.prepareStatement(
                                                "UPDATE `accounts` SET `password` = ?, `salt` = ? WHERE id = ?")) {
                                    pss.setString(1, LoginCrypto.makeSaltedSha1Hash(pwd));
                                    pss.setString(2, null);
                                    pss.setInt(3, accId);
                                    pss.executeUpdate();
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOG.error("ERROR", e);
        }
        return loginok;
    }

    public void unlockAcc() {
        boolean unLocked = false;
        for (final MapleClient c : World.Client.getClients()) {
            if (c.getAccID() == accId && c.isLoggedIn()) {
                if (c.getSession() != null && c.getSession().isActive()) {
                    List<String> charName = c.loadCharacterNames(c.getWorld());
                    for (final String cha : charName) {
                        MapleCharacter chr =
                                CashShopServer.getPlayerStorage().getCharacterByName(cha);
                        if (chr != null) {
                            chr.saveToDB(false, false);
                            CashShopServer.getPlayerStorage().deregisterPlayer(chr);
                            break;
                        }
                    }
                    for (ChannelServer cs : ChannelServer.getAllInstances()) {
                        for (final String cha : charName) {
                            MapleCharacter chr = cs.getPlayerStorage().getCharacterByName(cha);
                            if (chr != null) {
                                chr.saveToDB(false, false);
                                cs.removePlayer(chr);
                                break;
                            }
                        }
                    }
                }
                c.unLockDisconnect();
                unLocked = true;
                break;
            }
        }
        if (!unLocked) {
            try (Connection con = DatabaseConnection.getConnection();
                    PreparedStatement ps =
                            con.prepareStatement(
                                    "UPDATE accounts SET loggedin = 0 WHERE name = ?")) {
                ps.setString(1, accountName);
                ps.executeUpdate();
            } catch (SQLException se) {
                // 維持原方法行為
            }
        }
    }

    public final void unLockDisconnect() {
        sendPacket(MaplePacketCreator.serverNotice(1, "帳號被他人登入。"));
        disconnect(serverTransition, getChannel() == -10);

        Thread.ofVirtual()
                .name("unlock-acc" + accId)
                .start(() -> {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    final Channel s = getSession();
                    if (s != null && s.isOpen()) {
                        s.close();
                    }
                });
    }

    public boolean CheckSecondPassword(String in) {
        boolean allow = false;
        boolean updatePasswordHash = false;

        if (LoginCryptoLegacy.isLegacyPassword(secondPassword)
                && LoginCryptoLegacy.checkPassword(in, secondPassword)) {
            allow = true;
            updatePasswordHash = true;
        } else if (salt2 == null) {
            if (LoginCrypto.checkSha1Hash(secondPassword, in)) {
                allow = true;
                if (LoginCrypto.isLegacySha1Hash(secondPassword, in)) {
                    updatePasswordHash = true;
                }
            }
        } else if (LoginCrypto.checkSaltedSha512Hash(secondPassword, in, salt2)) {
            allow = true;
            if (LoginCrypto.isLegacySaltedSha512Hash(secondPassword, in, salt2)) {
                updatePasswordHash = true;
            }
        }
        if (updatePasswordHash) {
            try (Connection con = DatabaseConnection.getConnection();
                    PreparedStatement ps =
                            con.prepareStatement(
                                    "UPDATE `accounts` SET `2ndpassword` = ?, `salt2` = ? WHERE id = ?")) {
                final String newSalt = LoginCrypto.makeSalt();
                ps.setString(1, LoginCrypto.rand_s(LoginCrypto.makeSaltedSha512Hash(in, newSalt)));
                ps.setString(2, newSalt);
                ps.setInt(3, accId);
                ps.executeUpdate();
            } catch (SQLException e) {
                return false;
            }
        }
        return allow;
    }

    private void unban() {
        try (Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps =
                        con.prepareStatement(
                                "UPDATE accounts SET banned = 0 and banreason = '' WHERE id = ?")) {
            ps.setInt(1, accId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Error while unbanning", e);
        }
    }

    public static final byte unban(String charname) {
        try (Connection con = DatabaseConnection.getConnection()) {
            final int accid;
            try (PreparedStatement ps =
                    con.prepareStatement("SELECT accountid from characters where name = ?")) {
                ps.setString(1, charname);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return -1;
                    }
                    accid = rs.getInt(1);
                }
            }

            try (PreparedStatement ps =
                    con.prepareStatement(
                            "UPDATE accounts SET banned = 0 and banreason = '' WHERE id = ?")) {
                ps.setInt(1, accid);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            LOG.error("Error while unbanning", e);
            return -2;
        }
        return 0;
    }

    public void updateMacs(String macData) {
        for (String mac : macData.split(", ")) {
            macs.add(mac);
        }
        StringBuilder newMacData = new StringBuilder();
        Iterator<String> iter = macs.iterator();
        while (iter.hasNext()) {
            newMacData.append(iter.next());
            if (iter.hasNext()) {
                newMacData.append(", ");
            }
        }
        try (Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("UPDATE accounts SET macs = ? WHERE id = ?")) {
            ps.setString(1, newMacData.toString());
            ps.setInt(2, accId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Error saving MACs", e);
        }
    }

    public void setAccID(int id) {
        this.accId = id;
    }

    public int getAccID() {
        return this.accId;
    }

    public final void updateLoginState(final int newstate, final String SessionID) {
        try (Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps =
                        con.prepareStatement(
                                "UPDATE accounts SET loggedin = ?, SessionIP = ?, lastlogin = CURRENT_TIMESTAMP() WHERE id = ?")) {
            ps.setInt(1, newstate);
            ps.setString(2, SessionID);
            ps.setInt(3, getAccID());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("error updating login state", e);
        }
        if (newstate == MapleClient.LOGIN_NOTLOGGEDIN || newstate == MapleClient.LOGIN_WAITING) {
            loggedIn = false;
            serverTransition = false;
        } else {
            serverTransition =
                    (newstate == MapleClient.LOGIN_SERVER_TRANSITION
                            || newstate == MapleClient.CHANGE_CHANNEL);
            loggedIn = !serverTransition;
        }
    }

    public final void updateSecondPassword() {
        try (Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps =
                        con.prepareStatement(
                                "UPDATE `accounts` SET `2ndpassword` = ?, `salt2` = ? WHERE id = ?")) {
            final String newSalt = LoginCrypto.makeSalt();
            ps.setString(
                    1,
                    LoginCrypto.rand_s(LoginCrypto.makeSaltedSha512Hash(secondPassword, newSalt)));
            ps.setString(2, newSalt);
            ps.setInt(3, accId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("error updating login state", e);
        }
    }

    public final void updateGender() {
        try (Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("UPDATE `accounts` SET `gender` = ? WHERE id = ?")) {
            ps.setInt(1, gender);
            ps.setInt(2, accId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("error updating gender", e);
        }
    }

    public final byte getLoginState() {
        try (Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps =
                        con.prepareStatement(
                                "SELECT loggedin, lastlogin, `birthday` + 0 AS `bday` FROM accounts WHERE id = ?")) {
            ps.setInt(1, getAccID());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new DatabaseException("Everything sucks");
                }
                birthday = rs.getInt("bday");
                byte state = rs.getByte("loggedin");

                if (state == MapleClient.LOGIN_SERVER_TRANSITION
                        || state == MapleClient.CHANGE_CHANNEL) {
                    if (rs.getTimestamp("lastlogin").getTime() + 20000
                            < System.currentTimeMillis()) {
                        state = MapleClient.LOGIN_NOTLOGGEDIN;
                        updateLoginState(state, getSessionIPAddress());
                    }
                }
                if (state == MapleClient.LOGIN_LOGGEDIN) {
                    loggedIn = true;
                } else {
                    loggedIn = false;
                }
                return state;
            }
        } catch (SQLException e) {
            loggedIn = false;
            throw new DatabaseException("error getting login state", e);
        }
    }

    public final boolean checkBirthDate(final int date) {
        return birthday == date;
    }

    public final void cancelIdleTask() {
        final ScheduledFuture<?> task = this.idleTask;
        if (task != null) {
            task.cancel(false);
            this.idleTask = null;
        }
    }

    public final void cancelPingTask() {
        final ScheduledFuture<?> task = this.pingTask;
        if (task != null) {
            task.cancel(false);
            this.pingTask = null;
        }
    }

    public final void removalTask() {
        if (player == null) {
            return;
        }
        try {
            player.cancelAllBuffs_();
            player.cancelAllDebuffs();
            player.cancelAllTimers();
            if (player.getMarriageId() > 0) {
                final MapleQuestStatus stat1 = player.getQuestNAdd(MapleQuest.getInstance(160001));
                final MapleQuestStatus stat2 = player.getQuestNAdd(MapleQuest.getInstance(160002));
                if (stat1.getCustomData() != null
                        && (stat1.getCustomData().equals("2_")
                                || stat1.getCustomData().equals("2"))) {
                    if (stat2.getCustomData() != null) {
                        stat2.setCustomData("0");
                    }
                    stat1.setCustomData("3");
                }
            }
            player.changeRemoval(true);
            if (player.getEventInstance() != null) {
                player.getEventInstance().playerDisconnected(player, player.getId());
            }
            if (player.getMap() != null) {
                switch (player.getMapId()) {
                    case 541010100: // latanica
                    case 541020800: // scar/targa
                    case 551030200: // krexel
                    case 220080001: // pap
                        player.getMap().addDisconnected(player.getId());
                        break;
                }
                player.getMap().removePlayer(player);
            }

            final IMaplePlayerShop shop = player.getPlayerShop();
            if (shop != null) {
                shop.removeVisitor(player);
                if (shop.isOwner(player)) {
                    if (shop.getShopType() == 1 && shop.isAvailable()) {
                        shop.setOpen(true);
                    } else {
                        shop.closeShop(true, true);
                    }
                }
            }
            player.setMessenger(null);
        } catch (final Throwable e) {
            LOG.error("MapleClient.removalTask ERROR:", e);
        }
    }

    private void cleanupSessionResources() {
        cancelIdleTask();
        cancelPingTask();
        if (engines != null) {
            engines.clear();
        }
        if (allowedChar != null) {
            allowedChar.clear();
        }
        if (macs != null) {
            macs.clear();
        }
    }

    public final void disconnect(final boolean RemoveInChannelServer, final boolean fromCS) {
        disconnect(RemoveInChannelServer, fromCS, false);
    }

    public final void disconnect(
            final boolean RemoveInChannelServer, final boolean fromCS, final boolean shutdown) {
        if (!disconnecting.compareAndSet(false, true)) {
            return;
        }
        try {
            cancelIdleTask();
            cancelPingTask();

            if (player != null && isLoggedIn()) {
                MapleMap map = player.getMap();
                final MapleParty party = player.getParty();
                final boolean clone = player.isClone();
                final String namez = player.getName();
                final boolean hidden = player.isHidden();
                final int gmLevel = player.getGMLevel();
                final int idz = player.getId(),
                        messengerid =
                                player.getMessenger() == null ? 0 : player.getMessenger().getId(),
                        gid = player.getGuildId(),
                        fid = player.getFamilyId();
                final BuddyList bl = player.getBuddylist();
                final MaplePartyCharacter chrp = new MaplePartyCharacter(player);
                final MapleMessengerCharacter chrm = new MapleMessengerCharacter(player);
                final MapleGuildCharacter chrg = player.getMGC();
                final MapleFamilyCharacter chrf = player.getMFC();
                final IMaplePlayerShop ips = player.getPlayerShop();
                final IMaplePlayerShop fishing = player.getPlayerFishing();

                if (player.getTrade() != null) {
                    MapleTrade.cancelTrade(player.getTrade(), this);
                }
                if (ips != null) {
                    if (!ips.isAvailable()
                            || (ips.isOwner(player) && ips.getShopType() != 1)
                            || (ips.isOwner(player) && ips.getShopType() == 1 && !ips.isOpen())) {
                        ips.closeShop(true, true);
                    }
                }
                if (fishing != null) {
                    fishing.closeShop(true, true);
                }

                removalTask();
                player.saveToDB(true, fromCS);
                if (shutdown) {
                    player.disposeOnDisconnect();
                    player = null;
                    receiving = false;
                    return;
                }

                if (!fromCS) {
                    final ChannelServer ch =
                            ChannelServer.getInstance(map == null ? channel : map.getChannel());

                    try {
                        if (ch == null || clone || ch.isShutdown()) {
                            return;
                        }
                        if (messengerid > 0) {
                            World.Messenger.leaveMessenger(messengerid, chrm);
                        }
                        if (party != null) {
                            chrp.setOnline(false);
                            World.Party.updateParty(party.getId(), PartyOperation.LOG_ONOFF, chrp);
                            if (map != null && party.getLeader().getId() == idz) {
                                MaplePartyCharacter lchr = null;
                                for (MaplePartyCharacter pchr : party.getMembers()) {
                                    if (pchr != null
                                            && map.getCharacterById(pchr.getId()) != null
                                            && (lchr == null
                                                    || lchr.getLevel() < pchr.getLevel())) {
                                        lchr = pchr;
                                    }
                                }
                                if (lchr != null) {
                                    World.Party.updateParty(
                                            party.getId(), PartyOperation.CHANGE_LEADER_DC, lchr);
                                }
                            }
                        }
                        if (bl != null) {
                            if (!serverTransition && isLoggedIn()) {
                                World.Buddy.loggedOff(
                                        namez, idz, channel, bl.getBuddyIds(), gmLevel, hidden);
                            } else {
                                World.Buddy.loggedOn(
                                        namez, idz, channel, bl.getBuddyIds(), gmLevel, hidden);
                            }
                        }
                        if (gid > 0) {
                            World.Guild.setGuildMemberOnline(chrg, false, -1);
                        }
                        if (fid > 0) {
                            World.Family.setFamilyMemberOnline(chrf, false, -1);
                        }
                    } catch (final Exception e) {
                        LOG.error("{}", getLogMessage(this, "ERROR"), e);
                    } finally {
                        if (RemoveInChannelServer && ch != null) {
                            ch.removePlayer(idz, namez);
                        }
                        if (player != null) {
                            player.disposeOnDisconnect();
                            player = null;
                        }
                    }
                } else {
                    final int ch = World.Find.findChannel(idz);
                    if (ch > 0) {
                        ChannelServer ch_ = ChannelServer.getInstance(ch);
                        if (ch_ != null) {
                            MapleCharacter chr =
                                    ch_.getPlayerStorage().getCharacterByName(namez);
                            if (chr != null && chr.getClient() != null) {
                                chr.getClient().unLockDisconnect();
                            }
                        }
                    }
                    try {
                        if (party != null) {
                            chrp.setOnline(false);
                            World.Party.updateParty(party.getId(), PartyOperation.LOG_ONOFF, chrp);
                        }
                        if (bl != null) {
                            if (!serverTransition && isLoggedIn()) {
                                World.Buddy.loggedOff(
                                        namez, idz, channel, bl.getBuddyIds(), gmLevel, hidden);
                            } else {
                                World.Buddy.loggedOn(
                                        namez, idz, channel, bl.getBuddyIds(), gmLevel, hidden);
                            }
                        }
                        if (gid > 0) {
                            World.Guild.setGuildMemberOnline(chrg, false, -1);
                        }
                        if (player != null) {
                            player.setMessenger(null);
                        }
                    } catch (final Exception e) {
                        LOG.error("{}", getLogMessage(this, "ERROR"), e);
                    } finally {
                        if (RemoveInChannelServer && ch > 0) {
                            CashShopServer.getPlayerStorage().deregisterPlayer(idz, namez);
                        }
                        if (player != null) {
                            player.disposeOnDisconnect();
                            player = null;
                        }
                    }
                }
            } else if (player != null) {
                // 未完整登入或換頻途中斷線時，仍強制釋放殘留的 MapleCharacter 引用與排程
                try {
                    player.changeRemoval(true);
                    player.disposeOnDisconnect();
                } catch (final Throwable e) {
                    LOG.error("Acc_Stuck:", e);
                } finally {
                    player = null;
                }
            }

            if (!serverTransition && isLoggedIn()) {
                updateLoginState(MapleClient.LOGIN_NOTLOGGEDIN, getSessionIPAddress());
            }
        } finally {
            cleanupSessionResources();
            receiving = false;
            disconnecting.set(false);
        }
    }

    public final String getSessionIPAddress() {
        if (connection == null || connection.remoteAddress() == null) {
            return "";
        }
        return connection.remoteAddress().toString().split(":")[0];
    }

    public final boolean CheckIPAddress() {
        try (Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT SessionIP FROM accounts WHERE id = ?")) {
            ps.setInt(1, this.accId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean canlogin = false;

                if (rs.next()) {
                    final String sessionIP = rs.getString("SessionIP");

                    if (sessionIP != null) {
                        canlogin = getSessionIPAddress().equals(sessionIP.split(":")[0]);
                    }
                }

                return canlogin;
            }
        } catch (final SQLException e) {
            LOG.error("Failed in checking IP address for client.");
        }
        return true;
    }

    public final void DebugMessage(final StringBuilder sb) {
        sb.append(getSession() != null ? getSession().remoteAddress() : "null");
        sb.append("Connected: ");
        sb.append(getSession() != null && getSession().isActive());
        sb.append(" Writable: ");
        sb.append(getSession() != null && getSession().isWritable());
        sb.append(" ClientKeySet: ");
        sb.append(getSession() != null && getSession().attr(MapleClient.CLIENT_KEY) != null);
        sb.append(" loggedin: ");
        sb.append(isLoggedIn());
        sb.append(" has char: ");
        sb.append(getPlayer() != null);
    }

    public final int getChannel() {
        return channel;
    }

    public final ChannelServer getChannelServer() {
        return ChannelServer.getInstance(channel);
    }

    public final int deleteCharacter(final int cid) {
        try (Connection con = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps =
                    con.prepareStatement(
                            "SELECT guildid, guildrank, familyid, name FROM characters WHERE id = ? AND accountid = ?")) {
                ps.setInt(1, cid);
                ps.setInt(2, accId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return 1;
                    }
                    if (rs.getInt("guildid") > 0) {
                        if (rs.getInt("guildrank") == 1) {
                            return 1;
                        }
                        World.Guild.deleteGuildCharacter(rs.getInt("guildid"), cid);
                    }
                    if (rs.getInt("familyid") > 0) {
                        World.Family.getFamily(rs.getInt("familyid")).leaveFamily(cid);
                    }
                }
            }

            MapleCharacter.deleteWhereCharacterId(con, "DELETE FROM characters WHERE id = ?", cid);
            MapleCharacter.deleteWhereCharacterId(
                    con, "DELETE FROM monsterbook WHERE charid = ?", cid);
            MapleCharacter.deleteWhereCharacterId(
                    con, "DELETE FROM hiredmerch WHERE characterid = ?", cid);
            MapleCharacter.deleteWhereCharacterId(
                    con, "DELETE FROM mts_cart WHERE characterid = ?", cid);
            MapleCharacter.deleteWhereCharacterId(
                    con, "DELETE FROM mts_items WHERE characterid = ?", cid);
            MapleCharacter.deleteWhereCharacterId(
                    con, "DELETE FROM mountdata WHERE characterid = ?", cid);
            MapleCharacter.deleteWhereCharacterId(
                    con, "DELETE FROM inventoryitems WHERE characterid = ?", cid);
            MapleCharacter.deleteWhereCharacterId(
                    con, "DELETE FROM famelog WHERE characterid = ?", cid);
            MapleCharacter.deleteWhereCharacterId(
                    con, "DELETE FROM famelog WHERE characterid_to = ?", cid);
            MapleCharacter.deleteWhereCharacterId(
                    con, "DELETE FROM dueypackages WHERE RecieverId = ?", cid);
            MapleCharacter.deleteWhereCharacterId(
                    con, "DELETE FROM wishlist WHERE characterid = ?", cid);
            MapleCharacter.deleteWhereCharacterId(
                    con, "DELETE FROM buddies WHERE characterid = ?", cid);
            MapleCharacter.deleteWhereCharacterId(
                    con, "DELETE FROM buddies WHERE buddyid = ?", cid);
            MapleCharacter.deleteWhereCharacterId(
                    con, "DELETE FROM keymap WHERE characterid = ?", cid);
            MapleCharacter.deleteWhereCharacterId(
                    con, "DELETE FROM savedlocations WHERE characterid = ?", cid);
            MapleCharacter.deleteWhereCharacterId(
                    con, "DELETE FROM skills WHERE characterid = ?", cid);
            MapleCharacter.deleteWhereCharacterId(
                    con, "DELETE FROM mountdata WHERE characterid = ?", cid);
            MapleCharacter.deleteWhereCharacterId(
                    con, "DELETE FROM skillmacros WHERE characterid = ?", cid);
            MapleCharacter.deleteWhereCharacterId(
                    con, "DELETE FROM trocklocations WHERE characterid = ?", cid);
            MapleCharacter.deleteWhereCharacterId(
                    con, "DELETE FROM queststatus WHERE characterid = ?", cid);
            MapleCharacter.deleteWhereCharacterId(
                    con, "DELETE FROM inventoryslot WHERE characterid = ?", cid);
            return 0;
        } catch (Exception e) {
            LOG.error("PacketEx_Log ERROR: ", e);
        }
        return 1;
    }

    public final byte getGender() {
        return gender;
    }

    public final void setGender(final byte gender) {
        this.gender = gender;
    }

    public final String getSecondPassword() {
        return secondPassword;
    }

    public final void setSecondPassword(final String secondPassword) {
        this.secondPassword = secondPassword;
    }

    public final String getAccountName() {
        return accountName;
    }

    public final void setAccountName(final String accountName) {
        this.accountName = accountName;
    }

    public final void setChannel(final int channel) {
        this.channel = channel;
    }

    public final int getWorld() {
        return world;
    }

    public final void setWorld(final int world) {
        this.world = world;
    }

    public final int getLatency() {
        return (int) (lastPong - lastPing);
    }

    public final long getLastPong() {
        return lastPong;
    }

    public final long getLastPing() {
        return lastPing;
    }

    public final void pongReceived() {
        lastPong = System.currentTimeMillis();
    }

    public final void sendPing() {
        if (connection == null || !connection.isActive()) {
            return;
        }
        cancelPingTask();
        lastPing = System.currentTimeMillis();
        connection.writeAndFlush(LoginPacket.getPing());

        pingTask =
                PingTimer.getInstance()
                        .schedule(
                                new Runnable() {

                                    @Override
                                    public void run() {
                                        try {
                                            if (getLatency() < 0) {
                                                final Channel session = getSession();
                                                if (session != null && session.isActive()) {
                                                    session.close();
                                                }
                                            }
                                        } catch (final NullPointerException e) {
                                            // client already gone
                                        }
                                    }
                                },
                                15000);
    }

    public static final String getLogMessage(final MapleClient cfor, final String message) {
        return getLogMessage(cfor, message, new Object[0]);
    }

    public static final String getLogMessage(final MapleCharacter cfor, final String message) {
        return getLogMessage(cfor == null ? null : cfor.getClient(), message);
    }

    public static final String getLogMessage(
            final MapleCharacter cfor, final String message, final Object... parms) {
        return getLogMessage(cfor == null ? null : cfor.getClient(), message, parms);
    }

    public static final String getLogMessage(
            final MapleClient cfor, final String message, final Object... parms) {
        final StringBuilder builder = new StringBuilder();
        if (cfor != null) {
            if (cfor.getPlayer() != null) {
                builder.append("<");
                builder.append(MapleCharacterUtil.makeMapleReadable(cfor.getPlayer().getName()));
                builder.append(" (cid: ");
                builder.append(cfor.getPlayer().getId());
                builder.append(")> ");
            }
            if (cfor.getAccountName() != null) {
                builder.append("(Account: ");
                builder.append(cfor.getAccountName());
                builder.append(") ");
            }
        }
        builder.append(message);
        int start;
        for (final Object parm : parms) {
            start = builder.indexOf("{}");
            builder.replace(start, start + 2, parm.toString());
        }
        return builder.toString();
    }

    public static final int findAccIdForCharacterName(final String charName) {
        try (Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT accountid FROM characters WHERE name = ?")) {
            ps.setString(1, charName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("accountid");
                }
            }
        } catch (final SQLException e) {
            LOG.error("findAccIdForCharacterName SQL error");
        }
        return -1;
    }

    public final Set<String> getMacs() {
        return Collections.unmodifiableSet(macs);
    }

    public final boolean isGm() {
        return gm;
    }

    public final void setScriptEngine(final String name, final ScriptEngine e) {
        engines.put(name, e);
    }

    public final ScriptEngine getScriptEngine(final String name) {
        return engines.get(name);
    }

    public final void removeScriptEngine(final String name) {
        engines.remove(name);
    }

    public final ScheduledFuture<?> getIdleTask() {
        return idleTask;
    }

    public final void setIdleTask(final ScheduledFuture<?> idleTask) {
        cancelIdleTask();
        this.idleTask = idleTask;
    }

    protected static final class CharNameAndId {

        public final String name;
        public final int id;

        public CharNameAndId(final String name, final int id) {
            super();
            this.name = name;
            this.id = id;
        }
    }

    public int getCharacterSlots() {
        if (isGm()) {
            return 15;
        }
        if (charslots != DEFAULT_CHARSLOT) {
            return charslots;
        }
        try (Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps =
                        con.prepareStatement(
                                "SELECT * FROM character_slots WHERE accid = ? AND worldid = ?")) {
            ps.setInt(1, accId);
            ps.setInt(2, world);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    charslots = rs.getInt("charslots");
                } else {
                    try (PreparedStatement psu =
                            con.prepareStatement(
                                    "INSERT INTO character_slots (accid, worldid, charslots) VALUES (?, ?, ?)")) {
                        psu.setInt(1, accId);
                        psu.setInt(2, world);
                        psu.setInt(3, charslots);
                        psu.executeUpdate();
                    }
                }
            }
        } catch (SQLException sqlE) {
            LOG.error("MapleClient.getCharacterSlots 發生例外", sqlE);
        }

        return charslots;
    }

    public boolean gainCharacterSlot() {
        if (getCharacterSlots() >= 15) {
            return false;
        }
        charslots++;

        try (Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps =
                        con.prepareStatement(
                                "UPDATE character_slots SET charslots = ? WHERE worldid = ? AND accid = ?")) {
            ps.setInt(1, charslots);
            ps.setInt(2, world);
            ps.setInt(3, accId);
            ps.executeUpdate();
        } catch (SQLException sqlE) {
            LOG.error("MapleClient.gainCharacterSlot 發生例外", sqlE);
            return false;
        }
        return true;
    }

    public static final byte unbanIPMacs(String charname) {
        try (Connection con = DatabaseConnection.getConnection()) {
            final int accid;
            try (PreparedStatement ps =
                    con.prepareStatement("SELECT accountid from characters where name = ?")) {
                ps.setString(1, charname);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return -1;
                    }
                    accid = rs.getInt(1);
                }
            }

            final String sessionIP;
            final String macs;
            try (PreparedStatement ps =
                    con.prepareStatement("SELECT * FROM accounts WHERE id = ?")) {
                ps.setInt(1, accid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return -1;
                    }
                    sessionIP = rs.getString("sessionIP");
                    macs = rs.getString("macs");
                }
            }
            byte ret = 0;
            if (sessionIP != null) {
                try (PreparedStatement psa =
                        con.prepareStatement("DELETE FROM ipbans WHERE ip = ?")) {
                    psa.setString(1, sessionIP);
                    psa.execute();
                }
                ret++;
            }
            if (macs != null) {
                String[] macz = macs.split(", ");
                for (String mac : macz) {
                    if (!mac.equals("")) {
                        try (PreparedStatement psa =
                                con.prepareStatement("DELETE FROM macbans WHERE mac = ?")) {
                            psa.setString(1, mac);
                            psa.execute();
                        }
                    }
                }
                ret++;
            }
            return ret;
        } catch (SQLException e) {
            LOG.error("Error while unbanning", e);
            return -2;
        }
    }

    public static final byte unHellban(String charname) {
        try (Connection con = DatabaseConnection.getConnection()) {
            final int accid;
            try (PreparedStatement ps =
                    con.prepareStatement("SELECT accountid from characters where name = ?")) {
                ps.setString(1, charname);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return -1;
                    }
                    accid = rs.getInt(1);
                }
            }

            final String sessionIP;
            final String email;
            try (PreparedStatement ps =
                    con.prepareStatement("SELECT * FROM accounts WHERE id = ?")) {
                ps.setInt(1, accid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return -1;
                    }
                    sessionIP = rs.getString("sessionIP");
                    email = rs.getString("email");
                }
            }

            try (PreparedStatement ps =
                    con.prepareStatement(
                            "UPDATE accounts SET banned = 0, banreason = '' WHERE email = ?"
                                    + (sessionIP == null ? "" : " OR sessionIP = ?"))) {
                ps.setString(1, email);
                if (sessionIP != null) {
                    ps.setString(2, sessionIP);
                }
                ps.execute();
            }

            return 0;
        } catch (SQLException e) {
            LOG.error("Error while unbanning", e);
            return -2;
        }
    }

    public boolean isMonitored() {
        return monitored;
    }

    public void setMonitored(boolean m) {
        this.monitored = m;
    }

    public boolean isReceiving() {
        return receiving;
    }

    public void setReceiving(boolean m) {
        this.receiving = m;
    }

    public void sendPacket(MaplePacket packet) {
        final Channel session = this.getSession();
        if (session != null && session.isActive()) {
            session.writeAndFlush(packet);
        }
    }

    public boolean canClickNPC() {
        return lastNpcClick + 500 < System.currentTimeMillis();
    }

    public void setClickedNPC() {
        lastNpcClick = System.currentTimeMillis();
    }

    public void removeClickedNPC() {
        lastNpcClick = 0;
    }
}