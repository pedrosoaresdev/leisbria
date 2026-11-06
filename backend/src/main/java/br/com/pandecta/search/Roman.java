package br.com.pandecta.search;

import java.util.Locale;
import java.util.regex.Pattern;

/** Validação e conversão de algarismos romanos (incisos). */
public final class Roman {
    private static final Pattern VALID = Pattern.compile("^M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$");

    private Roman() {}

    public static boolean isValid(String s) {
        if (s == null || s.isEmpty()) return false;
        return VALID.matcher(s.toUpperCase(Locale.ROOT)).matches();
    }

    public static int toInt(String s) {
        String r = s.toUpperCase(Locale.ROOT);
        int total = 0, prev = 0;
        for (int i = r.length() - 1; i >= 0; i--) {
            int v = switch (r.charAt(i)) {
                case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50;
                case 'C' -> 100; case 'D' -> 500; case 'M' -> 1000; default -> 0;
            };
            total += v < prev ? -v : v;
            prev = Math.max(prev, v);
        }
        return total;
    }
}
