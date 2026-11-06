package br.com.pandecta.connectors;

/** Executado após cada sincronização (ex.: verificação de alertas dos usuários, limpeza de cache). */
public interface SyncListener {
    void aposSincronizacao();
}
