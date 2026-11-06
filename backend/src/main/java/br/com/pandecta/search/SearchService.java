package br.com.pandecta.search;

import br.com.pandecta.common.PageResult;
import br.com.pandecta.common.Texts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Montagem das consultas ao OpenSearch para legislação e jurisprudência, conversão em cards
 * e explicação objetiva da relevância de cada resultado.
 */
@Service
public class SearchService {
    public static final String HL_PRE = "\u0001";
    public static final String HL_POST = "\u0002";
    private static final Pattern HL_TERM = Pattern.compile("\u0001([^\u0002]{1,80})\u0002");

    public static final List<String> ABAS_LEGISLACAO = List.of("legislacao", "constituicao");
    public static final List<String> ABAS_JURIS = List.of("jurisprudencia", "stf", "stj", "tjs", "sumulas", "temas", "controle");

    private final SearchClient client;
    private final Indices indices;
    private final ObjectMapper mapper;

    public SearchService(SearchClient client, Indices indices) {
        this.client = client;
        this.indices = indices;
        this.mapper = client.mapper();
    }

    // ------------------------------------------------------------------ legislação

    public PageResult<Resultado> buscarDispositivos(String q, SearchFilters f, Citation citacao) {
        ObjectNode body = mapper.createObjectNode();
        body.put("from", f.pagina() * f.tamanho()).put("size", f.tamanho()).put("track_total_hits", true);
        ObjectNode bool = body.putObject("query").putObject("bool");
        textoDispositivo(bool, q);
        ArrayNode filter = bool.putArray("filter");
        if (!Boolean.FALSE.equals(f.somenteVigentes())) term(filter, "vigente", true);
        String normaId = "constituicao".equals(f.aba()) ? "cf1988" : f.normaId();
        if (normaId != null) term(filter, "normaId", normaId);
        if (f.normaTipo() != null) term(filter, "normaTipo", f.normaTipo());
        if (citacao != null && citacao.normaId() != null && normaId == null) {
            bool.withArray("should").addObject().putObject("term").putObject("normaId")
                    .put("value", citacao.normaId()).put("boost", 3.0);
        }
        if (citacao != null && citacao.artigo() != null) {
            bool.withArray("should").addObject().putObject("term").putObject("artigo")
                    .put("value", citacao.artigo()).put("boost", 4.0);
        }
        highlight(body, "texto", "rotulo", "textoCaput", "normaTitulo");
        if ("data".equals(f.ordenacao())) {
            body.putArray("sort").addObject().putObject("sincronizadoEm").put("order", "desc");
        }
        JsonNode res = client.search(indices.dispositivos(), body);
        List<Resultado> itens = new ArrayList<>();
        for (JsonNode hit : res.path("hits").path("hits")) itens.add(cardDispositivo(hit, citacao));
        return PageResult.of(total(res), f.pagina(), f.tamanho(), itens);
    }

    private void textoDispositivo(ObjectNode bool, String q) {
        if (Texts.isBlank(q)) {
            bool.withArray("must").addObject().putObject("match_all");
            return;
        }
        ObjectNode mm = bool.withArray("must").addObject().putObject("multi_match");
        mm.put("query", q).put("type", "best_fields").put("minimum_should_match", "2<50%");
        ArrayNode fields = mm.putArray("fields");
        for (String fld : List.of("rotulo^4", "texto^3", "textoCaput^1.5", "normaTitulo^1.2", "hierarquia^1", "notasAlteracao^0.3")) fields.add(fld);
        bool.withArray("should").addObject().putObject("match_phrase").putObject("texto")
                .put("query", q).put("slop", 4).put("boost", 2.0);
    }

    public PageResult<Resultado> buscarNormas(String q, String tipo, int pagina, int tamanho) {
        ObjectNode body = mapper.createObjectNode();
        body.put("from", pagina * tamanho).put("size", tamanho).put("track_total_hits", true);
        ObjectNode bool = body.putObject("query").putObject("bool");
        if (Texts.isBlank(q)) bool.withArray("must").addObject().putObject("match_all");
        else {
            ObjectNode mm = bool.withArray("must").addObject().putObject("multi_match");
            mm.put("query", q).put("minimum_should_match", "2<60%");
            ArrayNode fields = mm.putArray("fields");
            for (String fld : List.of("apelido^4", "titulo^3", "ementa^2", "siglas^5", "numero^5")) fields.add(fld);
        }
        if (tipo != null) term(bool.withArray("filter"), "tipo", tipo);
        ArrayNode sort = body.putArray("sort");
        if (Texts.isBlank(q)) {
            sort.addObject().putObject("textoDisponivel").put("order", "desc");
            sort.addObject().putObject("dataPublicacao").put("order", "desc").put("missing", "_last");
        } else {
            sort.add("_score");
        }
        JsonNode res = client.search(indices.normas(), body);
        List<Resultado> itens = new ArrayList<>();
        for (JsonNode hit : res.path("hits").path("hits")) itens.add(cardNorma(hit));
        return PageResult.of(total(res), pagina, tamanho, itens);
    }

