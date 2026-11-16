package server;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.HashMap;
import java.util.Map;

public class MapleShopFactory {

    private final Map<Integer, MapleShop> shops = new HashMap<>();
    private final Map<Integer, MapleShop> npcShops = new HashMap<>();

    private MapleShopFactory() {}

    private static final class Holder {
        private static final MapleShopFactory INSTANCE = new MapleShopFactory();
    }

    @SuppressFBWarnings(
            value = "MS_EXPOSE_REP",
            justification = "Singleton by design; callers must share the same shop cache.")
    public static MapleShopFactory getInstance() {
        return Holder.INSTANCE;
    }

    public void clear() {
        shops.clear();
        npcShops.clear();
    }

    public MapleShop getShop(int shopId) {
        if (shops.containsKey(shopId)) {
            return shops.get(shopId);
        }
        return loadShop(shopId, true);
    }

    public MapleShop getShopForNPC(int npcId) {
        if (npcShops.containsKey(npcId)) {
            return npcShops.get(npcId);
        }
        return loadShop(npcId, false);
    }

    private MapleShop loadShop(int id, boolean isShopId) {
        MapleShop ret = MapleShop.createFromDB(id, isShopId);
        if (ret != null) {
            shops.put(ret.getId(), ret);
            npcShops.put(ret.getNpcId(), ret);
        } else if (isShopId) {
            shops.put(id, null);
        } else {
            npcShops.put(id, null);
        }
        return ret;
    }
}
