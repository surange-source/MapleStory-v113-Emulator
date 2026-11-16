package server;

import constants.GameConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomRewards {
    private static final Logger log = LoggerFactory.getLogger(RandomRewards.class);

    private static final class Holder {
        private static final RandomRewards INSTANCE = new RandomRewards();
    }

    private List<Integer> compiledGold = null;
    private List<Integer> compiledSilver = null;
    private List<Integer> compiledFishing = null;
    private List<Integer> compiledXmaxs = null;
    private List<Integer> compiledEvent = null;
    private List<Integer> compiledEventC = null;
    private List<Integer> compiledEventB = null;
    private List<Integer> compiledEventA = null;
    private List<Integer> tenPercent = null;

    public static RandomRewards getInstance() {
        return Holder.INSTANCE;
    }

    private RandomRewards() {
        log.info("Loading RandomRewards :::");
        // Gold Box
        List<Integer> returnArray = new ArrayList<>();
        processRewards(returnArray, GameConstants.goldrewards);
        compiledGold = returnArray;

        // Silver Box
        returnArray = new ArrayList<>();
        processRewards(returnArray, GameConstants.silverrewards);
        compiledSilver = returnArray;

        // Fishing Rewards
        returnArray = new ArrayList<>();
        processRewards(returnArray, GameConstants.getFishingReward());
        compiledFishing = returnArray;

        // Xmaxs Rewards
        returnArray = new ArrayList<>();
        processRewards(returnArray, GameConstants.getXmaxsReward());
        compiledXmaxs = returnArray;

        // Event Rewards
        returnArray = new ArrayList<>();
        processRewards(returnArray, GameConstants.getEventCommonReward());
        compiledEventC = returnArray;

        returnArray = new ArrayList<>();
        processRewards(returnArray, GameConstants.getEventUncommonReward());
        compiledEventB = returnArray;

        returnArray = new ArrayList<>();
        processRewards(returnArray, GameConstants.getEventSuperReward());
        compiledEvent = returnArray;

        // Rare event rewards (A tier). No dedicated constant available; keep empty
        // so getEventReward() falls back to the super tier.
        compiledEventA = new ArrayList<>();

        returnArray = new ArrayList<>();
        processRewardsSimple(returnArray, GameConstants.getTenPercent());
        tenPercent = returnArray;

        log.info(" Loaded"); // ????
    }

    private final void processRewards(final List<Integer> returnArray, final int[] list) {
        int lastitem = 0;
        for (int i = 0; i < list.length; i++) {
            if (i % 2 == 0) { // Even
                lastitem = list[i];
            } else { // Odd
                for (int j = 0; j < list[i]; j++) {
                    returnArray.add(lastitem);
                }
            }
        }
        Collections.shuffle(returnArray);
    }

    private static void processRewardsSimple(final List<Integer> returnArray, final int[] list) {
        for (int i = 0; i < list.length; i++) {
            returnArray.add(list[i]);
        }
        Collections.shuffle(returnArray);
    }

    public final int getGoldBoxReward() {
        return compiledGold.get(Randomizer.nextInt(compiledGold.size()));
    }

    public final int getSilverBoxReward() {
        return compiledSilver.get(Randomizer.nextInt(compiledSilver.size()));
    }

    public final int getFishingReward() {
        return compiledFishing.get(Randomizer.nextInt(compiledFishing.size()));
    }

    public final int getXmasreward() {
        return compiledXmaxs.get(Randomizer.nextInt(compiledXmaxs.size()));
    }

    public final int getEventReward() {
        final int chance = Randomizer.nextInt(100);
        if (chance < 50) {
            return compiledEventC.get(Randomizer.nextInt(compiledEventC.size()));
        } else if (chance < 80) {
            return compiledEventB.get(Randomizer.nextInt(compiledEventB.size()));
        } else if (chance < 95 && compiledEventA != null && !compiledEventA.isEmpty()) {
            return compiledEventA.get(Randomizer.nextInt(compiledEventA.size()));
        } else {
            return compiledEvent.get(Randomizer.nextInt(compiledEvent.size()));
        }
    }

    public final List<Integer> getTenPercent() {
        return tenPercent;
    }
}
