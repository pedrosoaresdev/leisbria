package br.com.pandecta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "pandecta")
public record AppProperties(
        List<String> corsAllowedOrigins,
        String frontendPublicUrl,
        List<String> adminUids,
        String userAgent,
        Search search,
        Firebase firebase,
        Ai ai,
        Sync sync,
        DataJud datajud,
        Djen djen,
        Stj stj,
        Lexml lexml) {

    public record Search(String url, String username, String password, String indexPrefix) {}

    public record Firebase(String projectId, String credentialsPath, String webApiKey, String syntheticEmailDomain) {}

    public record Ai(String apiKey, String model, int maxSources) {
        public boolean enabled() { return apiKey != null && !apiKey.isBlank(); }
    }

    public record Sync(boolean onStartup, String planaltoCron, String lexmlCron, String djenCron,
                       String datajudCron, String stjCron) {}

    public record DataJud(String apiKey, String baseUrl, List<String> radarAliases, int maxPerRun) {
        public boolean configured() { return apiKey != null && !apiKey.isBlank(); }
    }

    public record Djen(String baseUrl, List<String> tribunais, List<String> tiposDocumento, int maxPagesPerTribunal) {}

    public record Stj(String ckanUrl, String datasetQuery, int maxResourcesPerRun) {}

    public record Lexml(String sruUrl) {}

    public List<String> adminUidsSafe() {
        return adminUids == null ? List.of() : adminUids.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
