package br.com.pandecta.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Rate limiting por IP com token bucket, com limites distintos por grupo de rotas:
 * autenticação (anti força bruta), IA (custo alto) e demais rotas.
 * Em produção com várias instâncias, substitua por um limitador distribuído (Redis) ou pelo gateway/CDN.
 */
@Component
@Order(10)
public class RateLimitFilter extends OncePerRequestFilter {

    private record Limit(int capacidade, double recargaPorSegundo) {}

    private static final Limit AUTH = new Limit(10, 10.0 / 60);      // 10 por minuto
    private static final Limit IA = new Limit(8, 8.0 / 60);          // 8 por minuto
    private static final Limit GERAL = new Limit(120, 120.0 / 60);   // 120 por minuto

    private static final class Bucket {
        double tokens;
        long ultimo;
        Bucket(double t) { tokens = t; ultimo = System.nanoTime(); }

        synchronized boolean consumir(Limit l) {
            long agora = System.nanoTime();
            tokens = Math.min(l.capacidade(), tokens + (agora - ultimo) / 1e9 * l.recargaPorSegundo());
            ultimo = agora;
            if (tokens >= 1) { tokens -= 1; return true; }
            return false;
        }
    }

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(15)).maximumSize(200_000).build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String uri = req.getRequestURI();
        String grupo;
        Limit limit;
        if (uri.startsWith("/api/auth/login") || uri.startsWith("/api/auth/register") || uri.startsWith("/api/me/senha")) {
            grupo = "auth"; limit = AUTH;
        } else if (uri.startsWith("/api/ia/")) {
            grupo = "ia"; limit = IA;
        } else {
            grupo = "geral"; limit = GERAL;
        }
        String chave = grupo + "|" + clientIp(req);
        Bucket b = buckets.get(chave, k -> new Bucket(limit.capacidade()));
        if (!b.consumir(limit)) {
            res.setStatus(429);
            res.setHeader("Retry-After", "30");
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"erro\":\"Muitas requisições. Aguarde alguns segundos e tente novamente.\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    /** Usa X-Forwarded-For apenas do primeiro hop; configure o proxy reverso para sobrescrever o cabeçalho. */
    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
