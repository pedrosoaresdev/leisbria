package br.com.pandecta.connectors;

import br.com.pandecta.common.Texts;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Estado das fontes no Firestore: situação, horários de sincronização, cursor incremental,
 * histórico de execuções (syncRuns) e recursos já importados (controle de duplicidade).
 */
@Repository
public class SourceStatusRepository {
    private static final Logger log = LoggerFactory.getLogger(SourceStatusRepository.class);
    private final Firestore db;

    public SourceStatusRepository(Firestore db) {
        this.db = db;
    }

    public Map<String, Object> obter(String fonteId) {
        try {
            DocumentSnapshot s = db.collection("sources").document(fonteId).get().get();
            return s.exists() && s.getData() != null ? s.getData() : new HashMap<>();
        } catch (Exception e) {
            log.warn("Não foi possível ler o estado da fonte {}: {}", fonteId, e.getMessage());
            return new HashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> cursor(String fonteId) {
        Object c = obter(fonteId).get("cursor");
        return c instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    public void marcarInicio(String fonteId, String nome) {
        Map<String, Object> d = new HashMap<>();
        d.put("nome", nome);
        d.put("estado", "SINCRONIZANDO");
        d.put("ultimaSincronizacao", Instant.now().toString());
        salvar(fonteId, d);
    }

    public void marcarFim(String fonteId, SyncResult r, String iniciadoEm, Throwable falha) {
        String agora = Instant.now().toString();
        Map<String, Object> d = new HashMap<>();
        String estado;
        if (falha != null) estado = r.fonteIndisponivel ? "INDISPONIVEL" : "ERRO";
        else estado = r.fonteIndisponivel ? "INDISPONIVEL" : "ATIVA";
        d.put("estado", estado);
        d.put("ultimaSincronizacao", agora);
        if (falha == null && !r.fonteIndisponivel) {
            d.put("ultimoSucesso", agora);
            d.put("ultimoErro", r.erros.isEmpty() ? null : r.erros.get(0));
        } else {
            d.put("ultimoErro", falha != null ? Optional.ofNullable(falha.getMessage()).orElse(falha.getClass().getSimpleName())
                    : (r.erros.isEmpty() ? "Fonte não respondeu" : r.erros.get(0)));
        }
        if (!r.cursor.isEmpty()) d.put("cursor", r.cursor);
        salvar(fonteId, d);

        Map<String, Object> run = new HashMap<>();
        run.put("fonteId", fonteId);
        run.put("iniciadoEm", iniciadoEm);
        run.put("finalizadoEm", agora);
        run.put("status", estado);
        run.put("lidos", r.lidos);
        run.put("gravados", r.gravados);
        run.put("ignorados", r.ignorados);
        run.put("duplicados", r.duplicados);
        run.put("erros", r.erros);
        try {
            db.collection("syncRuns").add(run).get();
        } catch (Exception e) {
            log.warn("Falha ao registrar execução de {}: {}", fonteId, e.getMessage());
        }
    }

    public boolean recursoProcessado(String fonteId, String chave) {
        try {
            return db.collection("processedResources").document(Texts.sha256(fonteId + "|" + chave)).get().get().exists();
        } catch (Exception e) {
            return false;
        }
    }

    public void marcarRecursoProcessado(String fonteId, String chave) {
        try {
            db.collection("processedResources").document(Texts.sha256(fonteId + "|" + chave))
                    .set(Map.of("fonteId", fonteId, "chave", chave, "em", Instant.now().toString())).get();
        } catch (Exception e) {
            log.warn("Falha ao marcar recurso processado: {}", e.getMessage());
        }
    }

    private void salvar(String fonteId, Map<String, Object> dados) {
        try {
            db.collection("sources").document(fonteId).set(dados, SetOptions.merge()).get();
        } catch (Exception e) {
            log.warn("Falha ao salvar estado da fonte {}: {}", fonteId, e.getMessage());
        }
    }
}
