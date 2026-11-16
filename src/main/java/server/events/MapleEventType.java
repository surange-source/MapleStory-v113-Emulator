package server.events;

public enum MapleEventType {
    Coconut("採果子", new int[] {109080000}, 3), // just using one
    CokePlay("打瓶蓋", new int[] {109080010}, 14), // just using one
    Fitness("奇幻忍耐", new int[] {109040000, 109040001, 109040002, 109040003, 109040004}, 6),
    OlaOla("魔法森林猜繩子", new int[] {109030001, 109030002, 109030003}, 18),
    OxQuiz("小小考驗", new int[] {109020001}, 20),
    Snowball("滾雪球", new int[] {109060000}, 9); // just using one
    public String command;
    public int[] mapids;
    public int channel;

    private MapleEventType(String comm, int[] mapids, int channel) {
        this.command = comm;
        this.mapids = mapids;
        this.channel = channel;
    }

    public static final MapleEventType getByString(final String splitted) {
        for (MapleEventType t : MapleEventType.values()) {
            if (t.command.equalsIgnoreCase(splitted)) {
                return t;
            }
        }
        return null;
    }
}
