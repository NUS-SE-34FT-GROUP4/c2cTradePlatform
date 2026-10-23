package sg.edu.nus.iss.c2csectrade.config;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.elasticsearch.RestClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Bounds how long a search can wait for Elasticsearch.
 *
 * The REST client defaults to 10 connections per host and waits for a free one
 * without limit; the socket timeout only starts once a connection is leased. Under
 * load, ranking requests therefore queued behind each other for seconds. MySQL
 * already decides membership and has its own relevance order, so a search that
 * cannot get a connection quickly falls back to that order instead of waiting.
 */
@Configuration
public class ElasticsearchClientConfig {

    @Bean
    public RestClientBuilderCustomizer boundedElasticsearchWait(
            @Value("${app.search.elasticsearch.max-connections:50}") int maxConnections,
            @Value("${app.search.elasticsearch.pool-wait:250ms}") Duration poolWait) {
        return new RestClientBuilderCustomizer() {
            @Override
            public void customize(RestClientBuilder builder) {
                // Timeouts and pool size are set on the two builders below.
            }

            @Override
            public void customize(HttpAsyncClientBuilder builder) {
                builder.setMaxConnTotal(maxConnections).setMaxConnPerRoute(maxConnections);
            }

            @Override
            public void customize(RequestConfig.Builder builder) {
                builder.setConnectionRequestTimeout((int) poolWait.toMillis());
            }
        };
    }
}
