package tools.wztosql;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import provider.MapleData;
import provider.MapleDataProvider;
import provider.MapleDataProviderFactory;

public class WzStringDumper {
    private static final Logger LOG = LoggerFactory.getLogger(WzStringDumper.class);

    protected static final String rootWZpath = new java.io.File("wz").getAbsolutePath();
    protected static final String rootTXTpath = new java.io.File("Logs\\txt").getAbsolutePath();

    public static void main(String[] args) throws FileNotFoundException, IOException {
        try {
            System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));
            System.setErr(new java.io.PrintStream(System.err, true, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            LOG.error("WzStringDumper.main 發生例外", e);
        }
        //        File stringFile = MapleDataProviderFactory.fileInWZPath("string.wz");
        MapleDataProvider stringProvider =
                MapleDataProviderFactory.getDataProvider(new File(rootWZpath + "/String.wz"));

        MapleData cash = stringProvider.getData("Cash.img");
        MapleData consume = stringProvider.getData("Consume.img");
        MapleData eqp = stringProvider.getData("Eqp.img").getChildByPath("Eqp");
        MapleData etc = stringProvider.getData("Etc.img").getChildByPath("Etc");
        MapleData ins = stringProvider.getData("Ins.img");
        MapleData pet = stringProvider.getData("Pet.img");
        MapleData map = stringProvider.getData("Map.img");
        MapleData mob = stringProvider.getData("Mob.img");
        MapleData skill = stringProvider.getData("Skill.img");
        MapleData npc = stringProvider.getData("Npc.img");

        String output = rootTXTpath;

        File outputDir = new File(output);
        File cashTxt = new File(output + "\\Cash.txt");
        File useTxt = new File(output + "\\Use.txt");
        File eqpDir = new File(output + "\\Equip");
        File etcTxt = new File(output + "\\Etc.txt");
        File insTxt = new File(output + "\\Setup.txt");
        File petTxt = new File(output + "\\Pet.txt");
        File mapTxt = new File(output + "\\Map.txt");
        File mobTxt = new File(output + "\\Mob.txt");
        File skillTxt = new File(output + "\\Skill.txt");
        File npcTxt = new File(output + "\\NPC.txt");
        outputDir.mkdir();
        cashTxt.createNewFile();
        useTxt.createNewFile();
        eqpDir.mkdir();
        etcTxt.createNewFile();
        insTxt.createNewFile();
        petTxt.createNewFile();
        mapTxt.createNewFile();
        mobTxt.createNewFile();
        skillTxt.createNewFile();
        npcTxt.createNewFile();

        LOG.info("提取 Cash.img 數據...");
        try (PrintWriter writer =
                new PrintWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(cashTxt), StandardCharsets.UTF_8))) {
            for (MapleData child : cash.getChildren()) {
                MapleData nameData = child.getChildByPath("name");
                MapleData descData = child.getChildByPath("desc");
                String name = "";
                String desc = "(無描述)";
                if (nameData != null) {
                    name = (String) nameData.getData();
                }
                if (descData != null) {
                    desc = (String) descData.getData();
                }
                writer.println(child.getName() + " - " + name + " - " + desc);
            }
            writer.flush();
        }

        LOG.info("Cash.img 提取完成.");

        LOG.info("提取 Consume.img 數據...");
        try (PrintWriter writer =
                new PrintWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(useTxt), StandardCharsets.UTF_8))) {
            for (MapleData child : consume.getChildren()) {
                MapleData nameData = child.getChildByPath("name");
                MapleData descData = child.getChildByPath("desc");
                String name = "";
                String desc = "(無描述)";
                if (nameData != null) {
                    name = (String) nameData.getData();
                }
                if (descData != null) {
                    desc = (String) descData.getData();
                }
                writer.println(child.getName() + " - " + name + " - " + desc);
            }
            writer.flush();
        }

        LOG.info("Consume.img 提取完成.");

        LOG.info("提取 Eqp.img 數據...");
        for (MapleData child : eqp.getChildren()) {

            LOG.info("提取 {} 數據...", child.getName());
            File eqpFile = new File(output + "\\Equip\\" + child.getName() + ".txt");
            eqpFile.createNewFile();
            try (PrintWriter eqpWriter =
                    new PrintWriter(
                            new OutputStreamWriter(
                                    new FileOutputStream(eqpFile), StandardCharsets.UTF_8))) {
                for (MapleData child2 : child.getChildren()) {
                    MapleData nameData = child2.getChildByPath("name");
                    MapleData descData = child2.getChildByPath("desc");
                    String name = "";
                    String desc = "(無描述)";
                    if (nameData != null) {
                        name = (String) nameData.getData();
                    }
                    if (descData != null) {
                        desc = (String) descData.getData();
                    }
                    eqpWriter.println(child2.getName() + " - " + name + " - " + desc);
                }
                eqpWriter.flush();
            }

            LOG.info("{} 提取完成.", child.getName());
        }

        LOG.info("Eqp.img 提取完成.");

        LOG.info("提取 Etc.img 數據...");
        try (PrintWriter writer =
                new PrintWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(etcTxt), StandardCharsets.UTF_8))) {
            for (MapleData child : etc.getChildren()) {
                MapleData nameData = child.getChildByPath("name");
                MapleData descData = child.getChildByPath("desc");
                String name = "";
                String desc = "(無描述)";
                if (nameData != null) {
                    name = (String) nameData.getData();
                }
                if (descData != null) {
                    desc = (String) descData.getData();
                }
                writer.println(child.getName() + " - " + name + " - " + desc);
            }
            writer.flush();
        }

        LOG.info("Etc.img 提取完成.");

        LOG.info("提取 Ins.img 數據...");
        try (PrintWriter writer =
                new PrintWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(insTxt), StandardCharsets.UTF_8))) {
            for (MapleData child : ins.getChildren()) {
                MapleData nameData = child.getChildByPath("name");
                MapleData descData = child.getChildByPath("desc");
                String name = "";
                String desc = "(無描述)";
                if (nameData != null) {
                    name = (String) nameData.getData();
                }
                if (descData != null) {
                    desc = (String) descData.getData();
                }
                writer.println(child.getName() + " - " + name + " - " + desc);
            }
            writer.flush();
        }

        LOG.info("Ins.img 提取完成.");

        LOG.info("提取 Pet.img 數據...");
        try (PrintWriter writer =
                new PrintWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(petTxt), StandardCharsets.UTF_8))) {
            for (MapleData child : pet.getChildren()) {
                MapleData nameData = child.getChildByPath("name");
                MapleData descData = child.getChildByPath("desc");
                String name = "";
                String desc = "(無描述)";
                if (nameData != null) {
                    name = (String) nameData.getData();
                }
                if (descData != null) {
                    desc = (String) descData.getData();
                }
                writer.println(child.getName() + " - " + name + " - " + desc);
            }
            writer.flush();
        }

        LOG.info("Pet.img 提取完成.");

        LOG.info("提取 Map.img 數據...");
        try (PrintWriter writer =
                new PrintWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(mapTxt), StandardCharsets.UTF_8))) {
            for (MapleData child : map.getChildren()) {
                writer.println(child.getName());
                writer.println();
                for (MapleData child2 : child.getChildren()) {
                    MapleData streetData = child2.getChildByPath("streetName");
                    MapleData mapData = child2.getChildByPath("mapName");
                    String streetName = "(無數據名)";
                    String mapName = "(無地圖名)";
                    if (streetData != null) {
                        streetName = (String) streetData.getData();
                    }
                    if (mapData != null) {
                        mapName = (String) mapData.getData();
                    }
                    writer.println(child2.getName() + " - " + streetName + " - " + mapName);
                }
                writer.println();
            }
            writer.flush();
        }

        LOG.info("Map.img 提取完成.");

        LOG.info("提取 Mob.img 數據...");
        try (PrintWriter writer =
                new PrintWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(mobTxt), StandardCharsets.UTF_8))) {
            for (MapleData child : mob.getChildren()) {
                MapleData nameData = child.getChildByPath("name");
                String name = "";
                if (nameData != null) {
                    name = (String) nameData.getData();
                }
                writer.println(child.getName() + " - " + name);
            }
            writer.flush();
        }

        LOG.info("Mob.img 提取完成.");

        LOG.info("提取 Skill.img 數據...");
        try (PrintWriter writer =
                new PrintWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(skillTxt), StandardCharsets.UTF_8))) {
            for (MapleData child : skill.getChildren()) {
                MapleData nameData = child.getChildByPath("name");
                MapleData descData = child.getChildByPath("desc");
                MapleData bookData = child.getChildByPath("bookName");
                String name = "";
                String desc = "";
                if (nameData != null) {
                    name = (String) nameData.getData();
                }
                if (descData != null) {
                    desc = (String) descData.getData();
                }
                if (bookData == null) {
                    writer.println(child.getName() + " - " + name + " - " + desc);
                }
            }
            writer.flush();
        }

        LOG.info("Skill.img 提取完成.");

        LOG.info("提取 Npc.img 數據...");
        try (PrintWriter writer =
                new PrintWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(npcTxt), StandardCharsets.UTF_8))) {
            for (MapleData child : npc.getChildren()) {
                MapleData nameData = child.getChildByPath("name");
                String name = "";
                if (nameData != null) {
                    name = (String) nameData.getData();
                }
                writer.println(child.getName() + " - " + name);
            }
            writer.flush();
        }

        LOG.info("Npc.img 提取完成.");
    }
}
