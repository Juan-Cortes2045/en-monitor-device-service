package com.energymonitor.device.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.energymonitor.device.application.port.in.AuthorizeBrokerAccess;
import com.energymonitor.device.application.port.in.QueryDevices;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The address the service sees behind the gateway. MockMvc skips Tomcat, where the forwarded
 * headers are applied, so these requests go to a running server on a random port; they all come
 * from loopback, a trusted proxy, and differ only in what they forward.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Behind the gateway")
class BehindGatewayTest extends JwtKeyedTest {

    private static final String TOKEN = "test-only-internal-token";

    /**
     * A refusal on a running server: the chain answers 403, and Tomcat's error page for it is
     * then refused by the API chain with 401. Either way nothing reaches the controller.
     */
    private static final Integer[] REFUSED = {401, 403};

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @MockitoBean
    private QueryDevices queryDevices;

    @MockitoBean
    private AuthorizeBrokerAccess authorizeBrokerAccess;

    @BeforeEach
    void noDevice() {
        given(queryDevices.find(any())).willReturn(Optional.empty());
        given(authorizeBrokerAccess.authenticate(any(), any())).willReturn(true);
    }

    @Test
    @DisplayName("a service calling directly reaches the internal API")
    void directInternalCall() throws Exception {
        assertThat(internalCall(null)).isEqualTo(404);
    }

    @Test
    @DisplayName("a caller from the Docker network relayed by a proxy reaches the internal API")
    void relayedFromTheDockerNetwork() throws Exception {
        assertThat(internalCall("172.18.0.5")).isEqualTo(404);
    }

    @Test
    @DisplayName("an outside caller relayed by a proxy counts as itself and is refused")
    void relayedFromOutside() throws Exception {
        assertThat(internalCall("203.0.113.9")).isIn(REFUSED);
        verify(queryDevices, never()).find(any());
    }

    @Test
    @DisplayName("an outside caller cannot pass as loopback by forwarding it")
    void spoofedLoopback() throws Exception {
        // The proxy appends the real address; the rightmost untrusted one is the caller.
        assertThat(internalCall("127.0.0.1, 203.0.113.9")).isIn(REFUSED);
        verify(queryDevices, never()).find(any());
    }

    @Test
    @DisplayName("the broker reaches its authentication directly")
    void brokerDirect() throws Exception {
        assertThat(brokerLogin(null)).isEqualTo(200);
    }

    @Test
    @DisplayName("an outside caller relayed by a proxy never reaches the broker authentication")
    void brokerRelayedFromOutside() throws Exception {
        assertThat(brokerLogin("203.0.113.9")).isIn(REFUSED);
        verify(authorizeBrokerAccess, never()).authenticate(any(), any());
    }

    private int internalCall(String forwardedFor) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(url("/internal/devices/DEV0000001"))
                .header("X-Internal-Token", TOKEN)
                .GET();
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private int brokerLogin(String forwardedFor) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(url("/internal/mqtt/auth/user"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"ABC123\",\"password\":\"key\",\"clientid\":\"ABC123\"}"));
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private URI url(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