    // ------------------------------------------------------------------ jurisprudência

    public PageResult<Resultado> buscarJurisprudencia(String q, SearchFilters f, Citation citacao) {
        ObjectNode body = mapper.createObjectNode();
        body.put("from", f.pagina() * f.tamanho()).put("size", f.tamanho()).put("track_total_hits", true);
        ObjectNode bool = body.putObject("query").putObject("bool");
        textoJuris(bool, q);
        ArrayNode filter = bool.putArray("filter");
        aplicarFiltrosJuris(filter, f);
        if (citacao != null) {
            if (citacao.numeroProcessoCnj() != null) {
                bool.withArray("should").addObject().putObject("term").putObject("numeroProcesso")
                        .put("value", citacao.numeroProcessoCnj()).put("boost", 20.0);
            }
            if (citacao.sumulaNumero() != null) {
                ObjectNode b = bool.withArray("should").addObject().putObject("bool");
                b.withArray("filter").addObject().putObject("term").put("categoria", "SUMULA");
                b.withArray("filter").addObject().putObject("term").put("numeroRegistro", citacao.sumulaNumero());
                b.put("boost", 20.0);
            }
            if (citacao.temaNumero() != null) {
                bool.withArray("should").addObject().putObject("term").putObject("tema")
                        .put("value", citacao.temaNumero()).put("boost", 20.0);
            }
        }
        highlight(body, "ementa", "tese", "inteiroTeor", "referenciasLegislativas");
        ArrayNode sort = body.putArray("sort");
        switch (f.ordenacao()) {
            case "data" -> {
                sort.addObject().putObject("dataPublicacao").put("order", "desc").put("missing", "_last");
                sort.addObject().putObject("dataJulgamento").put("order", "desc").put("missing", "_last");
            }
            case "tribunal" -> {
                sort.addObject().putObject("tribunal").put("order", "asc");
                sort.add("_score");
            }
            default -> sort.add("_score");
        }
        ObjectNode aggs = body.putObject("aggs");
        aggs.putObject("tribunal").putObject("terms").put("field", "tribunal").put("size", 40);
        aggs.putObject("categoria").putObject("terms").put("field", "categoria").put("size", 20);
        aggs.putObject("classe").putObject("terms").put("field", "classe.kw").put("size", 20);
        aggs.putObject("uf").putObject("terms").put("field", "uf").put("size", 30);

        JsonNode res = client.search(indices.jurisprudencia(), body);
        List<Resultado> itens = new ArrayList<>();
        for (JsonNode hit : res.path("hits").path("hits")) itens.add(cardDecisao(hit, citacao));
        Map<String, List<PageResult.Facet>> facetas = new LinkedHashMap<>();
        res.path("aggregations").fields().forEachRemaining(e -> {
            List<PageResult.Facet> lista = new ArrayList<>();
            for (JsonNode bk : e.getValue().path("buckets")) lista.add(new PageResult.Facet(bk.path("key").asText(), bk.path("doc_count").asLong()));
            facetas.put(e.getKey(), lista);
        });
        return new PageResult<>(total(res), f.pagina(), f.tamanho(), itens, facetas);
    }

    private void textoJuris(ObjectNode bool, String q) {
        if (Texts.isBlank(q)) {
            bool.withArray("must").addObject().putObject("match_all");
            return;
        }
        ObjectNode mm = bool.withArray("must").addObject().putObject("multi_match");
        mm.put("query", q).put("type", "best_fields").put("minimum_should_match", "2<45%");
        ArrayNode fields = mm.putArray("fields");
        for (String fld : List.of("ementa^3", "tese^3", "assuntos^2", "referenciasLegislativas^1.5", "inteiroTeor^1",
                "classe^0.5", "orgaoJulgador^0.3", "relator^0.5")) fields.add(fld);
        bool.withArray("should").addObject().putObject("match_phrase").putObject("ementa")
                .put("query", q).put("slop", 5).put("boost", 2.0);
    }

