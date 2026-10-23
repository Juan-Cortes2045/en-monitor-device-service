package com.energymonitor.device.adapter.out.event;

import com.energymonitor.device.adapter.out.messaging.DeviceMessagePayloads.ConnectivityPayload;
import com.energymonitor.device.adapter.out.messaging.DeviceMessagePayloads.DeviceLinkedPayload;
import com.energymonitor.device.adapter.out.messaging.DeviceMessagePayloads.DeviceUnlinkedPayload;
import com.energymonitor.device.adapter.out.messaging.DeviceMessageTypes;
import com.energymonitor.device.adapter.out.messaging.DeviceOutboxWriter;
import com.energymonitor.device.api.DeviceConnectivityLost;
import com.energymonitor.device.api.DeviceConnectivityRestored;
import com.energymonitor.device.api.DeviceLinked;
import com.energymonitor.device.api.DeviceUnlinked;
import com.energymonitor.device.application.port.out.DeviceEventPort;
import com.energymonitor.device.application.port.out.DeviceHomePersistencePort;
import com.energymonitor.device.domain.model.DeviceHome;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes device events to the Spring application context, and to the outbox for the other
 * services.
 *
 * <p>In memory they drive the MQTT commands to the modules after commit; through the broker
 * energy-monitor-backend raises and closes the connectivity alerts and learns about links. The
 * use cases publish them inside their transaction, so the outbox row commits with the change.
 */
@Component
public class DeviceEventAdapter implements DeviceEventPort {

    private final ApplicationEventPublisher publisher;
    private final DeviceOutboxWriter outbox;
    private final DeviceHomePersistencePort deviceHomes;

    public DeviceEventAdapter(ApplicationEventPublisher publisher, DeviceOutboxWriter outbox,
                              DeviceHomePersistencePort deviceHomes) {
        this.publisher = publisher;
        this.outbox = outbox;
        this.deviceHomes = deviceHomes;
    }

    @Override
    public void publish(DeviceConnectivityLost event) {
        publisher.publishEvent(event);
        outbox.write(DeviceMessageTypes.CONNECTIVITY_LOST,
                new ConnectivityPayload(event.deviceId(), homeOf(event.deviceId()), event.occurredAt()),
                event.occurredAt());
    }

    @Override
    public void publish(DeviceConnectivityRestored event) {
        publisher.publishEvent(event);
        outbox.write(DeviceMessageTypes.CONNECTIVITY_RESTORED,
                new ConnectivityPayload(event.deviceId(), homeOf(event.deviceId()), event.occurredAt()),
                event.occurredAt());
    }

    @Override
    public void publish(DeviceLinked event) {
        publisher.publishEvent(event);
        outbox.write(DeviceMessageTypes.DEVICE_LINKED,
                new DeviceLinkedPayload(event.deviceId(), event.homeId(), event.deviceName(),
                        event.linkedBy(), event.occurredAt()),
                event.occurredAt());
    }

    @Override
    public void publish(DeviceUnlinked event) {
        publisher.publishEvent(event);
        outbox.write(DeviceMessageTypes.DEVICE_UNLINKED,
                new DeviceUnlinkedPayload(event.deviceId(), event.homeId(), event.occurredAt()),
                event.occurredAt());
    }

    private String homeOf(String deviceId) {
        return deviceHomes.findByDeviceId(deviceId).map(DeviceHome::homeId).orElse(null);
    }
}
