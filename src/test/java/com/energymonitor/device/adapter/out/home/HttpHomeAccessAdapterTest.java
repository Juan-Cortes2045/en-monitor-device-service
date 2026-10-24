package com.energymonitor.device.adapter.out.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.energymonitor.device.application.exception.HomeServiceUnavailableException;
import com.energymonitor.device.application.port.out.HomeLookupPort.HomeAccess;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("Home access over HTTP")
class HttpHomeAccessAdapterTest {

    private static final String ACCESS = "http://backend/internal/homes/hom0000001/access?userId=use0000001";

    private final RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://backend")
            .defaultHeader("X-Internal-Token", "secret");
    private final MockRestServiceServer backend = MockRestServiceServer.bindTo(builder).build();
    private final HttpHomeAccessAdapter homes = new HttpHomeAccessAdapter(builder.build());

    private static String body(boolean exists, boolean member, boolean owner) {
        return "{\"exists\":" + exists + ",\"member\":" + member + ",\"owner\":" + owner + "}";
    }

    @Test
    @DisplayName("an owner's answer comes back as such, with the internal token")
    void owner() {
        backend.expect(requestTo(ACCESS)).andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", "secret"))
                .andRespond(withSuccess(body(true, true, true), MediaType.APPLICATION_JSON));

        assertThat(homes.access("use0000001", "hom0000001")).isEqualTo(new HomeAccess(true, true, true));
        backend.verify();
    }

    @Test
    @DisplayName("a member is not an owner")
    void member() {
        backend.expect(requestTo(ACCESS))
                .andRespond(withSuccess(body(true, true, false), MediaType.APPLICATION_JSON));

        assertThat(homes.access("use0000001", "hom0000001")).isEqualTo(new HomeAccess(true, true, false));
    }

    @Test
    @DisplayName("no right is kept on a home that does not exist")
    void missingHome() {
        backend.expect(requestTo(ACCESS))
                .andRespond(withSuccess(body(false, true, true), MediaType.APPLICATION_JSON));

        assertThat(homes.access("use0000001", "hom0000001")).isEqualTo(HomeAccess.NONE);
    }

    @Test
    @DisplayName("no caller or no home asks nothing")
    void nothingToAsk() {
        assertThat(homes.access(null, "hom0000001")).isEqualTo(HomeAccess.NONE);
        assertThat(homes.access("use0000001", null)).isEqualTo(HomeAccess.NONE);
        backend.verify();
    }

    @Test
    @DisplayName("a refused token is no answer")
    void forbidden() {
        backend.expect(requestTo(ACCESS)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> homes.access("use0000001", "hom0000001"))
                .isInstanceOf(HomeServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a server error is no answer")
    void serverError() {
        backend.expect(requestTo(ACCESS)).andRespond(withServerError());

        assertThatThrownBy(() -> homes.access("use0000001", "hom0000001"))
                .isInstanceOf(HomeServiceUnavailableException.class);
    }

    @Test
    @DisplayName("an unreachable backend is no answer")
    void unreachable() {
        backend.expect(requestTo(ACCESS)).andRespond(withException(new IOException("refused")));

        assertThatThrownBy(() -> homes.access("use0000001", "hom0000001"))
                .isInstanceOf(HomeServiceUnavailableException.class);
    }
}
