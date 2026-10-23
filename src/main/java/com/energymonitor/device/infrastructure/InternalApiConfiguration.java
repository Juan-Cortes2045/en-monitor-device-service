package com.energymonitor.device.infrastructure;

import com.energymonitor.device.adapter.out.home.HttpHomeAccessAdapter;
import com.energymonitor.device.application.port.out.HomeLookupPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The calls from this service to energy-monitor-backend: whether a caller belongs to or owns a
 * home. They carry the shared token, which the backend checks together with the caller's network.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InternalApiProperties.class)
public class InternalApiConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(InternalApiConfiguration.class);

    @Bean
    public HomeLookupPort homeLookup(InternalApiProperties properties) {
        if (properties.token() == null || properties.token().isBlank()) {
            LOG.warn("internal-api.token is empty: energy-monitor-backend will refuse every home check");
        }
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(properties.connectTimeout());
        requests.setReadTimeout(properties.readTimeout());
        RestClient client = RestClient.builder()
                .baseUrl(properties.backendUrl().toString())
                .requestFactory(requests)
                .defaultHeader(InternalApiProperties.TOKEN_HEADER,
                        properties.token() == null ? "" : properties.token())
                .build();
        return new HttpHomeAccessAdapter(client);
    }
}
