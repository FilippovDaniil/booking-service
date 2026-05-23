package com.booking.config;

import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 * Вариант B: @ConditionalOnProperty — OpenSearch отключается через opensearch.enabled=false.
 * Используется в тестах (application-test.yml: opensearch.enabled: false).
 * Когда бин не создаётся — Spring инжектирует null в @Autowired(required=false) поля.
 */
@Configuration
@ConditionalOnProperty(name = "opensearch.enabled", havingValue = "true", matchIfMissing = true)
public class OpenSearchConfig {

    // Один параметр вместо трёх (host+port+scheme) — проще в Docker и K8s
    @Value("${opensearch.url:http://localhost:9200}")
    private String opensearchUrl;

    @Bean
    public OpenSearchClient openSearchClient() {
        URI uri = URI.create(opensearchUrl);
        String scheme = uri.getScheme() == null ? "http"      : uri.getScheme();
        String host   = uri.getHost()   == null ? "localhost" : uri.getHost();
        int    port   = uri.getPort()   == -1   ? 9200        : uri.getPort();

        // ⚠️ httpclient5: порядок параметров HttpHost — (scheme, host, port)
        // В httpclient4 было: (host, port, scheme) — легко перепутать
        HttpHost httpHost = new HttpHost(scheme, host, port);
        OpenSearchTransport transport = ApacheHttpClient5TransportBuilder
                .builder(httpHost)
                .setMapper(new JacksonJsonpMapper())
                .build();
        return new OpenSearchClient(transport);
    }
}
