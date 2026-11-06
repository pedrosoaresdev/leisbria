package br.com.pandecta.user;

import br.com.pandecta.common.ApiException;
import br.com.pandecta.common.PageResult;
import br.com.pandecta.common.Texts;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Dados do usuário no Firestore: favoritos, pastas, pesquisas salvas, histórico, alertas e notificações.
 * Todo acesso passa por aqui (regras do Firestore negam acesso direto do cliente). Cada operação é
 * restrita ao uid autenticado — não há rota que aceite uid de terceiros.
 */
@Service
public class UserDataService {
    private static final Logger log = LoggerFactory.getLogger(UserDataService.class);
    private static final Set<String> TIPOS_FAVORITO = Set.of("dispositivo", "norma", "decisao");
    private static final int LIMITE_ITENS = 2000;

    private final Firestore db;

    public UserDataService(Firestore db) {
        this.db = db;
    }

    private CollectionReference col(String uid, String nome) {
        return db.collection("users").document(uid).collection(nome);
    }

    private static <T> T aguardar(com.google.api.core.ApiFuture<T> f) {
        try {
            return f.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Operação interrompida.");
        } catch (ExecutionException e) {
            log.error("Firestore: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Banco de dados temporariamente indisponível.");
        }
    }

    private static Map<String, Object> comId(DocumentSnapshot d) {
        Map<String, Object> m = d.getData() == null ? new HashMap<>() : new HashMap<>(d.getData());
        m.put("id", d.getId());
        return m;
    }

