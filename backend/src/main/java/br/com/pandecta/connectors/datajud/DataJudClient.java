package br.com.pandecta.connectors.datajud;

import br.com.pandecta.common.ApiException;
import br.com.pandecta.config.AppProperties;
import br.com.pandecta.connectors.ConnectorHttp;
import br.com.pandecta.connectors.Tribunais;
import br.com.pandecta.search.CitationParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente da API Pública do DataJud (CNJ).
 * Endpoint: {base}/api_publica_{alias}/_search · Autenticação: "Authorization: APIKey {chave pública}".
 * Retorna apenas metadados (capa e movimentações); não há ementa nem inteiro teor.
 */
@Component
public class DataJudClient {
    private final ConnectorHttp http;
    private final ObjectMapper mapper;
    private final AppProperties.DataJud cfg;

    public DataJudClient(ConnectorHttp http, ObjectMapper mapper, AppProperties props) {
        this.http = http;
        this.mapper = mapper;
        this.cfg = props.datajud();
    }

    public boolean configurado() {
        return cfg.configured();
    }

    public JsonNode buscar(String alias, ObjectNode consulta) throws IOException {
        String url = cfg.baseUrl() + "/api_publica_" + alias + "/_search";
        ConnectorHttp.Resposta r = http.postJson(url, mapper.writeValueAsString(consulta),
                Map.of("Authorization", "APIKey " + cfg.apiKey(), "Accept", "application/json"));
        if (r.status() == 401 || r.status() == 403) {
            throw new IOException("DataJud recusou a chave (HTTP " + r.status() + "). A chave pública pode ter sido trocada pelo CNJ: atualize DATAJUD_API_KEY.");
        }
        if (r.status() == 404) throw new IOException("Alias DataJud inexistente: " + alias);
        if (r.status() >= 400) throw new IOException("DataJud HTTP " + r.status());
        return mapper.readTree(r.corpo());
    }

    /** Consulta processual por número CNJ (tempo real, com cache de 10 minutos). */
    @Cacheable(value = "datajud-processo", key = "#numero")
    public Map<String, Object> consultarProcesso(String numero) {
        if (!configurado()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Consulta ao DataJud não configurada (DATAJUD_API_KEY).");
        String cnj = CitationParser.normalizarCnj(numero);
        if (cnj == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Número de processo inválido. Use o padrão CNJ com 20 dígitos.");
        String digitos = cnj.replaceAll("\\D", "");
        String alias = Tribunais.aliasDataJud(digitos);
        if (alias == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "O tribunal deste processo não está disponível na API pública do DataJud (por exemplo, o STF não integra a base pública).");
        }
        ObjectNode q = mapper.createObjectNode();
        q.put("size", 5);
        q.putObject("query").putObject("match").put("numeroProcesso", digitos);
        JsonNode res;
        try {
            res = buscar(alias, q);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "DataJud indisponível no momento: " + e.getMessage());
        }
        JsonNode hits = res.path("hits").path("hits");
        if (!hits.isArray() || hits.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Processo não encontrado na base pública do DataJud (pode ser sigiloso ou ainda não enviado pelo tribunal).");
        }
        // Pode haver um registro por grau; usa o de atualização mais recente e junta as movimentações.
        JsonNode melhor = hits.get(0).path("_source");
        for (JsonNode h : hits) {
            if (h.path("_source").path("dataHoraUltimaAtualizacao").asText("").compareTo(melhor.path("dataHoraUltimaAtualizacao").asText("")) > 0) {
                melhor = h.path("_source");
            }
        }
        Map<String, Object> out = mapear(melhor, cnj);
        out.put("consultadoEm", Instant.now().toString());
        return out;
    }

    public Map<String, Object> mapear(JsonNode s, String cnjFormatado) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("numeroProcesso", cnjFormatado != null ? cnjFormatado : CitationParser.normalizarCnj(s.path("numeroProcesso").asText("")));
        out.put("tribunal", s.path("tribunal").asText(null));
        out.put("grau", s.path("grau").asText(null));
        out.put("classe", s.path("classe").path("nome").asText(null));
        out.put("classeCodigo", s.path("classe").path("codigo").asText(null));
        out.put("orgaoJulgador", s.path("orgaoJulgador").path("nome").asText(null));
        List<String> assuntos = new ArrayList<>();
        for (JsonNode a : s.path("assuntos")) {
            if (a.isArray()) a.forEach(x -> assuntos.add(x.path("nome").asText()));
            else assuntos.add(a.path("nome").asText());
        }
        out.put("assuntos", assuntos);
        out.put("dataAjuizamento", data(s.path("dataAjuizamento").asText(null)));
        out.put("dataHoraUltimaAtualizacao", s.path("dataHoraUltimaAtualizacao").asText(null));
        out.put("nivelSigilo", s.path("nivelSigilo").asInt(0));
        List<Map<String, Object>> movs = new ArrayList<>();
        for (JsonNode m : s.path("movimentos")) {
            Map<String, Object> mv = new LinkedHashMap<>();
            mv.put("codigo", m.path("codigo").asInt());
            mv.put("nome", m.path("nome").asText());
            mv.put("dataHora", m.path("dataHora").asText(null));
            List<String> compl = new ArrayList<>();
            for (JsonNode c : m.path("complementosTabelados")) compl.add(c.path("nome").asText(c.path("descricao").asText()));
            if (!compl.isEmpty()) mv.put("complemento", String.join("; ", compl));
            movs.add(mv);
        }
        movs.sort((a, b) -> String.valueOf(b.get("dataHora")).compareTo(String.valueOf(a.get("dataHora"))));
        out.put("movimentos", movs);
        return out;
    }

    /** DataJud usa formatos como "20180315000000" ou ISO; normaliza para yyyy-MM-dd quando possível. */
    public static String data(String v) {
        if (v == null || v.isBlank()) return null;
        if (v.matches("\\d{14}") || v.matches("\\d{8}")) return v.substring(0, 4) + "-" + v.substring(4, 6) + "-" + v.substring(6, 8);
        if (v.length() >= 10 && v.charAt(4) == '-') return v.substring(0, 10);
        return null;
    }
}
