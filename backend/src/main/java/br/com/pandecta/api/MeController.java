package br.com.pandecta.api;

import br.com.pandecta.auth.AuthService;
import br.com.pandecta.common.PageResult;
import br.com.pandecta.security.AuthUser;
import br.com.pandecta.security.CurrentUser;
import br.com.pandecta.user.UserDataService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Conta e área pessoal do usuário autenticado. Todas as rotas usam apenas o uid do token. */
@RestController
@RequestMapping("/api/me")
public class MeController {
    private final AuthService auth;
    private final UserDataService dados;

    public MeController(AuthService auth, UserDataService dados) {
        this.auth = auth;
        this.dados = dados;
    }

    public record NovaSenha(@NotBlank @Size(max = 128) String novaSenha) {}

    @GetMapping
    public Map<String, Object> me(@CurrentUser AuthUser u) {
        return auth.perfil(u.uid());
    }

    @PostMapping("/senha")
    public Map<String, Object> senha(@Valid @RequestBody NovaSenha b, @CurrentUser AuthUser u) {
        auth.alterarSenha(u.uid(), u.authTimeEpochSec(), b.novaSenha());
        return Map.of("alterada", true);
    }

    // Favoritos
    @GetMapping("/favoritos")
    public List<Map<String, Object>> favoritos(@RequestParam(required = false) String pastaId, @CurrentUser AuthUser u) {
        return dados.favoritos(u.uid(), pastaId);
    }

    @PostMapping("/favoritos")
    public Map<String, Object> addFavorito(@RequestBody Map<String, Object> b, @CurrentUser AuthUser u) {
        return dados.adicionarFavorito(u.uid(), b);
    }

    @PutMapping("/favoritos/{id}")
    public Map<String, Object> updFavorito(@PathVariable String id, @RequestBody Map<String, Object> b, @CurrentUser AuthUser u) {
        dados.atualizarFavorito(u.uid(), id, b);
        return Map.of("ok", true);
    }

    @DeleteMapping("/favoritos/{id}")
    public Map<String, Object> delFavorito(@PathVariable String id, @CurrentUser AuthUser u) {
        dados.removerFavorito(u.uid(), id);
        return Map.of("ok", true);
    }

    // Pastas
    @GetMapping("/pastas")
    public List<Map<String, Object>> pastas(@CurrentUser AuthUser u) {
        return dados.pastas(u.uid());
    }

    @PostMapping("/pastas")
    public Map<String, Object> criarPasta(@RequestBody Map<String, Object> b, @CurrentUser AuthUser u) {
        return dados.criarPasta(u.uid(), b);
    }

    @DeleteMapping("/pastas/{id}")
    public Map<String, Object> delPasta(@PathVariable String id, @CurrentUser AuthUser u) {
        dados.removerPasta(u.uid(), id);
        return Map.of("ok", true);
    }

    // Pesquisas salvas
    @GetMapping("/pesquisas")
    public List<Map<String, Object>> pesquisas(@CurrentUser AuthUser u) {
        return dados.pesquisas(u.uid());
    }

    @PostMapping("/pesquisas")
    public Map<String, Object> salvarPesquisa(@RequestBody Map<String, Object> b, @CurrentUser AuthUser u) {
        return dados.salvarPesquisa(u.uid(), b);
    }

    @DeleteMapping("/pesquisas/{id}")
    public Map<String, Object> delPesquisa(@PathVariable String id, @CurrentUser AuthUser u) {
        dados.removerPesquisa(u.uid(), id);
        return Map.of("ok", true);
    }

    // Histórico
    @GetMapping("/historico")
    public List<Map<String, Object>> historico(@CurrentUser AuthUser u) {
        return dados.historico(u.uid());
    }

    @DeleteMapping("/historico")
    public Map<String, Object> limparHistorico(@CurrentUser AuthUser u) {
        dados.limparHistorico(u.uid());
        return Map.of("ok", true);
    }

    // Alertas
    @GetMapping("/alertas")
    public List<Map<String, Object>> alertas(@CurrentUser AuthUser u) {
        return dados.alertas(u.uid());
    }

    @PostMapping("/alertas")
    public Map<String, Object> criarAlerta(@RequestBody Map<String, Object> b, @CurrentUser AuthUser u) {
        return dados.criarAlerta(u.uid(), b);
    }

    @PutMapping("/alertas/{id}")
    public Map<String, Object> updAlerta(@PathVariable String id, @RequestBody Map<String, Object> b, @CurrentUser AuthUser u) {
        dados.atualizarAlerta(u.uid(), id, b);
        return Map.of("ok", true);
    }

    @DeleteMapping("/alertas/{id}")
    public Map<String, Object> delAlerta(@PathVariable String id, @CurrentUser AuthUser u) {
        dados.removerAlerta(u.uid(), id);
        return Map.of("ok", true);
    }

    // Notificações
    @GetMapping("/notificacoes")
    public PageResult<Map<String, Object>> notificacoes(@RequestParam(defaultValue = "false") boolean somenteNaoLidas,
                                                        @RequestParam(defaultValue = "0") int pagina,
                                                        @RequestParam(defaultValue = "30") int tamanho,
                                                        @CurrentUser AuthUser u) {
        return dados.notificacoes(u.uid(), somenteNaoLidas, pagina, tamanho);
    }

    @PostMapping("/notificacoes/{id}/lida")
    public Map<String, Object> lida(@PathVariable String id, @CurrentUser AuthUser u) {
        dados.marcarLida(u.uid(), id);
        return Map.of("ok", true);
    }
}