    public void aplicarFiltrosJuris(ArrayNode filter, SearchFilters f) {
        switch (f.aba()) {
            case "stf" -> term(filter, "tribunal", "STF");
            case "stj" -> term(filter, "tribunal", "STJ");
            case "tjs" -> term(filter, "segmento", "ESTADUAL");
            case "sumulas" -> term(filter, "categoria", "SUMULA");
            case "temas" -> terms(filter, "categoria", List.of("TEMA_REPETITIVO", "REPERCUSSAO_GERAL"));
            case "controle" -> term(filter, "categoria", "CONTROLE_CONCENTRADO");
            default -> { }
        }
        if (!f.tribunais().isEmpty()) terms(filter, "tribunal", f.tribunais());
        if (f.uf() != null) term(filter, "uf", f.uf().toUpperCase());
        if (f.numeroProcesso() != null) {
            String cnj = CitationParser.normalizarCnj(f.numeroProcesso());
            term(filter, "numeroProcesso", cnj != null ? cnj : f.numeroProcesso());
        }
        if (f.tipoDecisao() != null) term(filter, "tipoDecisao", f.tipoDecisao());
        matchFilter(filter, "relator", f.relator());
        matchFilter(filter, "orgaoJulgador", f.orgaoJulgador());
        matchFilter(filter, "classe", f.classe());
        matchFilter(filter, "assuntos", f.assunto());
        matchFilter(filter, "referenciasLegislativas", f.legislacao());
        range(filter, "dataJulgamento", f.dataJulgamentoInicio(), f.dataJulgamentoFim());
        range(filter, "dataPublicacao", f.dataPublicacaoInicio(), f.dataPublicacaoFim());
    }

