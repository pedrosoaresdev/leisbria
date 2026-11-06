package br.com.pandecta.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class HttpConfig {

    /** Cliente HTTP compartilhado (HTTP/2, segue redirecionamentos seguros, timeout de conexão). */
    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
    }
}
