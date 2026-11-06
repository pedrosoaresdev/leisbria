package br.com.pandecta.legislation;

import java.util.Locale;

/**
 * Identificadores estáveis de dispositivos legais.
 * Formato: {norma}[:adct]:art-{n}[-{X}][:par-{n}|:par-unico][:inc-{ROMANO}][:al-{x}][:item-{n}]
 * Ex.: cf1988:art-5:inc-LXIII · cpp1941:art-310:par-1 · cf1988:adct:art-10:inc-II
 */
public final class DispositivoIds {
    private DispositivoIds() {}

    public static String artigo(String normaId, boolean adct, String artigo) {
        return normaId + (adct ? ":adct" : "") + ":art-" + artigo.toUpperCase(Locale.ROOT);
    }

    public static String paragrafo(String artigoId, String par) {
        return artigoId + ":par-" + ("unico".equals(par) ? "unico" : par.toUpperCase(Locale.ROOT));
    }

    public static String inciso(String paiId, String romano) {
        return paiId + ":inc-" + romano.toUpperCase(Locale.ROOT);
    }

    public static String alinea(String paiId, String letra) {
        return paiId + ":al-" + letra.toLowerCase(Locale.ROOT);
    }

    public static String item(String paiId, String num) {
        return paiId + ":item-" + num;
    }

    /** Ordinal no padrão de técnica legislativa: 1º a 9º; a partir de 10, número simples. */
    public static String ordinal(String numero) {
        String base = numero.replaceAll("-.*", "");
        String sufixo = numero.contains("-") ? numero.substring(numero.indexOf('-')) : "";
        try {
            int n = Integer.parseInt(base);
            return (n < 10 ? n + "º" : formatMilhar(n)) + sufixo;
        } catch (NumberFormatException e) {
            return numero;
        }
    }

    private static String formatMilhar(int n) {
        return n >= 1000 ? String.format(Locale.ROOT, "%d.%03d", n / 1000, n % 1000) : String.valueOf(n);
    }

    /** Rótulo legível: "Art. 5º, LXIII" · "Art. 310, § 1º" · "ADCT, Art. 10, II, a". */
    public static String rotulo(boolean adct, String artigo, String paragrafo, String inciso, String alinea, String item) {
        StringBuilder sb = new StringBuilder();
        if (adct) sb.append("ADCT, ");
        sb.append("Art. ").append(ordinal(artigo));
        if (paragrafo != null) sb.append(", ").append("unico".equals(paragrafo) ? "parágrafo único" : "§ " + ordinal(paragrafo));
        if (inciso != null) sb.append(", ").append(inciso);
        if (alinea != null) sb.append(", ").append(alinea);
        if (item != null) sb.append(", item ").append(item);
        return sb.toString();
    }
}
