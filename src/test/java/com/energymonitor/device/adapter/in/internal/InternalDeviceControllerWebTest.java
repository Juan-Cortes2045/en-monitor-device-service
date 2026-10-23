package com.energymonitor.device.adapter.in.internal;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.energymonitor.device.application.port.in.QueryDevices;
import com.energymonitor.device.application.result.DeviceSummary;
import com.energymonitor.device.infrastructure.JwtKeyedTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The internal device API energy-monitor-backend reads: who may call it, and what it answers. The
 * token is the one of the test profile; MockMvc requests come from 127.0.0.1.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Internal device API")
class InternalDeviceControllerWebTest extends JwtKeyedTest {

    private static final String DEVICE = "/internal/devices/DEV0000001";
    private static final String HOME_DEVICES = "/internal/homes/hom0000001/devices";
    private static final String TOKEN = "test-only-internal-token";
    private static final DeviceSummary FRIDGE = new DeviceSummary("DEV0000001", "Nevera", "appl000001",
            "Cocina", null, Instant.parse("2026-10-01T10:00:00Z"), "EM204", "hom0000001");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private QueryDevices queryDevices;

    @Test
    @DisplayName("a device with its home")
    void device() throws Exception {
        given(queryDevices.find("DEV0000001")).willReturn(Optional.of(FRIDGE));

        mvc.perform(trusted(get(DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDevice").value("DEV0000001"))
                .andExpect(jsonPath("$.name").value("Nevera"))
                .andExpect(jsonPath("$.homeId").value("hom0000001"))
                .andExpect(jsonPath("$.installationDate").value("2026-10-01T10:00:00Z"));
    }

    @Test
    @DisplayName("404 for a device that does not exist")
    void unknownDevice() throws Exception {
        given(queryDevices.find("DEV0000001")).willReturn(Optional.empty());

        mvc.perform(trusted(get(DEVICE))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the devices of a home, and an empty list for a home without any")
    void devicesOfHome() throws Exception {
        given(queryDevices.listByHome("hom0000001")).willReturn(List.of(FRIDGE));
        given(queryDevices.listByHome("hom0000002")).willReturn(List.of());

        mvc.perform(trusted(get(HOME_DEVICES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idDevice").value("DEV0000001"));
        mvc.perform(trusted(get("/internal/homes/hom0000002/devices")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("refused without the token, with a wrong one or with a user's access token")
    void untrustedCallers() throws Exception {
        mvc.perform(get(DEVICE)).andExpect(status().isForbidden());
        mvc.perform(get(HOME_DEVICES).header("X-Internal-Token", "guess")).andExpect(status().isForbidden());
        mvc.perform(get(DEVICE).header("Authorization", bearer("use0000001"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("refused from outside the allowed networks, even with the token")
    void outsideNetwork() throws Exception {
        mvc.perform(trusted(get(DEVICE)).with(request -> {
                    request.setRemoteAddr("203.0.113.7");
                    return request;
                }))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the broker endpoints keep their own chain: no token is asked")
    void brokerChainUntouched() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/internal/mqtt/auth/user").contentType("application/json")
                        .content("{\"username\":\"nobody\",\"password\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder trusted(MockHttpServletRequestBuilder request) {
        return request.header("X-Internal-Token", TOKEN);
    }
}
