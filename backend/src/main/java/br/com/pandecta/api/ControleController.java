package br.com.pandecta.api;

import br.com.pandecta.common.PageResult;
import br.com.pandecta.common.Texts;
import br.com.pandecta.search.Indices;
import br.com.pandecta.search.Resultado;
import br.com.pandecta.search.SearchClient;
import br.com.pandecta.search.SearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controle de constitucionalidade: ações de controle concentrado (inclusive ADIs estaduais nos TJs),
 * mandados de injunção, súmulas vinculantes, repercussão geral e recursos repetitivos presentes na base.
 */
@RestController
@RequestMapping("/api/controle")
public class ControleController {
    private static final Map<String, String> CLASSES = new LinkedHashMap<>();
    static {
        CLASSES.put("ADI", "Ação Direta de Inconstitucionalidade");
        CLASSES.put("ADC", "Ação Declaratória de Constitucionalidade");
        CLASSES.put("ADPF", "Arguição de Descumprimento de Preceito Fundamental");
        CLASSES.put("ADO", "Ação Direta de Inconstitucionalidade por Omissão");
        CLASSES.put("MI", "Mandado de Injunção");
    }

    private final SearchClient client;
    private final Indices indices;
    private final SearchService search;
    private final ObjectMapper mapper;

    public ControleController(SearchClient client, Indices indices, SearchService search) {
        this.client = client;
        this.indices = indices;
        this.search = search;
        this.mapper = client.mapper();
    }

    @GetMapping
    public PageResult<Resultado> listar(@RequestParam(required = false) String classe,
                                        @RequestParam(required = false) String numero,
                                        @RequestParam(required = false) String relator,
                                        @RequestParam(required = false) String dispositivo,
                                        @RequestParam(required = false) String lei,
                                        @RequestParam(required = false) String assunto,
                                        @RequestParam(required = false) String uf,
                                        @RequestParam(required = false) String resultado,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
                                        @RequestParam(defaultValue = "0") int pagina,
                                        @RequestParam(defaultValue = "20") int tamanho) {
        int tam = Math.max(1, Math.min(tamanho, 50));
        int pag = Math.max(0, Math.min(pagina, 200));
        ObjectNode q = mapper.createObjectNode();
        q.put("from", pag * tam).put("size", tam).put("track_total_hits", true);
        ObjectNode bool = q.putObject("query").putObject("bool");
        ArrayNode f = bool.putArray("filter");

        String c = classe == null ? null : Texts.userInput(classe, 10).toUpperCase();
        if (c != null && CLASSES.containsKey(c)) {
            f.addObject().putObject("match_phrase").put("classe", CLASSES.get(c));
        } else if ("SV".equals(c)) {
            SearchService.term(f, "categoria", "SUMULA");
            f.addObject().putObject("match").putObject("classe").put("query", "vinculante");
        } else if ("RG".equals(c)) {
            SearchService.term(f, "categoria", "REPERCUSSAO_GERAL");
        } else if ("REP".equals(c)) {
            SearchService.term(f, "categoria", "TEMA_REPETITIVO");
        } else {
            ObjectNode qualquer = f.addObject().putObject("bool");
            ArrayNode sh = qualquer.putArray("should");
            sh.addObject().putObject("terms").putArray("categoria").add("CONTROLE_CONCENTRADO").add("REPERCUSSAO_GERAL").add("TEMA_REPETITIVO");
            CLASSES.values().forEach(nome -> sh.addObject().putObject("match_phrase").put("classe", nome));
            qualquer.put("minimum_should_match", 1);
        }
        if (!Texts.isBlank(numero)) {
            f.addObject().putObject("wildcard").putObject("numeroProcesso").put("value", "*" + Texts.userInput(numero, 30).replaceAll("[^0-9.\\-]", "") + "*");
        }
        if (!Texts.isBlank(uf)) SearchService.term(f, "uf", Texts.userInput(uf, 2).toUpperCase());
        SearchService.range(f, "dataJulgamento", inicio, fim);
        ArrayNode must = bool.putArray("must");
        texto(must, "relator", relator);
        texto(must, "resultado", resultado);
        texto(must, "assuntos", assunto);
        for (String ref : new String[]{dispositivo, lei}) {
            if (Texts.isBlank(ref)) continue;
            ObjectNode mm = must.addObject().putObject("multi_match");
            mm.put("query", Texts.userInput(ref, 200)).put("type", "phrase").put("slop", 3);
            mm.putArray("fields").add("referenciasLegislativas").add("ementa").add("inteiroTeor");
        }
        if (must.isEmpty()) must.addObject().putObject("match_all");
        q.putArray("sort").addObject().putObject("dataJulgamento").put("order", "desc").put("missing", "_last");

        JsonNode res = client.search(indices.jurisprudencia(), q);
        List<Resultado> itens = new ArrayList<>();
        res.path("hits").path("hits").forEach(h -> itens.add(search.cardDecisao(h, null)));
        return PageResult.of(SearchService.total(res), pag, tam, itens);
    }

    private static void texto(ArrayNode must, String campo, String valor) {
        if (Texts.isBlank(valor)) return;
        must.addObject().putObject("match").putObject(campo).put("query", Texts.userInput(valor, 200)).put("operator", "and");
    }
}
