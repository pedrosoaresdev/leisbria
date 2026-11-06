package br.com.pandecta.auth;

import br.com.pandecta.common.ApiException;
import br.com.pandecta.common.Texts;
import br.com.pandecta.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Autenticação:
 *  - Cadastro com nome de usuário e senha: cria a conta no Firebase Auth (e-mail técnico interno, nunca
 *    exibido) e reserva o username no Firestore com transação (unicidade garantida).
 *  - Login por username e senha: o servidor resolve o username, valida a senha no Identity Toolkit e
 *    devolve um custom token (o e-mail nunca é exposto ao cliente; mensagens de erro são genéricas).
 *  - Google: login direto no cliente (Firebase JS SDK). Usuários que entram primeiro pelo Google escolhem
 *    um username depois.
 *  - Esqueceu a senha: entrar com o Google vinculado e trocar a senha em Conta → Configurações
 *    (exige login recente; ver {@link #alterarSenha}).
 *  - QR Code: ver {@link QrLoginService}.
 */
@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    public static final Pattern USERNAME = Pattern.compile("^[a-z0-9._]{3,30}$");
    private static final long LOGIN_RECENTE_SEG = 300;

    private final FirebaseAuth auth;
    private final Firestore db;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final AppProperties.Firebase cfg;
    private final SecureRandom random = new SecureRandom();

    public AuthService(FirebaseAuth auth, Firestore db, HttpClient http, ObjectMapper mapper, AppProperties props) {
        this.auth = auth;
        this.db = db;
        this.http = http;
        this.mapper = mapper;
        this.cfg = props.firebase();
    }

    public static String normalizarUsername(String u) {
        String n = u == null ? "" : u.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(n).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nome de usuário inválido: use 3 a 30 caracteres (letras, números, ponto ou sublinhado).");
        }
        return n;
    }

    public static void validarSenha(String senha) {
        if (senha == null || senha.length() < 10 || senha.length() > 128
                || !senha.matches(".*[A-Za-z].*") || !senha.matches(".*\\d.*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A senha deve ter de 10 a 128 caracteres, com letras e números.");
        }
    }

    /** Cria a conta e devolve um custom token para login imediato. */
    public String registrar(String usernameBruto, String nome, String senha) {
        String username = normalizarUsername(usernameBruto);
        validarSenha(senha);
        String nomeLimpo = Texts.userInput(nome, 80);
        if (nomeLimpo.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Informe seu nome.");
        DocumentReference reserva = db.collection("usernames").document(username);
        try {
            if (reserva.get().get().exists()) throw new ApiException(HttpStatus.CONFLICT, "Este nome de usuário já está em uso.");
            String emailTecnico = username + "+" + token(6).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "") + "@" + cfg.syntheticEmailDomain();
            UserRecord u = auth.createUser(new UserRecord.CreateRequest()
                    .setEmail(emailTecnico).setPassword(senha).setDisplayName(nomeLimpo).setEmailVerified(false));
            try {
                reservarUsername(username, u.getUid());
            } catch (ApiException e) {
                auth.deleteUser(u.getUid()); // corrida: outro cadastro reservou antes
                throw e;
            }
            Map<String, Object> perfil = new HashMap<>();
            perfil.put("username", username);
            perfil.put("nome", nomeLimpo);
            perfil.put("emailTecnico", emailTecnico);
            perfil.put("criadoEm", Instant.now().toString());
            db.collection("users").document(u.getUid()).set(perfil, SetOptions.merge()).get();
            return auth.createCustomToken(u.getUid());
        } catch (ApiException e) {
            throw e;
        } catch (FirebaseAuthException e) {
            log.warn("Falha no cadastro: {}", e.getAuthErrorCode());
            throw new ApiException(HttpStatus.BAD_REQUEST, "Não foi possível criar a conta.");
        } catch (Exception e) {
            log.error("Erro no cadastro", e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Serviço de contas indisponível.");
        }
    }

    /** Reserva atômica do username (transação no Firestore). */
    public void reservarUsername(String username, String uid) {
        try {
            db.runTransaction(tx -> {
                DocumentReference ref = db.collection("usernames").document(username);
                DocumentSnapshot s = tx.get(ref).get();
                if (s.exists() && !uid.equals(s.getString("uid"))) throw new IllegalStateException("EM_USO");
                tx.set(ref, Map.<String, Object>of("uid", uid, "criadoEm", Instant.now().toString()));
                return null;
            }).get();
        } catch (Exception e) {
            if (String.valueOf(e.getMessage()).contains("EM_USO")) {
                throw new ApiException(HttpStatus.CONFLICT, "Este nome de usuário já está em uso.");
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Serviço de contas indisponível.");
        }
    }

    /** Login por username e senha → custom token. Erros sempre genéricos (não revela se o usuário existe). */
    public String entrar(String usernameBruto, String senha) {
        ApiException generico = new ApiException(HttpStatus.UNAUTHORIZED, "Usuário ou senha incorretos.");
        String username;
        try {
            username = normalizarUsername(usernameBruto);
        } catch (ApiException e) {
            throw generico;
        }
        if (senha == null || senha.isEmpty() || senha.length() > 128) throw generico;
        if (cfg.webApiKey() == null || cfg.webApiKey().isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Login por senha não configurado (FIREBASE_WEB_API_KEY).");
        }
        try {
            DocumentSnapshot reserva = db.collection("usernames").document(username).get().get();
            if (!reserva.exists()) throw generico;
            String uid = reserva.getString("uid");
            UserRecord u = auth.getUser(uid);
            if (u.isDisabled() || u.getEmail() == null) throw generico;
            String body = mapper.writeValueAsString(Map.of("email", u.getEmail(), "password", senha, "returnSecureToken", false));
            HttpRequest req = HttpRequest.newBuilder(URI.create(
                            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + cfg.webApiKey()))
                    .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                JsonNode err = mapper.readTree(res.body()).path("error");
                String msg = err.path("message").asText("");
                if (msg.startsWith("TOO_MANY_ATTEMPTS")) {
                    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Aguarde alguns minutos ou entre com o Google.");
                }
                throw generico;
            }
            String localId = mapper.readTree(res.body()).path("localId").asText();
            if (!uid.equals(localId)) throw generico;
            return auth.createCustomToken(uid);
        } catch (ApiException e) {
            throw e;
        } catch (FirebaseAuthException e) {
            throw generico;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Serviço de contas indisponível.");
        } catch (Exception e) {
            log.error("Erro no login", e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Serviço de contas indisponível.");
        }
    }

    /** Usuário que entrou pelo Google define seu username. */
    public void definirUsername(String uid, String usernameBruto, String nome) {
        String username = normalizarUsername(usernameBruto);
        Map<String, Object> perfil = perfil(uid);
        if (perfil.get("username") != null) throw new ApiException(HttpStatus.CONFLICT, "Nome de usuário já definido.");
        reservarUsername(username, uid);
        Map<String, Object> upd = new HashMap<>();
        upd.put("username", username);
        if (perfil.get("nome") == null && nome != null) upd.put("nome", Texts.userInput(nome, 80));
        upd.putIfAbsent("criadoEm", Instant.now().toString());
        try {
            db.collection("users").document(uid).set(upd, SetOptions.merge()).get();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Serviço de contas indisponível.");
        }
    }

    /** Perfil do usuário (Firestore + provedores vinculados no Firebase Auth). */
    public Map<String, Object> perfil(String uid) {
        try {
            DocumentSnapshot s = db.collection("users").document(uid).get().get();
            Map<String, Object> out = new HashMap<>();
            if (s.exists() && s.getData() != null) out.putAll(s.getData());
            out.remove("emailTecnico");
            UserRecord u = auth.getUser(uid);
            List<String> provedores = new ArrayList<>();
            for (UserInfo info : u.getProviderData()) provedores.add(info.getProviderId());
            out.put("provedores", provedores);
            out.putIfAbsent("nome", u.getDisplayName());
            out.putIfAbsent("criadoEm", Instant.ofEpochMilli(u.getUserMetadata().getCreationTimestamp()).toString());
            out.put("uid", uid);
            return out;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Serviço de contas indisponível.");
        }
    }

    /**
     * Troca de senha (Conta → Configurações). Exige login feito há no máximo 5 minutos — inclusive pelo Google,
     * que é o caminho de recuperação para quem esqueceu a senha. Revoga as demais sessões.
     */
    public void alterarSenha(String uid, long authTime, String novaSenha) {
        validarSenha(novaSenha);
        if (Instant.now().getEpochSecond() - authTime > LOGIN_RECENTE_SEG) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Por segurança, entre novamente (por exemplo, com o Google) e repita a troca de senha em até 5 minutos.");
        }
        try {
            UserRecord u = auth.getUser(uid);
            UserRecord.UpdateRequest req = new UserRecord.UpdateRequest(uid).setPassword(novaSenha);
            if (u.getEmail() == null) {
                Map<String, Object> perfil = perfil(uid);
                String username = (String) perfil.get("username");
                if (username == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Defina um nome de usuário antes de criar uma senha.");
                req.setEmail(username + "+" + token(6).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "") + "@" + cfg.syntheticEmailDomain());
            }
            auth.updateUser(req);
            auth.revokeRefreshTokens(uid);
        } catch (ApiException e) {
            throw e;
        } catch (FirebaseAuthException e) {
            log.warn("Falha ao alterar senha: {}", e.getAuthErrorCode());
            throw new ApiException(HttpStatus.BAD_REQUEST, "Não foi possível alterar a senha.");
        }
    }

    public String customToken(String uid) {
        try {
            return auth.createCustomToken(uid);
        } catch (FirebaseAuthException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Serviço de contas indisponível.");
        }
    }

    public String token(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
