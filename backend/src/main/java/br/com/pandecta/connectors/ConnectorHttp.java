package br.com.pandecta.connectors;

import br.com.pandecta.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP para conectores: identifica o robô (User-Agent), respeita limites (429/Retry-After),
 * repete com backoff exponencial em falhas transitórias e diferencia "fonte indisponível" de erro de dados.
 */
@Component
public class ConnectorHttp {
    private static final Logger log = LoggerFactory.getLogger(ConnectorHttp.class);
    private static final Pattern CHARSET = Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE);

    private final HttpClient http;
    private final String userAgent;

    public ConnectorHttp(HttpClient http, AppProperties props) {
        this.http = http;
        this.userAgent = props.userAgent();
    }

    /** Erro indicando que a fonte não respondeu adequadamente (rede, 5xx, 429 persistente). */
    public static class FonteIndisponivelException extends IOException {
        public final int status;
        public FonteIndisponivelException(String msg, int status) {
            super(msg);
            this.status = status;
        }
    }

    public record Resposta(int status, byte[] corpo, String contentType) {
        public String texto() {
            Charset cs = StandardCharsets.UTF_8;
            if (contentType != null) {
                Matcher m = CHARSET.matcher(contentType);
                if (m.find()) {
                    try { cs = Charset.forName(m.group(1)); } catch (Exception ignored) { }
                }
            }
            return new String(corpo, cs);
        }
    }

    public Resposta get(String url, Map<String, String> headers) throws IOException {
        return enviar(builder(url, headers).GET().build(), 4);
    }

    public Resposta postJson(String url, String json, Map<String, String> headers) throws IOException {
        HttpRequest req = builder(url, headers).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
        return enviar(req, 4);
    }

    /** Baixa um arquivo grande direto para o disco (evita carregar em memória). */
    public Path download(String url, Path destino) throws IOException {
        HttpRequest req = builder(url, Map.of()).timeout(Duration.ofMinutes(15)).GET().build();
        try {
            HttpResponse<Path> res = http.send(req, HttpResponse.BodyHandlers.ofFile(destino));
            if (res.statusCode() >= 400) throw new FonteIndisponivelException("HTTP " + res.statusCode() + " ao baixar " + url, res.statusCode());
            return res.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrompido", e);
        }
    }

    private HttpRequest.Builder builder(String url, Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", userAgent)
                .header("Accept-Language", "pt-BR,pt;q=0.9");
        headers.forEach(b::header);
        return b;
    }

    private Resposta enviar(HttpRequest req, int tentativas) throws IOException {
        long espera = 2000;
        IOException ultimo = null;
        for (int i = 1; i <= tentativas; i++) {
            try {
                HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
                int st = res.statusCode();
                if (st == 429 || st >= 500) {
                    long retry = res.headers().firstValue("Retry-After").map(v -> {
                        try { return Long.parseLong(v.trim()) * 1000; } catch (NumberFormatException e) { return -1L; }
                    }).orElse(-1L);
                    ultimo = new FonteIndisponivelException("HTTP " + st + " em " + req.uri().getHost(), st);
                    dormir(retry > 0 ? Math.min(retry, 60_000) : espera);
                    espera *= 2;
                    continue;
                }
                return new Resposta(st, res.body(), res.headers().firstValue("Content-Type").orElse(null));
            } catch (IOException e) {
                ultimo = new FonteIndisponivelException("Falha de rede em " + req.uri().getHost() + ": " + e.getMessage(), 0);
                log.debug("Tentativa {}/{} falhou para {}: {}", i, tentativas, req.uri(), e.getMessage());
                dormir(espera);
                espera *= 2;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrompido", e);
            }
        }
        throw ultimo;
    }

    /** Pausa educada entre requisições sequenciais à mesma fonte. */
    public static void pausa(long ms) {
        dormir(ms);
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
