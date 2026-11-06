package br.com.pandecta.search;

import br.com.pandecta.config.AppProperties;
import org.springframework.stereotype.Component;

/** Nomes (aliases) dos índices. Cada alias aponta para a versão física mais recente (ex.: pandecta_normas_v1). */
@Component
public class Indices {
    public static final String NORMAS = "normas";
    public static final String DISPOSITIVOS = "dispositivos";
    public static final String JURISPRUDENCIA = "jurisprudencia";
    public static final String RADAR = "radar";
    public static final String PROCESSOS = "processos";

    private final String prefix;

    public Indices(AppProperties props) {
        this.prefix = props.search().indexPrefix();
    }

    public String alias(String logical) {
        return prefix + "_" + logical;
    }

    public String normas() { return alias(NORMAS); }
    public String dispositivos() { return alias(DISPOSITIVOS); }
    public String jurisprudencia() { return alias(JURISPRUDENCIA); }
    public String radar() { return alias(RADAR); }
    public String processos() { return alias(PROCESSOS); }
}
