package tools;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a suite of utilities for manipulating strings. (Merged from the former StringUtil and
 * StringTool.)
 *
 * @author Frz
 * @author pungin
 */
public class StringUtil {
    private static final Logger log = LoggerFactory.getLogger(StringUtil.class);

    private static final Charset BIG5 = Charset.forName("BIG5");
    private static final Pattern NUMBER = Pattern.compile("-?(\\d+\\.?\\d*|\\.\\d+)");
    private static final Pattern NON_DIGIT = Pattern.compile("[^0-9]");

    // ------------------------------------------------------------------
    // Padding / length / joining
    // ------------------------------------------------------------------

    /** Gets the byte length of a string, counted in BIG5. */
    public static int getlength(final String str) {
        return str.getBytes(BIG5).length;
    }

    /**
     * Gets a string padded from the left to <code>length</code> (in BIG5 bytes) by <code>padchar
     * </code>.
     */
    public static String getLeftPaddedStr(final String in, final char padchar, final int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int x = getlength(in); x < length; x++) {
            builder.append(padchar);
        }
        return builder.append(in).toString();
    }

    /**
     * Gets a string padded from the right to <code>length</code> (in BIG5 bytes) by <code>padchar
     * </code>.
     */
    public static String getRightPaddedStr(final String in, final char padchar, final int length) {
        StringBuilder builder = new StringBuilder(in);
        for (int x = getlength(in); x < length; x++) {
            builder.append(padchar);
        }
        return builder.toString();
    }

    /** Joins an array of strings starting from index <code>start</code> with a space. */
    public static String joinStringFrom(final String[] arr, final int start) {
        return joinStringFrom(arr, start, " ");
    }

    /**
     * Joins an array of strings starting from index <code>start</code> with <code>sep</code> as a
     * separator.
     */
    public static String joinStringFrom(final String[] arr, final int start, final String sep) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < arr.length; i++) {
            builder.append(arr[i]);
            if (i != arr.length - 1) {
                builder.append(sep);
            }
        }
        return builder.toString();
    }

    /** Makes an enum name human readable (fixes spaces, capitalization, etc). */
    public static String makeEnumHumanReadable(final String enumName) {
        StringBuilder builder = new StringBuilder(enumName.length() + 1);
        for (String word : enumName.split("_")) {
            if (word.length() <= 2) {
                builder.append(word); // assume that it's an abbreviation
            } else {
                builder.append(word.charAt(0));
                builder.append(word.substring(1).toLowerCase());
            }
            builder.append(' ');
        }
        return builder.toString().trim();
    }

    /** Counts the number of <code>chr</code>'s in <code>str</code>. */
    public static int countCharacters(final String str, final char chr) {
        return (int) str.chars().filter(c -> c == chr).count();
    }

    // ------------------------------------------------------------------
    // Time
    // ------------------------------------------------------------------

    public static String getReadableMillis(long startMillis, long endMillis) {
        StringBuilder sb = new StringBuilder();
        double elapsedSeconds = (endMillis - startMillis) / 1000.0;
        int elapsedSecs = ((int) elapsedSeconds) % 60;
        int elapsedMinutes = (int) (elapsedSeconds / 60.0);
        int elapsedMins = elapsedMinutes % 60;
        int elapsedHrs = elapsedMinutes / 60;
        int elapsedHours = elapsedHrs % 24;
        int elapsedDays = elapsedHrs / 24;
        if (elapsedDays > 0) {
            boolean mins = elapsedHours > 0;
            sb.append(elapsedDays);
            sb.append(" day" + (elapsedDays > 1 ? "s" : "") + (mins ? ", " : "."));
            if (mins) {
                boolean secs = elapsedMins > 0;
                if (!secs) {
                    sb.append("and ");
                }
                sb.append(elapsedHours);
                sb.append(" hour" + (elapsedHours > 1 ? "s" : "") + (secs ? ", " : "."));
                if (secs) {
                    boolean millis = elapsedSecs > 0;
                    if (!millis) {
                        sb.append("and ");
                    }
                    sb.append(elapsedMins);
                    sb.append(" minute" + (elapsedMins > 1 ? "s" : "") + (millis ? ", " : "."));
                    if (millis) {
                        sb.append("and ");
                        sb.append(elapsedSecs);
                        sb.append(" second" + (elapsedSecs > 1 ? "s" : "") + ".");
                    }
                }
            }
        } else if (elapsedHours > 0) {
            boolean mins = elapsedMins > 0;
            sb.append(elapsedHours);
            sb.append(" hour" + (elapsedHours > 1 ? "s" : "") + (mins ? ", " : "."));
            if (mins) {
                boolean secs = elapsedSecs > 0;
                if (!secs) {
                    sb.append("and ");
                }
                sb.append(elapsedMins);
                sb.append(" minute" + (elapsedMins > 1 ? "s" : "") + (secs ? ", " : "."));
                if (secs) {
                    sb.append("and ");
                    sb.append(elapsedSecs);
                    sb.append(" second" + (elapsedSecs > 1 ? "s" : "") + ".");
                }
            }
        } else if (elapsedMinutes > 0) {
            boolean secs = elapsedSecs > 0;
            sb.append(elapsedMinutes);
            sb.append(" minute" + (elapsedMinutes > 1 ? "s" : "") + (secs ? " " : "."));
            if (secs) {
                sb.append("and ");
                sb.append(elapsedSecs);
                sb.append(" second" + (elapsedSecs > 1 ? "s" : "") + ".");
            }
        } else if (elapsedSeconds > 0) {
            sb.append((int) elapsedSeconds);
            sb.append(" second" + (elapsedSeconds > 1 ? "s" : "") + ".");
        } else {
            sb.append("None.");
        }
        return sb.toString();
    }

    public static int getDaysAmount(long startMillis, long endMillis) {
        double elapsedSeconds = (endMillis - startMillis) / 1000.0;
        int elapsedMinutes = (int) (elapsedSeconds / 60.0);
        int elapsedHrs = elapsedMinutes / 60;
        return elapsedHrs / 24;
    }

    // ------------------------------------------------------------------
    // Files
    // ------------------------------------------------------------------

    public static String readFileAsString(String path) {
        try {
            StringBuilder builder = new StringBuilder();
            for (String str : Files.readAllLines(Path.of(path), BIG5)) {
                builder.append(str).append(System.lineSeparator());
            }
            return builder.toString();
        } catch (IOException e) {
            log.error("Error while loading file {} {}", path, e);
            return "";
        }
    }

    /**
     * 判斷檔案的編碼格式 (依 BOM 判斷, 無 BOM 預設 UTF-8)
     *
     * @param file :file
     * @return 檔案的編碼格式
     * @throws java.io.FileNotFoundException
     */
    public static String codeString(File file) throws FileNotFoundException {
        int p = 0;
        try (BufferedInputStream bin = new BufferedInputStream(new FileInputStream(file))) {
            p = (bin.read() << 8) + bin.read();
        } catch (FileNotFoundException ex) {
            throw ex;
        } catch (IOException ex) {
            log.error("StringUtil.codeString ERROR]", ex);
        }
        switch (p) {
            case 0xFFFE:
                return "UTF-16LE";
            case 0xFEFF:
                return "UTF-16BE";
            default:
                return "UTF-8";
        }
    }

    // ------------------------------------------------------------------
    // Parsing (formerly StringTool)
    // ------------------------------------------------------------------

    public static boolean parseBoolean(final String info) {
        return info != null && info.equalsIgnoreCase("true") || parseLong(info) > 0;
    }

    public static byte parseSByte(final String info) {
        return (byte) parseLong(info);
    }

    public static short parseByte(final String info) {
        return (short) (parseLong(info) & 0xFF);
    }

    public static short parseShort(final String info) {
        return (short) parseLong(info);
    }

    public static int parseUShort(final String info) {
        return (int) (parseLong(info) & 0xFFFF);
    }

    public static int parseInt(final String info) {
        return (int) parseLong(info);
    }

    public static long parseUInt(final String info) {
        return parseLong(info) & 0xFFFFFFFFL;
    }

    public static long parseLong(final String info) {
        return (long) parseDouble(info);
    }

    public static float parseFloat(final String info) {
        return (float) parseDouble(info);
    }

    public static double parseDouble(final String info) {
        if (info == null || !NUMBER.matcher(info).matches()) {
            return 0.0D;
        }
        return Double.parseDouble(info);
    }

    public static int getIntFromString(final String a) {
        if (a == null) {
            return 0;
        }
        return parseInt(NON_DIGIT.matcher(a).replaceAll("").trim());
    }

    // ------------------------------------------------------------------
    // Split / join / key=value;key=value helpers (formerly StringTool)
    // ------------------------------------------------------------------

    /** Splits on a literal (non-regex) separator. */
    public static String[] split(final String info, final String value) {
        if (info == null || value == null) {
            return null;
        }
        return info.split(Pattern.quote(value));
    }

    /**
     * Splits on a regular expression. Use this for callers that really pass a regex such as "\\s+"
     * or "[,;]".
     */
    public static String[] splitRegex(final String info, final String regex) {
        if (info == null || regex == null) {
            return null;
        }
        return info.split(regex);
    }

    public static List<String> splitList(final String info, final String value) {
        String[] parts = split(info, value);
        return parts == null ? null : new LinkedList<>(Arrays.asList(parts));
    }

    public static String unite(final String[] infos, final String value) {
        return infos == null ? null : unite(Arrays.asList(infos), value);
    }

    public static String unite(final List<String> infos, final String value) {
        if (infos == null || infos.isEmpty() || value == null) {
            return null;
        }
        return String.join(value, infos);
    }

    public static String getOneValue(final String info, final String key) {
        if (info == null || key == null) {
            return null;
        }
        for (String x : info.split(";")) {
            final String[] kv = x.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return kv[1];
            }
        }
        return null;
    }

    /**
     * Sets, replaces or (when <code>value</code> is null) removes a key in a "k=v;k=v" string.
     * Returns null when the result is empty.
     */
    public static String updateOneValue(final String info, final String key, final String value) {
        if (key == null) {
            return null;
        }
        if (info == null) {
            return value != null ? key + "=" + value : null;
        }

        final List<String> out = new ArrayList<>();
        boolean match = false;
        for (String x : info.split(";")) {
            if (x.isEmpty()) {
                continue;
            }
            final String[] kv = x.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                match = true;
                if (value != null) {
                    out.add(key + "=" + value);
                }
            } else {
                out.add(x); // keep other entries untouched
            }
        }
        if (!match && value != null) {
            out.add(key + "=" + value);
        }
        return out.isEmpty() ? null : String.join(";", out);
    }
}
