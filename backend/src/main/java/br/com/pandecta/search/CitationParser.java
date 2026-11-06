package br.com.pandecta.search;

import br.com.pandecta.common.Texts;
import br.com.pandecta.legislation.NormaCatalog;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconhece citações jurídicas em texto livre: artigos/parágrafos/incisos/alíneas, normas por sigla ou
 * por número, súmulas, temas, número CNJ de processo e ações de controle concentrado.
 * Trabalha sobre o texto "dobrado" (minúsculo e sem acentos) para tolerar variações de digitação.
 */
@Component
public class CitationParser {

    private static final Pattern ARTIGO = Pattern.compile(
            "\\bart(?:igo)?s?\\.?\\s*(\\d{1,4}(?:\\.\\d{3})?)\\s*(?:o|º|°)?\\s*(?:-\\s*([a-z])\\b)?");
    private static final Pattern PARAGRAFO = Pattern.compile(
            "(?:§|par(?:agrafo)?\\.?)\\s*(\\d{1,3})\\s*(?:o|º|°)?|paragrafo\\s+unico|par\\.?\\s*un(?:ico)?\\.?");
    private static final Pattern INCISO = Pattern.compile(
            "(?:\\binc(?:iso)?\\.?\\s*|,\\s*)([ivxlcdm]{1,7})\\b(?!\\s*\\))");
    private static final Pattern ALINEA = Pattern.compile(
            "(?:\\bal(?:inea)?\\.?\\s*['\"]?([a-z])['\"]?\\b|,\\s*([a-z])\\s*(?:\\)|,|$|\\s+d[aoe]\\b))");
    private static final Pattern LEI_NUMERO = Pattern.compile(
            "\\b(lei complementar|lc|lei|decreto-lei|decreto lei|decreto|medida provisoria|mp|emenda constitucional|ec)\\s*(?:n\\.?\\s*(?:o|º|°)?\\s*)?(\\d{1,2}\\.?\\d{3}|\\d{1,4})\\s*(?:/|,\\s*de\\s*|\\s+de\\s+)?(\\d{2,4})?");
    private static final Pattern SUMULA = Pattern.compile(
            "\\bsumula\\s*(vinculante)?\\s*(?:n\\.?\\s*(?:o|º|°)?\\s*)?(\\d{1,4})(?:\\s*(?:/|do|da)\\s*(stf|stj|tst|tse))?");
    private static final Pattern TEMA = Pattern.compile(
            "\\btema\\s*(?:n\\.?\\s*(?:o|º|°)?\\s*)?(\\d{1,2}\\.?\\d{3}|\\d{1,4})(?:\\s*(?:/|do|da)\\s*(stf|stj|tst))?");
    private static final Pattern CNJ = Pattern.compile("\\b(\\d{7}-?\\d{2}\\.?\\d{4}\\.?\\d\\.?\\d{2}\\.?\\d{4})\\b");
    private static final Pattern CONTROLE = Pattern.compile("\\b(adi|adc|adpf|ado)\\s*(?:n\\.?\\s*(?:o|º|°)?\\s*)?(\\d{1,5})\\b");
    private static final Pattern ADCT = Pattern.compile("\\badct\\b|disposicoes constitucionais transitorias");

    private final NormaCatalog catalog;

    public CitationParser(NormaCatalog catalog) {
        this.catalog = catalog;
    }