    private List<Map<String, Object>> listar(Query q) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (QueryDocumentSnapshot d : aguardar(q.get()).getDocuments()) out.add(comId(d));
        return out;
    }

    private void garantirLimite(CollectionReference c) {
        long n = aguardar(c.count().get()).getCount();
        if (n >= LIMITE_ITENS) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Limite de itens atingido. Remova itens antigos.");
    }

    private void garantirExiste(DocumentReference ref) {
        if (!aguardar(ref.get()).exists()) throw new ApiException(HttpStatus.NOT_FOUND, "Item não encontrado.");
    }

    // ------------------------------------------------------------ favoritos

    public List<Map<String, Object>> favoritos(String uid, String pastaId) {
        Query q = col(uid, "favoritos");
        if (!Texts.isBlank(pastaId)) q = q.whereEqualTo("pastaId", pastaId);
        return listar(q.orderBy("criadoEm", Query.Direction.DESCENDING).limit(500));
    }

    public Map<String, Object> adicionarFavorito(String uid, Map<String, Object> body) {
        String tipo = str(body, "tipo", 20);
        String refId = str(body, "refId", 300);
        if (!TIPOS_FAVORITO.contains(tipo) || Texts.isBlank(refId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Favorito inválido.");
        }
        CollectionReference c = col(uid, "favoritos");
        garantirLimite(c);
        String id = Texts.sha256(tipo + "|" + refId).substring(0, 32); // idempotente: mesmo item = mesmo id
        Map<String, Object> d = new HashMap<>();
        d.put("tipo", tipo);
        d.put("refId", refId);
        d.put("titulo", str(body, "titulo", 400));
        d.put("fonte", str(body, "fonte", 120));
        String url = str(body, "url", 600);
        if (url != null && url.startsWith("https://")) d.put("url", url);
        d.put("normaId", str(body, "normaId", 80));
        d.put("pastaId", str(body, "pastaId", 60));
        d.put("nota", str(body, "nota", 2000));
        d.put("criadoEm", Instant.now().toString());
        aguardar(c.document(id).set(d, SetOptions.merge()));
        d.put("id", id);
        return d;
    }

    public void atualizarFavorito(String uid, String id, Map<String, Object> body) {
        DocumentReference ref = col(uid, "favoritos").document(id);
        garantirExiste(ref);
        Map<String, Object> upd = new HashMap<>();
        if (body.containsKey("pastaId")) {
            String pasta = str(body, "pastaId", 60);
            if (pasta != null) garantirExiste(col(uid, "pastas").document(pasta));
            upd.put("pastaId", pasta);
        }
        if (body.containsKey("nota")) upd.put("nota", str(body, "nota", 2000));
        if (!upd.isEmpty()) aguardar(ref.update(upd));
    }

    public void removerFavorito(String uid, String id) {
        aguardar(col(uid, "favoritos").document(id).delete());
    }

    // ------------------------------------------------------------ pastas

    public List<Map<String, Object>> pastas(String uid) {
        return listar(col(uid, "pastas").orderBy("nome").limit(300));
    }

    public Map<String, Object> criarPasta(String uid, Map<String, Object> body) {
        String nome = str(body, "nome", 60);
        if (Texts.isBlank(nome)) throw new ApiException(HttpStatus.BAD_REQUEST, "Informe o nome da pasta.");
        CollectionReference c = col(uid, "pastas");
        garantirLimite(c);
        Map<String, Object> d = new HashMap<>(Map.of("nome", nome, "criadoEm", Instant.now().toString()));
        String desc = str(body, "descricao", 300);
        if (desc != null) d.put("descricao", desc);
        DocumentReference ref = aguardar(c.add(d));
        d.put("id", ref.getId());
        return d;
    }

    /** Exclui a pasta e devolve seus favoritos para "sem pasta". */
    public void removerPasta(String uid, String id) {
        WriteBatch b = db.batch();
        for (QueryDocumentSnapshot f : aguardar(col(uid, "favoritos").whereEqualTo("pastaId", id).get()).getDocuments()) {
            b.update(f.getReference(), "pastaId", null);
        }
        b.delete(col(uid, "pastas").document(id));
        aguardar(b.commit());
    }

    // ------------------------------------------------------------ pesquisas salvas e histórico

    public List<Map<String, Object>> pesquisas(String uid) {
        return listar(col(uid, "pesquisas").orderBy("criadoEm", Query.Direction.DESCENDING).limit(300));
    }

    public Map<String, Object> salvarPesquisa(String uid, Map<String, Object> body) {
        String consulta = str(body, "consulta", 2000);
        if (Texts.isBlank(consulta)) throw new ApiException(HttpStatus.BAD_REQUEST, "Pesquisa vazia.");
        CollectionReference c = col(uid, "pesquisas");
        garantirLimite(c);
        Map<String, Object> d = new HashMap<>();
        d.put("consulta", consulta);
        d.put("aba", str(body, "aba", 30));
        d.put("nome", str(body, "nome", 120));
        d.put("filtros", filtros(body.get("filtros")));
        d.put("criadoEm", Instant.now().toString());
        DocumentReference ref = aguardar(c.add(d));
        d.put("id", ref.getId());
        return d;
    }

    public void removerPesquisa(String uid, String id) {
        aguardar(col(uid, "pesquisas").document(id).delete());
    }

    @Async
    public void registrarHistoricoAsync(String uid, String consulta, String aba, long total) {
        try {
            Map<String, Object> d = new HashMap<>();
            d.put("consulta", Texts.truncate(consulta, 500));
            d.put("aba", aba);
            d.put("totalResultados", total);
            d.put("criadoEm", Instant.now().toString());
            col(uid, "historico").add(d).get();
        } catch (Exception e) {
            log.debug("Histórico não registrado: {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> historico(String uid) {
        return listar(col(uid, "historico").orderBy("criadoEm", Query.Direction.DESCENDING).limit(100));
    }

    public void limparHistorico(String uid) {
        List<QueryDocumentSnapshot> docs;
        do {
            docs = aguardar(col(uid, "historico").limit(400).get()).getDocuments();
            if (docs.isEmpty()) break;
            WriteBatch b = db.batch();
            docs.forEach(d -> b.delete(d.getReference()));
            aguardar(b.commit());
        } while (docs.size() == 400);
    }

    // ------------------------------------------------------------ alertas e notificações

    public List<Map<String, Object>> alertas(String uid) {
        return listar(col(uid, "alertas").orderBy("criadoEm", Query.Direction.DESCENDING).limit(100));
    }

    public Map<String, Object> criarAlerta(String uid, Map<String, Object> body) {
        String termos = str(body, "termos", 200);
        if (termos == null || termos.length() < 3) throw new ApiException(HttpStatus.BAD_REQUEST, "Informe ao menos 3 caracteres.");
        CollectionReference c = col(uid, "alertas");
        if (aguardar(c.count().get()).getCount() >= 50) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Limite de 50 alertas.");
        List<String> tribunais = new ArrayList<>();
        if (body.get("tribunais") instanceof List<?> l) {
            for (Object o : l) if (o != null && tribunais.size() < 10) tribunais.add(Texts.userInput(String.valueOf(o), 20).toUpperCase());
        }
        Map<String, Object> d = new HashMap<>();
        d.put("termos", termos);
        d.put("tribunais", tribunais);
        d.put("ativo", true);
        d.put("uid", uid);
        d.put("criadoEm", Instant.now().toString());
        d.put("ultimaVerificacao", Instant.now().toString());
        DocumentReference ref = aguardar(c.add(d));
        d.put("id", ref.getId());
        return d;
    }

    public void atualizarAlerta(String uid, String id, Map<String, Object> body) {
        DocumentReference ref = col(uid, "alertas").document(id);
        garantirExiste(ref);
        if (body.get("ativo") instanceof Boolean b) aguardar(ref.update("ativo", b));
    }

    public void removerAlerta(String uid, String id) {
        aguardar(col(uid, "alertas").document(id).delete());
    }

    public PageResult<Map<String, Object>> notificacoes(String uid, boolean somenteNaoLidas, int pagina, int tamanho) {
        int tam = Math.max(1, Math.min(tamanho, 50));
        int pag = Math.max(0, Math.min(pagina, 50));
        Query base = col(uid, "notificacoes");
        if (somenteNaoLidas) base = base.whereEqualTo("lida", false);
        long total = aguardar(base.count().get()).getCount();
        List<Map<String, Object>> itens = listar(base.orderBy("criadoEm", Query.Direction.DESCENDING).offset(pag * tam).limit(tam));
        return PageResult.of(total, pag, tam, itens);
    }

    public void marcarLida(String uid, String id) {
        DocumentReference ref = col(uid, "notificacoes").document(id);
        garantirExiste(ref);
        aguardar(ref.update("lida", true));
    }

    // ------------------------------------------------------------ util

    private static String str(Map<String, Object> body, String k, int max) {
        Object v = body.get(k);
        if (v == null) return null;
        String s = Texts.userInput(String.valueOf(v), max);
        return s.isBlank() ? null : s;
    }

    private static Map<String, String> filtros(Object o) {
        Map<String, String> out = new HashMap<>();
        if (o instanceof Map<?, ?> m) {
            m.forEach((k, v) -> {
                if (k != null && v != null && out.size() < 20) out.put(Texts.userInput(String.valueOf(k), 40), Texts.userInput(String.valueOf(v), 200));
            });
        }
        return out;
    }
}
