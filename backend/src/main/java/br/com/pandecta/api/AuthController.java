package br.com.pandecta.api;

import br.com.pandecta.auth.AuthService;
import br.com.pandecta.auth.QrLoginService;
import br.com.pandecta.security.AuthUser;
import br.com.pandecta.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final QrLoginService qr;

    public AuthController(AuthService auth, QrLoginService qr) {
        this.auth = auth;
        this.qr = qr;
    }

    public record Registro(@NotBlank @Size(max = 30) String username, @NotBlank @Size(max = 80) String nome,
                           @NotBlank @Size(max = 128) String senha) {}

    public record Login(@NotBlank @Size(max = 30) String username, @NotBlank @Size(max = 128) String senha) {}

    public record Segredo(@NotBlank @Size(max = 100) String segredo) {}

    public record Username(@NotBlank @Size(max = 30) String username, @Size(max = 80) String nome) {}

    @PostMapping("/registrar")
    public Map<String, String> registrar(@Valid @RequestBody Registro r) {
        return Map.of("customToken", auth.registrar(r.username(), r.nome(), r.senha()));
    }

    @PostMapping("/entrar")
    public Map<String, String> entrar(@Valid @RequestBody Login l) {
        return Map.of("customToken", auth.entrar(l.username(), l.senha()));
    }

    @PostMapping("/google/username")
    public Map<String, Object> definirUsername(@Valid @RequestBody Username u, @CurrentUser AuthUser user) {
        auth.definirUsername(user.uid(), u.username(), u.nome() != null ? u.nome() : user.nome());
        return auth.perfil(user.uid());
    }

    @PostMapping("/qr")
    public Map<String, Object> criarQr(HttpServletRequest req) {
        return qr.criar(req.getHeader("User-Agent"));
    }

    @GetMapping("/qr/{id}")
    public Map<String, Object> consultarQr(@PathVariable String id, @RequestParam String segredo) {
        return qr.consultar(id, segredo);
    }

    @PostMapping("/qr/{id}/aprovar")
    public Map<String, Object> aprovarQr(@PathVariable String id, @Valid @RequestBody Segredo s, @CurrentUser AuthUser user) {
        qr.aprovar(id, s.segredo(), user.uid());
        return Map.of("aprovado", true);
    }
}
