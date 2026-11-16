package client;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import tools.HexTool;

public class LoginCrypto {

    protected static final int extralength = 6;
    private static final String[] Alphabet = {
        "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R",
        "S", "T", "U", "V", "W", "X", "Y", "Z"
    };
    private static final String[] Number = {"1", "2", "3", "4", "5", "6", "7", "8", "9"};
    private static final SecureRandom rand = new SecureRandom();

    public static String Generate_13DigitAsiasoftPassport() {
        StringBuilder sb = new StringBuilder();
        sb.append(Alphabet[rand.nextInt(Alphabet.length)]); // First Letter

        for (int i = 0; i < 11; i++) {
            sb.append(Number[rand.nextInt(Number.length)]); // 11 Numbers
        }
        sb.append(Alphabet[rand.nextInt(Alphabet.length)]); // Last Letter

        return sb.toString();
    }

    private static String toSimpleHexString(final byte[] bytes) {
        return HexTool.toString(bytes).replace(" ", "").toLowerCase();
    }

    private static String hashWithDigest(final String in, final String digest) {
        try {
            MessageDigest Digester = MessageDigest.getInstance(digest);
            Digester.update(in.getBytes(StandardCharsets.UTF_8));
            byte[] sha1Hash = Digester.digest();
            return toSimpleHexString(sha1Hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("Hashing the password failed", ex);
        }
    }

    private static String legacyHashWithDigest(final String in, final String digest) {
        try {
            MessageDigest Digester = MessageDigest.getInstance(digest);
            byte[] bytes = in.getBytes("UTF-8");
            Digester.update(bytes, 0, in.length());
            byte[] hash = Digester.digest();
            return toSimpleHexString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("Hashing the password failed", ex);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Encoding the string failed", e);
        }
    }

    public static String hexSha1(final String in) {
        return hashWithDigest(in, "SHA-1");
    }

    private static String legacyHexSha1(final String in) {
        return legacyHashWithDigest(in, "SHA-1");
    }

    private static String hexSha512(final String in) {
        return hashWithDigest(in, "SHA-512");
    }

    private static String legacyHexSha512(final String in) {
        return legacyHashWithDigest(in, "SHA-512");
    }

    public static boolean checkSha1Hash(final String hash, final String password) {
        if (hash == null || password == null) {
            return false;
        }
        final String newHash = makeSaltedSha1Hash(password);
        if (hash.equals(newHash)) {
            return true;
        }
        return hash.equals(legacyHexSha1(password));
    }

    public static boolean checkSaltedSha512Hash(
            final String hash, final String password, final String salt) {
        if (hash == null || password == null || salt == null) {
            return false;
        }
        final String newHash = makeSaltedSha512Hash(password, salt);
        if (hash.equals(newHash)) {
            return true;
        }
        return hash.equals(legacyHexSha512(password + salt));
    }

    public static boolean isLegacySha1Hash(final String hash, final String password) {
        if (hash == null || password == null) {
            return false;
        }
        final String newHash = makeSaltedSha1Hash(password);
        final String legacyHash = legacyHexSha1(password);
        return hash.equals(legacyHash) && !hash.equals(newHash);
    }

    public static boolean isLegacySaltedSha512Hash(
            final String hash, final String password, final String salt) {
        if (hash == null || password == null || salt == null) {
            return false;
        }
        final String newHash = makeSaltedSha512Hash(password, salt);
        final String legacyHash = legacyHexSha512(password + salt);
        return hash.equals(legacyHash) && !hash.equals(newHash);
    }

    public static String makeSaltedSha512Hash(final String password, final String salt) {
        return hexSha512(password + salt);
    }

    public static String makeSaltedSha1Hash(final String password) {
        return hexSha1(password);
    }

    public static String makeSalt() {
        byte[] salt = new byte[16];
        rand.nextBytes(salt);
        return toSimpleHexString(salt);
    }

    public static String rand_s(final String in) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < extralength; i++) {
            sb.append(
                    rand.nextBoolean()
                            ? Alphabet[rand.nextInt(Alphabet.length)]
                            : Number[rand.nextInt(Number.length)]);
        }
        return sb.toString() + in;
    }

    public static String rand_r(final String in) {
        return in.substring(extralength, extralength + 128);
    }
}
