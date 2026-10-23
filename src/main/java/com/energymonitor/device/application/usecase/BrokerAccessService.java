package com.energymonitor.device.application.usecase;

import com.energymonitor.device.application.port.in.AuthorizeBrokerAccess;
import com.energymonitor.device.application.port.out.DevicePersistencePort;
import com.energymonitor.device.domain.model.Device;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Authentication and topic ACL for the MQTT broker.
 *
 * <p>Three kinds of client exist:
 *
 * <ul>
 *   <li><strong>This service</strong>, with the credentials it connects with. It reads the
 *       telemetry and status topics of every device (connectivity) and is the only client that
 *       writes on a device's command topic.</li>
 *   <li><strong>energy-monitor-backend</strong>, with the credentials configured for it. It may
 *       only subscribe to and read the telemetry and status topics of every device (it stores the
 *       measurements); it writes nothing.</li>
 *   <li><strong>A device</strong>, which logs in with its {@code device_code} as username and its
 *       API key as password; only the key's hash is stored ({@code ApiKeyHash}). It may only
 *       publish on its own two topics and read its own command topic, so a device cannot
 *       impersonate another one or read anything else.</li>
 * </ul>
 *
 * <p>Secrets are compared in constant time. A configured client with no password is refused.
 *
 * <p>This service contains no Spring annotations.
 */
public class BrokerAccessService implements AuthorizeBrokerAccess {

    static final String TOPIC_PREFIX = "energy-monitor/devices/";

    /** A concrete device topic or the {@code +} subscription filter the platform clients use. */
    private static final Pattern READ_TOPIC =
            Pattern.compile(Pattern.quote(TOPIC_PREFIX) + "(\\+|[^/+#]+)/(telemetry|status)");

    /** The command topic of one device, which only this service writes. */
    private static final Pattern COMMAND_TOPIC =
            Pattern.compile(Pattern.quote(TOPIC_PREFIX) + "[^/+#]+/command");

    private final DevicePersistencePort devices;
    private final PlatformClient service;
    private final PlatformClient backend;

    /**
     * @param devices          the device registry, source of the device credentials
     * @param serviceUsername  the username this service connects with
     * @param servicePassword  its password
     * @param backendUsername  the username energy-monitor-backend connects with
     * @param backendPassword  its password
     */
    public BrokerAccessService(DevicePersistencePort devices,
                               String serviceUsername, String servicePassword,
                               String backendUsername, String backendPassword) {
        this.devices = devices;
        this.service = new PlatformClient(serviceUsername, servicePassword);
        this.backend = new PlatformClient(backendUsername, backendPassword);
    }

    @Override
    public boolean authenticate(String username, String password) {
        if (isBlank(username) || isBlank(password)) {
            return false;
        }
        if (service.is(username)) {
            return service.accepts(password);
        }
        if (backend.is(username)) {
            return backend.accepts(password);
        }
        return devices.findByDeviceCode(username)
                .map(device -> device.acceptsApiKey(password))
                .orElse(false);
    }

    @Override
    public boolean authorize(String username, String topic, Access access) {
        if (isBlank(username) || isBlank(topic) || access == null) {
            return false;
        }
        if (service.is(username)) {
            if (access == Access.WRITE) {
                return COMMAND_TOPIC.matcher(topic).matches();
            }
            return READ_TOPIC.matcher(topic).matches();
        }
        if (backend.is(username)) {
            return access != Access.WRITE && READ_TOPIC.matcher(topic).matches();
        }
        Optional<Device> device = devices.findByDeviceCode(username);
        if (device.isEmpty()) {
            return false;
        }
        String own = TOPIC_PREFIX + device.get().idDevice() + "/";
        if (access != Access.WRITE) {
            // A module only reads (subscribes to) its own command topic.
            return topic.equals(own + "command");
        }
        return topic.equals(own + "telemetry") || topic.equals(own + "status");
    }

    private static boolean secretEquals(String given, String expected) {
        return MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** A client of the platform whose credentials come from configuration. */
    private record PlatformClient(String username, String password) {

        boolean is(String candidate) {
            return !isBlank(username) && username.equals(candidate);
        }

        boolean accepts(String given) {
            return !isBlank(password) && secretEquals(given, password);
        }
    }
}
