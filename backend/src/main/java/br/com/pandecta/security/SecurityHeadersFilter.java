package br.com.pandecta.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Cabeçalhos de segurança aplicados a todas as respostas da API. */
@Component
@Order(1)
public class SecurityHeadersFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        res.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        chain.doFilter(req, res);
    }
}
