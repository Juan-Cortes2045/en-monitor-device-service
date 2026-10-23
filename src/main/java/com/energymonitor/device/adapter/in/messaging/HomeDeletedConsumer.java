package com.energymonitor.device.adapter.in.messaging;

import com.energymonitor.device.application.port.in.UnlinkHomeDevices;
import com.energymonitor.device.infrastructure.HomeEventsMessagingConfiguration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Unlinks the modules of a home energy-monitor-backend deleted.
 *
 * <p>The deletion is already committed when the message arrives, so the unlinking runs in a
 * transaction of its own ({@code HomeDeviceService} is transactional), and the MQTT unlink command
 * goes to each module after it commits. It is idempotent: a second delivery finds no module left
 * linked to the home and does nothing.
 */
@Component
public class HomeDeletedConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(HomeDeletedConsumer.class);

    private final UnlinkHomeDevices unlinkHomeDevices;
    private final ObjectMapper json;

    public HomeDeletedConsumer(UnlinkHomeDevices unlinkHomeDevices, ObjectMapper json) {
        this.unlinkHomeDevices = unlinkHomeDevices;
        this.json = json;
    }

    @RabbitListener(queues = HomeEventsMessagingConfiguration.HOME_DELETED_QUEUE)
    public void on(Message message) {
        HomeDeletedMessage event = json.readValue(message.getBody(), HomeDeletedMessage.class);
        if (event.homeId() == null || event.homeId().isBlank()) {
            // Retrying cannot fix it: dropped, not dead-lettered.
            LOG.warn("Ignoring home.deleted {} without a homeId", message.getMessageProperties().getMessageId());
            return;
        }
        int unlinked = unlinkHomeDevices.unlinkAll(event.homeId());
        if (unlinked > 0) {
            LOG.info("Unlinked {} module(s) of deleted home {}", unlinked, event.homeId());
        }
    }

    /** Body of {@code home.deleted}. */
    record HomeDeletedMessage(String homeId, String deletedBy, Instant deletedAt) {
    }
}
