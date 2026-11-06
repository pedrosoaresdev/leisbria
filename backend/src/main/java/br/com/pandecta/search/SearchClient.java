package br.com.pandecta.search;

import br.com.pandecta.common.SearchUnavailableException;
import br.com.pandecta.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cliente REST mínimo para OpenSearch 2.x / Elasticsearch 8.x.
 * Todas as consultas são montadas como JSON estruturado (nunca por concatenação de strings
 * com entrada do usuário), o que elimina injeção na linguagem de consulta.
 */
@Component
public class SearchClient {
    private static final Logger log = LoggerFactory.getLogger(SearchClient.class);

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String authHeader;

    public SearchClient(HttpClient http, ObjectMapper mapper, AppProperties props) {
        this.http = http;
        this.mapper = mapper;
        String url = props.search().url();
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        String user = props.search().username();
        this.authHeader = (user != null && !user.isBlank())
                ? "Basic " + Base64.getEncoder().encodeToString((user + ":" + props.search().password()).getBytes(StandardCharsets.UTF_8))
                : null;
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public record Response(int status, JsonNode body) {
        public boolean ok() { return status >= 200 && status < 300; }
    }

    public Response call(String method, String path, Object body, String contentType) {
        try {
            HttpRequest.BodyPublisher pub;
            if (body == null) {
                pub = HttpRequest.BodyPublishers.noBody();
            } else if (body instanceof String s) {
                pub = HttpRequest.BodyPublishers.ofString(s, StandardCharsets.UTF_8);
            } else {
                pub = HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body));
            }
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", contentType)
                    .header("Accept", "application/json")
                    .method(method, pub);
            if (authHeader != null) b.header("Authorization", authHeader);
            HttpResponse<byte[]> res = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            JsonNode node = res.body() == null || res.body().length == 0 ? mapper.createObjectNode() : mapper.readTree(res.body());
            return new Response(res.statusCode(), node);
        } catch (IOException e) {
            throw new SearchUnavailableException("Falha de comunicação com o motor de busca: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SearchUnavailableException("Interrompido", e);
        }
    }

    public Response call(String method, String path, Object body) {
        return call(method, path, body, "application/json");
    }

    public JsonNode search(String index, ObjectNode query) {
        Response r = call("POST", "/" + index + "/_search", query);
        if (r.status() == 404) {
            return mapper.createObjectNode().set("hits", mapper.createObjectNode().put("total", 0).set("hits", mapper.createArrayNode()));
        }
        if (!r.ok()) {
            throw new SearchUnavailableException("Consulta rejeitada (" + r.status() + "): " + shortErr(r.body()));
        }
        return r.body();
    }

    public long count(String index, ObjectNode query) {
        Response r = call("POST", "/" + index + "/_count", query);
        return r.ok() ? r.body().path("count").asLong(0) : 0;
    }

    public Optional<JsonNode> get(String index, String id) {
        Response r = call("GET", "/" + index + "/_doc/" + enc(id), null);
        if (r.status() == 404 || !r.body().path("found").asBoolean(false)) return Optional.empty();
        if (!r.ok()) throw new SearchUnavailableException("Falha ao obter documento: " + shortErr(r.body()));
        return Optional.of(r.body().path("_source"));
    }

    /**
     * Indexação em lote. Retorna quantos documentos foram gravados com sucesso.
     * Usa o id determinístico do documento → reprocessar a mesma fonte não gera duplicidade.
     */
    public int bulkIndex(String index, List<Map.Entry<String, Object>> docs) {
        if (docs.isEmpty()) return 0;
        StringBuilder sb = new StringBuilder();
        try {
            for (Map.Entry<String, Object> d : docs) {
                ObjectNode action = mapper.createObjectNode();
                action.putObject("index").put("_index", index).put("_id", d.getKey());
                sb.append(mapper.writeValueAsString(action)).append('\n');
                sb.append(mapper.writeValueAsString(d.getValue())).append('\n');
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Response r = call("POST", "/_bulk?refresh=false", sb.toString(), "application/x-ndjson");
        if (!r.ok()) throw new SearchUnavailableException("Bulk rejeitado (" + r.status() + "): " + shortErr(r.body()));
        int ok = 0;
        for (JsonNode item : r.body().path("items")) {
            JsonNode res = item.path("index");
            int st = res.path("status").asInt(500);
            if (st >= 200 && st < 300) ok++;
            else log.warn("Falha ao indexar {}: {}", res.path("_id").asText(), res.path("error").toString());
        }
        return ok;
    }

    public void refresh(String index) {
        call("POST", "/" + index + "/_refresh", null);
    }

    public boolean ping() {
        try {
            return call("GET", "/", null).ok();
        } catch (SearchUnavailableException e) {
            return false;
        }
    }

    public static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String shortErr(JsonNode body) {
        String s = body.path("error").toString();
        return s.length() > 400 ? s.substring(0, 400) : s;
    }
}
