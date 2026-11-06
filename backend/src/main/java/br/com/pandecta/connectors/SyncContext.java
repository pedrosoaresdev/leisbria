package br.com.pandecta.connectors;

import br.com.pandecta.search.SearchClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Contexto entregue ao conector: cursor da execução anterior e gravação em lote no índice,
 * com contagem automática no {@link SyncResult}.
 */
public class SyncContext {
    private final Map<String, Object> cursorAnterior;
    private final SearchClient search;
    private final SourceStatusRepository status;
    private final SyncResult result;
    private final String fonteId;

    public SyncContext(String fonteId, Map<String, Object> cursorAnterior, SearchClient search,
                       SourceStatusRepository status, SyncResult result) {
        this.fonteId = fonteId;
        this.cursorAnterior = cursorAnterior == null ? Map.of() : cursorAnterior;
        this.search = search;
        this.status = status;
        this.result = result;
    }

    public Map<String, Object> cursorAnterior() { return cursorAnterior; }
    public SyncResult result() { return result; }
    public String fonteId() { return fonteId; }

    public String cursorString(String chave) {
        Object v = cursorAnterior.get(chave);
        return v == null ? null : String.valueOf(v);
    }

    /** Buffer de gravação: acumula e envia em blocos de 500 documentos. */
    public Batch batch(String index) {
        return new Batch(index);
    }

    /** Controle de recursos já importados (ex.: arquivos). */
    public boolean jaProcessado(String chave) {
        return status.recursoProcessado(fonteId, chave);
    }

    public void marcarProcessado(String chave) {
        status.marcarRecursoProcessado(fonteId, chave);
    }

    public class Batch implements AutoCloseable {
        private final String index;
        private final List<Map.Entry<String, Object>> buffer = new ArrayList<>();

        private Batch(String index) {
            this.index = index;
        }

        public void add(String id, Object doc) {
            buffer.add(Map.entry(id, doc));
            if (buffer.size() >= 500) flush();
        }

        public void flush() {
            if (buffer.isEmpty()) return;
            int ok = search.bulkIndex(index, new ArrayList<>(buffer));
            result.gravados += ok;
            if (ok < buffer.size()) result.erro((buffer.size() - ok) + " documento(s) rejeitado(s) pelo índice " + index);
            buffer.clear();
        }

        @Override
        public void close() {
            flush();
        }
    }
}
