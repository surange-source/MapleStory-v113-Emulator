package provider.WzXML;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import provider.MapleData;
import provider.MapleDataDirectoryEntry;
import provider.MapleDataProvider;

public class XMLWZFile implements MapleDataProvider {

    private File root;
    private WZDirectoryEntry rootForNavigation;

    public XMLWZFile(File fileIn) {
        root = fileIn;
        //        System.out.println(root);
        rootForNavigation = new WZDirectoryEntry(fileIn.getName(), 0, 0, null);
        fillMapleDataEntitys(root, rootForNavigation);
    }

    private void fillMapleDataEntitys(File lroot, WZDirectoryEntry wzdir) {
        File[] files = lroot.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            String fileName = file.getName();

            if (file.isDirectory() && !fileName.endsWith(".img")) {
                WZDirectoryEntry newDir = new WZDirectoryEntry(fileName, 0, 0, wzdir);
                wzdir.addDirectory(newDir);
                fillMapleDataEntitys(file, newDir);

            } else if (fileName.endsWith(".xml")) { // get the real size here?
                wzdir.addFile(
                        new WZFileEntry(fileName.substring(0, fileName.length() - 4), 0, 0, wzdir));
            }
        }
    }

    @Override
    public MapleData getData(String path) {
        File dataFile = new File(root, path + ".xml");
        File imageDataDir = new File(root, path);
        /*		if (!dataFile.exists()) {
        throw new RuntimeException("Datafile " + path + " does not exist in " + root.getAbsolutePath());
        }*/
        try (FileInputStream fis = new FileInputStream(dataFile)) {
            return new XMLDomMapleData(fis, imageDataDir.getParentFile());
        } catch (FileNotFoundException e) {
            throw new RuntimeException(
                    "Datafile " + path + " does not exist in " + root.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public MapleDataDirectoryEntry getRoot() {
        return rootForNavigation;
    }
}