    public Citation parse(String consulta) {
        String t = " " + Texts.fold(Texts.userInput(consulta, 2000)) + " ";
        String artigo = null, paragrafo = null, inciso = null, alinea = null;

        Matcher ma = ARTIGO.matcher(t);
        int fimArtigo = -1;
        if (ma.find()) {
            artigo = ma.group(1).replace(".", "");
            if (ma.group(2) != null) artigo = artigo + "-" + ma.group(2).toUpperCase(Locale.ROOT);
            fimArtigo = ma.end();
        }
        if (artigo != null) {
            // Só procura §/inciso/alínea na janela logo após o artigo, para não confundir com outras partes da frase.
            String janela = t.substring(fimArtigo, Math.min(t.length(), fimArtigo + 80));
            janela = cortarNaNorma(janela);
            Matcher mp = PARAGRAFO.matcher(janela);
            int desde = 0;
            if (mp.find()) {
                paragrafo = mp.group(1) != null ? mp.group(1) : "unico";
                desde = mp.end();
            }
            Matcher mi = INCISO.matcher(janela);
            if (mi.find(desde)) {
                String cand = mi.group(1);
                if (Roman.isValid(cand)) {
                    inciso = cand.toUpperCase(Locale.ROOT);
                    desde = mi.end();
                }
            }
            if (inciso != null) {
                Matcher mal = ALINEA.matcher(janela);
                if (mal.find(desde)) {
                    alinea = mal.group(1) != null ? mal.group(1) : mal.group(2);
                }
            }
        }

        // Norma por número (Lei 11.343/2006)
        String normaId = null, normaTipo = null, normaNumero = null;
        Integer normaAno = null;
        Matcher ml = LEI_NUMERO.matcher(t);
        if (ml.find()) {
            normaTipo = tipoCanonico(ml.group(1));
            normaNumero = ml.group(2).replace(".", "");
            if (ml.group(3) != null) {
                int ano = Integer.parseInt(ml.group(3));
                normaAno = ano < 100 ? (ano > 30 ? 1900 + ano : 2000 + ano) : ano;
            }
            if (normaAno != null) {
                normaId = catalog.porNumeroAno(normaNumero, normaAno).map(NormaCatalog.Entrada::id).orElse(null);
            }
        }
        // Norma por sigla/apelido (CF, CPP, Código Civil...)
        if (normaId == null) {
            for (String chave : catalog.chavesSigla()) {
                if (chave.isBlank()) continue;
                Pattern p = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(chave) + "(?![a-z0-9])");
                if (p.matcher(t).find()) {
                    normaId = catalog.porSigla(chave).map(NormaCatalog.Entrada::id).orElse(null);
                    if (normaId != null) break;
                }
            }
        }
        boolean adct = ADCT.matcher(t).find();
        if (adct) normaId = "cf1988";

        String sumNum = null, sumTrib = null;
        boolean vinc = false;
        Matcher ms = SUMULA.matcher(t);
        if (ms.find()) {
            vinc = ms.group(1) != null;
            sumNum = ms.group(2);
            sumTrib = vinc ? "STF" : (ms.group(3) != null ? ms.group(3).toUpperCase(Locale.ROOT) : null);
        }
        String temaNum = null, temaTrib = null;
        Matcher mt = TEMA.matcher(t);
        if (mt.find()) {
            temaNum = mt.group(1).replace(".", "");
            temaTrib = mt.group(2) != null ? mt.group(2).toUpperCase(Locale.ROOT) : null;
        }
        String cnj = null;
        Matcher mc = CNJ.matcher(t);
        if (mc.find()) cnj = normalizarCnj(mc.group(1));

        String ctrlClasse = null, ctrlNum = null;
        Matcher mctl = CONTROLE.matcher(t);
        if (mctl.find()) {
            ctrlClasse = mctl.group(1).toUpperCase(Locale.ROOT);
            ctrlNum = mctl.group(2);
        }

        return new Citation(normaId, normaTipo, normaNumero, normaAno, adct, artigo, paragrafo, inciso, alinea,
                sumNum, sumTrib, vinc, temaNum, temaTrib, cnj, ctrlClasse, ctrlNum);
    }

    /** "0001234-56.2024.8.26.0100" a partir de qualquer variação com ou sem pontuação. */
    public static String normalizarCnj(String raw) {
        String d = raw.replaceAll("\\D", "");
        if (d.length() != 20) return null;
        return d.substring(0, 7) + "-" + d.substring(7, 9) + "." + d.substring(9, 13) + "." + d.charAt(13) + "."
                + d.substring(14, 16) + "." + d.substring(16, 20);
    }

    private static String cortarNaNorma(String janela) {
        Matcher m = Pattern.compile("\\s(?:d[aoe]s?|na|no)\\s+(?:lei|cf|constituicao|codigo|decreto|cpp|cpc|cp|cc|clt|cdc|ctn|eca)").matcher(janela);
        return m.find() ? janela.substring(0, m.start() + 1) : janela;
    }

    private static String tipoCanonico(String s) {
        return switch (s) {
            case "lei complementar", "lc" -> "Lei Complementar";
            case "decreto-lei", "decreto lei" -> "Decreto-Lei";
            case "decreto" -> "Decreto";
            case "medida provisoria", "mp" -> "Medida Provisória";
            case "emenda constitucional", "ec" -> "Emenda Constitucional";
            default -> "Lei";
        };
    }
}
