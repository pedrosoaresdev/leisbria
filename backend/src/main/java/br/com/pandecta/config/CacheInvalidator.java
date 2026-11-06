package br.com.pandecta.config;

import br.com.pandecta.connectors.SyncListener;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/** Limpa caches de leitura após cada sincronização para que dados novos apareçam imediatamente. */
@Component
public class CacheInvalidator implements SyncListener {
    private final CacheManager caches;

    public CacheInvalidator(CacheManager caches) {
        this.caches = caches;
    }

    @Override
    public void aposSincronizacao() {
        for (String nome : new String[]{"inicio", "norma"}) {
            var c = caches.getCache(nome);
            if (c != null) c.clear();
        }
    }
}
