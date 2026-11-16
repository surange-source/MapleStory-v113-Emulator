package server.maps;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import tools.Pair;

public class MapleNodes {

    private final Map<Integer, MapleNodeInfo> nodes; // used for HOB pq.
    private final List<Rectangle> areas;
    private final List<MaplePlatform> platforms;
    private final List<MonsterPoint> monsterPoints;
    private final List<Integer> skillIds;
    private final List<Pair<Integer, Integer>> mobsToSpawn;
    private final List<Pair<Point, Integer>> guardiansToSpawn;
    private int nodeStart = -1, nodeEnd = -1, mapid;
    private boolean firstHighest = true;

    // 細粒度讀寫鎖：保護 nodes 的結構性操作（sortNodes 需要原子性重建）
    private final ReentrantReadWriteLock nodesLock = new ReentrantReadWriteLock();

    public MapleNodes(final int mapid) {
        // 使用併發容器：讀多寫少、遍歷頻繁，避免 ConcurrentModificationException
        nodes = new ConcurrentHashMap<>();
        areas = new CopyOnWriteArrayList<>();
        platforms = new CopyOnWriteArrayList<>();
        skillIds = new CopyOnWriteArrayList<>();
        monsterPoints = new CopyOnWriteArrayList<>();
        mobsToSpawn = new CopyOnWriteArrayList<>();
        guardiansToSpawn = new CopyOnWriteArrayList<>();
        this.mapid = mapid;
    }

    public void setNodeStart(final int ns) {
        this.nodeStart = ns;
    }

    public void setNodeEnd(final int ns) {
        this.nodeEnd = ns;
    }

    public static class MapleNodeInfo {

        public int node, key, x, y, attr;
        public List<Integer> edge;

        public MapleNodeInfo(int node, int key, int x, int y, int attr, List<Integer> edge) {
            this.node = node;
            this.key = key;
            this.x = x;
            this.y = y;
            this.attr = attr;
            this.edge = edge;
        }
    }

    public void addNode(final MapleNodeInfo mni) {
        this.nodes.put(mni.key, mni);
    }

    public Collection<MapleNodeInfo> getNodes() {
        // ConcurrentHashMap 的 values() 遍歷本身安全，但為保留原有「快照」語意仍回傳新 ArrayList
        return new ArrayList<>(nodes.values());
    }

    public MapleNodeInfo getNode(final int index) {
        int i = 1;
        for (MapleNodeInfo x : getNodes()) {
            if (i == index) {
                return x;
            }
            i++;
        }
        return null;
    }

    private int getNextNode(final MapleNodeInfo mni) {
        if (mni == null) {
            return -1;
        }
        addNode(mni);
        // output part
        /*StringBuilder b = new StringBuilder(mapid + " added key " + mni.key + ". edges: ");
        for (int i : mni.edge) {
        b.append(i + ", ");
        }
        System.out.println(b.toString());
        FileoutputUtil.log(FileoutputUtil.PacketEx_Log, b.toString());*/
        // output part end

        int ret = -1;
        for (int i : mni.edge) {
            if (nodes.get(i) == null) {
                if (ret != -1 && mapid / 100 == 9211204) {
                    if (!firstHighest) {
                        ret = Math.min(ret, i);
                    } else {
                        firstHighest = false;
                        ret = Math.max(ret, i);
                        // two ways for stage 5 to get to end, thats highest ->lowest, and lowest ->
                        // highest(doesn't work)
                        break;
                    }
                } else {
                    ret = i;
                }
            }
        }
        return ret;
    }

    public void sortNodes() {
        // 寫鎖：sortNodes 會清空並重建 nodes，需與其他讀寫互斥以保證原子性
        nodesLock.writeLock().lock();
        try {
            if (nodes.size() <= 0 || nodeStart < 0) {
                return;
            }
            Map<Integer, MapleNodeInfo> unsortedNodes = new HashMap<>(nodes);
            final int nodeSize = unsortedNodes.size();
            nodes.clear();
            int nextNode = getNextNode(unsortedNodes.get(nodeStart));
            while (nodes.size() != nodeSize && nextNode >= 0) {
                nextNode = getNextNode(unsortedNodes.get(nextNode));
            }
        } finally {
            nodesLock.writeLock().unlock();
        }
    }

    public final void addMapleArea(final Rectangle rec) {
        areas.add(rec);
    }

    public final List<Rectangle> getAreas() {
        return new ArrayList<>(areas);
    }

    public final Rectangle getArea(final int index) {
        return getAreas().get(index);
    }

    public static class MaplePlatform {

        public String name;
        public int start, speed, x1, y1, x2, y2, r;
        public List<Integer> SN;

        public MaplePlatform(
                String name,
                int start,
                int speed,
                int x1,
                int y1,
                int x2,
                int y2,
                int r,
                List<Integer> SN) {
            this.name = name;
            this.start = start;
            this.speed = speed;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.r = r;
            this.SN = SN;
        }
    }

    public final void addPlatform(final MaplePlatform mp) {
        this.platforms.add(mp);
    }

    public final List<MaplePlatform> getPlatforms() {
        return new ArrayList<>(platforms);
    }

    public static class MonsterPoint {

        public int x, y, fh, cy, team;

        public MonsterPoint(int x, int y, int fh, int cy, int team) {
            this.x = x;
            this.y = y;
            this.fh = fh;
            this.cy = cy;
            this.team = team;
        }
    }

    public final List<MonsterPoint> getMonsterPoints() {
        // 回傳快照，避免外部持有內部可變集合造成併發修改
        return new ArrayList<>(monsterPoints);
    }

    public final void addMonsterPoint(int x, int y, int fh, int cy, int team) {
        this.monsterPoints.add(new MonsterPoint(x, y, fh, cy, team));
    }

    public final void addMobSpawn(int mobId, int spendCP) {
        this.mobsToSpawn.add(new Pair<>(mobId, spendCP));
    }

    public final List<Pair<Integer, Integer>> getMobsToSpawn() {
        return new ArrayList<>(mobsToSpawn);
    }

    public final void addGuardianSpawn(Point guardian, int team) {
        this.guardiansToSpawn.add(new Pair<>(guardian, team));
    }

    public final List<Pair<Point, Integer>> getGuardians() {
        return new ArrayList<>(guardiansToSpawn);
    }

    public final List<Integer> getSkillIds() {
        return new ArrayList<>(skillIds);
    }

    public final void addSkillId(int z) {
        this.skillIds.add(z);
    }
}
