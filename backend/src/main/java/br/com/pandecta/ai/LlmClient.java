package br.com.pandecta.ai;

import br.com.pandecta.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Cliente da API de mensagens da Anthropic (Claude). Isolado para permitir trocar de provedor
 * implementando o mesmo contrato (texto de sistema + mensagem do usuário → texto).
 */
@Component
public class LlmClient {
    private static final String URL = "https://api.anthropic.com/v1/messages";

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final AppProperties.Ai cfg;

    public LlmClient(HttpClient http, ObjectMapper mapper, AppProperties props) {
        this.http = http;
        this.mapper = mapper;
        this.cfg = props.ai();
    }

    public boolean habilitado() {
        return cfg.enabled();
    }

    public String modelo() {
        return cfg.model();
    }

    public String completar(String sistema, String usuario, int maxTokens) throws IOException {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", cfg.model());
        body.put("max_tokens", maxTokens);
        body.put("temperature", 0);
        body.put("system", sistema);
        ObjectNode msg = body.putArray("messages").addObject();
        msg.put("role", "user");
        msg.put("content", usuario);
        HttpRequest req = HttpRequest.newBuilder(URI.create(URL))
                .timeout(Duration.ofSeconds(90))
                .header("x-api-key", cfg.apiKey())
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new IOException("Provedor de IA respondeu HTTP " + res.statusCode());
            }
            JsonNode json = mapper.readTree(res.body());
            StringBuilder sb = new StringBuilder();
            for (JsonNode c : json.path("content")) if ("text".equals(c.path("type").asText())) sb.append(c.path("text").asText());
            return sb.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrompido", e);
        }
    }

    /** Extrai o primeiro objeto JSON da resposta (tolera cercas de código). */
    public JsonNode json(String texto) throws IOException {
        int ini = texto.indexOf('{');
        int fim = texto.lastIndexOf('}');
        if (ini < 0 || fim <= ini) throw new IOException("Resposta da IA sem JSON");
        return mapper.readTree(texto.substring(ini, fim + 1));
    }
}