    /** Contagem de resultados por aba (usada nos rótulos das abas). */
    public Map<String, Long> contarAbas(String q) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (String aba : ABAS_LEGISLACAO) {
            ObjectNode body = mapper.createObjectNode();
            ObjectNode bool = body.putObject("query").putObject("bool");
            textoDispositivo(bool, q);
            bool.remove("should");
            term(bool.putArray("filter"), "vigente", true);
            if ("constituicao".equals(aba)) term(bool.withArray("filter"), "normaId", "cf1988");
            out.put(aba, client.count(indices.dispositivos(), body));
        }
        for (String aba : ABAS_JURIS) {
            ObjectNode body = mapper.createObjectNode();
            ObjectNode bool = body.putObject("query").putObject("bool");
            textoJuris(bool, q);
            bool.remove("should");
            aplicarFiltrosJuris(bool.putArray("filter"), SearchFilters.simples(aba, 0, 1));
            out.put(aba, client.count(indices.jurisprudencia(), body));
        }
        out.put("doutrina", 0L);
        return out;
    }

    // ------------------------------------------------------------------ cards

    public Resultado cardDispositivo(JsonNode hit, Citation citacao) {
        JsonNode s = hit.path("_source");
        String trecho = fragmento(hit, "texto", s.path("texto").asText(""));
        List<String> termos = termos(hit);
        StringBuilder rel = new StringBuilder();
        if (citacao != null && citacao.artigo() != null && citacao.artigo().equalsIgnoreCase(s.path("artigo").asText())) {
            rel.append("Dispositivo correspondente ao artigo citado na pesquisa. ");
        }
        if (!termos.isEmpty()) rel.append("Termos da pesquisa presentes no texto legal: ").append(String.join(", ", termos)).append('.');
        if (rel.isEmpty()) rel.append("Proximidade textual com a pesquisa (análise léxica).");
        String hierarquia = s.path("hierarquia").asText("");
        return new Resultado("dispositivo", s.path("id").asText(),
                s.path("normaTitulo").asText() + " — " + s.path("rotulo").asText(),
                hierarquia.isBlank() ? null : hierarquia,
                s.path("fonte").asText(), s.path("fonteNome").asText(), null,
                null, trecho, termos, rel.toString().trim(), hit.path("_score").asDouble(0),
                s.path("urlOficial").asText(null), true, null, s.path("vigente").asBoolean(true), null,
                s.path("normaId").asText());
    }

    public Resultado cardNorma(JsonNode hit) {
        JsonNode s = hit.path("_source");
        String titulo = s.path("apelido").asText("").isBlank() ? s.path("titulo").asText()
                : s.path("apelido").asText() + " (" + s.path("titulo").asText() + ")";
        return new Resultado("norma", s.path("id").asText(), titulo,
                s.path("situacao").asText(null),
                s.path("fonte").asText(), s.path("fonteNome").asText(), null,
                s.path("dataPublicacao").asText(null), Texts.truncate(s.path("ementa").asText(""), 400), List.of(),
                s.path("textoDisponivel").asBoolean(false) ? "Texto integral disponível no leitor."
                        : "Somente metadados (ementa/identificação). Consulte o texto na fonte oficial.",
                hit.path("_score").asDouble(0), s.path("urlOficial").asText(null), true, null, null,
                s.path("tipo").asText(null), s.path("id").asText());
    }

    public Resultado cardDecisao(JsonNode hit, Citation citacao) {
        JsonNode s = hit.path("_source");
        String campoTrecho = hit.path("highlight").has("ementa") ? "ementa"
                : hit.path("highlight").has("tese") ? "tese"
                : hit.path("highlight").has("inteiroTeor") ? "inteiroTeor" : "ementa";
        String base = s.path("ementa").asText("");
        if (base.isBlank()) base = s.path("inteiroTeor").asText("");
        String trecho = fragmento(hit, campoTrecho, base);
        List<String> termos = termos(hit);
        StringBuilder rel = new StringBuilder();
        if (citacao != null && citacao.numeroProcessoCnj() != null && citacao.numeroProcessoCnj().equals(s.path("numeroProcesso").asText())) {
            rel.append("Número do processo informado na pesquisa. ");
        }
        if (!termos.isEmpty()) {
            rel.append("Termos encontrados ").append(switch (campoTrecho) {
                case "tese" -> "na tese";
                case "inteiroTeor" -> "no inteiro teor";
                default -> "na ementa";
            }).append(": ").append(String.join(", ", termos)).append('.');
        }
        if (rel.isEmpty()) rel.append("Proximidade textual com a pesquisa (análise léxica).");
        String classe = s.path("classe").asText("");
        String numero = s.path("numeroProcesso").asText("");
        String titulo = (s.path("tribunal").asText() + " — " + (classe.isBlank() ? "" : classe + " ") + numero).trim();
        String sub = String.join(" · ", nonBlank(
                s.path("orgaoJulgador").asText(""),
                s.path("relator").asText("").isBlank() ? "" : "Rel. " + s.path("relator").asText()));
        String data = s.path("dataPublicacao").asText(s.path("dataJulgamento").asText(null));
        return new Resultado("decisao", s.path("id").asText(), titulo, sub.isBlank() ? null : sub,
                s.path("fonte").asText(), s.path("fonteNome").asText(), s.path("tribunal").asText(),
                data, trecho, termos, rel.toString().trim(), hit.path("_score").asDouble(0),
                s.path("urlOficial").asText(null), true, s.path("statusPublicacao").asText(null), null,
                s.path("categoria").asText(null), null);
    }

    // ------------------------------------------------------------------ helpers

    public static long total(JsonNode res) {
        JsonNode t = res.path("hits").path("total");
        return t.isNumber() ? t.asLong() : t.path("value").asLong(0);
    }

    private void highlight(ObjectNode body, String... campos) {
        ObjectNode hl = body.putObject("highlight");
        hl.putArray("pre_tags").add(HL_PRE);
        hl.putArray("post_tags").add(HL_POST);
        ObjectNode fields = hl.putObject("fields");
        for (String c : campos) fields.putObject(c).put("fragment_size", 260).put("number_of_fragments", 2);
    }

    private static String fragmento(JsonNode hit, String campo, String fallback) {
        JsonNode frags = hit.path("highlight").path(campo);
        if (frags.isArray() && !frags.isEmpty()) {
            List<String> partes = new ArrayList<>();
            frags.forEach(fr -> partes.add(fr.asText()));
            return String.join(" … ", partes);
        }
        return Texts.truncate(fallback, 320);
    }

    private static List<String> termos(JsonNode hit) {
        Set<String> out = new LinkedHashSet<>();
        hit.path("highlight").fields().forEachRemaining(e -> e.getValue().forEach(fr -> {
            Matcher m = HL_TERM.matcher(fr.asText());
            while (m.find() && out.size() < 8) out.add(m.group(1).toLowerCase());
        }));
        return List.copyOf(out);
    }

    private static List<String> nonBlank(String... v) {
        List<String> out = new ArrayList<>();
        for (String s : v) if (s != null && !s.isBlank()) out.add(s);
        return out;
    }

    public static void term(ArrayNode filter, String campo, Object valor) {
        ObjectNode t = filter.addObject().putObject("term");
        if (valor instanceof Boolean b) t.put(campo, b);
        else t.put(campo, String.valueOf(valor));
    }

    public static void terms(ArrayNode filter, String campo, List<String> valores) {
        ArrayNode arr = filter.addObject().putObject("terms").putArray(campo);
        valores.forEach(arr::add);
    }

    private static void matchFilter(ArrayNode filter, String campo, String valor) {
        if (valor == null) return;
        filter.addObject().putObject("match").putObject(campo).put("query", valor).put("operator", "and");
    }

    public static void range(ArrayNode filter, String campo, java.time.LocalDate de, java.time.LocalDate ate) {
        if (de == null && ate == null) return;
        ObjectNode r = filter.addObject().putObject("range").putObject(campo);
        if (de != null) r.put("gte", de.toString());
        if (ate != null) r.put("lte", ate.toString());
    }
}
