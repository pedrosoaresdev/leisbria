package br.com.pandecta.connectors;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Tabelas do Judiciário: segmento, UF e o código "TR" do número único (Resolução CNJ 65/2008). */
public final class Tribunais {
    private Tribunais() {}

    /** Código TR (2 dígitos) → UF, para Justiça Estadual/Eleitoral (ordem alfabética do nome do estado). */
    public static final List<String> UF_POR_TR = List.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR",
            "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SE", "SP", "TO");

    public static String segmento(String sigla) {
        String s = sigla.toUpperCase(Locale.ROOT);
        if (s.equals("STF") || s.equals("STJ")) return "SUPERIOR";
        if (s.equals("TST") || s.startsWith("TRT")) return "TRABALHO";
        if (s.equals("TSE") || s.startsWith("TRE")) return "ELEITORAL";
        if (s.equals("STM") || s.startsWith("TJM")) return "MILITAR";
        if (s.startsWith("TRF")) return "FEDERAL";
        if (s.startsWith("TJ")) return "ESTADUAL";
        return "OUTRO";
    }

    /** UF de tribunais estaduais/eleitorais a partir da sigla (TJSP → SP, TJDFT → DF, TRE-MG → MG). */
    public static String uf(String sigla) {
        String s = sigla.toUpperCase(Locale.ROOT);
        if (s.equals("TJDFT")) return "DF";
        if (s.startsWith("TRE-") && s.length() == 6) return s.substring(4);
        if (s.startsWith("TJM") && s.length() == 5) return s.substring(3);
        if (s.startsWith("TJ") && s.length() == 4) return s.substring(2);
        return null;
    }

    /**
     * Alias do DataJud a partir do número CNJ (NNNNNNN-DD.AAAA.J.TR.OOOO). Retorna null quando o
     * segmento não está na API pública (ex.: STF, J=1).
     */
    public static String aliasDataJud(String numero20) {
        if (numero20 == null || numero20.length() != 20) return null;
        char j = numero20.charAt(13);
        String tr = numero20.substring(14, 16);
        int t = Integer.parseInt(tr);
        return switch (j) {
            case '3' -> "stj";
            case '4' -> t >= 1 && t <= 6 ? "trf" + t : null;
            case '5' -> t == 0 ? "tst" : (t <= 24 ? "trt" + t : null);
            case '6' -> t == 0 ? "tse" : (t >= 1 && t <= 27 ? "tre-" + UF_POR_TR.get(t - 1).toLowerCase(Locale.ROOT) : null);
            case '7' -> "stm";
            case '8' -> t >= 1 && t <= 27 ? (t == 7 ? "tjdft" : "tj" + UF_POR_TR.get(t - 1).toLowerCase(Locale.ROOT)) : null;
            case '9' -> switch (t) { case 13 -> "tjmmg"; case 21 -> "tjmrs"; case 26 -> "tjmsp"; default -> null; };
            default -> null;
        };
    }

    public static final Map<String, String> NOMES = Map.ofEntries(
            Map.entry("STF", "Supremo Tribunal Federal"),
            Map.entry("STJ", "Superior Tribunal de Justiça"),
            Map.entry("TST", "Tribunal Superior do Trabalho"),
            Map.entry("TSE", "Tribunal Superior Eleitoral"),
            Map.entry("STM", "Superior Tribunal Militar"));

    public static String nome(String sigla) {
        return NOMES.getOrDefault(sigla, sigla);
    }
}
