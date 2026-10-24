package com.energymonitor.device.infrastructure;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the calls between this service and energy-monitor-backend ({@code internal-api.*}).
 *
 * @param token           the secret the services share; sent as {@code X-Internal-Token} on every
 *                        internal call, and required on every inbound one. Empty refuses every
 *                        inbound internal call
 * @param allowedNetworks the networks an inbound internal call may come from: loopback and the
 *                        private ranges Docker uses
 * @param backendUrl      where energy-monitor-backend answers its internal endpoints
 * @param connectTimeout  how long to wait for the connection to energy-monitor-backend
 * @param readTimeout     how long to wait for its answer
 */
@ConfigurationProperties("internal-api")
public record InternalApiProperties(
        String token,
        @DefaultValue({"127.0.0.1/32", "::1/128", "172.16.0.0/12"}) List<String> allowedNetworks,
        @DefaultValue("http://localhost:8083") URI backendUrl,
        @DefaultValue("PT1S") Duration connectTimeout,
        @DefaultValue("PT2S") Duration readTimeout) {

    /** The header carrying {@link #token()}. */
    public static final String TOKEN_HEADER = "X-Internal-Token";
}
