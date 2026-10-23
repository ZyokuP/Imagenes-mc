package com.example.imagenes;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TiempoUtil {
    private static final Pattern FORMATO =
            Pattern.compile("^(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?$", Pattern.CASE_INSENSITIVE);

    private TiempoUtil() {}

    /** "90", "30s", "5m", "1h30m" -> segundos. Devuelve -1 si el formato no es válido. */
    public static long parsear(String t) {
        try {
            if (t.matches("\\d+")) return Long.parseLong(t);
            Matcher m = FORMATO.matcher(t);
            if (t.isEmpty() || !m.matches()) return -1;
            long h = valor(m, 1), mi = valor(m, 2), s = valor(m, 3);
            return Math.addExact(Math.addExact(Math.multiplyExact(h, 3600L), Math.multiplyExact(mi, 60L)), s);
        } catch (NumberFormatException | ArithmeticException ex) {
            return -1;
        }
    }

    private static long valor(Matcher m, int grupo) {
        String g = m.group(grupo);
        return g == null ? 0 : Long.parseLong(g);
    }

    public static String formatear(long totalSegundos) {
        long h = totalSegundos / 3600;
        long m = (totalSegundos % 3600) / 60;
        long s = totalSegundos % 60;
        if (h > 0) return String.format(Locale.ROOT, "%dh %02dm %02ds", h, m, s);
        if (m > 0) return String.format(Locale.ROOT, "%dm %02ds", m, s);
        return s + "s";
    }
}
