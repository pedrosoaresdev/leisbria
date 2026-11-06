package br.com.pandecta.search;

import br.com.pandecta.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Migrations" do motor de busca. Para cada arquivo opensearch/{nome}.v{N}.json, garante que o índice
 * físico {prefixo}_{nome}_v{N} existe e que o alias {prefixo}_{nome} aponta para a maior versão.
 * Quando uma nova versão é criada e existe uma anterior, os dados são copiados com _reindex.
 */
@Component
public class IndexMigrator {
    private static final Logger log = LoggerFactory.getLogger(IndexMigrator.class);
    private static final Pattern FILE = Pattern.compile("([a-z_]+)\\.v(\\d+)\\.json");

    private final SearchClient client;
    private final String prefix;
    private volatile boolean pronto = false;

    public IndexMigrator(SearchClient client, AppProperties props) {
        this.client = client;
        this.prefix = props.search().indexPrefix();
    }

    public boolean pronto() {
        return pronto;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(0)
    public void migrar() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:opensearch/*.json");
            Map<String, Integer> maiorVersao = new HashMap<>();
            Map<String, Resource> arquivo = new HashMap<>();
            for (Resource r : resources) {
                Matcher m = FILE.matcher(r.getFilename() == null ? "" : r.getFilename());
                if (!m.matches()) continue;
                int v = Integer.parseInt(m.group(2));
                if (v > maiorVersao.getOrDefault(m.group(1), 0)) {
                    maiorVersao.put(m.group(1), v);
                    arquivo.put(m.group(1), r);
                }
            }
            for (var e : maiorVersao.entrySet()) {
                aplicar(e.getKey(), e.getValue(), arquivo.get(e.getKey()));
            }
            pronto = true;
            log.info("Índices verificados: {}", maiorVersao);
        } catch (Exception e) {
            log.error("Não foi possível preparar os índices. Verifique OPENSEARCH_URL. Detalhe: {}", e.getMessage());
        }
    }

    private void aplicar(String nome, int versao, Resource def) throws IOException {
        String alias = prefix + "_" + nome;
        String fisico = alias + "_v" + versao;
        if (client.call("HEAD", "/" + fisico, null).status() == 404) {
            JsonNode body;
            try (InputStream in = def.getInputStream()) {
                body = client.mapper().readTree(in);
            }
            var r = client.call("PUT", "/" + fisico, body);
            if (!r.ok()) throw new IOException("Falha ao criar " + fisico + ": " + r.body());
            log.info("Índice criado: {}", fisico);
        }
        // Alias atual
        var atual = client.call("GET", "/_alias/" + alias, null);
        ObjectNode acoes = client.mapper().createObjectNode();
        ArrayNode actions = acoes.putArray("actions");
        boolean jaAponta = false;
        String anterior = null;
        if (atual.ok()) {
            Iterator<String> it = atual.body().fieldNames();
            while (it.hasNext()) {
                String idx = it.next();
                if (idx.equals(fisico)) jaAponta = true;
                else {
                    anterior = idx;
                    actions.addObject().putObject("remove").put("index", idx).put("alias", alias);
                }
            }
        }
        if (jaAponta && actions.isEmpty()) return;
        if (anterior != null) {
            ObjectNode reindex = client.mapper().createObjectNode();
            reindex.putObject("source").put("index", anterior);
            reindex.putObject("dest").put("index", fisico);
            log.info("Reindexando {} -> {}", anterior, fisico);
            client.call("POST", "/_reindex?wait_for_completion=true", reindex);
        }
        if (!jaAponta) actions.addObject().putObject("add").put("index", fisico).put("alias", alias);
        var r = client.call("POST", "/_aliases", acoes);
        if (!r.ok()) throw new IOException("Falha ao atualizar alias " + alias + ": " + r.body());
        log.info("Alias {} -> {}", alias, fisico);
    }
}
