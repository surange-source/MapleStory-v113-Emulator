package constants;

public class ServerConstants {

    /*
     * Specifics which job gives an additional EXP to party
     * returns the percentage of EXP to increase
     */
    public static final byte Class_Bonus_EXP(final int job) {
        switch (job) {
            case 3000: // whenever these arrive, they'll give bonus
            case 3200:
            case 3210:
            case 3211:
            case 3212:
            case 3300:
            case 3310:
            case 3311:
            case 3312:
            case 3500:
            case 3510:
            case 3511:
            case 3512:
                return 10;
        }
        return 0;
    }

    // Start of Poll
    public static final boolean PollEnabled = false;
    public static final String Poll_Question = "Are you mudkiz?";
    private static final String[] POLL_ANSWERS = {"test1", "test2", "test3"};

    public static String[] getPollAnswers() {
        return POLL_ANSWERS.clone();
    }

    // End of Poll
    public static final short MAPLE_VERSION = 113;
    public static final String MAPLE_PATCH = "1";
    public static final boolean Use_Fixed_IV = false;
    public static final int MAXIMUM_CONNECTIONS = 600;
    public static final int MIN_MTS = 110;
    public static final int MTS_BASE = 100; // +1000 to everything in MSEA but cash is costly here
    public static final int MTS_TAX = 10; // +% to everything
    public static final int MTS_MESO = 5000; // mesos needed
    public static final int CHANNEL_COUNT = 200;
    public static final String CashShop_Key = "a;!%dfb_=*-a123d9{P~";
    public static final String Login_Key = "pWv]xq:SPTCtk^LGnU9F";
    private static final String[] CHANNEL_KEY = {
        "a56=-_dcSAgb",
        "y5(9=8@nV$;G",
        "yS5j943GzdUm",
        "G]R8Frg;kx6Y",
        "Z)?7fh*([N6S",
        "p4H8=*sknaEK",
        "A!Z7:mS.2?Kq",
        "M5:!rfv[?mdF",
        "Ee@3-7u5s6xy",
        "p]6L3eS(R;8A",
        "gZ,^k9.npy#F",
        "cG3M,*7%@zgt",
        "t+#@TV^3)hL9",
        "mw4:?sAU7[!6",
        "b6L]HF(2S,aE",
        "H@rAq]#^Y3+J",
        "o2A%wKCuqc7Txk5?#rNZ",
        "d4.Np*B89C6+]y2M^z-7",
        "oTL2jy9^zkH.84u(%b[d",
        "WCSJZj3tGX,[4hu;9s?g"
    };

    public static String[] getChannelKey() {
        return CHANNEL_KEY.clone();
    }

    public static enum PlayerGMRank {
        NORMAL('@', 0),
        INTERN('!', 1),
        GM('!', 2),
        ADMIN('!', 3);
        // SUPERADMIN('!', 3);
        private char commandPrefix;
        private int level;

        PlayerGMRank(char ch, int level) {
            commandPrefix = ch;
            this.level = level;
        }

        public char getCommandPrefix() {
            return commandPrefix;
        }

        public int getLevel() {
            return level;
        }
    }

    public static enum CommandType {
        NORMAL(0),
        TRADE(1);
        private int level;

        CommandType(int level) {
            this.level = level;
        }

        public int getType() {
            return level;
        }
    }
}
