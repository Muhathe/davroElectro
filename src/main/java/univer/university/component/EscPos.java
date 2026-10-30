package univer.university.component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ESC/POS buyruqlari (Xprinter XP-58 va boshqa 58/80 mm termoprinterlar).
 * Matn ASCII ga o'giriladi: printer kod sahifasidan qat'i nazar to'g'ri chiqadi.
 */
public final class EscPos {

    private static final Map<Character, String> CYR = Map.ofEntries(
            Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"), Map.entry('г', "g"), Map.entry('д', "d"),
            Map.entry('е', "e"), Map.entry('ё', "yo"), Map.entry('ж', "j"), Map.entry('з', "z"), Map.entry('и', "i"),
            Map.entry('й', "y"), Map.entry('к', "k"), Map.entry('л', "l"), Map.entry('м', "m"), Map.entry('н', "n"),
            Map.entry('о', "o"), Map.entry('п', "p"), Map.entry('р', "r"), Map.entry('с', "s"), Map.entry('т', "t"),
            Map.entry('у', "u"), Map.entry('ф', "f"), Map.entry('х', "x"), Map.entry('ц', "ts"), Map.entry('ч', "ch"),
            Map.entry('ш', "sh"), Map.entry('щ', "sh"), Map.entry('ъ', "'"), Map.entry('ы', "i"), Map.entry('ь', ""),
            Map.entry('э', "e"), Map.entry('ю', "yu"), Map.entry('я', "ya"), Map.entry('ў', "o'"), Map.entry('қ', "q"),
            Map.entry('ғ', "g'"), Map.entry('ҳ', "h"));

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final int width;

    public EscPos(int width) {
        this.width = width;
        raw(0x1B, 0x40);          // init
        raw(0x1B, 0x74, 0x00);    // PC437
    }

    public int width() {
        return width;
    }

    public EscPos center() {
        return raw(0x1B, 0x61, 1);
    }

    public EscPos left() {
        return raw(0x1B, 0x61, 0);
    }

    public EscPos bold(boolean on) {
        return raw(0x1B, 0x45, on ? 1 : 0);
    }

    /** Ikki baravar balandlik (kenglik o'zgarmaydi — qatorga sig'ish hisobi buzilmaydi). */
    public EscPos tall(boolean on) {
        return raw(0x1D, 0x21, on ? 0x01 : 0x00);
    }

    public EscPos line(String text) {
        for (String l : wrap(ascii(text), width)) {
            text(l);
            raw(0x0A);
        }
        return this;
    }

    /** Chapda nom, o'ngda summa; sig'masa summa keyingi qatorga. */
    public EscPos pair(String left, String right) {
        String l = ascii(left);
        String r = ascii(right);
        if (l.length() + r.length() + 1 > width) {
            line(l);
            l = "";
        }
        text(l + " ".repeat(Math.max(1, width - l.length() - r.length())) + r);
        return raw(0x0A);
    }

    public EscPos separator() {
        text("-".repeat(width));
        return raw(0x0A);
    }

    public EscPos feed(int lines) {
        return raw(0x1B, 0x64, lines);
    }

    public EscPos cut() {
        return raw(0x1D, 0x56, 0x42, 0x00);
    }

    /** QR (GS ( k, model 2). */
    public EscPos qr(String data) {
        byte[] d = data.getBytes(StandardCharsets.US_ASCII);
        int len = d.length + 3;
        raw(0x1D, 0x28, 0x6B, 4, 0, 0x31, 0x41, 0x32, 0x00);
        raw(0x1D, 0x28, 0x6B, 3, 0, 0x31, 0x43, 5);
        raw(0x1D, 0x28, 0x6B, 3, 0, 0x31, 0x45, 0x30);
        raw(0x1D, 0x28, 0x6B, len & 0xFF, (len >> 8) & 0xFF, 0x31, 0x50, 0x30);
        out.writeBytes(d);
        raw(0x1D, 0x28, 0x6B, 3, 0, 0x31, 0x51, 0x30);
        return raw(0x0A);
    }

    public byte[] bytes() {
        return out.toByteArray();
    }

    private EscPos raw(int... b) {
        for (int x : b) {
            out.write(x);
        }
        return this;
    }

    private void text(String s) {
        out.writeBytes(s.getBytes(StandardCharsets.US_ASCII));
    }

    static List<String> wrap(String s, int width) {
        List<String> res = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            while (word.length() > width) {
                if (!cur.isEmpty()) {
                    res.add(cur.toString());
                    cur.setLength(0);
                }
                res.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (cur.length() + word.length() + (cur.isEmpty() ? 0 : 1) > width) {
                res.add(cur.toString());
                cur.setLength(0);
            }
            if (!cur.isEmpty()) {
                cur.append(' ');
            }
            cur.append(word);
        }
        if (!cur.isEmpty() || res.isEmpty()) {
            res.add(cur.toString());
        }
        return res;
    }

    /** O'zbek apostroflari va kirill harflari lotinga; qolgan ASCII bo'lmagan belgi "?". */
    public static String ascii(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= 32 && c < 127) {
                b.append(c);
            } else if ("ʻʼ‘’`´".indexOf(c) >= 0) {
                b.append('\'');
            } else if (c == '\t' || c == '\n' || c == '\r') {
                b.append(' ');
            } else if (c == '«' || c == '»' || c == '“' || c == '”') {
                b.append('"');
            } else if (c == '–' || c == '—') {
                b.append('-');
            } else {
                char lower = Character.toLowerCase(c);
                String t = CYR.get(lower);
                if (t == null) {
                    b.append('?');
                } else if (Character.isUpperCase(c) && !t.isEmpty()) {
                    b.append(Character.toUpperCase(t.charAt(0))).append(t.substring(1));
                } else {
                    b.append(t);
                }
            }
        }
        return b.toString();
    }
}
