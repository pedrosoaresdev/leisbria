package br.com.pandecta.api;

import br.com.pandecta.common.ApiException;
import br.com.pandecta.common.Texts;
import br.com.pandecta.connectors.FontesPendentes;
import br.com.pandecta.connectors.SourceConnector;
import br.com.pandecta.connectors.SourceStatusRepository;
import br.com.pandecta.connectors.SyncOrchestrator;
import br.com.pandecta.connectors.lexml.LexmlConnector;
import br.com.pandecta.search.Indices;
import br.com.pandecta.search.SearchClient;
import br.com.pandecta.search.SearchService;
import br.com.pandecta.security.AuthUser;
import br.com.pandecta.security.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Página inicial, status das fontes e disparo manual de sincronização (administradores). */
@RestController
@RequestMapping("/api")
public class InicioFontesController {
    private final SearchClient client;
    private final Indices indices;
    private final SyncOrchestrator orquestrador;
    private final SourceStatusRepository status;
    private final RadarController radar;
    private final ObjectMapper mapper;

    public InicioFontesController(SearchClient client, Indices indices, SyncOrchestrator orquestrador,
                                  SourceStatusRepository status, RadarController radar) {
        this.client = client;
        this.indices = indices;
        this.orquestrador = orquestrador;
        this.status = status;
        this.radar = radar;
        this.mapper = client.mapper();
    }

    @GetMapping("/inicio")
    @Cacheable(value = "inicio", key = "'home'")
    public Map<String, Object> inicio() {
        Map<String, Object> out = new LinkedHashMap<>();
        // Últimas decisões publicadas (com documento na base)
        ObjectNode q = mapper.createObjectNode();
        q.put("size", 8);
        ArrayNode f = q.putObject("query").putObject("bool").putArray("filter");
        SearchService.term(f, "statusPublicacao", "PUBLICADA");
        f.addObject().putObject("exists").put("field", "documentoId");
        q.putArray("sort").addObject().putObject("dataEvento").put("order", "desc");
        out.put("ultimasDecisoes", itensRadar(client.search(indices.radar(), q)));

        // Relevantes do dia: súmulas, temas, repercussão geral, controle concentrado publicados desde ontem
        ObjectNode r = mapper.createObjectNode();
        r.put("size", 6);
        ArrayNode fr = r.putObject("query").putObject("bool").putArray("filter");
        SearchService.terms(fr, "categoria", List.of("SUMULA", "TEMA_REPETITIVO", "REPERCUSSAO_GERAL", "CONTROLE_CONCENTRADO"));
        SearchService.range(fr, "dataPublicacao", LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(1), null);
        r.putArray("sort").addObject().putObject("dataPublicacao").put("order", "desc");
        List<Map<String, Object>> relevantes = new ArrayList<>();
        for (JsonNode h : client.search(indices.jurisprudencia(), r).path("hits").path("hits")) {
            JsonNode s = h.path("_source");
            relevantes.add(item(s.path("id").asText(), "decisao", s.path("tribunal").asText(),
                    (s.path("classe").asText("") + " " + s.path("numeroProcesso").asText("")).trim(),
                    s.path("dataPublicacao").asText(null), Texts.truncate(s.path("ementa").asText(s.path("tese").asText("")), 240),
                    s.path("statusPublicacao").asText(null), false));
        }
        out.put("relevantesDoDia", relevantes);

        // Atualizações legislativas (LexML)
        ObjectNode l = mapper.createObjectNode();
        l.put("size", 8);
        l.putObject("query").putObject("term").put("fonte", LexmlConnector.ID);
        l.putArray("sort").addObject().putObject("dataPublicacao").put("order", "desc").put("missing", "_last");
        List<Map<String, Object>> leis = new ArrayList<>();
        for (JsonNode h : client.search(indices.normas(), l).path("hits").path("hits")) {
            JsonNode s = h.path("_source");
            Map<String, Object> it = item(s.path("id").asText(), "norma", null, s.path("titulo").asText(),
                    s.path("dataPublicacao").asText(null), Texts.truncate(s.path("ementa").asText(""), 240), null, false);
            it.put("urlOficial", s.path("urlOficial").asText(null));
            leis.add(it);
        }
        out.put("atualizacoesLegislativas", leis);

        Map<String, Object> sinc = new LinkedHashMap<>();
        radar.ultimasSincronizacoes().values().stream().map(String::valueOf).max(String::compareTo).ifPresent(v -> sinc.put("radar", v));
        Object lex = status.obter(LexmlConnector.ID).get("ultimoSucesso");
        if (lex != null) sinc.put("lexml", lex);
        out.put("sincronizacao", sinc);
        return out;
    }

