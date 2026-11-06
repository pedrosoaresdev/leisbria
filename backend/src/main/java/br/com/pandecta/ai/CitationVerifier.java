package br.com.pandecta.ai;

import br.com.pandecta.common.Texts;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verificação anti-alucinação: toda menção a artigo, lei, súmula, tema ou número de processo feita pela IA
 * precisa existir no texto das fontes recuperadas. Frases com menções não verificadas são retiradas do
 * texto e listadas como "informação não confirmada". Referências [S#] inexistentes são removidas.
 */
public class CitationVerifier {

    private static final Pattern ART = Pattern.compile("\\bart(?:igo)?s?\\.?\\s*(\\d{1,4}(?:\\.\\d{3})?)");
    private static final Pattern LEI = Pattern.compile("\\b(?:lei(?: complementar)?|decreto(?:-lei)?|medida provisoria|emenda constitucional|ec|lc)\\s*(?:n\\.?\\s*[oº°]?\\s*)?(\\d{1,2}\\.?\\d{3}|\\d{1,4})");
    private static final Pattern SUMULA = Pattern.compile("\\bsumula\\s*(vinculante)?\\s*(?:n\\.?\\s*[oº°]?\\s*)?(\\d{1,4})");
    private static final Pattern TEMA = Pattern.compile("\\btema\\s*(?:n\\.?\\s*[oº°]?\\s*)?(\\d{1,2}\\.?\\d{3}|\\d{1,4})");
    private static final Pattern CNJ = Pattern.compile("\\b\\d{7}-?\\d{2}\\.?\\d{4}\\.?\\d\\.?\\d{2}\\.?\\d{4}\\b");
    private static final Pattern REF = Pattern.compile("\\[S(\\d+)\\]");

    private final Set<String> chavesFontes;
    private final int totalFontes;

    public CitationVerifier(List<String> textosFontes) {
        this.totalFontes = textosFontes.size();
        Set<String> k = new LinkedHashSet<>();
        for (String t : textosFontes) k.addAll(chaves(t));
        this.chavesFontes = k;
    }

    public record Mencao(String chave, String trecho) {}

    /** Chaves normalizadas das menções de um texto (ex.: "art:5", "lei:11343", "sumula:7", "cnj:000..."). */
    public static Set<String> chaves(String texto) {
        Set<String> out = new LinkedHashSet<>();
        for (Mencao m : mencoes(texto)) out.add(m.chave());
        return out;
    }

    public static List<Mencao> mencoes(String texto) {
        List<Mencao> out = new ArrayList<>();
        if (texto == null) return out;
        String t = Texts.fold(texto);
        Matcher m = ART.matcher(t);
        while (m.find()) out.add(new Mencao("art:" + m.group(1).replace(".", ""), m.group()));
        m = LEI.matcher(t);
        while (m.find()) out.add(new Mencao("lei:" + m.group(1).replace(".", ""), m.group()));
        m = SUMULA.matcher(t);
        while (m.find()) out.add(new Mencao("sumula:" + m.group(2), m.group()));
        m = TEMA.matcher(t);
        while (m.find()) out.add(new Mencao("tema:" + m.group(1).replace(".", ""), m.group()));
        m = CNJ.matcher(t);
        while (m.find()) out.add(new Mencao("cnj:" + m.group().replaceAll("\\D", ""), m.group()));
        return out;
    }

    public record Resultado(String texto, List<String> removidas, List<String> refsInvalidas) {}

    /** Remove referências [S#] inexistentes e frases com menções que não constam das fontes. */
    public Resultado verificar(String texto) {
        if (texto == null || texto.isBlank()) return new Resultado(texto, List.of(), List.of());
        List<String> refsInvalidas = new ArrayList<>();
        Matcher r = REF.matcher(texto);
        StringBuilder sb = new StringBuilder();
        while (r.find()) {
            int n = Integer.parseInt(r.group(1));
            if (n < 1 || n > totalFontes) {
                refsInvalidas.add(r.group());
                r.appendReplacement(sb, "");
            } else {
                r.appendReplacement(sb, Matcher.quoteReplacement(r.group()));
            }
        }
        r.appendTail(sb);

        List<String> mantidas = new ArrayList<>();
        List<String> removidas = new ArrayList<>();
        for (String frase : sb.toString().split("(?<=[.!?;])\\s+")) {
            boolean ok = true;
            for (Mencao m : mencoes(frase)) {
                if (!chavesFontes.contains(m.chave())) {
                    ok = false;
                    break;
                }
            }
            if (ok) mantidas.add(frase);
            else removidas.add(frase.trim());
        }
        return new Resultado(String.join(" ", mantidas).trim(), removidas, refsInvalidas);
    }
}
