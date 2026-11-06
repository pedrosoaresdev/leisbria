package br.com.pandecta.auth;

import br.com.pandecta.common.ApiException;
import br.com.pandecta.common.Texts;
import br.com.pandecta.config.AppProperties;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Login por QR Code (dispositivo A sem sessão; dispositivo B já autenticado, ex.: celular com Google):
 *  1. A cria uma sessão: recebe {id, segredo}; o QR contém a URL de aprovação com id e segredo.
 *  2. B abre a URL, autenticado, e aprova (envia o segredo) → a sessão fica APROVADA com o uid de B.
 *  3. A consulta periodicamente com o segredo; ao ver APROVADA recebe um custom token UMA única vez (CONSUMIDA).
 * O segredo é guardado só como hash; sessões expiram em 2 minutos.
 */
@Service
public class QrLoginService {
    private static final long VALIDADE_SEG = 120;

    private final Firestore db;
    private final AuthService auth;
    private final String frontendUrl;

    public QrLoginService(Firestore db, AuthService auth, AppProperties props) {
        this.db = db;
        this.auth = auth;
        this.frontendUrl = props.frontendPublicUrl().replaceAll("/$", "");
    }

    public Map<String, Object> criar(String userAgent) {
        String id = auth.token(16);
        String segredo = auth.token(32);
        Map<String, Object> s = new HashMap<>();
        s.put("secretHash", Texts.sha256(segredo));
        s.put("status", "PENDENTE");
        s.put("expiraEm", Instant.now().plusSeconds(VALIDADE_SEG).toString());
        s.put("userAgent", Texts.userInput(userAgent, 200));
        s.put("criadoEm", Instant.now().toString());
        executar(db.collection("qrSessions").document(id).set(s));
        Map<String, Object> out = new HashMap<>();
        out.put("id", id);
        out.put("segredo", segredo);
        out.put("expiraEm", s.get("expiraEm"));
        out.put("urlAprovacao", frontendUrl + "/#/qr/" + id + "?s=" + segredo);
        return out;
    }

    public void aprovar(String id, String segredo, String uid) {
        try {
            db.runTransaction(tx -> {
                DocumentReference ref = db.collection("qrSessions").document(id);
                DocumentSnapshot s = tx.get(ref).get();
                validar(s, segredo);
                if (!"PENDENTE".equals(s.getString("status"))) throw new IllegalStateException("USADA");
                tx.update(ref, Map.<String, Object>of("status", "APROVADA", "uid", uid, "aprovadoEm", Instant.now().toString()));
                return null;
            }).get();
        } catch (Exception e) {
            throw traduzir(e);
        }
    }

    /** Consulta do dispositivo que exibe o QR. Entrega o custom token apenas uma vez. */
    public Map<String, Object> consultar(String id, String segredo) {
        try {
            return db.runTransaction(tx -> {
                DocumentReference ref = db.collection("qrSessions").document(id);
                DocumentSnapshot s = tx.get(ref).get();
                Map<String, Object> out = new HashMap<>();
                try {
                    validar(s, segredo);
                } catch (IllegalStateException e) {
                    if ("EXPIRADA".equals(e.getMessage())) {
                        out.put("status", "EXPIRADA");
                        return out;
                    }
                    throw e;
                }
                String status = s.getString("status");
                if ("APROVADA".equals(status)) {
                    tx.update(ref, Map.<String, Object>of("status", "CONSUMIDA", "consumidoEm", Instant.now().toString()));
                    out.put("status", "APROVADA");
                    out.put("customToken", auth.customToken(s.getString("uid")));
                } else {
                    out.put("status", status);
                }
                return out;
            }).get();
        } catch (Exception e) {
            throw traduzir(e);
        }
    }

    private static void validar(DocumentSnapshot s, String segredo) {
        if (!s.exists() || segredo == null) throw new IllegalStateException("INVALIDA");
        String hash = s.getString("secretHash");
        if (hash == null || !MessageDigest.isEqual(hash.getBytes(), Texts.sha256(segredo).getBytes())) {
            throw new IllegalStateException("INVALIDA");
        }
        if (Instant.parse(s.getString("expiraEm")).isBefore(Instant.now())) throw new IllegalStateException("EXPIRADA");
    }

    private static ApiException traduzir(Exception e) {
        Throwable c = e;
        while (c.getCause() != null && !(c instanceof IllegalStateException) && !(c instanceof ApiException)) c = c.getCause();
        if (c instanceof ApiException a) return a;
        String m = String.valueOf(c.getMessage());
        if (m.contains("EXPIRADA")) return new ApiException(HttpStatus.GONE, "QR Code expirado. Gere um novo.");
        if (m.contains("USADA")) return new ApiException(HttpStatus.CONFLICT, "Este QR Code já foi utilizado.");
        if (m.contains("INVALIDA")) return new ApiException(HttpStatus.NOT_FOUND, "QR Code inválido.");
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Serviço de login indisponível.");
    }

    private static void executar(com.google.api.core.ApiFuture<?> f) {
        try {
            f.get();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Serviço de login indisponível.");
        }
    }
}
