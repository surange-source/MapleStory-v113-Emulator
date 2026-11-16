package server;

import java.util.concurrent.ThreadLocalRandom;

public class Randomizer {

    public static final int nextInt() {
        return ThreadLocalRandom.current().nextInt();
    }

    public static final int nextInt(final int arg0) {
        return ThreadLocalRandom.current().nextInt(arg0);
    }

    public static final void nextBytes(final byte[] bytes) {
        ThreadLocalRandom.current().nextBytes(bytes);
    }

    public static final boolean nextBoolean() {
        return ThreadLocalRandom.current().nextBoolean();
    }

    public static final double nextDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }

    public static final float nextFloat() {
        return ThreadLocalRandom.current().nextFloat();
    }

    public static final long nextLong() {
        return ThreadLocalRandom.current().nextLong();
    }

    public static final int rand(final int lbound, final int ubound) {
        return (int) ((ThreadLocalRandom.current().nextDouble() * (ubound - lbound + 1)) + lbound);
    }
}
