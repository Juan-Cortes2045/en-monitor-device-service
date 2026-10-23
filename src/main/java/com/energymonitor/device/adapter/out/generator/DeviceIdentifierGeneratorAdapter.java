package com.energymonitor.device.adapter.out.generator;

import com.energymonitor.device.application.port.out.IdentifierGeneratorPort;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Identifier generator for device aggregates.
 */
@Component
public class DeviceIdentifierGeneratorAdapter implements IdentifierGeneratorPort {

    private static final String DEVICE_PREFIX = "dev";
    private static final String STATUS_PREFIX = "dss";
    private static final String MESSAGE_PREFIX = "dmo";
    private static final int ID_LENGTH = 10;
    private final SecureRandom random = new SecureRandom();

    @Override
    public String nextDeviceId() {
        return generate(DEVICE_PREFIX);
    }

    @Override
    public String nextDeviceStatusLogId() {
        return generate(STATUS_PREFIX);
    }

    /**
     * The id of an outbox message, also its message id on the broker. Only the outbox adapter
     * needs it, so it is not part of {@link IdentifierGeneratorPort}.
     */
    public String nextMessageId() {
        return generate(MESSAGE_PREFIX);
    }

    @Override
    public String nextApiKey() {
        // 32 random bytes as hex: 256 bits, well inside VARCHAR(100) and safe for MQTT.
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String generate(String prefix) {
        StringBuilder sb = new StringBuilder(prefix.toLowerCase(Locale.ROOT));
        while (sb.length() < ID_LENGTH) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
