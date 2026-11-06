package br.com.pandecta.api;

import br.com.pandecta.common.PageResult;
import br.com.pandecta.common.Texts;
import br.com.pandecta.connectors.SourceStatusRepository;
import br.com.pandecta.connectors.datajud.DataJudConnector;
import br.com.pandecta.connectors.djen.DjenConnector;
import br.com.pandecta.connectors.stj.StjDadosAbertosConnector;
import br.com.pandecta.search.Indices;
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

/** Radar Jurídico: eventos recentes (publicações e julgamentos) com filtros e horário da última sincronização. */
@RestController
@RequestMapping("/api/radar")
public class RadarController {
    private final SearchClient client;
    private final Indices indices;
    private final SourceStatusRepository status;
    private final ObjectMapper mapper;

    public RadarController(SearchClient client, Indices indices, SourceStatusRepository status) {
        this.client = client;
        this.indices = indices;
        this.status = status;
        this.mapper = client.mapper();
    }

    @GetMapping
    public Map<String, Object> radar(@RequestParam(required = false) String tribunal,
                                     @RequestParam(required = false) String uf,
                                     @RequestParam(required = false) String assunto,
                                     @RequestParam(required = false) String tipo,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
                                     @RequestParam(defaultValue = "0") int pagina,
                                     @RequestParam(defaultValue = "25") int tamanho) {
        int tam = Math.max(1, Math.min(tamanho, 50));
        int pag = Math.max(0, Math.min(pagina, 200));
        ObjectNode q = mapper.createObjectNode();
        q.put("from", pag * tam).put("size", tam).put("track_total_hits", true);
        ObjectNode bool = q.putObject("query").putObject("bool");
        ArrayNode f = bool.putArray("filter");
        if (!Texts.isBlank(tribunal)) SearchService.term(f, "tribunal", Texts.userInput(tribunal, 20).toUpperCase());
        if (!Texts.isBlank(uf)) SearchService.term(f, "uf", Texts.userInput(uf, 2).toUpperCase());
        if (!Texts.isBlank(tipo)) SearchService.term(f, "tipoEvento", Texts.userInput(tipo, 40));
        if (!Texts.isBlank(status)) SearchService.term(f, "statusPublicacao", Texts.userInput(status, 20));
        SearchService.range(f, "dataEvento", inicio, fim);
        if (!Texts.isBlank(assunto)) {
            ObjectNode mm = bool.withArray("must").addObject().putObject("multi_match");
            mm.put("query", Texts.userInput(assunto, 200)).put("operator", "and");
            mm.putArray("fields").add("assuntos").add("titulo").add("resumo").add("ementa");
        }
        ArrayNode sort = q.putArray("sort");
        sort.addObject().putObject("dataEvento").put("order", "desc");
        sort.addObject().putObject("sincronizadoEm").put("order", "desc");
        JsonNode res = client.search(indices.radar(), q);
        List<JsonNode> itens = new ArrayList<>();
        res.path("hits").path("hits").forEach(h -> itens.add(h.path("_source")));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ultimaSincronizacao", ultimasSincronizacoes());
        out.put("itens", PageResult.of(SearchService.total(res), pag, tam, itens));
        return out;
    }

    public Map<String, Object> ultimasSincronizacoes() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String[] f : List.of(new String[]{DjenConnector.ID, "DJEN"}, new String[]{StjDadosAbertosConnector.ID, "STJ"},
                new String[]{DataJudConnector.ID, "DataJud"})) {
            Object ts = status.obter(f[0]).get("ultimoSucesso");
            if (ts != null) m.put(f[1], ts);
        }
        return m;
    }
}
