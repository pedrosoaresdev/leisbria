package br.com.pandecta.legislation;

import br.com.pandecta.common.Texts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Catálogo das normas com texto integral (fonte: Planalto). Também resolve siglas (CF, CPP...) em ids. */
@Component
public class NormaCatalog {

    public record Entrada(String id, String tipo, String numero, int ano, String titulo, List<String> siglas,
                          String apelido, String dataPublicacao, String url, boolean temAdct) {}

    private final List<Entrada> entradas = new ArrayList<>();
    private final Map<String, Entrada> porId = new HashMap<>();
    private final Map<String, Entrada> porSigla = new HashMap<>();
    private final Map<String, Entrada> porNumeroAno = new HashMap<>();

    public NormaCatalog(ObjectMapper mapper) throws IOException {
        try (InputStream in = new ClassPathResource("sources/catalogo-normas.json").getInputStream()) {
            JsonNode root = mapper.readTree(in);
            for (JsonNode n : root.path("normas")) {
                List<String> siglas = new ArrayList<>();
                n.path("siglas").forEach(s -> siglas.add(s.asText()));
                Entrada e = new Entrada(n.path("id").asText(), n.path("tipo").asText(), n.path("numero").asText(),
                        n.path("ano").asInt(), n.path("titulo").asText(), siglas, n.path("apelido").asText(),
                        n.path("dataPublicacao").asText(null), n.path("url").asText(), n.path("temAdct").asBoolean(false));
                entradas.add(e);
                porId.put(e.id(), e);
                for (String s : siglas) porSigla.put(Texts.fold(s), e);
                porSigla.put(Texts.fold(e.apelido()), e);
                if (!e.numero().isBlank()) porNumeroAno.put(e.numero() + "/" + e.ano(), e);
            }
        }
        // Atalhos comuns usados na escrita jurídica
        alias("constituicao", "cf1988");
        alias("constituicao federal", "cf1988");
        alias("crfb/88", "cf1988");
        alias("codigo civil", "cc2002");
        alias("codigo penal", "cp1940");
        alias("codigo de processo civil", "cpc2015");
        alias("codigo de processo penal", "cpp1941");
    }

    private void alias(String chave, String id) {
        Entrada e = porId.get(id);
        if (e != null) porSigla.put(chave, e);
    }

    public List<Entrada> todas() {
        return List.copyOf(entradas);
    }

    public Optional<Entrada> porId(String id) {
        return Optional.ofNullable(porId.get(id));
    }

    public Optional<Entrada> porSigla(String sigla) {
        if (sigla == null) return Optional.empty();
        return Optional.ofNullable(porSigla.get(Texts.fold(sigla).trim()));
    }

    public Optional<Entrada> porNumeroAno(String numero, int ano) {
        return Optional.ofNullable(porNumeroAno.get(numero.replace(".", "") + "/" + ano));
    }

    /** Todas as chaves de sigla conhecidas (para o parser de citações). */
    public List<String> chavesSigla() {
        return porSigla.keySet().stream().sorted((a, b) -> b.length() - a.length()).toList();
    }
}
