package com.energymonitor.device.adapter.in.mqtt;

import com.energymonitor.device.application.port.in.ReceiveDeviceReport;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import tools.jackson.databind.ObjectMapper;

/**
 * MQTT inbound wiring of the connectivity: one subscription and one channel per topic.
 *
 * <p>Each handler listens on its own channel. A {@link DirectChannel} shared by two subscribers
 * would round-robin the messages between them, so every other telemetry message would reach the
 * status handler.
 *
 * <p>The client ids get a {@code -telemetry} / {@code -status} suffix of this service's
 * {@code mqtt.client-id}. The broker disconnects a client when another one connects with the same
 * id, so they must differ from energy-monitor-backend's, which subscribes to the same topics.
 *
 * <p>{@code mqtt.inbound.enabled=false} turns the subscriptions off; the test profile does, so a
 * test never takes the session of a running service. {@link EnableIntegration} is explicit because
 * the project depends on {@code spring-integration-mqtt} without Spring Boot's integration starter.
 */
@Configuration(proxyBeanMethods = false)
@EnableIntegration
@ConditionalOnProperty(name = "mqtt.inbound.enabled", havingValue = "true", matchIfMissing = true)
public class MqttConnectivityConfiguration {

    static final String TELEMETRY_CHANNEL = "mqttTelemetryChannel";
    static final String STATUS_CHANNEL = "mqttStatusChannel";

    @Value("${mqtt.broker-url:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${mqtt.client-id:device-service}")
    private String clientId;

    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    @Value("${mqtt.topics.telemetry:energy-monitor/devices/+/telemetry}")
    private String telemetryTopic;

    @Value("${mqtt.topics.status:energy-monitor/devices/+/status}")
    private String statusTopic;

    @Value("${mqtt.qos:0}")
    private int qos;

    @Value("${mqtt.keep-alive-interval:60}")
    private int keepAliveInterval;

    @Value("${mqtt.connection-timeout:30}")
    private int connectionTimeout;

    @Value("${mqtt.clean-session:true}")
    private boolean cleanSession;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        if (username != null && !username.isBlank()) {
            options.setUserName(username);
        }
        if (password != null && !password.isBlank()) {
            options.setPassword(password.toCharArray());
        }
        options.setKeepAliveInterval(keepAliveInterval);
        options.setConnectionTimeout(connectionTimeout);
        options.setCleanSession(cleanSession);
        options.setAutomaticReconnect(true);
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean(name = TELEMETRY_CHANNEL)
    public MessageChannel mqttTelemetryChannel() {
        return new DirectChannel();
    }

    @Bean(name = STATUS_CHANNEL)
    public MessageChannel mqttStatusChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer inboundTelemetry(MqttPahoClientFactory factory) {
        return inbound(factory, "-telemetry", telemetryTopic, mqttTelemetryChannel());
    }

    @Bean
    public MessageProducer inboundStatus(MqttPahoClientFactory factory) {
        return inbound(factory, "-status", statusTopic, mqttStatusChannel());
    }

    @Bean
    public ConnectivityMqttConsumer connectivityMqttConsumer(ObjectMapper objectMapper,
                                                             ReceiveDeviceReport reports) {
        return new ConnectivityMqttConsumer(objectMapper, reports);
    }

    private MessageProducer inbound(MqttPahoClientFactory factory, String suffix, String topic,
                                    MessageChannel channel) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId + suffix, factory, topic);
        adapter.setQos(qos);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setOutputChannel(channel);
        return adapter;
    }
}