    private List<Map<String, Object>> itensRadar(JsonNode res) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode h : res.path("hits").path("hits")) {
            JsonNode s = h.path("_source");
            out.add(item(s.path("documentoId").asText(), "decisao", s.path("tribunal").asText(), s.path("titulo").asText(),
                    s.path("dataEvento").asText(null), s.path("resumo").asText(""), s.path("statusPublicacao").asText(null),
                    s.path("horarioDisponivel").asBoolean(false)));
        }
        return out;
    }

    private static Map<String, Object> item(String id, String tipo, String tribunal, String titulo, String data, String trecho,
                                            String status, boolean horario) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("tipo", tipo);
        if (tribunal != null && !tribunal.isBlank()) m.put("tribunal", tribunal);
        m.put("titulo", titulo);
        m.put("data", data);
        m.put("trecho", trecho);
        if (status != null) m.put("statusPublicacao", status);
        m.put("horarioDisponivel", horario);
        return m;
    }

    @GetMapping("/fontes")
    public List<Map<String, Object>> fontes() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SourceConnector c : orquestrador.conectores().values()) {
            Map<String, Object> st = status.obter(c.id());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id());
            m.put("nome", c.nome());
            m.put("descricao", c.descricao());
            m.put("url", c.url());
            m.put("frequencia", c.frequencia());
            String estado = !c.configurado() ? "NAO_CONFIGURADA"
                    : orquestrador.emExecucao(c.id()) ? "SINCRONIZANDO"
                    : String.valueOf(st.getOrDefault("estado", "ATIVA"));
            m.put("estado", estado);
            if (!c.configurado()) m.put("requisitos", c.requisitos());
            m.put("ultimaSincronizacao", st.get("ultimaSincronizacao"));
            m.put("ultimoSucesso", st.get("ultimoSucesso"));
            if (st.get("ultimoErro") != null && !"ATIVA".equals(estado)) m.put("ultimoErro", st.get("ultimoErro"));
            m.put("documentos", contarDocumentos(c.id()));
            out.add(m);
        }
        for (FontesPendentes.Pendente p : FontesPendentes.LISTA) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.id());
            m.put("nome", p.nome());
            m.put("descricao", p.descricao());
            m.put("url", p.url());
            m.put("estado", "PENDENTE");
            m.put("requisitos", p.requisitos());
            out.add(m);
        }
        return out;
    }

    private long contarDocumentos(String fonte) {
        ObjectNode q = mapper.createObjectNode();
        q.putObject("query").putObject("term").put("fonte", fonte);
        long total = 0;
        for (String idx : List.of(indices.dispositivos(), indices.jurisprudencia(), indices.processos())) {
            total += client.count(idx, q);
        }
        if ("lexml".equals(fonte)) total += client.count(indices.normas(), q);
        return total;
    }

    @PostMapping("/admin/sync/{fonteId}")
    public Map<String, Object> sincronizar(@PathVariable String fonteId, @CurrentUser AuthUser user) {
        if (!user.admin()) throw new ApiException(HttpStatus.FORBIDDEN, "Acesso restrito a administradores.");
        if (orquestrador.conector(fonteId).isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "Fonte desconhecida.");
        boolean iniciou = orquestrador.disparar(fonteId);
        return Map.of("iniciada", iniciou, "mensagem", iniciou ? "Sincronização iniciada." : "Esta fonte já está sincronizando.");
    }
}
