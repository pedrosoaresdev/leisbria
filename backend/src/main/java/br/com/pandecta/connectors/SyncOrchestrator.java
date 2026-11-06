package br.com.pandecta.connectors;

import br.com.pandecta.config.AppProperties;
import br.com.pandecta.search.IndexMigrator;
import br.com.pandecta.search.SearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Agenda e executa as sincronizações. Cada fonte roda isolada, com trava própria (não executa duas
 * vezes em paralelo), registro de início/fim e histórico no Firestore.
 */
@Service
public class SyncOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(SyncOrchestrator.class);

    private final Map<String, SourceConnector> conectores = new LinkedHashMap<>();
    private final Map<String, Boolean> emExecucao = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "sync");
        t.setDaemon(true);
        return t;
    });
    private final SourceStatusRepository status;
    private final SearchClient search;
    private final IndexMigrator migrator;
    private final AppProperties props;
    private final List<Runnable> aposSync;

    public SyncOrchestrator(List<SourceConnector> lista, SourceStatusRepository status, SearchClient search,
                            IndexMigrator migrator, AppProperties props, List<SyncListener> listeners) {
        lista.forEach(c -> conectores.put(c.id(), c));
        this.status = status;
        this.search = search;
        this.migrator = migrator;
        this.props = props;
        this.aposSync = listeners.stream().<Runnable>map(l -> l::aposSincronizacao).toList();
    }

    public Map<String, SourceConnector> conectores() {
        return conectores;
    }

    public Optional<SourceConnector> conector(String id) {
        return Optional.ofNullable(conectores.get(id));
    }

    public boolean emExecucao(String id) {
        return emExecucao.getOrDefault(id, false);
    }

    /** Dispara em segundo plano; retorna false se a fonte já estiver sincronizando ou não existir. */
    public boolean disparar(String id) {
        SourceConnector c = conectores.get(id);
        if (c == null || emExecucao.putIfAbsent(id, true) != null) return false;
        executor.submit(() -> executar(c));
        return true;
    }

    private void executar(SourceConnector c) {
        String inicio = Instant.now().toString();
        SyncResult r = new SyncResult();
        Throwable falha = null;
        try {
            if (!c.configurado()) {
                log.info("Fonte {} não configurada; sincronização ignorada.", c.id());
                return;
            }
            if (!migrator.pronto()) {
                log.warn("Índices não preparados; sincronização de {} adiada.", c.id());
                return;
            }
            status.marcarInicio(c.id(), c.nome());
            SyncContext ctx = new SyncContext(c.id(), status.cursor(c.id()), search, status, r);
            log.info("Sincronizando {}…", c.id());
            c.sincronizar(ctx);
            log.info("Sincronização de {} concluída: {}", c.id(), r);
        } catch (ConnectorHttp.FonteIndisponivelException e) {
            r.fonteIndisponivel = true;
            falha = e;
            log.warn("Fonte {} indisponível: {}", c.id(), e.getMessage());
        } catch (Throwable e) {
            falha = e;
            log.error("Erro ao sincronizar {}", c.id(), e);
        } finally {
            if (c.configurado() && migrator.pronto()) status.marcarFim(c.id(), r, inicio, falha);
            emExecucao.remove(c.id());
            aposSync.forEach(run -> {
                try { run.run(); } catch (Exception e) { log.warn("Pós-sincronização falhou: {}", e.getMessage()); }
            });
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void aoIniciar() {
        if (props.sync().onStartup()) conectores.keySet().forEach(this::disparar);
    }

    @Scheduled(cron = "${pandecta.sync.planalto-cron}", zone = "America/Sao_Paulo")
    public void agendaPlanalto() { disparar("planalto"); }

    @Scheduled(cron = "${pandecta.sync.lexml-cron}", zone = "America/Sao_Paulo")
    public void agendaLexml() { disparar("lexml"); }

    @Scheduled(cron = "${pandecta.sync.djen-cron}", zone = "America/Sao_Paulo")
    public void agendaDjen() { disparar("djen"); }

    @Scheduled(cron = "${pandecta.sync.datajud-cron}", zone = "America/Sao_Paulo")
    public void agendaDatajud() { disparar("datajud"); }

    @Scheduled(cron = "${pandecta.sync.stj-cron}", zone = "America/Sao_Paulo")
    public void agendaStj() { disparar("stj-dados-abertos"); }
}
