package br.com.pandecta.connectors;

import br.com.pandecta.common.Texts;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Construtor dos documentos dos índices "jurisprudencia" e "radar", garantindo campos
 * consistentes (e compatíveis com o mapeamento estrito) entre os conectores.
 */
public final class DecisaoDoc {
    private DecisaoDoc() {}

    private static final Pattern REF_LEGAL = Pattern.compile(
            "(?i)\\bart(?:igo)?s?\\.?\\s*(\\d{1,4}(?:\\.\\d{3})?)\\s*(?:º|°)?(?:[^.;]{0,40}?)\\b(?:d[aoe]s?\\s+)?(CF|CPP|CPC|CP|CC|CLT|CDC|CTN|ECA|LEP|Constitui[çc][ãa]o Federal|Lei\\s+n?[º°.]*\\s*[\\d.]+/\\d{2,4})");

    public static Map<String, Object> base(String id, String tribunal, String fonte, String fonteNome, String conector) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", id);
        d.put("tribunal", tribunal);
        d.put("tribunalNome", Tribunais.nome(tribunal));
        d.put("segmento", Tribunais.segmento(tribunal));
        String uf = Tribunais.uf(tribunal);
        if (uf != null) d.put("uf", uf);
        d.put("fonte", fonte);
        d.put("fonteNome", fonteNome);
        d.put("conector", conector);
        d.put("sincronizadoEm", Instant.now().toString());
        return d;
    }

    /** Extrai citações legais do texto, no formato "art. 312 CPP" (melhor esforço, para filtros e IA). */
    public static List<String> dispositivosCitados(String texto) {
        if (texto == null) return List.of();
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        Matcher m = REF_LEGAL.matcher(texto);
        while (m.find() && out.size() < 40) {
            out.add("art. " + m.group(1).replace(".", "") + " " + m.group(2).replaceAll("\\s+", " ").toUpperCase());
        }
        return List.copyOf(out);
    }

    /** Separa a ementa de um texto de acórdão quando ela vem marcada ("EMENTA: ..."). */
    public static String extrairEmenta(String texto) {
        if (texto == null) return null;
        Matcher m = Pattern.compile("(?is)\\bE\\s?M\\s?E\\s?N\\s?T\\s?A\\b\\s*[:.\\-–]?\\s*(.{40,4000}?)(?=\\n\\s*(?:A\\s?C\\s?[ÓO]\\s?R\\s?D\\s?[ÃA]\\s?O|RELAT[ÓO]RIO|VOTO|DECIS[ÃA]O|Vistos)\\b|$)").matcher(texto);
        if (m.find()) return Texts.collapse(m.group(1));
        return null;
    }

    public static Map<String, Object> eventoRadar(Map<String, Object> decisao, String tipoEvento, String dataEvento, boolean horario) {
        Map<String, Object> ev = new LinkedHashMap<>();
        String id = "ev:" + decisao.get("id");
        ev.put("id", id);
        ev.put("tipoEvento", tipoEvento);
        ev.put("statusPublicacao", decisao.getOrDefault("statusPublicacao", "PUBLICADA"));
        ev.put("tribunal", decisao.get("tribunal"));
        if (decisao.get("uf") != null) ev.put("uf", decisao.get("uf"));
        ev.put("numeroProcesso", decisao.get("numeroProcesso"));
        String classe = decisao.get("classe") == null ? "" : decisao.get("classe") + " ";
        ev.put("titulo", (classe + (decisao.get("numeroProcesso") == null ? "" : decisao.get("numeroProcesso"))).trim());
        String ementa = (String) decisao.get("ementa");
        String teor = (String) decisao.get("inteiroTeor");
        ev.put("resumo", Texts.truncate(ementa != null ? ementa : teor, 700));
        if (ementa != null) ev.put("ementa", ementa);
        if (decisao.get("relator") != null) ev.put("relator", decisao.get("relator"));
        if (decisao.get("orgaoJulgador") != null) ev.put("orgaoJulgador", decisao.get("orgaoJulgador"));
        if (decisao.get("assuntos") != null) ev.put("assuntos", decisao.get("assuntos"));
        if (decisao.get("dispositivosCitados") != null) ev.put("legislacaoRelacionada", decisao.get("dispositivosCitados"));
        ev.put("dataEvento", dataEvento);
        ev.put("horarioDisponivel", horario);
        ev.put("documentoId", decisao.get("id"));
        ev.put("fonte", decisao.get("fonte"));
        ev.put("fonteNome", decisao.get("fonteNome"));
        ev.put("urlOficial", decisao.get("urlOficial"));
        ev.put("conector", decisao.get("conector"));
        ev.put("sincronizadoEm", Instant.now().toString());
        return ev;
    }
}
