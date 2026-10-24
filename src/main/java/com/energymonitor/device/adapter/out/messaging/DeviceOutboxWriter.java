package com.energymonitor.device.adapter.out.messaging;

import com.energymonitor.device.adapter.out.generator.DeviceIdentifierGeneratorAdapter;
import com.energymonitor.device.adapter.out.persistence.entity.DeviceOutboxEntity;
import com.energymonitor.device.adapter.out.persistence.repository.DeviceOutboxRepository;
import com.energymonitor.device.infrastructure.DeviceMessagingProperties;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes a device message to the outbox.
 *
 * <p>Called from inside the use case's transaction, so the row is committed or rolled back
 * together with the change it announces. Publishing to the broker is left to
 * {@link DeviceOutboxRelay}.
 */
@Component
public class DeviceOutboxWriter {

    private final DeviceOutboxRepository outbox;
    private final DeviceIdentifierGeneratorAdapter identifiers;
    private final ObjectMapper json;
    private final Clock clock;
    private final boolean discardWritten;

    public DeviceOutboxWriter(DeviceOutboxRepository outbox, DeviceIdentifierGeneratorAdapter identifiers,
                              ObjectMapper json, Clock clock, DeviceMessagingProperties properties) {
        this.outbox = outbox;
        this.identifiers = identifiers;
        this.json = json;
        this.clock = clock;
        this.discardWritten = properties.discardWritten();
    }

    /**
     * @param type       the routing key, one of {@link DeviceMessageTypes}
     * @param payload    the body, serialized as JSON
     * @param occurredAt when the change happened
     */
    public void write(String type, Object payload, Instant occurredAt) {
        DeviceOutboxEntity message = new DeviceOutboxEntity();
        message.setIdMessage(identifiers.nextMessageId());
        message.setMessageType(type);
        message.setPayload(json.writeValueAsString(payload));
        message.setOccurredAt(occurredAt);
        if (discardWritten) {
            message.setDiscardedAt(clock.instant());
        }
        outbox.save(message);
    }
}
