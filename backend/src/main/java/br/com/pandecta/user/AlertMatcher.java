package br.com.pandecta.user;

import br.com.pandecta.connectors.SyncListener;
import br.com.pandecta.search.Indices;
import br.com.pandecta.search.SearchClient;
import br.com.pandecta.search.SearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Após cada sincronização, compara os novos eventos do Radar com os alertas ativos dos usuários e
 * cria notificações. O id da notificação é determinístico (alerta + evento): não há duplicidade.
 */
@Component
public class AlertMatcher implements SyncListener {
    private static final Logger log = LoggerFactory.getLogger(AlertMatcher.class);

    private final Firestore db;
    private final SearchClient client;
    private final Indices indices;
    private final AtomicBoolean rodando = new AtomicBoolean(false);

    public AlertMatcher(Firestore db, SearchClient client, Indices indices) {
        this.db = db;
        this.client = client;
        this.indices = indices;
    }

    @Override
    public void aposSincronizacao() {
        if (!rodando.compareAndSet(false, true)) return;
        try {
            for (QueryDocumentSnapshot alerta : db.collectionGroup("alertas").whereEqualTo("ativo", true).get().get().getDocuments()) {
                try {
                    verificar(alerta);
                } catch (Exception e) {
                    log.warn("Alerta {} não verificado: {}", alerta.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Verificação de alertas falhou: {}", e.getMessage());
        } finally {
            rodando.set(false);
        }
    }

    @SuppressWarnings("unchecked")
    private void verificar(QueryDocumentSnapshot alerta) throws Exception {
        DocumentReference userRef = alerta.getReference().getParent().getParent();
        if (userRef == null) return;
        String desde = alerta.getString("ultimaVerificacao");
        String agora = Instant.now().toString();
        ObjectNode q = client.mapper().createObjectNode();
        q.put("size", 20);
        ObjectNode bool = q.putObject("query").putObject("bool");
        ObjectNode mm = bool.withArray("must").addObject().putObject("multi_match");
        mm.put("query", alerta.getString("termos")).put("operator", "and");
        mm.putArray("fields").add("titulo").add("resumo").add("ementa").add("assuntos");
        ArrayNode f = bool.putArray("filter");
        if (desde != null) f.addObject().putObject("range").putObject("sincronizadoEm").put("gt", desde);
        Object tribs = alerta.get("tribunais");
        if (tribs instanceof List<?> l && !l.isEmpty()) SearchService.terms(f, "tribunal", (List<String>) l);
        q.putArray("sort").addObject().putObject("dataEvento").put("order", "desc");

        int novas = 0;
        for (JsonNode h : client.search(indices.radar(), q).path("hits").path("hits")) {
            JsonNode ev = h.path("_source");
            String id = (alerta.getId() + "_" + ev.path("id").asText()).replaceAll("[^A-Za-z0-9_\\-:.]", "_");
            if (id.length() > 400) id = id.substring(0, 400);
            Map<String, Object> n = new HashMap<>();
            n.put("alertaId", alerta.getId());
            n.put("alertaTermos", alerta.getString("termos"));
            n.put("titulo", ev.path("titulo").asText());
            n.put("tribunal", ev.path("tribunal").asText());
            n.put("numeroProcesso", ev.path("numeroProcesso").asText(null));
            n.put("documentoId", ev.path("documentoId").asText(null));
            n.put("urlOficial", ev.path("urlOficial").asText(null));
            n.put("lida", false);
            n.put("criadoEm", agora);
            try {
                userRef.collection("notificacoes").document(id).create(n).get();
                novas++;
            } catch (Exception jaExiste) {
                // "create" falha se a notificação já existe: evita duplicidade
            }
        }
        alerta.getReference().update("ultimaVerificacao", agora).get();
        if (novas > 0) log.info("Alerta {}: {} nova(s) notificação(ões)", alerta.getId(), novas);
    }
}
