package br.com.pandecta.common;

import org.jsoup.Jsoup;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

/** Utilitários de texto: normalização, sanitização (anti-XSS), hash e slug. */
public final class Texts {
    private Texts() {}

    /** Remove todo HTML, preservando apenas texto. Usado em tudo que vem de fontes externas. */
    public static String plain(String html) {
        if (html == null) return null;
        org.jsoup.nodes.Document doc = Jsoup.parse(html);
        doc.select("script,style,noscript").remove();
        for (org.jsoup.nodes.Element el : doc.select("br,p,div,li,tr,h1,h2,h3,h4,h5,h6")) {
            el.before(new org.jsoup.nodes.TextNode("\n"));
        }
        return collapse(doc.wholeText());
    }

    /** Colapsa espaços (inclui NBSP) e apara. */
    public static String collapse(String s) {
        if (s == null) return null;
        return s.replace(' ', ' ').replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll(" *\\n *", "\n").replaceAll("\\n{3,}", "\n\n").trim();
    }

    /** Minúsculas sem acentos, para comparações. */
    public static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT);
    }

    public static String slug(String s) {
        return fold(s).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        int cut = s.lastIndexOf(' ', max);
        return s.substring(0, cut > max * 0.6 ? cut : max) + "…";
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Limita o tamanho e remove caracteres de controle de entradas do usuário. */
    public static String userInput(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("\\p{Cntrl}", " ").trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
