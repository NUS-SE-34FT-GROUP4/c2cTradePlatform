package sg.edu.nus.iss.c2csectrade.config;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.elasticsearch.RestClientBuilderCustomizer;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ElasticsearchClientConfigTest {

    private final RestClientBuilderCustomizer customizer =
            new ElasticsearchClientConfig().boundedElasticsearchWait(50, Duration.ofMillis(250));

    @Test
    void waitForAPooledConnectionIsBounded() {
        RequestConfig.Builder builder = RequestConfig.custom();
        customizer.customize(builder);
        // The client default is -1: wait for a free connection forever.
        assertEquals(250, builder.build().getConnectionRequestTimeout());
    }

    @Test
    void poolIsLargerThanTheClientDefaultOfTenPerHost() {
        HttpAsyncClientBuilder builder = mock(HttpAsyncClientBuilder.class);
        when(builder.setMaxConnTotal(50)).thenReturn(builder);
        customizer.customize(builder);
        verify(builder).setMaxConnTotal(50);
        verify(builder).setMaxConnPerRoute(50);
    }
}
