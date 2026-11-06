package br.com.pandecta.security;

import br.com.pandecta.config.AppProperties;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Valida o ID token do Firebase enviado em "Authorization: Bearer ...".
 * Token inválido → 401. Ausência de token → requisição anônima (rotas públicas continuam funcionando).
 * A verificação checa assinatura, expiração e revogação (checkRevoked=true).
 */
@Component
@Order(20)
public class FirebaseAuthFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthFilter.class);
    private final FirebaseAuth firebaseAuth;
    private final AppProperties props;

    public FirebaseAuthFilter(FirebaseAuth firebaseAuth, AppProperties props) {
        this.firebaseAuth = firebaseAuth;
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            try {
                FirebaseToken decoded = firebaseAuth.verifyIdToken(token, true);
                Map<String, Object> claims = decoded.getClaims();
                long authTime = claims.get("auth_time") instanceof Number n ? n.longValue() : 0L;
                boolean admin = Boolean.TRUE.equals(claims.get("admin"))
                        || props.adminUidsSafe().contains(decoded.getUid());
                String provider = null;
                if (claims.get("firebase") instanceof Map<?, ?> fb && fb.get("sign_in_provider") != null) {
                    provider = String.valueOf(fb.get("sign_in_provider"));
                }
                req.setAttribute(AuthUser.REQUEST_ATTR,
                        new AuthUser(decoded.getUid(), decoded.getName(), admin, authTime, provider));
            } catch (FirebaseAuthException | IllegalArgumentException e) {
                log.debug("Token rejeitado: {}", e.getMessage());
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"erro\":\"Sessão inválida ou expirada. Entre novamente.\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
