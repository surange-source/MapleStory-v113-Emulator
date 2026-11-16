package client.messages;

import client.MapleCharacter;
import client.MapleClient;
import client.messages.commands.*;
import constants.ServerConstants.CommandType;
import constants.ServerConstants.PlayerGMRank;
import database.DatabaseConnection;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(CommandProcessor.class);

    private static final HashMap<String, CommandObject> commands = new HashMap<>();
    private static final HashMap<Integer, ArrayList<String>> commandList = new HashMap<>();

    static {
        Class<?>[] CommandFiles = {
            PlayerCommand.class, AdminCommand.class, GMCommand.class, InternCommand.class
        };

        for (Class<?> clasz : CommandFiles) {
            try {
                PlayerGMRank rankNeeded =
                        (PlayerGMRank)
                                clasz.getMethod("getPlayerLevelRequired", new Class<?>[] {})
                                        .invoke(null, (Object[]) null);
                Class<?>[] a = clasz.getDeclaredClasses();
                ArrayList<String> cL = new ArrayList<>();
                for (Class<?> c : a) {
                    try {
                        if (!Modifier.isAbstract(c.getModifiers()) && !c.isSynthetic()) {
                            Object o = c.getDeclaredConstructor().newInstance();
                            boolean enabled;
                            try {
                                enabled =
                                        c.getDeclaredField("enabled")
                                                .getBoolean(c.getDeclaredField("enabled"));
                            } catch (NoSuchFieldException ex) {
                                enabled = true; // Enable all coded commands by default.
                            }
                            if (o instanceof CommandExecute execute && enabled) {
                                cL.add(
                                        rankNeeded.getCommandPrefix()
                                                + c.getSimpleName().toLowerCase());
                                commands.put(
                                        rankNeeded.getCommandPrefix()
                                                + c.getSimpleName().toLowerCase(),
                                        new CommandObject(
                                                rankNeeded.getCommandPrefix()
                                                        + c.getSimpleName().toLowerCase(),
                                                execute,
                                                rankNeeded.getLevel()));
                            }
                        }
                    } catch (IllegalAccessException
                            | IllegalArgumentException
                            | InstantiationException
                            | NoSuchMethodException
                            | InvocationTargetException ex) {
                        LOG.error("",ex);
                    }
                }
                Collections.sort(cL);
                commandList.put(rankNeeded.getLevel(), cL);
            } catch (IllegalAccessException
                    | NoSuchMethodException
                    | InvocationTargetException ex) {
                LOG.error("CommandProcessor static ERROR:",ex);
            }
        }
    }

    private static void sendDisplayMessage(MapleClient c, String msg, CommandType type) {
        if (c.getPlayer() == null) {
            return;
        }
        switch (type) {
            case NORMAL:
                c.getPlayer().dropMessage(6, msg);
                break;
            case TRADE:
                c.getPlayer().dropMessage(-2, "錯誤 : " + msg);
                break;
        }
    }

    public static boolean processCommand(MapleClient c, String line, CommandType type) {
        if (line.charAt(0) == PlayerGMRank.NORMAL.getCommandPrefix()) {
            String[] splitted = line.split(" ");
            splitted[0] = splitted[0].toLowerCase();

            CommandObject co = commands.get(splitted[0]);
            if (co == null || co.getType() != type) {
                sendDisplayMessage(c, "沒有這個指令,可以使用 @幫助/@help 來查看指令.", type);
                return true;
            }
            try {
                int ret = co.execute(c, splitted); // Don't really care about the return value. ;D
            } catch (Exception e) {
                sendDisplayMessage(c, "有錯誤.", type);
                if (c.getPlayer().isGM()) {
                    sendDisplayMessage(c, "錯誤: " + e, type);
                }
            }
            return true;
        }

        if (c.getPlayer().getGMLevel() > PlayerGMRank.NORMAL.getLevel()) {
            if (line.charAt(0) == PlayerGMRank.GM.getCommandPrefix()
                    || line.charAt(0) == PlayerGMRank.ADMIN.getCommandPrefix()
                    || line.charAt(0)
                            == PlayerGMRank.INTERN
                                    .getCommandPrefix()) { // Redundant for now, but in case we
                // change symbols later.
                // This will become extensible.
                String[] splitted = line.split(" ");
                splitted[0] = splitted[0].toLowerCase();

                if (line.charAt(0) == '!') { // GM Commands
                    CommandObject co = commands.get(splitted[0]);
                    if (co == null || co.getType() != type) {
                        sendDisplayMessage(c, "沒有這個指令.", type);
                        return true;
                    }
                    if (c.getPlayer().getGMLevel() >= co.getReqGMLevel()) {
                        int ret = co.execute(c, splitted);
                        if (ret > 0
                                && c.getPlayer() != null) { // incase d/c after command or something
                            logGMCommandToDB(c.getPlayer(), line);
                            LOG.info("[ {} ] 使用了指令: {}", c.getPlayer().getName(), line);
                        }
                    } else {
                        sendDisplayMessage(c, "你沒有權限可以使用指令.", type);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static void logGMCommandToDB(MapleCharacter player, String command) {
        if (player == null) {
            return;
        }
        final int mapId = player.getMap() != null ? player.getMap().getId() : -1;
        try (Connection con = DatabaseConnection.getConnection()) {
            if (con == null) {
                return;
            }
            try (PreparedStatement ps =
                    con.prepareStatement(
                            "INSERT INTO gmlog (cid, command, mapid) VALUES (?, ?, ?)")) {
                ps.setInt(1, player.getId());
                ps.setString(2, command);
                ps.setInt(3, mapId);
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            LOG.error("CommandProcessor.logGMCommandToDB ERROR:",ex);
        }
    }
}
