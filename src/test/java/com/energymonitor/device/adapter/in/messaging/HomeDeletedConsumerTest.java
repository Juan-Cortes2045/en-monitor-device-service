package com.energymonitor.device.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.energymonitor.device.application.port.in.UnlinkHomeDevices;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Home deleted consumer")
class HomeDeletedConsumerTest {

    private final UnlinkHomeDevices unlinkHomeDevices = mock(UnlinkHomeDevices.class);
    private final HomeDeletedConsumer consumer =
            new HomeDeletedConsumer(unlinkHomeDevices, JsonMapper.builder().build());

    @Test
    @DisplayName("unlinks the modules of the deleted home")
    void unlinksTheHome() {
        consumer.on(message("{\"homeId\":\"hom0000001\",\"deletedBy\":\"use0000001\","
                + "\"deletedAt\":\"2026-10-23T15:00:00Z\"}"));

        verify(unlinkHomeDevices).unlinkAll("hom0000001");
    }

    @Test
    @DisplayName("a message without a home is dropped")
    void withoutHomeDropped() {
        consumer.on(message("{\"deletedBy\":\"use0000001\"}"));
        consumer.on(message("{\"homeId\":\" \"}"));

        verify(unlinkHomeDevices, never()).unlinkAll(anyString());
    }

    @Test
    @DisplayName("an unreadable body fails, so the broker retries it and then dead-letters it")
    void unreadableBodyFails() {
        assertThatThrownBy(() -> consumer.on(message("{not json")))
                .isInstanceOf(JacksonException.class);
    }

    private static Message message(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setType("home.deleted");
        properties.setMessageId("hmo0000001");
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
